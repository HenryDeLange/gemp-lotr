package com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.AbstractTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.DecisionOptionFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.LabeledPoint;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.semanticaction.ActionChoiceAction;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import smile.classification.Classifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GenericActionChoiceTrainer extends AbstractTrainer {
    @Override
    public boolean appliesTo(GameState gameState, AwaitingDecision decision, String playerName) {
        String[] options = decision.getDecisionParameters().get("actionText");
        return decision.getDecisionType() == AwaitingDecisionType.ACTION_CHOICE
                && options != null && options.length > 0;
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
        String[] options = decision.getDecisionParameters().get("actionText");
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
        return step.decision.getDecisionType() == AwaitingDecisionType.ACTION_CHOICE
                && step.action instanceof ActionChoiceAction
                && step.decision.getDecisionParameters().get("actionText") != null;
    }

    @Override
    protected List<LabeledPoint> extractTrainingData(List<LearningStep> steps) {
        List<LabeledPoint> points = new ArrayList<>();
        for (LearningStep step : steps) {
            if (!isStepRelevant(step))
                continue;
            String[] options = step.decision.getDecisionParameters().get("actionText");
            String chosen = ((ActionChoiceAction) step.action).getActionText();
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
}
