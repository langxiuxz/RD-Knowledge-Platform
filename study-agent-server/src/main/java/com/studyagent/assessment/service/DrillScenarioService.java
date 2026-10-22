package com.studyagent.assessment.service;

import com.studyagent.assessment.dto.AssessmentRequests;
import com.studyagent.assessment.entity.*;
import com.studyagent.assessment.mapper.AssessmentMapper;
import com.studyagent.assessment.vo.AssessmentVOs;
import com.studyagent.common.exception.*;
import com.studyagent.knowledge.mapper.KnowledgeSnapshotMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static com.studyagent.assessment.service.AssessmentSupport.*;

@Service
public class DrillScenarioService {
    private final AssessmentMapper mapper;
    private final AssessmentSupport support;
    private final AssessmentViews views;
    private final KnowledgeSnapshotMapper snapshots;
    public DrillScenarioService(AssessmentMapper mapper,AssessmentSupport support,AssessmentViews views,KnowledgeSnapshotMapper snapshots) {
        this.mapper=mapper;this.support=support;this.views=views;this.snapshots=snapshots;
    }
    public List<AssessmentVOs.Scenario> list(Long userId,Long projectId,String status) {
        boolean manager=support.manager(userId,projectId);String filter=manager?status:"PUBLISHED";
        return mapper.scenarios(projectId,filter).stream().map(s->detail(userId,s.getId())).toList();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AssessmentVOs.Id create(Long userId,Long projectId,AssessmentRequests.Scenario request) {
        support.lock(userId,projectId,true);support.snapshot(projectId,request.knowledgeSnapshotId(),true);
        var scenario=new DrillScenarioEntity();copy(scenario,request);scenario.setProjectId(projectId);scenario.setCreatedBy(userId);
        scenario.setVersionNo(1);scenario.setStatus("DRAFT");scenario.setRubricVersion(1);mapper.insertScenario(scenario);
        replaceChildren(scenario,request);support.audit(projectId,userId,"DRILL_SCENARIO_CREATE","DRILL",scenario.getId());
        return new AssessmentVOs.Id(scenario.getId());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void update(Long userId,Long id,AssessmentRequests.Scenario request) {
        var old=find(id);support.lock(userId,old.getProjectId(),true);var scenario=mapper.lockScenario(id);
        if(!"DRAFT".equals(scenario.getStatus())) throw conflict("已发布场景不可原地修改");
        support.snapshot(scenario.getProjectId(),request.knowledgeSnapshotId(),true);copy(scenario,request);
        if(mapper.updateScenario(scenario)!=1) throw conflict("场景状态已变化");
        mapper.deleteRules(id);mapper.deleteEvidence(id);replaceChildren(scenario,request);
        support.audit(scenario.getProjectId(),userId,"DRILL_SCENARIO_UPDATE","DRILL",id);
    }
    public AssessmentVOs.Scenario detail(Long userId,Long id) {
        var s=find(id);boolean manager=support.manager(userId,s.getProjectId());
        if(!manager && !"PUBLISHED".equals(s.getStatus())) throw missing();
        List<AssessmentVOs.Rule> rules=manager?mapper.rules(id).stream().map(this::rule).toList():List.of();
        List<AssessmentVOs.Evidence> evidence=mapper.evidence(id).stream()
            .filter(e->manager || e.getUnlockRuleCode()==null).map(e->views.evidence(e,manager)).toList();
        return scenario(s,rules,evidence);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void publish(Long userId,Long id) {
        var initial=find(id);support.lock(userId,initial.getProjectId(),true);var s=mapper.lockScenario(id);
        if("PUBLISHED".equals(s.getStatus())) return;
        if(!"DRAFT".equals(s.getStatus())) throw conflict("只有 DRAFT 场景可以发布");
        support.snapshot(s.getProjectId(),s.getKnowledgeSnapshotId(),true);
        var rules=mapper.rules(id);var evidence=mapper.evidence(id);validate(s,rules,evidence);
        var versions=snapshots.findVersionIds(s.getKnowledgeSnapshotId(),s.getProjectId(),support.snapshot(s.getProjectId(),s.getKnowledgeSnapshotId(),false).getKnowledgeBaseId());
        if(versions.isEmpty()) throw invalid("演练场景必须绑定非空知识快照");
        support.depend(s.getProjectId(),"DRILL",id,versions);support.requireCurrent("DRILL",id);
        if(mapper.publishScenario(id)!=1) throw conflict("场景状态已变化");
        support.audit(s.getProjectId(),userId,"DRILL_SCENARIO_PUBLISH","DRILL",id);
    }
    private void validate(DrillScenarioEntity s,List<DrillRuleEntity> rules,List<DrillEvidenceEntity> evidence) {
        var rubric=support.read(s.getRubricJson());if(rubric==null || !rubric.isArray() || rubric.isEmpty()) throw invalid("Rubric 不能为空");
        Set<String> dims=new HashSet<>();BigDecimal total=BigDecimal.ZERO;
        for(var d:rubric) {
            var code=d.get("code");var name=d.get("name");var score=d.get("maxScore");
            if(code==null || name==null || score==null || !dims.add(code.asText()) || score.decimalValue().signum()<=0) throw invalid("Rubric 维度不合法");
            total=total.add(score.decimalValue());
        }
        if(total.compareTo(BigDecimal.valueOf(100))!=0) throw invalid("Rubric 满分合计必须为 100");
        if(rules.isEmpty()) throw invalid("至少需要一条评分规则");
        Set<String> codes=new HashSet<>();BigDecimal positive=BigDecimal.ZERO;
        for(var r:rules) {
            if(!codes.add(r.getCode())) throw invalid("Rule code 不能重复");
            if("RISKY".equals(r.getRuleType())) {if(r.getPoints().signum()>0) throw invalid("RISKY 规则分值必须小于等于 0");}
            else {if(r.getPoints().signum()<0) throw invalid("非 RISKY 规则分值不能为负数");positive=positive.add(r.getPoints());}
            if(r.getAliasesJson()==null || support.read(r.getAliasesJson()).isEmpty()) throw invalid("Rule 至少要有一个匹配别名");
        }
        if(positive.signum()<=0) throw invalid("至少需要一条正向评分规则");
        Set<String> evidenceCodes=new HashSet<>();
        for(var e:evidence) {
            if(!evidenceCodes.add(e.getCode()) || e.getUnlockRuleCode()!=null && !codes.contains(e.getUnlockRuleCode()))
                throw invalid("证据 code 重复或引用了不存在的 Rule");
        }
    }
    private void replaceChildren(DrillScenarioEntity s,AssessmentRequests.Scenario request) {
        List<DrillRuleEntity> rules=new ArrayList<>();int i=0;
        for(var input:request.rules()) {
            var r=new DrillRuleEntity();r.setScenarioId(s.getId());r.setCode(input.code());r.setKnowledgeKey(input.knowledgeKey());
            r.setTitle(input.title()==null||input.title().isBlank()?input.code():input.title().strip());r.setRuleType(input.type());
            r.setAliasesJson(support.write(input.aliases()));r.setSequenceGroup(input.sequenceGroup());r.setMinOrder(input.minOrder());
            r.setPoints(input.points());r.setSortOrder(i++);rules.add(r);
        }
        if(!rules.isEmpty()) mapper.insertRules(rules);
        List<DrillEvidenceEntity> evidence=new ArrayList<>();i=0;
        for(var input:request.evidence()) {
            var e=new DrillEvidenceEntity();e.setScenarioId(s.getId());e.setCode(input.code());e.setEvidenceType(input.type());
            e.setTitle(input.title());e.setContent(input.content());e.setUnlockRuleCode(input.unlockRuleCode());e.setSortOrder(i++);evidence.add(e);
        }
        if(!evidence.isEmpty()) mapper.insertEvidence(evidence);
    }
    private AssessmentVOs.Scenario scenario(DrillScenarioEntity s,List<AssessmentVOs.Rule> rules,List<AssessmentVOs.Evidence> evidence) {
        return new AssessmentVOs.Scenario(s.getId(),s.getProjectId(),s.getKnowledgeSnapshotId(),s.getName(),s.getDescription(),s.getSeverity(),
            s.getInitialSymptom(),s.getStatus(),s.getVersionNo(),s.getRubricVersion(),s.getPassScore(),support.read(s.getRubricJson()),rules,evidence);
    }
    private AssessmentVOs.Rule rule(DrillRuleEntity r) {
        return new AssessmentVOs.Rule(r.getCode(),r.getKnowledgeKey(),r.getTitle(),r.getRuleType(),r.getPoints(),support.strings(r.getAliasesJson()),r.getSequenceGroup(),r.getMinOrder());
    }
    private void copy(DrillScenarioEntity s,AssessmentRequests.Scenario r) {
        s.setKnowledgeSnapshotId(r.knowledgeSnapshotId());s.setName(r.name().strip());s.setDescription(r.description());s.setSeverity(r.severity());
        s.setInitialSymptom(r.initialSymptom());s.setPassScore(r.passScore());s.setRubricJson(support.write(r.rubric()));
    }
    private DrillScenarioEntity find(Long id) {var s=mapper.scenario(id);if(s==null)throw missing();return s;}
}
