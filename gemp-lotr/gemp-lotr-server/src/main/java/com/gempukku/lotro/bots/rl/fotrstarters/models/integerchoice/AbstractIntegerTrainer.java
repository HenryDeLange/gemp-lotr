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
import java.util.Map;

public abstract class AbstractIntegerTrainer extends AbstractTrainer {

    protected abstract String getTextTrigger(); // e.g., "burdens to bid"
    public abstract int getMaxChoice();

    @Override
    public boolean appliesTo(GameState gameState, AwaitingDecision decision, String playerName) {
        return decision.getDecisionType() == AwaitingDecisionType.INTEGER &&
                decision.getText().toLowerCase().contains(getTextTrigger().toLowerCase());
    }

    @Override
    public String getAnswer(GameState gameState, AwaitingDecision decision, String playerName, RLGameStateFeatures features, ModelRegistry modelRegistry) {
        Classifier<double[]> model = modelRegistry.getModel(getClass());
        double[] stateVector = features.extractFeatures(gameState, decision, playerName);
        int[] classes = model.classes();
        double[] probs = new double[classes.length];
        model.predict(stateVector, probs);
        int minimum = integerBound(decision, "min", 0);
        int maximum = Math.min(getMaxChoice(), integerBound(decision, "max", getMaxChoice()));
        if (minimum < 0 || maximum < minimum)
            throw new IllegalArgumentException("Invalid integer choice range for "
                    + decision.getText() + ": " + minimum + ".." + maximum);
        int predictedValue = sampleClass(classes, probs, minimum, maximum);
        return String.valueOf(predictedValue);
    }

    private int sampleClass(int[] classes, double[] probabilities, int minimum, int maximum) {
        if (classes.length == 0 || classes.length != probabilities.length)
            throw new IllegalStateException(getClass().getSimpleName() + " has an invalid class distribution");
        double totalProbability = 0.0;
        int lastLegalClass = -1;
        for (int index = 0; index < probabilities.length; index++) {
            double probability = probabilities[index];
            if (!Double.isFinite(probability) || probability < 0.0)
                throw new IllegalStateException(getClass().getSimpleName()
                        + " produced an invalid class probability");
            if (classes[index] >= minimum && classes[index] <= maximum) {
                totalProbability += probability;
                lastLegalClass = classes[index];
            }
        }
        if (!Double.isFinite(totalProbability) || totalProbability <= 0.0 || lastLegalClass < 0)
            throw new IllegalStateException(getClass().getSimpleName()
                    + " assigned no probability to a legal integer choice");

        double selectedProbability = Math.random() * totalProbability;
        double cumulative = 0.0;
        for (int i = 0; i < probabilities.length; i++) {
            if (classes[i] < minimum || classes[i] > maximum)
                continue;
            cumulative += probabilities[i];
            if (selectedProbability < cumulative)
                return classes[i];
        }
        return lastLegalClass;
    }

    private static int integerBound(AwaitingDecision decision, String name, int defaultValue) {
        Map<String, String[]> parameters = decision.getDecisionParameters();
        String[] value = parameters == null ? null : parameters.get(name);
        if (value == null || value.length == 0)
            return defaultValue;
        try {
            return Integer.parseInt(value[0]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name + " bound for integer decision "
                    + decision.getText(), e);
        }
    }

    protected List<LabeledPoint> extractTrainingData(List<LearningStep> steps) {
        List<LabeledPoint> data = new ArrayList<>();

        for (LearningStep step : steps) {
            if (!isStepRelevant(step)) continue;

            if (step.reward <= 0) continue; // Only learn from positively rewarded steps

            int chosen = ((IntegerChoiceAction) step.action).getValue();
            data.add(new LabeledPoint(chosen, step.state, step.gameId, step.trainingWeight));
        }

        return data;
    }

    @Override
    public boolean isStepRelevant(LearningStep step) {
        return step.decision.getDecisionType() == AwaitingDecisionType.INTEGER &&
                step.decision.getText().toLowerCase().contains(getTextTrigger().toLowerCase()) &&
                step.action instanceof IntegerChoiceAction;
    }
}
