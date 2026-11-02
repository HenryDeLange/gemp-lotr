package com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.SkirmishOrderAssignment;
import com.gempukku.lotro.bots.rl.fotrstarters.models.AbstractTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.CardCandidateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.LabeledPoint;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.semanticaction.CardSelectionAssignedAction;
import com.gempukku.lotro.game.CardNotFoundException;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import smile.classification.Classifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class SkirmishOrderTrainer extends AbstractTrainer {
    private static final String SKIRMISH = "next skirmish to resolve";

    public List<LabeledPoint> extractTrainingData(List<LearningStep> steps) {
        List<LabeledPoint> data = new ArrayList<>();

        for (LearningStep step : steps) {
            if (!isStepRelevant(step)) continue;

            CardSelectionAssignedAction action = (CardSelectionAssignedAction) step.action;
            int chosenCount = action.getChosenBlueprintIds().size();
            boolean hasAssignmentContext = action.getAssignedFellowshipBlueprintIds().size() == chosenCount
                    && action.getWoundsOnAssignedFellowship().size() == chosenCount
                    && action.getMinionsOnChosen().size() == chosenCount
                    && action.getStrengthOfMinionsOnChosen().size() == chosenCount;
            boolean hasLegacyContext = action.getMinionsOnChosen().size() == chosenCount
                    && action.getStrengthOfMinionsOnChosen().size() == chosenCount
                    && action.getWoundsOnChosen().size() == chosenCount;
            if (!hasAssignmentContext && !hasLegacyContext)
                continue;

            for (int i = 0; i < chosenCount; i++) {
                try {
                    String blueprintId = hasAssignmentContext
                            ? action.getAssignedFellowshipBlueprintIds().get(i)
                            : action.getChosenBlueprintIds().get(i);
                    int wounds = hasAssignmentContext
                            ? action.getWoundsOnAssignedFellowship().get(i)
                            : action.getWoundsOnChosen().get(i);
                    double[] blueprintVector = CardFeatures.getFpAssignedCardFeatures(blueprintId, wounds,
                            action.getMinionsOnChosen().get(i), action.getStrengthOfMinionsOnChosen().get(i));
                    double[] extended = CardCandidateFeatures.append(step.state, blueprintVector);

                    int label = step.reward > 0 ? 1 : 0;

                    data.add(new LabeledPoint(label, extended, step.gameId, step.trainingWeight));
                } catch (CardNotFoundException ignore) {

                }
            }
        }

        return data;
    }

    @Override
    public boolean isStepRelevant(LearningStep step) {
        return step.decision.getDecisionType() == AwaitingDecisionType.CARD_SELECTION
                && step.decision.getText().toLowerCase().contains(SKIRMISH.toLowerCase())
                && step.action instanceof CardSelectionAssignedAction;
    }

    @Override
    public boolean appliesTo(GameState gameState, AwaitingDecision decision, String playerName) {
        if (decision.getDecisionType() != AwaitingDecisionType.CARD_SELECTION)
            return false;
        return decision.getText().toLowerCase().contains(SKIRMISH.toLowerCase());
    }

    @Override
    public String getAnswer(GameState gameState, AwaitingDecision decision, String playerName, RLGameStateFeatures features, ModelRegistry modelRegistry) {
        List<String> cardIds = Arrays.stream(decision.getDecisionParameters().get("cardId")).toList();

        Classifier<double[]> model = modelRegistry.getModel(SkirmishOrderTrainer.class);
        double[] stateVector = features.extractFeatures(gameState, decision, playerName);
        List<ScoredCard> scoredCards = new ArrayList<>();

        for (String physicalId : cardIds) {
            try {
                var assignment = SkirmishOrderAssignment.findForChoice(
                        gameState.getAssignments(), Integer.parseInt(physicalId));
                if (assignment.isEmpty())
                    continue;

                PhysicalCard fellowshipCharacter = assignment.get().getFellowshipCharacter();
                String blueprintId = fellowshipCharacter.getBlueprintId();
                int wounds = gameState.getWounds(fellowshipCharacter);
                double[] cardVector = CardFeatures.getFpAssignedCardFeatures(blueprintId, wounds,
                        assignment.get().getShadowCharacters().size(), assignment.get().getShadowCharacters().stream()
                                .mapToInt(value -> value.getBlueprint().getStrength()).sum());
                double[] extended = CardCandidateFeatures.append(stateVector, cardVector);

                double[] probs = new double[2];
                model.predict(extended, probs);
                scoredCards.add(new ScoredCard(physicalId, probs[1]));
            } catch (CardNotFoundException ignored) {

            }
        }

        if (scoredCards.isEmpty())
            throw new IllegalStateException("No pending skirmish matches the offered skirmish-order choices");

        // Find the card with the highest probability
        scoredCards.sort(Comparator.comparingDouble(c -> -c.score));
        ScoredCard best = scoredCards.get(0);
        return best.cardId;
    }
}
