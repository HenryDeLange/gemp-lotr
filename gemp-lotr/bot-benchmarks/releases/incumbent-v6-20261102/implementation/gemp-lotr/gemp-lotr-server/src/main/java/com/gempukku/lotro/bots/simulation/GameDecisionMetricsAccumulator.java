package com.gempukku.lotro.bots.simulation;

import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

final class GameDecisionMetricsAccumulator {
    private final Map<Phase, Integer> decisionsByPhase = new EnumMap<>(Phase.class);
    private final Map<AwaitingDecisionType, Integer> decisionsByType = new EnumMap<>(AwaitingDecisionType.class);
    private final Map<Integer, AttachmentMove> previousMoves = new HashMap<>();
    private Map<Integer, Integer> previousAttachments = Map.of();
    private int previousTurn = -1;
    private Phase previousPhase;
    private int decisions;
    private int cardActionChoices;
    private int cardActionPasses;
    private int transferActions;
    private int attachmentReversals;

    void recordDecision(Phase phase, AwaitingDecision decision, String acceptedAnswer) {
        decisions++;
        decisionsByPhase.merge(phase, 1, Integer::sum);
        decisionsByType.merge(decision.getDecisionType(), 1, Integer::sum);
        if (decision.getDecisionType() != AwaitingDecisionType.CARD_ACTION_CHOICE)
            return;

        cardActionChoices++;
        if (acceptedAnswer.isEmpty()) {
            cardActionPasses++;
            return;
        }

        String[] actionTexts = decision.getDecisionParameters().get("actionText");
        int index = Integer.parseInt(acceptedAnswer);
        if (actionTexts == null || index < 0 || index >= actionTexts.length)
            throw new IllegalStateException("Accepted card action has no matching action text: " + acceptedAnswer);
        if (actionTexts[index].startsWith("Transfer"))
            transferActions++;
    }

    void capture(GameState gameState) {
        Phase phase = gameState.getCurrentPhase();
        int turn = gameState.getTurnNumber();
        if (phase != Phase.FELLOWSHIP) {
            previousAttachments = Map.of();
            previousMoves.clear();
        } else {
            Map<Integer, Integer> attachments = new HashMap<>();
            for (PhysicalCard card : gameState.getInPlay()) {
                CardType type = card.getBlueprint().getCardType();
                if ((type == CardType.POSSESSION || type == CardType.ARTIFACT) && card.getAttachedTo() != null)
                    attachments.put(card.getCardId(), card.getAttachedTo().getCardId());
            }

            if (previousPhase == Phase.FELLOWSHIP && previousTurn == turn) {
                for (Map.Entry<Integer, Integer> entry : attachments.entrySet()) {
                    Integer oldBearer = previousAttachments.get(entry.getKey());
                    int newBearer = entry.getValue();
                    if (oldBearer == null || oldBearer == newBearer)
                        continue;
                    AttachmentMove previousMove = previousMoves.get(entry.getKey());
                    if (previousMove != null && previousMove.from() == newBearer && previousMove.to() == oldBearer)
                        attachmentReversals++;
                    previousMoves.put(entry.getKey(), new AttachmentMove(oldBearer, newBearer));
                }
                previousMoves.keySet().retainAll(attachments.keySet());
            } else {
                previousMoves.clear();
            }
            previousAttachments = attachments;
        }
        previousTurn = turn;
        previousPhase = phase;
    }

    GameDecisionMetrics finish() {
        return new GameDecisionMetrics(decisions, cardActionChoices, cardActionPasses, transferActions,
                attachmentReversals, decisionsByPhase, decisionsByType);
    }

    private record AttachmentMove(int from, int to) {
    }
}
