package com.gempukku.lotro.bots;

import com.gempukku.lotro.bots.random.RandomDecisionBot;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Safety net for bot answers the engine rejects. A rejected human answer just re-asks the same decision; a bot's
 * model would usually give the same answer again, so the mediator used to concede the game for the bot
 * ("~bot lost due to: Invalid decision", issue #1097). Instead we try, in order:
 * <ol>
 *     <li>ASSIGN_MINIONS: the bot's own assignment with the forbidden pairings removed, then "assign nothing"
 *     (always legal for the Free Peoples player; for Shadow, the minions simply are not assigned);</li>
 *     <li>CARD_ACTION_CHOICE: pass;</li>
 *     <li>anything else: a few random answers built from the decision's own options.</li>
 * </ol>
 * Retrying is safe because every decision validates its answer before acting on it; the human path relies on the
 * same property when it re-sends a decision after DecisionResultInvalidException. Only if every fallback is also
 * rejected does the original exception propagate (and the bot concedes, as before).
 */
public final class BotDecisionFallback {
    private static final int RANDOM_ATTEMPTS = 5;

    private BotDecisionFallback() {
    }

    /**
     * Gives the decision the bot's answer, falling back as described above. {@code answer} may be null when the bot
     * failed to produce one. Returns the answer the engine accepted.
     */
    public static String decide(LotroGame game, String botName, AwaitingDecision decision, String answer) throws DecisionResultInvalidException {
        DecisionResultInvalidException rejection = null;
        if (answer != null) {
            try {
                decision.decisionMade(answer);
                return answer;
            } catch (DecisionResultInvalidException exp) {
                rejection = exp;
            }
        }

        for (String fallback : fallbackAnswers(game, botName, decision, answer)) {
            try {
                decision.decisionMade(fallback);
                return fallback;
            } catch (DecisionResultInvalidException ignored) {
                // try the next one
            }
        }
        throw rejection != null ? rejection : new DecisionResultInvalidException("Bot produced no valid answer");
    }

    /**
     * Chooses a different legal candidate when a simulator breaks a repeated decision cycle.
     * Declining an optional multiple-choice effect is preferred when its visible option text allows it.
     */
    public static String alternativeAnswer(
            LotroGame game,
            String botName,
            AwaitingDecision decision,
            String repeatedAnswer) {
        if (decision.getDecisionType() == AwaitingDecisionType.CARD_ACTION_CHOICE
                && (repeatedAnswer == null || repeatedAnswer.isEmpty())) {
            String[] actionIds = decision.getDecisionParameters().get("actionId");
            if (actionIds != null) {
                for (String actionId : actionIds) {
                    if (!actionId.equals(repeatedAnswer))
                        return actionId;
                }
            }
        }

        if (decision.getDecisionType() == AwaitingDecisionType.MULTIPLE_CHOICE) {
            String[] results = decision.getDecisionParameters().get("results");
            if (results != null) {
                for (int i = 0; i < results.length; i++) {
                    if (!String.valueOf(i).equals(repeatedAnswer) && isDecliningChoice(results[i]))
                        return String.valueOf(i);
                }
            }
        }

        if (decision.getDecisionType() == AwaitingDecisionType.ARBITRARY_CARDS) {
            String alternative = alternativeArbitraryCardsAnswer(
                    decision.getDecisionParameters(), repeatedAnswer);
            if (alternative != null)
                return alternative;
        }

        return fallbackAnswers(game, botName, decision, repeatedAnswer).stream()
                .findFirst()
                .orElse(null);
    }

    private static String alternativeArbitraryCardsAnswer(
            Map<String, String[]> params,
            String repeatedAnswer) {
        String[] cardIds = params.get("cardId");
        String[] selectable = params.get("selectable");
        if (cardIds == null || selectable == null || cardIds.length != selectable.length)
            return null;

        int minimum;
        int maximum;
        try {
            minimum = Integer.parseInt(params.get("min")[0]);
            maximum = Integer.parseInt(params.get("max")[0]);
        } catch (NullPointerException | IndexOutOfBoundsException | NumberFormatException e) {
            return null;
        }

        List<String> selectableIds = new ArrayList<>();
        for (int i = 0; i < cardIds.length; i++) {
            if (Boolean.parseBoolean(selectable[i]))
                selectableIds.add(cardIds[i]);
        }

        int maxSelectable = Math.min(maximum, selectableIds.size());
        if (minimum < 0 || maxSelectable < minimum)
            return null;

        List<String> selection = new ArrayList<>(selectableIds.subList(0, minimum));
        if (!sameArbitraryCardSelection(selection, repeatedAnswer))
            return String.join(",", selection);

        if (selection.size() < maxSelectable) {
            selection.add(selectableIds.get(selection.size()));
            return String.join(",", selection);
        }

        if (!selection.isEmpty() && selection.size() < selectableIds.size()) {
            selection.set(selection.size() - 1, selectableIds.get(selection.size()));
            return String.join(",", selection);
        }

        return null;
    }

    private static boolean sameArbitraryCardSelection(List<String> selection, String answer) {
        if (answer == null)
            return false;
        if (answer.isEmpty())
            return selection.isEmpty();

        Set<String> answerIds = new LinkedHashSet<>(List.of(answer.split(",")));
        return answerIds.size() == selection.size() && answerIds.containsAll(selection);
    }

    private static boolean isDecliningChoice(String choice) {
        String normalized = choice.trim().toLowerCase(Locale.ROOT);
        return Set.of("no", "stop", "pass", "decline", "cancel", "done", "finish", "end",
                "stay", "skip", "do not continue", "don't continue").contains(normalized);
    }

    static List<String> fallbackAnswers(LotroGame game, String botName, AwaitingDecision decision, String rejected) {
        Set<String> answers = new LinkedHashSet<>();
        AssignmentLegality legality = new AssignmentLegality(game);
        Map<String, String[]> params = decision.getDecisionParameters();

        switch (decision.getDecisionType()) {
            case ASSIGN_MINIONS -> {
                if (rejected != null) {
                    try {
                        answers.add(AssignmentLegality.format(legality.keepLegal(
                                AssignmentLegality.assigningSide(game.getGameState(), botName),
                                params.get("freeCharacters"), params.get("minions"), AssignmentLegality.parse(rejected))));
                    } catch (NumberFormatException ignored) {
                        // unparseable answer: nothing of it to keep
                    }
                }
                answers.add("");
            }
            case CARD_ACTION_CHOICE -> answers.add("");
            default -> {
                RandomDecisionBot random = new RandomDecisionBot(botName);
                for (int i = 0; i < RANDOM_ATTEMPTS; i++) {
                    try {
                        answers.add(random.chooseAction(game.getGameState(), decision, legality));
                    } catch (RuntimeException ignored) {
                        // e.g. a decision with no options; nothing random to offer
                    }
                }
            }
        }
        if (rejected != null)
            answers.remove(rejected);
        return List.copyOf(answers);
    }
}
