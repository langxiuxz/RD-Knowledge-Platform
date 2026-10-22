package com.studyagent.assessment.service;

import com.studyagent.ai.client.AiClient;
import com.studyagent.assessment.entity.*;
import com.studyagent.assessment.mapper.AssessmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static com.studyagent.assessment.service.AssessmentSupport.invalid;

@Service
public class ProjectQuizGenerationPersistence {
    private final AssessmentMapper mapper;
    private final AssessmentSupport support;
    public ProjectQuizGenerationPersistence(AssessmentMapper mapper,AssessmentSupport support) {this.mapper=mapper;this.support=support;}
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public void complete(Long id,String taskId,AiClient.QuizGenerateResponse response) {
        var initial=mapper.quiz(id);if(initial==null) return;
        support.lock(initial.getCreatedBy(),initial.getProjectId(),true);
        var quiz=mapper.lockQuiz(id);
        if(!"GENERATING".equals(quiz.getStatus()) || !Objects.equals(taskId,quiz.getCurrentTaskId())) return;
        support.snapshot(quiz.getProjectId(),quiz.getKnowledgeSnapshotId(),true);
        if(response==null || response.questions()==null || response.questions().size()!=quiz.getQuestionCount()) throw invalid("生成题量不合法");
        var allowed=new HashSet<>(support.strings(quiz.getTypesJson())); List<AssessmentQuestionEntity> rows=new ArrayList<>();
        Set<Long> sources=new HashSet<>();
        for(var generated:response.questions()) {
            if(generated==null || !allowed.contains(generated.type()) || generated.knowledgeKey()==null ||
                generated.knowledgeKey().isBlank() || generated.knowledgeKey().length()>128) throw invalid("生成题型或知识点不合法");
            var q=new AssessmentQuestionEntity();q.setQuizId(id);q.setType(generated.type());q.setKnowledgeKey(generated.knowledgeKey());
            q.setContent(generated.content());q.setOptionsJson(support.write(generated.options()));q.setCorrectAnswerJson(support.write(generated.answer()));
            q.setExplanation(generated.explanation());q.setSourceRefsJson(support.write(generated.sourceRefs()));q.setMaxScore(BigDecimal.ONE);q.setSortOrder(rows.size());
            QuizScoring.validate(q,support.read(q.getOptionsJson()),support.read(q.getCorrectAnswerJson()));
            var refs=support.read(q.getSourceRefsJson());support.validateSources(quiz.getProjectId(),quiz.getKnowledgeSnapshotId(),refs);
            refs.forEach(r->sources.add(r.get("documentVersionId").longValue()));rows.add(q);
        }
        // Status fence and all questions commit together; a timeout/stale result cannot create partial content.
        if(mapper.updateQuizStatus(id,taskId,"GENERATING","READY",null)!=1) return;
        mapper.insertQuestions(rows);support.depend(quiz.getProjectId(),"QUIZ",id,sources);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void fail(Long id,String taskId) { mapper.updateQuizStatus(id,taskId,"GENERATING","FAILED","测验生成失败，请检查 AI 服务或来源知识后重新生成"); }
}
