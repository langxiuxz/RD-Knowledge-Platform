package com.studyagent.assessment;

import com.studyagent.assessment.service.DrillRuleLogic;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DrillRuleLogicTest {
    @Test
    void aliasMatchingOnlyReturnsDatabaseRuleAndNormalizesWhitespaceAndUnicode() {
        var rule = new DrillRuleLogic.Rule("CHECK_LAG", "REQUIRED", BigDecimal.valueOf(15), List.of("查看 consumer lag"));
        assertSame(rule, DrillRuleLogic.matchAlias("  先查看  consumer lag 和异常分区 ", List.of(rule)));
        assertNull(DrillRuleLogic.matchAlias("restart production", List.of(rule)));
        assertNull(DrillRuleLogic.matchAlias(" ", List.of(rule)));
    }

    @Test
    void scoreUsesDatabasePointsAndPenalizesMissingRequiredRules() {
        var rules = List.of(
                new DrillRuleLogic.Rule("REQUIRED", "REQUIRED", BigDecimal.valueOf(15), List.of()),
                new DrillRuleLogic.Rule("RISK", "RISKY", BigDecimal.valueOf(-20), List.of()),
                new DrillRuleLogic.Rule("VALIDATE", "VALIDATION", BigDecimal.valueOf(10), List.of()));
        assertEquals(new BigDecimal("5.00"), DrillRuleLogic.score(rules, Set.of("REQUIRED", "RISK", "VALIDATE")));
        assertEquals(new BigDecimal("0.00"), DrillRuleLogic.score(rules, Set.of()));
    }

    @Test
    void unknownRuleTypesCannotChangeScore() {
        var rules = List.of(new DrillRuleLogic.Rule("MODEL", "MODEL_INVENTED", BigDecimal.valueOf(99), List.of()));
        assertEquals(new BigDecimal("0.00"), DrillRuleLogic.score(rules, Set.of("MODEL")));
    }

    @Test
    void passRequiresRequiredAndValidationActionsEvidenceAndThreshold() {
        var rules = List.of(
                new DrillRuleLogic.Rule("CHECK", "REQUIRED", BigDecimal.valueOf(70), List.of()),
                new DrillRuleLogic.Rule("VERIFY", "VALIDATION", BigDecimal.valueOf(30), List.of()),
                new DrillRuleLogic.Rule("OPTIONAL", "OPTIONAL", BigDecimal.ZERO, List.of()));
        var allDone = Set.of("CHECK", "VERIFY");
        var evidence = Set.of("METRIC");
        var score = new BigDecimal("100.00");
        var threshold = new BigDecimal("70.00");

        assertTrue(DrillRuleLogic.meetsPassConditions(rules, allDone, evidence, evidence, true, score, threshold));
        assertFalse(DrillRuleLogic.meetsPassConditions(rules, Set.of("CHECK"), evidence, evidence, true, score, threshold));
        assertFalse(DrillRuleLogic.meetsPassConditions(rules, allDone, evidence, Set.of(), true, score, threshold));
        assertFalse(DrillRuleLogic.meetsPassConditions(rules, allDone, evidence, evidence, true,
                new BigDecimal("69.99"), threshold));
    }

    @Test
    void passDoesNotRequireEvidenceWhenScenarioHasNone() {
        var rules = List.of(new DrillRuleLogic.Rule("VERIFY", "VALIDATION", BigDecimal.valueOf(100), List.of()));
        assertTrue(DrillRuleLogic.meetsPassConditions(rules, Set.of("VERIFY"), Set.of(), Set.of(), false,
                new BigDecimal("100.00"), new BigDecimal("70.00")));
    }

    @Test
    void sequenceGroupsRequireEarlierRulesAndRespectMinimumActionOrder() {
        var rules = List.of(
                new DrillRuleLogic.OrderedRule("INSPECT", "SAFE_CHECKS", 1),
                new DrillRuleLogic.OrderedRule("MITIGATE", "SAFE_CHECKS", 2),
                new DrillRuleLogic.OrderedRule("UNRELATED", "OTHER", 1));

        assertFalse(DrillRuleLogic.isOrderValid("SAFE_CHECKS", null, 1, 2, rules, Set.of()));
        assertTrue(DrillRuleLogic.isOrderValid("SAFE_CHECKS", null, 2, 2, rules, Set.of("INSPECT")));
        assertFalse(DrillRuleLogic.isOrderValid(null, 3, 2, 3, rules, Set.of()));
        assertTrue(DrillRuleLogic.isOrderValid(null, 3, 3, 3, rules, Set.of()));
    }
}
