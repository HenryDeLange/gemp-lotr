package com.gempukku.lotro.bots.rl.fotrstarters.models.integerchoice;

import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import smile.classification.Classifier;

public class BurdenTrainer extends AbstractIntegerTrainer {
    public static final int DEFAULT_MAXIMUM_BURDENS_TO_BID = 9;
    private final int maximumBurdensToBid;

    public BurdenTrainer() {
        this(DEFAULT_MAXIMUM_BURDENS_TO_BID);
    }

    public BurdenTrainer(int maximumBurdensToBid) {
        this.maximumBurdensToBid = validateMaximumBurdensToBid(maximumBurdensToBid);
    }

    public static int validateMaximumBurdensToBid(int maximumBurdensToBid) {
        if (maximumBurdensToBid < 0 || maximumBurdensToBid > DEFAULT_MAXIMUM_BURDENS_TO_BID)
            throw new IllegalArgumentException("Maximum burdens to bid must be between 0 and "
                    + DEFAULT_MAXIMUM_BURDENS_TO_BID);
        return maximumBurdensToBid;
    }

    @Override
    protected String getTextTrigger() {
        return "burdens to bid";
    }

    public int getMaxChoice() {
        return maximumBurdensToBid;
    }

    @Override
    public String getAnswer(
            GameState gameState,
            AwaitingDecision decision,
            String playerName,
            RLGameStateFeatures features,
            ModelRegistry modelRegistry) {
        return Integer.toString(getAnswerWithDiagnostics(
                gameState, decision, playerName, features, modelRegistry).selectedBid());
    }

    public BidDecision getAnswerWithDiagnostics(
            GameState gameState,
            AwaitingDecision decision,
            String playerName,
            RLGameStateFeatures features,
            ModelRegistry modelRegistry) {
        Classifier<double[]> model = modelRegistry.getModel(getClass());
        double[] stateVector = features.extractFeatures(gameState, decision, playerName);
        int[] classes = model.classes();
        double[] probabilities = new double[classes.length];
        model.predict(stateVector, probabilities);
        int minimum = integerBound(decision, "min", 0);
        int decisionMaximum = integerBound(decision, "max", DEFAULT_MAXIMUM_BURDENS_TO_BID);
        int maximum = getMaximumChoice(decision, minimum);
        if (minimum < 0 || decisionMaximum < minimum || maximum < minimum)
            throw new IllegalArgumentException("Invalid integer choice range for "
                    + decision.getText() + ": " + minimum + ".." + decisionMaximum);

        int uncappedMaximum = Math.max(
                minimum, Math.min(decisionMaximum, DEFAULT_MAXIMUM_BURDENS_TO_BID));
        IntegerChoiceSample sample = sampleClassWithAlternativeMaximum(
                classes, probabilities, minimum, maximum, uncappedMaximum);
        boolean minimumExceedsCap = minimum > maximumBurdensToBid;
        boolean capConstrained = !minimumExceedsCap && sample.alternativeValue() > maximumBurdensToBid;
        return new BidDecision(sample.selectedValue(), capConstrained, minimumExceedsCap);
    }

    public boolean engineMinimumExceedsMaximumBid(AwaitingDecision decision) {
        return integerBound(decision, "min", 0) > maximumBurdensToBid;
    }

    @Override
    protected int getMaximumChoice(AwaitingDecision decision, int minimum) {
        int decisionMaximum = integerBound(decision, "max", DEFAULT_MAXIMUM_BURDENS_TO_BID);
        if (decisionMaximum < minimum)
            return decisionMaximum;
        return Math.max(minimum, Math.min(maximumBurdensToBid, decisionMaximum));
    }

    public String applyMaximumBid(String answer, AwaitingDecision decision) {
        int minimum = integerBound(decision, "min", 0);
        int decisionMaximum = integerBound(decision, "max", DEFAULT_MAXIMUM_BURDENS_TO_BID);
        int maximum = getMaximumChoice(decision, minimum);
        if (minimum < 0 || decisionMaximum < minimum || maximum < minimum)
            throw new IllegalArgumentException("Invalid integer choice range for "
                    + decision.getText() + ": " + minimum + ".." + decisionMaximum);

        int value;
        try {
            value = Integer.parseInt(answer);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid burden bid answer: " + answer, e);
        }
        if (value < minimum || value > decisionMaximum)
            throw new IllegalArgumentException("Burden bid answer is outside the engine range: "
                    + value + " not in " + minimum + ".." + decisionMaximum);
        return Integer.toString(Math.min(value, maximum));
    }

    public record BidDecision(int selectedBid, boolean capConstrained, boolean engineMinimumExceedsCap) {
    }
}
