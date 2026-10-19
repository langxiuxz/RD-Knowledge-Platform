package com.studyagent.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyagent.ai.client.AiClient;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.common.result.PageResult;
import com.studyagent.knowledge.service.KnowledgeBaseService;
import com.studyagent.quiz.dto.GenerateQuizRequest;
import com.studyagent.quiz.dto.QuizQuery;
import com.studyagent.quiz.dto.SubmitQuizRequest;
import com.studyagent.quiz.entity.QuizAnswerEntity;
import com.studyagent.quiz.entity.QuizEntity;
import com.studyagent.quiz.entity.QuizQuestionEntity;
import com.studyagent.quiz.mapper.QuizAnswerMapper;
import com.studyagent.quiz.mapper.QuizMapper;
import com.studyagent.quiz.mapper.QuizQuestionMapper;
import com.studyagent.quiz.vo.QuizVOs;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class QuizService {
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final Set<String> TYPES = Set.of("SINGLE", "MULTIPLE", "JUDGE", "SHORT");
    private static final Set<String> STATUSES = Set.of("PROCESSING", "READY", "FAILED", "COMPLETED");
    private final QuizMapper quizMapper;
    private final QuizQuestionMapper questionMapper;
    private final QuizAnswerMapper answerMapper;
    private final QuizGenerationPersistenceService generationPersistence;
    private final QuizGenerationWorker generationWorker;
    private final QuizSubmissionPersistenceService submissionPersistence;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public QuizService(QuizMapper quizMapper, QuizQuestionMapper questionMapper, QuizAnswerMapper answerMapper,
                       QuizGenerationPersistenceService generationPersistence, QuizGenerationWorker generationWorker,
                       QuizSubmissionPersistenceService submissionPersistence, KnowledgeBaseService knowledgeBaseService,
                       AiClient aiClient, ObjectMapper objectMapper) {
        this.quizMapper = quizMapper;
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
        this.generationPersistence = generationPersistence;
        this.generationWorker = generationWorker;
        this.submissionPersistence = submissionPersistence;
        this.knowledgeBaseService = knowledgeBaseService;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public QuizVOs.Generate generate(Long userId, GenerateQuizRequest request) {
        validateGenerate(request);
        if (request.knowledgeBaseId() != null) knowledgeBaseService.requireOwned(userId, request.knowledgeBaseId());
        QuizEntity quiz = generationPersistence.create(userId, request);
        try {
            generationWorker.generate(userId, quiz.getId(), request);
        } catch (TaskRejectedException exception) {
            generationPersistence.fail(userId, quiz.getId(), "AI任务队列已满，请稍后重新生成");
        }
        return new QuizVOs.Generate(quiz.getId(), "PROCESSING");
    }

    public PageResult<QuizVOs.Summary> list(Long userId, QuizQuery query) {
        if (query.status() != null && !STATUSES.contains(query.status())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status不合法");
        }
        long total = quizMapper.countByUserId(userId, query.keyword(), query.status());
        var rows = quizMapper.findPageByUserId(userId, query.keyword(), query.status(),
                        (query.page() - 1) * query.pageSize(), query.pageSize()).stream()
                .map(q -> new QuizVOs.Summary(q.getId(), q.getTitle(), q.getDifficulty(), q.getQuestionCount(), q.getStatus()))
                .toList();
        return new PageResult<>(total, rows);
    }

    public QuizVOs.Detail detail(Long userId, Long id) {
        QuizEntity quiz = requireOwned(userId, id);
        var questions = "READY".equals(quiz.getStatus()) || "COMPLETED".equals(quiz.getStatus())
                ? questionMapper.findByQuizIdAndUserId(id, userId).stream()
                    .map(q -> new QuizVOs.Question(q.getId(), q.getType(), q.getContent(), readJsonValue(q.getOptionsJson())))
                    .toList()
                : List.<QuizVOs.Question>of();
        return new QuizVOs.Detail(id, quiz.getStatus(), quiz.getErrorMessage(), questions);
    }

    public QuizVOs.Submit submit(Long userId, Long id, SubmitQuizRequest request) {
        QuizEntity quiz = requireOwned(userId, id);
        if ("COMPLETED".equals(quiz.getStatus())) throw new BusinessException(ErrorCode.QUIZ_ALREADY_SUBMITTED);
        if (!"READY".equals(quiz.getStatus())) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "测验尚未就绪");
        List<QuizQuestionEntity> questions = questionMapper.findByQuizIdAndUserId(id, userId);
        Map<Long, JsonNode> submitted = new LinkedHashMap<>();
        for (SubmitQuizRequest.Answer answer : request.answers()) {
            if (submitted.put(answer.questionId(), objectMapper.valueToTree(answer.answer())) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "questionId重复");
            }
        }
        if (submitted.size() != questions.size() || questions.stream().anyMatch(q -> !submitted.containsKey(q.getId()))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "必须提交全部题目答案");
        }
        List<QuizSubmissionPersistenceService.GradedAnswer> graded = new ArrayList<>();
        for (QuizQuestionEntity question : questions) {
            JsonNode userAnswer = submitted.get(question.getId());
            boolean correct = "SHORT".equals(question.getType())
                    ? gradeShort(question, userAnswer)
                    : gradeObjective(question, userAnswer);
            graded.add(new QuizSubmissionPersistenceService.GradedAnswer(question, writeJson(userAnswer), correct));
        }
        var result = submissionPersistence.commit(userId, id, graded);
        return new QuizVOs.Submit(result.score(), result.correctCount(), result.wrongCount(), result.analysis());
    }

    public QuizVOs.Result result(Long userId, Long id) {
        QuizEntity quiz = requireOwned(userId, id);
        if (!"COMPLETED".equals(quiz.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "测验尚未提交");
        }
        Map<Long, QuizAnswerEntity> answers = new HashMap<>();
        answerMapper.findByQuizIdAndUserId(id, userId).forEach(answer -> answers.put(answer.getQuestionId(), answer));
        var rows = questionMapper.findByQuizIdAndUserId(id, userId).stream().map(question -> {
            QuizAnswerEntity answer = answers.get(question.getId());
            return new QuizVOs.ResultQuestion(question.getId(), readJsonValue(answer.getUserAnswerJson()),
                    readJsonValue(question.getCorrectAnswerJson()), Boolean.TRUE.equals(answer.getIsCorrect()),
                    question.getExplanation());
        }).toList();
        return new QuizVOs.Result(id, quiz.getScore(), rows);
    }

    private boolean gradeShort(QuizQuestionEntity question, JsonNode userAnswer) {
        if (!userAnswer.isTextual() || userAnswer.asText().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "简答题答案不能为空");
        }
        String reference = readJsonNode(question.getCorrectAnswerJson()).asText();
        try {
            AiClient.ShortGradeResponse grade = aiClient.gradeShort(new AiClient.ShortGradeRequest(
                    question.getContent(), reference, userAnswer.asText()));
            if (grade.score() == null || grade.correct() == null || grade.score() < 0 || grade.score() > 1
                    || grade.correct() != (grade.score() >= 0.6)) {
                throw new IllegalStateException("invalid SHORT grading response");
            }
            return grade.correct();
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "简答题评分失败，请稍后重试");
        }
    }

    private boolean gradeObjective(QuizQuestionEntity question, JsonNode userAnswer) {
        JsonNode correct = readJsonNode(question.getCorrectAnswerJson());
        return switch (question.getType()) {
            case "MULTIPLE" -> normalizedSet(userAnswer).equals(normalizedSet(correct));
            case "SINGLE", "JUDGE" -> scalar(userAnswer).equals(scalar(correct));
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "未知题型");
        };
    }

    private Set<String> normalizedSet(JsonNode node) {
        if (!node.isArray()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "多选题答案必须为数组");
        Set<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        node.forEach(item -> values.add(scalar(item)));
        return values;
    }

    private String scalar(JsonNode node) {
        if (node == null || node.isContainerNode() || node.isNull()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "答案格式不合法");
        }
        return node.asText().trim().toLowerCase(Locale.ROOT);
    }

    private QuizEntity requireOwned(Long userId, Long id) {
        QuizEntity quiz = quizMapper.findByIdAndUserId(id, userId);
        if (quiz == null) throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);
        return quiz;
    }

    private void validateGenerate(GenerateQuizRequest request) {
        if (!DIFFICULTIES.contains(request.difficulty()) || request.types().stream().anyMatch(type -> !TYPES.contains(type))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "difficulty或types不合法");
        }
    }

    private JsonNode readJsonNode(String value) {
        if (value == null) return null;
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
    }

    private Object readJsonValue(String value) {
        if (value == null) return null;
        try { return objectMapper.readValue(value, Object.class); }
        catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
    }
}
