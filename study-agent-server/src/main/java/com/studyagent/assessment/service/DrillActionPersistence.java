package com.studyagent.assessment.service;

import com.studyagent.assessment.dto.AssessmentRequests;
import com.studyagent.assessment.entity.*;
import com.studyagent.assessment.mapper.AssessmentMapper;
import com.studyagent.assessment.vo.AssessmentVOs;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static com.studyagent.assessment.service.AssessmentSupport.*;

@Service
public class DrillActionPersistence {
    private final AssessmentMapper mapper;private final AssessmentSupport support;private final AssessmentViews views;
    public DrillActionPersistence(AssessmentMapper mapper,AssessmentSupport support,AssessmentViews views) {this.mapper=mapper;this.support=support;this.views=views;}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AssessmentVOs.ActionAccepted persist(Long userId,Long runId,AssessmentRequests.Action request,
                                                String ruleCode,String source,BigDecimal confidence) {
        var initial=mapper.run(runId);if(initial==null) throw missing();support.lock(userId,initial.getProjectId(),false);
        var run=mapper.runForUpdate(runId);if(!userId.equals(run.getUserId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        var replay=mapper.actionByRequest(runId,request.clientRequestId());
        if(replay!=null) {
            if(!Objects.equals(replay.getActionText(),request.actionText().strip())) throw conflict("clientRequestId 已被不同动作占用");
            return accepted(replay,run.getScenarioId());
        }
        if(!"IN_PROGRESS".equals(run.getStatus())) throw conflict("Drill Run 已结束");
        var rules=mapper.rules(run.getScenarioId());var existing=mapper.actions(runId);int seq=existing.size()+1;
        DrillRuleEntity matched=ruleCode==null?null:rules.stream().filter(r->ruleCode.equals(r.getCode())).findFirst().orElse(null);
        Set<Long> already= new HashSet<>();existing.stream().map(DrillRunActionEntity::getMatchedRuleId).filter(Objects::nonNull).forEach(already::add);
        Set<String> completedCodes=new HashSet<>();
        rules.stream().filter(r->already.contains(r.getId())).map(DrillRuleEntity::getCode).forEach(completedCodes::add);
        if(matched!=null && !DrillRuleLogic.isOrderValid(matched.getSequenceGroup(),matched.getMinOrder(),seq,
                matched.getSortOrder(),rules.stream().map(r->new DrillRuleLogic.OrderedRule(r.getCode(),r.getSequenceGroup(),r.getSortOrder())).toList(),completedCodes)) {
            matched=null;source="NONE";confidence=null;
        }
        BigDecimal awarded=matched!=null&&!already.contains(matched.getId())?matched.getPoints():BigDecimal.ZERO;
        String matchedCode=matched==null?null:matched.getCode();
        List<String> unlocked=matchedCode==null?List.of():mapper.evidence(run.getScenarioId()).stream()
            .filter(e->matchedCode.equals(e.getUnlockRuleCode())).map(DrillEvidenceEntity::getCode).toList();
        var action=new DrillRunActionEntity();action.setRunId(runId);action.setSeqNo(seq);action.setClientRequestId(request.clientRequestId());
        action.setActionText(request.actionText().strip());action.setNormalizedAction(matched==null?null:matched.getCode());action.setMatchedRuleId(matched==null?null:matched.getId());
        action.setMatchSource(source);action.setMatchConfidence(confidence);action.setEvidenceUnlockedJson(support.write(unlocked));
        action.setRiskFlag(matched!=null&&"RISKY".equals(matched.getRuleType()));action.setPointsAwarded(awarded);mapper.insertAction(action);
        support.audit(run.getProjectId(),userId,"DRILL_ACTION","DRILL_RUN",runId);
        return new AssessmentVOs.ActionAccepted(action.getId(),action.getNormalizedAction(),
            mapper.evidence(run.getScenarioId()).stream().filter(e->unlocked.contains(e.getCode())).map(e->views.evidence(e,false)).toList());
    }
    private AssessmentVOs.ActionAccepted accepted(DrillRunActionEntity action,Long scenarioId) {
        var codes=support.strings(action.getEvidenceUnlockedJson());return new AssessmentVOs.ActionAccepted(action.getId(),action.getNormalizedAction(),
            mapper.evidence(scenarioId).stream().filter(e->codes.contains(e.getCode())).map(e->views.evidence(e,false)).toList());
    }
}
