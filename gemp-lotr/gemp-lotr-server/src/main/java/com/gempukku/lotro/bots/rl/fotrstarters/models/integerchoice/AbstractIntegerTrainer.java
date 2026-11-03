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
        int maximum = getMaximumChoice(decision, minimum);
        if (minimum < 0 || maximum < minimum)
            throw new IllegalArgumentException("Invalid integer choice range for "
                    + decision.getText() + ": " + minimum + ".." + maximum);
        IntegerChoiceSample sample = sampleClassWithAlternativeMaximum(
                classes, probs, minimum, maximum, maximum);
        return String.valueOf(sample.selectedValue());
    }

    protected int getMaximumChoice(AwaitingDecision decision, int minimum) {
        return Math.min(getMaxChoice(), integerBound(decision, "max", getMaxChoice()));
    }

    protected IntegerChoiceSample sampleClassWithAlternativeMaximum(
            int[] classes,
            double[] probabilities,
            int minimum,
            int maximum,
            int alternativeMaximum) {
        return sampleClassWithAlternativeMaximum(
                classes, probabilities, minimum, maximum, alternativeMaximum, Math.random());
    }

    protected IntegerChoiceSample sampleClassWithAlternativeMaximum(
            int[] classes,
            double[] probabilities,
            int minimum,
            int maximum,
            int alternativeMaximum,
            double quantile) {
        if (classes.length == 0 || classes.length != probabilities.length)
            throw new IllegalStateException(getClass().getSimpleName() + " has an invalid class distribution");
        if (maximum < minimum || alternativeMaximum < maximum)
            throw new IllegalArgumentException("Invalid integer choice bounds");
        if (!Double.isFinite(quantile) || quantile < 0.0 || quantile >= 1.0)
            throw new IllegalArgumentException("Choice quantile must be in [0, 1)");
        double totalProbability = 0.0;
        double alternativeTotalProbability = 0.0;
        int lastLegalClass = -1;
        int lastAlternativeClass = -1;
        for (int index = 0; index < probabilities.length; index++) {
            double probability = probabilities[index];
            if (!Double.isFinite(probability) || probability < 0.0)
                throw new IllegalStateException(getClass().getSimpleName()
                        + " produced an invalid class probability");
            if (classes[index] >= minimum && classes[index] <= maximum) {
                totalProbability += probability;
                lastLegalClass = classes[index];
            }
            if (classes[index] >= minimum && classes[index] <= alternativeMaximum) {
                alternativeTotalProbability += probability;
                lastAlternativeClass = classes[index];
            }
        }
        if (!Double.isFinite(totalProbability) || totalProbability <= 0.0 || lastLegalClass < 0)
            throw new IllegalStateException(getClass().getSimpleName()
                    + " assigned no probability to a legal integer choice");
        if (!Double.isFinite(alternativeTotalProbability) || alternativeTotalProbability <= 0.0
                || lastAlternativeClass < 0)
            throw new IllegalStateException(getClass().getSimpleName()
                    + " assigned no probability to an alternative integer choice");

        int selected = selectClass(classes, probabilities, minimum, maximum,
                quantile * totalProbability, lastLegalClass);
        int alternative = selectClass(classes, probabilities, minimum, alternativeMaximum,
                quantile * alternativeTotalProbability, lastAlternativeClass);
        return new IntegerChoiceSample(selected, alternative);
    }

    private static int selectClass(
            int[] classes,
            double[] probabilities,
            int minimum,
            int maximum,
            double selectedProbability,
            int lastLegalClass) {
        double cumulative = 0.0;
        for (int index = 0; index < probabilities.length; index++) {
            if (classes[index] < minimum || classes[index] > maximum)
                continue;
            cumulative += probabilities[index];
            if (selectedProbability < cumulative)
                return classes[index];
        }
        return lastLegalClass;
    }

    protected record IntegerChoiceSample(int selectedValue, int alternativeValue) {
    }

    protected static int integerBound(AwaitingDecision decision, String name, int defaultValue) {
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
