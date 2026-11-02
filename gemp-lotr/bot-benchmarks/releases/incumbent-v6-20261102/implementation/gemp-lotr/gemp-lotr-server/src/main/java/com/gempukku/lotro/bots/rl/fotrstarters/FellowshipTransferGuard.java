package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import com.gempukku.lotro.logic.decisions.CardActionSelectionDecision;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FellowshipTransferGuard {
    private static final String FELLOWSHIP_ACTIONS = "Play Fellowship action or Pass";
    private final String player;
    private final Set<Integer> movedCards = new HashSet<>();
    private int turn = -1;
    private PendingTransfer pending;

    FellowshipTransferGuard(String player) {
        this.player = player;
    }

    void observe(GameState state, AwaitingDecision decision) {
        if (state.getCurrentPhase() != Phase.FELLOWSHIP || state.getTurnNumber() != turn) {
            clear();
            if (state.getCurrentPhase() == Phase.FELLOWSHIP)
                turn = state.getTurnNumber();
        }
        if (pending == null)
            return;

        PhysicalCard card = state.findCardById(pending.cardId());
        if (card != null && card.getAttachedTo() != null
                && card.getAttachedTo().getCardId() != pending.bearerId()) {
            movedCards.add(pending.cardId());
            pending = null;
        } else if (isFellowshipActionMenu(decision)) {
            pending = null;
        }
    }

    PreparedDecision prepare(GameState state, AwaitingDecision decision) {
        if (state.getCurrentPhase() != Phase.FELLOWSHIP || !isFellowshipActionMenu(decision)
                || movedCards.isEmpty())
            return new PreparedDecision(decision, List.of(), false);

        String[] cardIds = decision.getDecisionParameters().get("cardId");
        String[] blueprintIds = decision.getDecisionParameters().get("blueprintId");
        String[] texts = decision.getDecisionParameters().get("actionText");
        if (cardIds == null || blueprintIds == null || texts == null
                || cardIds.length != texts.length || blueprintIds.length != texts.length)
            throw new IllegalArgumentException("Invalid card-action options for transfer guard");
        List<String> keptCards = new ArrayList<>();
        List<String> keptBlueprints = new ArrayList<>();
        List<String> keptTexts = new ArrayList<>();
        List<Integer> originalIndices = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            int cardId = Integer.parseInt(cardIds[i]);
            PhysicalCard card = state.findCardById(cardId);
            if (movedCards.contains(cardId) && isVoluntaryTransfer(card, texts[i]))
                continue;
            originalIndices.add(i);
            keptCards.add(cardIds[i]);
            keptBlueprints.add(blueprintIds[i]);
            keptTexts.add(texts[i]);
        }
        if (originalIndices.size() == texts.length)
            return new PreparedDecision(decision, List.of(), false);
        CardActionSelectionDecision filtered = new CardActionSelectionDecision(
                decision.getAwaitingDecisionId(), decision.getText(), keptCards, keptBlueprints, keptTexts) {
            @Override
            public void decisionMade(String result) throws DecisionResultInvalidException {
                throw new UnsupportedOperationException("Filtered bot decision cannot be submitted to the game");
            }
        };
        return new PreparedDecision(filtered, List.copyOf(originalIndices), true);
    }

    void selected(GameState state, AwaitingDecision decision, String answer) {
        if (state.getCurrentPhase() != Phase.FELLOWSHIP || !isFellowshipActionMenu(decision) || answer.isEmpty())
            return;
        int index = Integer.parseInt(answer);
        String[] cardIds = decision.getDecisionParameters().get("cardId");
        String[] texts = decision.getDecisionParameters().get("actionText");
        if (cardIds == null || texts == null || index < 0 || index >= cardIds.length || index >= texts.length)
            throw new IllegalArgumentException("Invalid card-action answer for transfer guard: " + answer);
        PhysicalCard card = state.findCardById(Integer.parseInt(cardIds[index]));
        if (isVoluntaryTransfer(card, texts[index]))
            pending = new PendingTransfer(card.getCardId(), card.getAttachedTo().getCardId());
    }

    private boolean isVoluntaryTransfer(PhysicalCard card, String actionText) {
        if (card == null || card.getAttachedTo() == null || !player.equals(card.getOwner()))
            return false;
        CardType type = card.getBlueprint().getCardType();
        return (type == CardType.POSSESSION || type == CardType.ARTIFACT)
                && actionText.equals("Transfer " + GameUtils.getFullName(card));
    }

    private static boolean isFellowshipActionMenu(AwaitingDecision decision) {
        return decision.getDecisionType() == AwaitingDecisionType.CARD_ACTION_CHOICE
                && FELLOWSHIP_ACTIONS.equals(decision.getText());
    }

    void clear() {
        movedCards.clear();
        pending = null;
        turn = -1;
    }

    record PreparedDecision(AwaitingDecision decision, List<Integer> originalIndices, boolean filtered) {
        String toOriginalAnswer(String answer) {
            if (!filtered() || answer.isEmpty())
                return answer;
            int index = Integer.parseInt(answer);
            if (index < 0 || index >= originalIndices.size())
                throw new IllegalArgumentException("Invalid filtered card-action answer: " + answer);
            return String.valueOf(originalIndices.get(index));
        }
    }

    private record PendingTransfer(int cardId, int bearerId) { }
}
