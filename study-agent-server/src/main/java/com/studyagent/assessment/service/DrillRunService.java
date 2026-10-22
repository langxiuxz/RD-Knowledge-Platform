package com.studyagent.assessment.service;

import com.studyagent.ai.client.AiClient;
import com.studyagent.assessment.dto.AssessmentRequests;
import com.studyagent.assessment.entity.DrillEvidenceEntity;
import com.studyagent.assessment.entity.DrillRuleEntity;
import com.studyagent.assessment.entity.DrillRunActionEntity;
import com.studyagent.assessment.entity.DrillRunEntity;
import com.studyagent.assessment.mapper.AssessmentMapper;
import com.studyagent.assessment.vo.AssessmentVOs;
import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.onboarding.service.AssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.math.BigDecimal;
import java.util.*;

import static com.studyagent.assessment.service.AssessmentSupport.*;

@Service
public class DrillRunService {
    private final AssessmentMapper mapper;
    private final AssessmentSupport support;
    private final AssessmentViews views;
    private final DrillActionPersistence actionPersistence;
    private final AiClient ai;
    private final AssignmentService assignments;

    public DrillRunService(AssessmentMapper mapper, AssessmentSupport support, AssessmentViews views,
                           DrillActionPersistence actionPersistence, AiClient ai, AssignmentService assignments) {
        this.mapper = mapper;
        this.support = support;
        this.views = views;
        this.actionPersistence = actionPersistence;
        this.ai = ai;
        this.assignments = assignments;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssessmentVOs.RunStarted start(Long userId, Long scenarioId, AssessmentRequests.Start request) {
        var scenario = mapper.scenario(scenarioId);
        if (scenario == null) throw missing();
        support.lock(userId, scenario.getProjectId(), false);
        scenario = mapper.lockScenario(scenarioId);
        if (!"PUBLISHED".equals(scenario.getStatus())) throw conflict("Only published scenarios can be run");
        if (request != null && request.assignmentTaskId() != null)
            assignments.requireAssessmentTask(userId, request.assignmentTaskId(), "DRILL", scenarioId,
                scenario.getProjectId(), scenario.getKnowledgeSnapshotId());
        var run = new DrillRunEntity();
        run.setProjectId(scenario.getProjectId());
        run.setScenarioId(scenarioId);
        run.setUserId(userId);
        run.setAssignmentTaskId(request == null ? null : request.assignmentTaskId());
        run.setRubricVersion(scenario.getRubricVersion());
        run.setStatus("IN_PROGRESS");
        mapper.insertRun(run);
        support.audit(run.getProjectId(), userId, "DRILL_RUN_START", "DRILL_RUN", run.getId());
        var initialEvidence = mapper.evidence(scenarioId).stream().filter(e -> e.getUnlockRuleCode() == null)
            .map(e -> views.evidence(e, false)).toList();
        return new AssessmentVOs.RunStarted(run.getId(), run.getStatus(), scenario.getInitialSymptom(), initialEvidence);
    }

    public AssessmentVOs.ActionAccepted action(Long userId, Long runId, AssessmentRequests.Action request) {
        var run = ownedRun(userId, runId);
        if (!"IN_PROGRESS".equals(run.getStatus())) throw conflict("Drill run is terminal");
        var rules = mapper.rules(run.getScenarioId());
        var alias = DrillRuleLogic.matchAlias(request.actionText(), rules.stream()
            .map(r -> new DrillRuleLogic.Rule(r.getCode(), r.getRuleType(), r.getPoints(), support.strings(r.getAliasesJson())))
            .toList());
        String code = alias == null ? null : alias.code();
        String source = code == null ? "NONE" : "RULE";
        BigDecimal confidence = null;
        if (code == null && !rules.isEmpty()) {
            try {
                var candidates = rules.stream().map(r -> new AiClient.DrillCandidate(r.getCode(), r.getTitle(),
                    support.strings(r.getAliasesJson()))).toList();
                var match = ai.normalizeDrillAction(new AiClient.DrillNormalizeRequest(run.getScenarioId(),
                    request.actionText(), candidates));
                if (match != null && match.matchedRuleCode() != null && rules.stream()
                    .anyMatch(r -> r.getCode().equals(match.matchedRuleCode()))
                    && match.confidence() != null && match.confidence() >= 0 && match.confidence() <= 1) {
                    code = match.matchedRuleCode();
                    source = "AI";
                    confidence = BigDecimal.valueOf(match.confidence()).setScale(4, RoundingMode.HALF_UP);
                }
            } catch (RuntimeException ignored) {
                // AI is optional; failed normalization leaves a valid unmatched action.
            }
        }
        return actionPersistence.persist(userId, runId, request, code, source, confidence);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssessmentVOs.RunSubmitted submit(Long userId, Long runId, AssessmentRequests.Conclusion request) {
        var initial = ownedRun(userId, runId);
        support.lock(userId, initial.getProjectId(), false);
        var run = mapper.lockRun(runId);
        if (!userId.equals(run.getUserId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!"IN_PROGRESS".equals(run.getStatus())) {
            if (Objects.equals(run.getConclusionJson(), support.write(request))) return submitted(run);
            throw conflict("Drill run has already been submitted");
        }
        Set<String> unlocked = new HashSet<>(mapper.unlockedEvidenceCodes(runId));
        if (!unlocked.containsAll(request.evidenceCodes())) throw invalid("Conclusion references evidence not unlocked in this run");

        List<DrillRuleEntity> rules = mapper.rules(run.getScenarioId());
        Set<Long> matchedIds = new HashSet<>();
        mapper.actions(runId).stream().map(DrillRunActionEntity::getMatchedRuleId).filter(Objects::nonNull).forEach(matchedIds::add);
        Set<String> completed = new HashSet<>();
        rules.stream().filter(r -> matchedIds.contains(r.getId())).forEach(r -> completed.add(r.getCode()));
        var scoringRules = rules.stream().map(r -> new DrillRuleLogic.Rule(r.getCode(), r.getRuleType(), r.getPoints(), List.of())).toList();
        BigDecimal score = DrillRuleLogic.score(scoringRules, completed);
        var scenario = mapper.scenario(run.getScenarioId());
        Set<String> requiredRuleCodes = rules.stream()
            .filter(r -> "REQUIRED".equals(r.getRuleType()) || "VALIDATION".equals(r.getRuleType()))
            .map(DrillRuleEntity::getCode).collect(java.util.stream.Collectors.toSet());
        Set<String> requiredEvidenceCodes = mapper.evidence(run.getScenarioId()).stream()
            .filter(e -> requiredRuleCodes.contains(e.getUnlockRuleCode()))
            .map(DrillEvidenceEntity::getCode).collect(java.util.stream.Collectors.toSet());
        Set<String> submittedEvidence = new HashSet<>(request.evidenceCodes());
        boolean evidenceAvailable = !unlocked.isEmpty();
        boolean passed = DrillRuleLogic.meetsPassConditions(scoringRules, completed, requiredEvidenceCodes,
            submittedEvidence, evidenceAvailable, score, scenario.getPassScore());
        run.setProgramScore(score);
        run.setFinalScore(null);
        run.setPassCandidate(passed);
        run.setConclusionJson(support.write(request));
        run.setStatus("REVIEW_PENDING");
        if (mapper.completeRun(run) != 1) throw conflict("Drill run state changed during submission");
        support.audit(run.getProjectId(), userId, "DRILL_RUN_SUBMIT", "DRILL_RUN", runId);
        return submitted(run);
    }

    public AssessmentVOs.Run get(Long userId, Long runId) {
        var run = ownedRun(userId, runId);
        boolean manager = support.manager(userId, run.getProjectId());
        var scenario = mapper.scenario(run.getScenarioId());
        List<DrillRunActionEntity> recorded = mapper.actions(runId);
        if (!manager && !"COMPLETED".equals(run.getStatus())) {
            var visibleActions = recorded.stream().map(a -> new AssessmentVOs.Action(a.getId(), a.getSeqNo(),
                a.getClientRequestId(), a.getActionText(), null, null, null, null, null, evidenceForAction(run, a))).toList();
            return new AssessmentVOs.Run(runId, run.getScenarioId(), run.getProjectId(), run.getUserId(),
                run.getAssignmentTaskId(), scenario.getKnowledgeSnapshotId(), run.getStatus(), run.getRubricVersion(),
                scenario.getInitialSymptom(), visibleActions, currentEvidence(runId), List.<String>of(),
                List.<String>of(), List.<String>of(),
                List.<AssessmentVOs.ScoreItem>of(), null, null, false, null, null, null, null, null);
        }
        List<DrillRuleEntity> rules = mapper.rules(run.getScenarioId());
        Set<Long> matchedIds = new HashSet<>();
        recorded.stream().map(DrillRunActionEntity::getMatchedRuleId).filter(Objects::nonNull).forEach(matchedIds::add);
        Set<String> completed = new HashSet<>();
        rules.stream().filter(r -> matchedIds.contains(r.getId())).forEach(r -> completed.add(r.getCode()));
        List<String> missing = rules.stream().filter(r -> "REQUIRED".equals(r.getRuleType()))
            .filter(r -> !completed.contains(r.getCode())).map(DrillRuleEntity::getCode).toList();
        List<String> risky = rules.stream().filter(r -> "RISKY".equals(r.getRuleType()))
            .filter(r -> completed.contains(r.getCode())).map(DrillRuleEntity::getCode).toList();
        var breakdown = rules.stream().map(r -> new AssessmentVOs.ScoreItem(r.getCode(), r.getRuleType(),
            completed.contains(r.getCode()), r.getPoints())).toList();
        var actionViews = recorded.stream().map(a -> new AssessmentVOs.Action(a.getId(), a.getSeqNo(),
            a.getClientRequestId(), a.getActionText(), a.getNormalizedAction(), a.getMatchSource(), a.getMatchConfidence(),
            a.getRiskFlag(), a.getPointsAwarded(), evidenceForAction(run, a))).toList();
        return new AssessmentVOs.Run(runId, run.getScenarioId(), run.getProjectId(), run.getUserId(),
            run.getAssignmentTaskId(), scenario.getKnowledgeSnapshotId(), run.getStatus(), run.getRubricVersion(),
            scenario.getInitialSymptom(), actionViews, currentEvidence(runId), completed.stream().sorted().toList(),
            missing, risky, breakdown, run.getProgramScore(), run.getPassCandidate(),
            "REVIEW_PENDING".equals(run.getStatus()), run.getFinalScore(),
            support.read(run.getConclusionJson()), run.getAiExplanation(), null, null);
    }

    private List<AssessmentVOs.Evidence> evidenceForAction(DrillRunEntity run, DrillRunActionEntity action) {
        Set<String> codes = new HashSet<>(support.strings(action.getEvidenceUnlockedJson()));
        return mapper.evidence(run.getScenarioId()).stream().filter(e -> codes.contains(e.getCode()))
            .map(e -> views.evidence(e, false)).toList();
    }

    private List<AssessmentVOs.Evidence> currentEvidence(Long runId) {
        var run = mapper.run(runId);
        Set<String> codes = new HashSet<>(mapper.unlockedEvidenceCodes(runId));
        return mapper.evidence(run.getScenarioId()).stream().filter(e -> codes.contains(e.getCode()))
            .map(e -> views.evidence(e, false)).toList();
    }

    private AssessmentVOs.RunSubmitted submitted(DrillRunEntity run) {
        return new AssessmentVOs.RunSubmitted(run.getId(), run.getStatus(), run.getProgramScore(), run.getPassCandidate(),
            "REVIEW_PENDING".equals(run.getStatus()));
    }

    private DrillRunEntity ownedRun(Long userId, Long runId) {
        var run = mapper.run(runId);
        if (run == null) throw missing();
        support.lock(userId, run.getProjectId(), false);
        if (!userId.equals(run.getUserId()) && !support.manager(userId, run.getProjectId()))
            throw new BusinessException(ErrorCode.FORBIDDEN);
        return run;
    }
}
