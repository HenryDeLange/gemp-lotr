package com.gempukku.lotro.bots.simulation;

import com.alibaba.fastjson2.annotation.JSONField;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public record BatchDecisionMetrics(
        int gamesMeasured,
        long decisionsTotal,
        int maximumDecisions,
        long cardActionChoices,
        long cardActionPasses,
        long transferActions,
        long attachmentReversals,
        Map<Phase, Long> decisionsByPhase,
        Map<AwaitingDecisionType, Long> decisionsByType,
        Map<String, Integer> decisionsPerGameHistogram) {
    public BatchDecisionMetrics {
        decisionsByPhase = Map.copyOf(decisionsByPhase);
        decisionsByType = Map.copyOf(decisionsByType);
        decisionsPerGameHistogram = Map.copyOf(decisionsPerGameHistogram);
    }

    public static BatchDecisionMetrics empty() {
        return new BatchDecisionMetrics(0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of(), Map.of());
    }

    public BatchDecisionMetrics plus(GameDecisionMetrics game) {
        Map<Phase, Long> phases = new EnumMap<>(Phase.class);
        phases.putAll(decisionsByPhase);
        game.decisionsByPhase().forEach((phase, count) -> phases.merge(phase, (long) count, Long::sum));
        Map<AwaitingDecisionType, Long> types = new EnumMap<>(AwaitingDecisionType.class);
        types.putAll(decisionsByType);
        game.decisionsByType().forEach((type, count) -> types.merge(type, (long) count, Long::sum));
        Map<String, Integer> histogram = new HashMap<>(decisionsPerGameHistogram);
        histogram.merge(String.valueOf(game.decisions()), 1, Integer::sum);
        return new BatchDecisionMetrics(gamesMeasured + 1, decisionsTotal + game.decisions(),
                Math.max(maximumDecisions, game.decisions()), cardActionChoices + game.cardActionChoices(),
                cardActionPasses + game.cardActionPasses(), transferActions + game.transferActions(),
                attachmentReversals + game.attachmentReversals(), phases, types, histogram);
    }

    public BatchDecisionMetrics plus(BatchDecisionMetrics other) {
        Map<Phase, Long> phases = new EnumMap<>(Phase.class);
        phases.putAll(decisionsByPhase);
        other.decisionsByPhase.forEach((phase, count) -> phases.merge(phase, count, Long::sum));
        Map<AwaitingDecisionType, Long> types = new EnumMap<>(AwaitingDecisionType.class);
        types.putAll(decisionsByType);
        other.decisionsByType.forEach((type, count) -> types.merge(type, count, Long::sum));
        Map<String, Integer> histogram = new HashMap<>(decisionsPerGameHistogram);
        other.decisionsPerGameHistogram.forEach((count, games) -> histogram.merge(count, games, Integer::sum));
        return new BatchDecisionMetrics(gamesMeasured + other.gamesMeasured, decisionsTotal + other.decisionsTotal,
                Math.max(maximumDecisions, other.maximumDecisions), cardActionChoices + other.cardActionChoices,
                cardActionPasses + other.cardActionPasses, transferActions + other.transferActions,
                attachmentReversals + other.attachmentReversals, phases, types, histogram);
    }

    @JSONField(name = "medianDecisions")
    public int getMedianDecisions() {
        return percentile(0.5);
    }

    @JSONField(name = "p95Decisions")
    public int getP95Decisions() {
        return percentile(0.95);
    }

    private int percentile(double fraction) {
        if (gamesMeasured == 0)
            return 0;
        int rank = (int) Math.ceil(gamesMeasured * fraction);
        int cumulative = 0;
        Map<Integer, Integer> sortedHistogram = new java.util.TreeMap<>();
        decisionsPerGameHistogram.forEach((count, games) -> sortedHistogram.put(Integer.parseInt(count), games));
        for (Map.Entry<Integer, Integer> entry : sortedHistogram.entrySet()) {
            cumulative += entry.getValue();
            if (cumulative >= rank)
                return entry.getKey();
        }
        throw new IllegalStateException("Decision histogram does not contain all measured games");
    }
}
