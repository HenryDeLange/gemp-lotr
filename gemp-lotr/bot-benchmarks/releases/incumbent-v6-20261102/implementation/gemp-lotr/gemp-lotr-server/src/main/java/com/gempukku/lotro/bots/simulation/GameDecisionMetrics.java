package com.gempukku.lotro.bots.simulation;

import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;

import java.util.Map;

public record GameDecisionMetrics(
        int decisions,
        int cardActionChoices,
        int cardActionPasses,
        int transferActions,
        int attachmentReversals,
        Map<Phase, Integer> decisionsByPhase,
        Map<AwaitingDecisionType, Integer> decisionsByType) {
    public GameDecisionMetrics {
        decisionsByPhase = Map.copyOf(decisionsByPhase);
        decisionsByType = Map.copyOf(decisionsByType);
    }
}
