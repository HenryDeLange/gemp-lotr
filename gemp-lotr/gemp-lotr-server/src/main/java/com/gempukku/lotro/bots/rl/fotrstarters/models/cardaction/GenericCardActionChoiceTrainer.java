package com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.AbstractTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.DecisionOptionFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.LabeledPoint;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.semanticaction.CardActionChoiceAction;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import smile.classification.Classifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GenericCardActionChoiceTrainer extends AbstractTrainer {
    private static final String PASS_OPTION = "Pass";

    @Override
    public boolean appliesTo(GameState gameState, AwaitingDecision decision, String playerName) {
        return hasOptions(decision);
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
        int optionCount = options.length + 1;
        int bestIndex = options.length;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < optionCount; index++) {
            String option = index == options.length ? PASS_OPTION : options[index];
            double[] probabilities = new double[2];
            model.predict(DecisionOptionFeatures.append(state, option, index, optionCount), probabilities);
            if (probabilities[1] > bestScore) {
                bestScore = probabilities[1];
                bestIndex = index;
            }
        }
        return bestIndex == options.length ? "" : String.valueOf(bestIndex);
    }

    @Override
    public boolean isStepRelevant(LearningStep step) {
        return step.decision.getDecisionType() == AwaitingDecisionType.CARD_ACTION_CHOICE
                && step.action instanceof CardActionChoiceAction
                && hasOptions(step.decision);
    }

    @Override
    protected List<LabeledPoint> extractTrainingData(List<LearningStep> steps) {
        List<LabeledPoint> points = new ArrayList<>();
        for (LearningStep step : steps) {
            if (!isStepRelevant(step))
                continue;

            CardActionChoiceAction action = (CardActionChoiceAction) step.action;
            String[] options = step.decision.getDecisionParameters().get("actionText");
            int selectedIndex = action.getSelectedActionIndex();
            int chosenIndex = selectedIndex >= 0
                    ? selectedIndex
                    : action.getActionText() == null
                    ? options.length
                    : Arrays.asList(options).indexOf(action.getActionText());
            if (chosenIndex < 0 || chosenIndex > options.length)
                continue;

            int optionCount = options.length + 1;
            for (int index = 0; index < optionCount; index++) {
                boolean selected = index == chosenIndex;
                if (step.reward <= 0 && !selected)
                    continue;
                String option = index == options.length ? PASS_OPTION : options[index];
                int label = step.reward > 0 && selected ? 1 : 0;
                points.add(new LabeledPoint(label,
                        DecisionOptionFeatures.append(step.state, option, index, optionCount),
                        step.gameId, step.trainingWeight));
            }
        }
        return points;
    }

    private static boolean hasOptions(AwaitingDecision decision) {
        if (decision.getDecisionType() != AwaitingDecisionType.CARD_ACTION_CHOICE)
            return false;
        String[] actionIds = decision.getDecisionParameters().get("actionId");
        String[] actionTexts = decision.getDecisionParameters().get("actionText");
        return actionIds != null && actionIds.length > 0
                && actionTexts != null && actionIds.length == actionTexts.length;
    }
}
