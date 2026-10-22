package com.gempukku.lotro.bots.simulation;

import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class DecisionCycleGuard {
    private static final int REQUIRED_CYCLE_REPETITIONS = 4;
    private static final int MAX_CYCLE_LENGTH = 16;
    private static final int MAX_HISTORY_SIZE = MAX_CYCLE_LENGTH * REQUIRED_CYCLE_REPETITIONS + MAX_CYCLE_LENGTH;
    private static final int RECENT_DECISION_HISTORY_SIZE = 24;

    private final List<DecisionRecord> history = new ArrayList<>();
    private DeferredEscape deferredEscape;

    static String signature(String playerId, String phase, AwaitingDecision decision, GameState gameState) {
        return signature(playerId, phase, decision, visibleStateSignature(playerId, gameState));
    }

    static String signature(
            String playerId,
            String phase,
            AwaitingDecision decision,
            String visibleStateSignature) {
        StringBuilder signature = new StringBuilder()
                .append(playerId)
                .append('/')
                .append(phase)
                .append('/')
                .append(decision.getDecisionType())
                .append(':')
                .append(decision.getText());
        Map<String, String[]> parameters = decision.getDecisionParameters();
        for (String parameter : new TreeSet<>(parameters.keySet())) {
            signature.append('|')
                    .append(parameter)
                    .append('=')
                    .append(Arrays.toString(parameters.get(parameter)));
        }
        signature.append("|visibleState=").append(visibleStateSignature);
        return signature.toString();
    }

    private static String visibleStateSignature(String observerId, GameState gameState) {
        StringBuilder signature = new StringBuilder()
                .append(gameState.getCurrentPlayerId())
                .append('/')
                .append(gameState.getCurrentPhase())
                .append("/site=")
                .append(gameState.getCurrentSiteNumber())
                .append("/turn=")
                .append(gameState.getTurnNumber())
                .append("/move=")
                .append(gameState.getMoveCount())
                .append("/ring=")
                .append(gameState.isWearingRing());

        if (!gameState.isInit())
            return signature.toString();

        List<String> playerIds = new ArrayList<>(gameState.getPlayerNames());
        playerIds.sort(Comparator.naturalOrder());
        for (String playerId : playerIds) {
            boolean ownHand = observerId.equals(playerId);
            signature.append("|player=")
                    .append(playerId)
                    .append(",position=")
                    .append(gameState.getPlayerPosition(playerId))
                    .append(",threats=")
                    .append(gameState.getPlayerThreats(playerId))
                    .append(",burdens=")
                    .append(gameState.getPlayerBurdens(playerId))
                    .append(",deckCount=")
                    .append(gameState.getDeck(playerId).size());
            appendVisiblePile(signature, "hand", gameState.getHand(playerId),
                    ownHand || gameState.isHandRevealed(playerId));
            appendVisiblePile(signature, "discard", gameState.getDiscard(playerId),
                    ownHand || gameState.isDiscardPilePublic());
            appendVisiblePile(signature, "dead", gameState.getDeadPile(playerId), true);
            appendVisiblePile(signature, "stacked", gameState.getStacked(playerId), true);
            signature.append(",voidFromHandCount=").append(gameState.getVoidFromHand(playerId).size())
                    .append(",removedCount=").append(gameState.getRemoved(playerId).size());
        }

        List<PhysicalCard> inPlay = new ArrayList<>(gameState.getInPlay());
        inPlay.sort(Comparator.comparingInt(PhysicalCard::getCardId));
        signature.append("|inPlay=");
        for (PhysicalCard card : inPlay) {
            signature.append(card.getCardId())
                    .append(':')
                    .append(card.getBlueprintId())
                    .append(':')
                    .append(card.getOwner())
                    .append(':')
                    .append(card.getZone())
                    .append(":attachedTo=")
                    .append(card.getAttachedTo() == null ? "-" : card.getAttachedTo().getCardId())
                    .append(":stackedOn=")
                    .append(card.getStackedOn() == null ? "-" : card.getStackedOn().getCardId())
                    .append(":flipped=")
                    .append(card.isFlipped())
                    .append(":tokens=")
                    .append(gameState.getTokens(card).entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.joining(",")))
                    .append(';');
        }

        // Twilight is excluded so repeated actions that only accumulate twilight can still be recognized as a cycle.
        return signature.toString();
    }

    private static void appendVisiblePile(
            StringBuilder signature,
            String name,
            List<? extends PhysicalCard> cards,
            boolean revealCards) {
        signature.append(',').append(name).append('=').append(cards.size());
        if (revealCards) {
            signature.append('[')
                    .append(cards.stream()
                            .map(card -> card.getCardId() + ":" + card.getBlueprintId())
                            .sorted()
                            .collect(Collectors.joining(",")))
                    .append(']');
        }
    }

    Optional<RepeatedCycle> findRepeatedCycle(String currentSignature) {
        for (int cycleLength = 1; cycleLength <= MAX_CYCLE_LENGTH; cycleLength++) {
            int repeatedLength = cycleLength * REQUIRED_CYCLE_REPETITIONS;
            int start = history.size() - repeatedLength;
            if (start < 0 || !history.get(start).signature().equals(currentSignature))
                continue;

            boolean repeated = true;
            for (int index = cycleLength; index < repeatedLength; index++) {
                if (!history.get(start + index).equals(history.get(start + index % cycleLength))) {
                    repeated = false;
                    break;
                }
            }
            if (repeated) {
                return Optional.of(new RepeatedCycle(
                        cycleLength,
                        List.copyOf(history.subList(start, start + cycleLength))));
            }
        }
        return Optional.empty();
    }

    void record(String signature, AwaitingDecisionType decisionType, String summary, String answer) {
        history.add(new DecisionRecord(signature, decisionType, summary, answer));
        if (history.size() > MAX_HISTORY_SIZE)
            history.remove(0);
    }

    void clear() {
        history.clear();
        deferredEscape = null;
    }

    void deferEscape(DeferredEscape escape) {
        deferredEscape = escape;
    }

    Optional<DeferredEscape> takeDeferredEscape(String currentSignature) {
        DeferredEscape escape = deferredEscape;
        deferredEscape = null;
        return escape != null && escape.signature().equals(currentSignature)
                ? Optional.of(escape)
                : Optional.empty();
    }

    static void addRecentDecision(Deque<String> recentDecisions, String summary) {
        if (recentDecisions.size() == RECENT_DECISION_HISTORY_SIZE)
            recentDecisions.removeFirst();
        recentDecisions.addLast(summary);
    }

    static String formatDecisionOutcome(
            String playerId,
            String phase,
            AwaitingDecision decision,
            String answer,
            boolean forcedPass) {
        StringBuilder summary = new StringBuilder()
                .append(playerId)
                .append('/')
                .append(phase)
                .append(' ')
                .append(decision.getDecisionType())
                .append(": ")
                .append(decision.getText());

        if (decision.getDecisionType() == AwaitingDecisionType.CARD_ACTION_CHOICE) {
            if (answer.isEmpty()) {
                summary.append(" -> PASS");
            } else {
                int actionIndex = Integer.parseInt(answer);
                summary.append(" -> ")
                        .append(parameterAt(decision, "actionText", actionIndex))
                        .append(" [cardId=")
                        .append(parameterAt(decision, "cardId", actionIndex))
                        .append(']');
            }
        } else if (decision.getDecisionType() == AwaitingDecisionType.MULTIPLE_CHOICE) {
            int choiceIndex = Integer.parseInt(answer);
            summary.append(" -> ").append(parameterAt(decision, "results", choiceIndex));
        } else {
            summary.append(" -> ").append(answer.isEmpty() ? "<empty>" : answer);
        }
        if (forcedPass)
            summary.append(" [cycle breaker]");
        return summary.toString();
    }

    private static String parameterAt(AwaitingDecision decision, String name, int index) {
        String[] values = decision.getDecisionParameters().get(name);
        return values != null && index >= 0 && index < values.length ? values[index] : "unspecified";
    }

    record RepeatedCycle(int length, List<DecisionRecord> decisions) {
        String repeatedAnswer() {
            return decisions.getFirst().answer();
        }

        Optional<DeferredEscape> deferredEscape() {
            if (decisions.size() < 2)
                return Optional.empty();

            DecisionRecord nextDecision = decisions.get(1);
            if (nextDecision.decisionType() == AwaitingDecisionType.CARD_ACTION_CHOICE
                    && !nextDecision.answer().isEmpty()) {
                return Optional.of(new DeferredEscape(
                        nextDecision.signature(),
                        nextDecision.answer()));
            }
            return Optional.empty();
        }

        String describe() {
            List<String> summaries = decisions.stream()
                    .map(DecisionRecord::summary)
                    .toList();
            return String.join(" | ", summaries) + " (cycle length " + length + ", repeated "
                    + REQUIRED_CYCLE_REPETITIONS + " times)";
        }
    }

    private record DecisionRecord(
            String signature,
            AwaitingDecisionType decisionType,
            String summary,
            String answer) {
    }

    record DeferredEscape(String signature, String repeatedAnswer) {
    }
}
