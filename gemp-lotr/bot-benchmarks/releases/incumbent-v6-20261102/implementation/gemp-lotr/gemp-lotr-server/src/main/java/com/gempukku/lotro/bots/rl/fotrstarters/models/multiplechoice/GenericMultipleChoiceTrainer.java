package com.gempukku.lotro.bots.rl.fotrstarters.models.multiplechoice;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.AbstractTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.DecisionOptionFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.LabeledPoint;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.semanticaction.MultipleChoiceAction;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import smile.classification.Classifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GenericMultipleChoiceTrainer extends AbstractTrainer {
    @Override
    public boolean appliesTo(GameState gameState, AwaitingDecision decision, String playerName) {
        return decision.getDecisionType() == AwaitingDecisionType.MULTIPLE_CHOICE
                && hasUnmodeledChoice(decision);
    }

    @Override
    public String getAnswer(
            GameState gameState,
            AwaitingDecision decision,
            String playerName,
            RLGameStateFeatures features,
            ModelRegistry modelRegistry) {
        Classifier<double[]> model = modelRegistry.getModel(getClass());
        double[] state = features.extractFeatures(gameState, decision, playerName);
        String[] options = decision.getDecisionParameters().get("results");
        int bestIndex = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < options.length; index++) {
            double[] probabilities = new double[2];
            model.predict(DecisionOptionFeatures.append(state, options[index], index, options.length), probabilities);
            if (probabilities[1] > bestScore) {
                bestScore = probabilities[1];
                bestIndex = index;
            }
        }
        return String.valueOf(bestIndex);
    }

    @Override
    public boolean isStepRelevant(LearningStep step) {
        if (step.decision.getDecisionType() != AwaitingDecisionType.MULTIPLE_CHOICE
                || !(step.action instanceof MultipleChoiceAction))
            return false;
        return hasUnmodeledChoice(step.decision);
    }

    @Override
    protected List<LabeledPoint> extractTrainingData(List<LearningStep> steps) {
        List<LabeledPoint> points = new ArrayList<>();
        for (LearningStep step : steps) {
            if (!isStepRelevant(step))
                continue;

            String[] options = step.decision.getDecisionParameters().get("results");
            String chosen = ((MultipleChoiceAction) step.action).getChosenOption();
            int chosenIndex = Arrays.asList(options).indexOf(chosen);
            if (chosenIndex < 0)
                continue;

            for (int index = 0; index < options.length; index++) {
                boolean selected = index == chosenIndex;
                if (step.reward <= 0 && !selected)
                    continue;
                int label = step.reward > 0 && selected ? 1 : 0;
                points.add(new LabeledPoint(label,
                        DecisionOptionFeatures.append(step.state, options[index], index, options.length),
                        step.gameId, step.trainingWeight));
            }
        }
        return points;
    }

    private static boolean hasUnmodeledChoice(AwaitingDecision decision) {
        String text = decision.getText().toLowerCase();
        if (text.contains("mulligan") || text.contains("another move"))
            return false;
        String[] options = decision.getDecisionParameters().get("results");
        return options != null && options.length > 1
                && Arrays.stream(options).noneMatch(option -> option.equalsIgnoreCase("Go first"));
    }

}
