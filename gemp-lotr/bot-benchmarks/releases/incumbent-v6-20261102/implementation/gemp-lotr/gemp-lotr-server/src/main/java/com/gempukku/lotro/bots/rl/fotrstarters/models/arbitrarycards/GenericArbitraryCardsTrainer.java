package com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.CardCandidateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.game.CardNotFoundException;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import smile.classification.Classifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GenericArbitraryCardsTrainer extends AbstractArbitraryCardsTrainer {
    private static final double MINIMUM_OPTION_SCORE = 0.5;

    @Override
    protected boolean useNotChosen() {
        return true;
    }

    @Override
    public String getAnswer(
            GameState gameState,
            AwaitingDecision decision,
            String playerName,
            RLGameStateFeatures features,
            ModelRegistry modelRegistry) {
        String[] cardIds = decision.getDecisionParameters().get("cardId");
        String[] blueprintIds = decision.getDecisionParameters().get("blueprintId");
        String[] selectable = decision.getDecisionParameters().get("selectable");
        if (cardIds == null || blueprintIds == null || selectable == null
                || cardIds.length != blueprintIds.length || cardIds.length != selectable.length)
            throw new IllegalArgumentException("Invalid card options for arbitrary-card decision: " + decision.getText());

        int minimum = parseBound(decision, "min", 0);
        int maximum = parseBound(decision, "max", cardIds.length);
        if (minimum < 0 || maximum < minimum)
            throw new IllegalArgumentException("Invalid selection range for arbitrary-card decision: "
                    + minimum + ".." + maximum);

        Classifier<double[]> model = modelRegistry.getModel(getClass());
        double[] state = features.extractFeatures(gameState, decision, playerName);
        List<ScoredCard> candidates = new ArrayList<>();
        for (int i = 0; i < cardIds.length; i++) {
            if (!Boolean.parseBoolean(selectable[i]))
                continue;
            try {
                double[] candidate = CardCandidateFeatures.append(state, getCardVector(blueprintIds[i]));
                double[] probabilities = new double[2];
                model.predict(candidate, probabilities);
                candidates.add(new ScoredCard(cardIds[i], probabilities[1]));
            } catch (CardNotFoundException e) {
                continue;
            }
        }

        candidates.sort(Comparator.comparingDouble(ScoredCard::score).reversed());
        int maximumSelectable = Math.min(maximum, candidates.size());
        if (maximumSelectable < minimum)
            throw new IllegalStateException("Too few valid cards for arbitrary-card decision: " + decision.getText());

        List<String> chosenCardIds = new ArrayList<>(maximumSelectable);
        for (ScoredCard candidate : candidates) {
            if (chosenCardIds.size() >= maximumSelectable)
                break;
            if (chosenCardIds.size() < minimum || candidate.score() >= MINIMUM_OPTION_SCORE)
                chosenCardIds.add(candidate.cardId());
        }
        return String.join(",", chosenCardIds);
    }

    private static int parseBound(AwaitingDecision decision, String name, int defaultValue) {
        String[] values = decision.getDecisionParameters().get(name);
        return values == null || values.length == 0 ? defaultValue : Integer.parseInt(values[0]);
    }

    private record ScoredCard(String cardId, double score) {
    }
}
