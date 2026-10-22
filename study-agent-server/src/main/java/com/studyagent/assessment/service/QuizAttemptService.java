package com.studyagent.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.studyagent.ai.client.AiClient;
import com.studyagent.assessment.dto.AssessmentRequests;
import com.studyagent.assessment.entity.*;
import com.studyagent.assessment.mapper.AssessmentMapper;
import com.studyagent.assessment.vo.AssessmentVOs;
import com.studyagent.common.exception.*;
import com.studyagent.onboarding.service.AssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import static com.studyagent.assessment.service.AssessmentSupport.*;

@Service
public class QuizAttemptService {
    private final AssessmentMapper mapper;
    private final ProjectQuizService quizzes;
    private final AssessmentSupport support;
    private final AssessmentViews views;
    private final AiClient ai;
    private final AssignmentService assignments;
    public QuizAttemptService(AssessmentMapper mapper,ProjectQuizService quizzes,AssessmentSupport support,
                              AssessmentViews views,AiClient ai,AssignmentService assignments) {
        this.mapper=mapper;this.quizzes=quizzes;this.support=support;this.views=views;this.ai=ai;this.assignments=assignments;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AssessmentVOs.Attempt start(Long userId,Long quizId,AssessmentRequests.Start request) {
        var quiz=quizzes.find(quizId);
        support.lock(userId,quiz.getProjectId(),false);
        quiz=mapper.lockQuiz(quizId);
        if(!"PUBLISHED".equals(quiz.getStatus())) throw conflict("只能开始已发布测验");
        if(request!=null && request.assignmentTaskId()!=null)
            assignments.requireAssessmentTask(userId,request.assignmentTaskId(),"QUIZ",quizId,quiz.getProjectId(),quiz.getKnowledgeSnapshotId());
        var attempt=new QuizAttemptEntity();attempt.setProjectId(quiz.getProjectId());attempt.setQuizId(quizId);attempt.setUserId(userId);
        attempt.setAssignmentTaskId(request==null?null:request.assignmentTaskId());attempt.setStatus("IN_PROGRESS");attempt.setRubricVersion(quiz.getRubricVersion());
        mapper.insertAttempt(attempt);
        support.audit(quiz.getProjectId(),userId,"QUIZ_ATTEMPT_START","QUIZ_ATTEMPT",attempt.getId());
        return view(attempt,quiz,mapper.questions(quizId),List.of(),false);
    }
    public AssessmentVOs.Attempt get(Long userId,Long attemptId) {
        var attempt=find(attemptId);var quiz=quizzes.find(attempt.getQuizId());
        boolean manager=support.manager(userId,quiz.getProjectId());
        if(!manager && !userId.equals(attempt.getUserId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        return view(attempt,quiz,mapper.questions(quiz.getId()),mapper.answers(attemptId),manager||!"IN_PROGRESS".equals(attempt.getStatus()));
    }
    public AssessmentVOs.Attempt submit(Long userId,Long attemptId,AssessmentRequests.SubmitQuiz request) {
        var initial=find(attemptId);var quiz=quizzes.find(initial.getQuizId());
        support.manager(userId,quiz.getProjectId());
        if(!userId.equals(initial.getUserId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if(!"IN_PROGRESS".equals(initial.getStatus())) {
            var old=mapper.answers(attemptId);
            if(samePayload(old,request.answers())) return get(userId,attemptId);
            throw new BusinessException(ErrorCode.QUIZ_ALREADY_SUBMITTED);
        }
        var questions=mapper.questions(quiz.getId());var byId=new HashMap<Long,AssessmentQuestionEntity>();questions.forEach(q->byId.put(q.getId(),q));
        Map<Long,JsonNode> inputs=new LinkedHashMap<>();
        for(var answer:request.answers()) {
            if(!byId.containsKey(answer.questionId()) || inputs.put(answer.questionId(),answer.answer())!=null) throw invalid("答案包含重复或不属于该测验的题目");
            var q=byId.get(answer.questionId());QuizScoring.validateAnswer(q.getType(),support.read(q.getOptionsJson()),answer.answer());
        }
        if(inputs.size()!=questions.size()) throw invalid("必须提交全部题目答案");
        // All external grading finishes before any write. A timeout leaves the attempt retryable and untouched.
        List<AssessmentAnswerEntity> scored=new ArrayList<>();BigDecimal earned=BigDecimal.ZERO,total=BigDecimal.ZERO;int correctCount=0;boolean reviewRequired=false;
        for(var q:questions) {
            JsonNode input=inputs.get(q.getId());var answer=new AssessmentAnswerEntity();answer.setAttemptId(attemptId);answer.setQuestionId(q.getId());
            answer.setUserAnswerJson(support.write(input));total=total.add(q.getMaxScore());
            if("SHORT".equals(q.getType())) {
                reviewRequired=true;var rubric=new HashMap<String,Object>();rubric.put("maxScore",q.getMaxScore());rubric.put("requiredPoints",List.of());
                AiClient.ShortGradeResponse suggestion;
                try { suggestion=ai.gradeShort(new AiClient.ShortGradeRequest(q.getContent(),support.read(q.getCorrectAnswerJson()).asText(),input.asText(),rubric)); }
                catch(RuntimeException ex) { throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,"简答题评分建议暂不可用，未保存本次提交"); }
                if(suggestion==null) throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,"简答题评分建议为空，未保存本次提交");
                BigDecimal score=suggestion.scoreSuggestion()!=null?BigDecimal.valueOf(suggestion.scoreSuggestion()):
                    BigDecimal.valueOf(Objects.requireNonNullElse(suggestion.score(),0d)).multiply(q.getMaxScore());
                if(score.signum()<0 || score.compareTo(q.getMaxScore())>0) throw new BusinessException(ErrorCode.STRUCTURED_OUTPUT_INVALID);
                answer.setAiSuggestedScore(score.setScale(2,RoundingMode.HALF_UP));answer.setFinalScore(null);answer.setIsCorrect(null);
                answer.setScoringSource("AI_SUGGESTION");answer.setAiFeedback(suggestion.feedback());
                answer.setSourceRefsJson(support.write(suggestion.evidence()==null?List.of():suggestion.evidence()));earned=earned.add(score);
            } else {
                boolean right=QuizScoring.correct(q.getType(),support.read(q.getCorrectAnswerJson()),input);
                BigDecimal score=right?q.getMaxScore():BigDecimal.ZERO;answer.setRuleScore(score);answer.setFinalScore(score);
                answer.setIsCorrect(right);answer.setScoringSource("RULE");answer.setSourceRefsJson(q.getSourceRefsJson());earned=earned.add(score);
                if(right) correctCount++;
            }
            scored.add(answer);
        }
        BigDecimal autoScore=QuizScoring.percentage(earned,total);String status=reviewRequired?"REVIEW_PENDING":"COMPLETED";
        boolean passed=!reviewRequired && autoScore.compareTo(quiz.getPassScore())>=0;
        support.lock(userId,quiz.getProjectId(),false);
        var locked=mapper.lockAttempt(attemptId);
        if(!userId.equals(locked.getUserId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if(!"IN_PROGRESS".equals(locked.getStatus())) return get(userId,attemptId);
        mapper.insertAnswers(scored);locked.setStatus(status);locked.setAutoScore(autoScore);locked.setFinalScore(reviewRequired?null:autoScore);
        locked.setCorrectCount(correctCount);locked.setReviewRequired(reviewRequired);locked.setCompletedAt(reviewRequired?null:LocalDateTime.now());
        if(mapper.completeAttempt(locked)!=1) throw new BusinessException(ErrorCode.QUIZ_ALREADY_SUBMITTED);
        if(locked.getAssignmentTaskId()!=null && !reviewRequired)
            assignments.completeFromAssessment(userId,locked.getAssignmentTaskId(),"QUIZ",quiz.getId(),attemptId,quiz.getProjectId(),quiz.getKnowledgeSnapshotId(),passed);
        support.audit(quiz.getProjectId(),userId,"QUIZ_ATTEMPT_SUBMIT","QUIZ_ATTEMPT",attemptId);
        return view(locked,quiz,questions,scored,true);
    }
    private AssessmentVOs.Attempt view(QuizAttemptEntity a,ProjectQuizEntity q,List<AssessmentQuestionEntity> questions,
                                       List<AssessmentAnswerEntity> answers,boolean solutions) {
        return new AssessmentVOs.Attempt(a.getId(),a.getQuizId(),a.getProjectId(),a.getUserId(),a.getAssignmentTaskId(),q.getKnowledgeSnapshotId(),
            a.getStatus(),a.getRubricVersion(),a.getAutoScore(),a.getFinalScore(),a.getReviewRequired(),
            "COMPLETED".equals(a.getStatus())&&a.getFinalScore()!=null&&a.getFinalScore().compareTo(q.getPassScore())>=0,
            questions.stream().map(item->views.question(item,solutions)).toList(),answers.stream().map(views::answer).toList());
    }
    private QuizAttemptEntity find(Long id) {var a=mapper.attempt(id);if(a==null)throw missing();return a;}
    private boolean samePayload(List<AssessmentAnswerEntity> existing,List<AssessmentRequests.Answer> input) {
        if(input==null || existing.size()!=input.size()) return false;
        Map<Long,JsonNode> values=new HashMap<>();for(var item:existing) values.put(item.getQuestionId(),support.read(item.getUserAnswerJson()));
        for(var item:input) if(!Objects.equals(values.get(item.questionId()),item.answer())) return false;
        return true;
    }
}
