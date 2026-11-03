package com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.LabeledPoint;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelIO;
import com.gempukku.lotro.bots.rl.semanticaction.ChooseFromArbitraryCardsAction;
import com.gempukku.lotro.game.CardNotFoundException;
import smile.classification.Classifier;

import java.util.ArrayList;
import java.util.List;

public class StartingFellowshipTrainer extends AbstractArbitraryCardsTrainer {
    private static final double MINIMUM_OPTION_SCORE = 0.5;

    @Override
    protected String getTextTrigger() {
        return "starting fellowship";
    }

    @Override
    protected boolean useNotChosen() {
        return true;
    }

    @Override
    protected double getMinimumOptionScore() {
        return MINIMUM_OPTION_SCORE;
    }

    @Override
    protected double[] getCandidateVector(double[] state, String blueprintId) throws CardNotFoundException {
        return StartingFellowshipCandidateFeatures.append(state, getCardVector(blueprintId), blueprintId);
    }

    @Override
    protected double[] getModelInputVector(Classifier<double[]> model, double[] candidateVector) {
        return ModelIO.adaptModelInputVector(model, candidateVector,
                candidateVector.length - StartingFellowshipCandidateFeatures.FEATURE_COUNT);
    }

    @Override
    public List<LabeledPoint> extractTrainingData(List<LearningStep> steps) {
        List<LabeledPoint> data = new ArrayList<>(super.extractTrainingData(steps));
        for (LearningStep step : steps) {
            if (!isStepRelevant(step))
                continue;

            ChooseFromArbitraryCardsAction action = (ChooseFromArbitraryCardsAction) step.action;
            if (action.getChosenBlueprintIds().isEmpty()) {
                addLabeledPoints(data, List.of(CardFeatures.PASS), step.state, step.gameId,
                        step.trainingWeight, step.reward > 0 ? 1 : 0);
            } else if (step.reward > 0) {
                addLabeledPoints(data, List.of(CardFeatures.PASS), step.state, step.gameId,
                        step.trainingWeight, 0);
            }
        }
        return data;
    }
}
