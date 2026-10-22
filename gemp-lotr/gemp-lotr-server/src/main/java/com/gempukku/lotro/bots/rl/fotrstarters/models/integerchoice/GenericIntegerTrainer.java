package com.gempukku.lotro.bots.rl.fotrstarters.models.integerchoice;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.AbstractTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.LabeledPoint;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.semanticaction.IntegerChoiceAction;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import smile.classification.Classifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GenericIntegerTrainer extends AbstractTrainer {
    private static final int MAXIMUM_OPTIONS = 256;
    private static final String BURDEN_BID = "burdens to bid";
    private static final String SPOT_COUNT = "how many to spot";

    @Override
    public boolean appliesTo(GameState gameState, AwaitingDecision decision, String playerName) {
        return isGenericIntegerDecision(decision);
    }

    @Override
    public String getAnswer(
            GameState gameState,
            AwaitingDecision decision,
            String playerName,
            RLGameStateFeatures features,
            ModelRegistry modelRegistry) {
        Range range = getRange(decision);
        if (range == null)
            throw new IllegalArgumentException("Unsupported integer decision range: " + decision.getText());

        Classifier<double[]> model = modelRegistry.getModel(getClass());
        double[] state = features.extractFeatures(gameState, decision, playerName);
        int bestValue = range.minimum();
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int value = range.minimum(); value <= range.maximum(); value++) {
            double[] probabilities = new double[2];
            model.predict(IntegerOptionFeatures.append(state, value, range.minimum(), range.maximum()), probabilities);
            if (probabilities[1] > bestScore) {
                bestScore = probabilities[1];
                bestValue = value;
            }
            if (value == Integer.MAX_VALUE)
                break;
        }
        return Integer.toString(bestValue);
    }

    @Override
    public boolean isStepRelevant(LearningStep step) {
        return isGenericIntegerDecision(step.decision)
                && step.action instanceof IntegerChoiceAction;
    }

    @Override
    protected List<LabeledPoint> extractTrainingData(List<LearningStep> steps) {
        List<LabeledPoint> points = new ArrayList<>();
        for (LearningStep step : steps) {
            if (!isStepRelevant(step))
                continue;

            Range range = getRange(step.decision);
            int chosen = ((IntegerChoiceAction) step.action).getValue();
            if (chosen < range.minimum() || chosen > range.maximum())
                throw new IllegalArgumentException("Recorded integer answer is outside its legal range: "
                        + step.decision.getText());

            for (int value = range.minimum(); value <= range.maximum(); value++) {
                boolean selected = value == chosen;
                if (step.reward <= 0 && !selected)
                    continue;
                int label = step.reward > 0 && selected ? 1 : 0;
                points.add(new LabeledPoint(label,
                        IntegerOptionFeatures.append(step.state, value, range.minimum(), range.maximum()),
                        step.gameId, step.trainingWeight));
                if (value == Integer.MAX_VALUE)
                    break;
            }
        }
        return points;
    }

    private static Range getRange(AwaitingDecision decision) {
        String[] minimumParameter = decision.getDecisionParameters().get("min");
        String[] maximumParameter = decision.getDecisionParameters().get("max");
        Integer minimum = parseBound(minimumParameter, 0);
        if (minimum == null)
            return null;
        Integer maximum = parseBound(maximumParameter, 9);
        if (maximum == null || maximum < minimum)
            return null;
        long optionCount = (long) maximum - minimum + 1;
        return optionCount <= MAXIMUM_OPTIONS ? new Range(minimum, maximum) : null;
    }

    private static boolean isGenericIntegerDecision(AwaitingDecision decision) {
        if (decision.getDecisionType() != AwaitingDecisionType.INTEGER)
            return false;
        String text = decision.getText().toLowerCase(Locale.ROOT);
        return !text.contains(BURDEN_BID)
                && !text.contains(SPOT_COUNT)
                && getRange(decision) != null;
    }

    private static Integer parseBound(String[] value, int defaultValue) {
        if (value == null || value.length == 0)
            return defaultValue;
        try {
            return Integer.parseInt(value[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Range(int minimum, int maximum) {
    }
}
