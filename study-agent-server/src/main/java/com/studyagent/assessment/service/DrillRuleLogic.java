package com.studyagent.assessment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure deterministic helpers. Rule definitions and point values must be loaded from MySQL. */
public final class DrillRuleLogic {
    private DrillRuleLogic() {}

    public record Rule(String code, String type, BigDecimal points, List<String> aliases) {}
    public record OrderedRule(String code, String sequenceGroup, Integer sortOrder) {}

    public static boolean isOrderValid(String sequenceGroup, Integer minOrder, int actionOrder,
                                       Integer sortOrder, Collection<OrderedRule> rules,
                                       Set<String> completedCodes) {
        if (minOrder != null && actionOrder < minOrder) return false;
        if (sequenceGroup == null || sequenceGroup.isBlank() || sortOrder == null) return true;
        return rules.stream()
                .filter(rule -> sequenceGroup.equals(rule.sequenceGroup()))
                .filter(rule -> rule.sortOrder() != null && rule.sortOrder() < sortOrder)
                .allMatch(rule -> completedCodes.contains(rule.code()));
    }

    public static Rule matchAlias(String actionText, Collection<Rule> databaseRules) {
        String action = normalize(actionText);
        if (action.isEmpty()) return null;
        return databaseRules.stream()
                .filter(java.util.Objects::nonNull)
                .filter(rule -> rule.aliases() != null)
                .filter(rule -> rule.aliases().stream().map(DrillRuleLogic::normalize)
                        .anyMatch(alias -> !alias.isEmpty() && action.contains(alias)))
                .findFirst().orElse(null);
    }

    public static BigDecimal score(Collection<Rule> rules, Set<String> completedCodes) {
        BigDecimal earned = BigDecimal.ZERO;
        for (Rule rule : rules) {
            if (!Set.of("REQUIRED", "OPTIONAL", "RISKY", "VALIDATION").contains(rule.type())) continue;
            boolean completed = completedCodes.contains(rule.code());
            if (completed) {
                earned = earned.add(rule.points());
            }
        }
        return earned.setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean meetsPassConditions(Collection<Rule> rules, Set<String> completedCodes,
                                              Set<String> requiredEvidenceCodes,
                                              Set<String> submittedEvidenceCodes,
                                              boolean evidenceAvailable,
                                              BigDecimal score, BigDecimal passScore) {
        boolean requiredActionsComplete = rules.stream()
                .filter(rule -> "REQUIRED".equals(rule.type()) || "VALIDATION".equals(rule.type()))
                .allMatch(rule -> completedCodes.contains(rule.code()));
        boolean requiredEvidenceUsed = submittedEvidenceCodes.containsAll(requiredEvidenceCodes);
        boolean evidenceUsed = !evidenceAvailable || !submittedEvidenceCodes.isEmpty();
        return requiredActionsComplete && requiredEvidenceUsed && evidenceUsed
                && score.compareTo(passScore) >= 0;
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
