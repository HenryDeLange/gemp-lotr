package com.gempukku.lotro.bots.rl.fotrstarters.models.cardselection;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.AbstractTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.CardCandidateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.LabeledPoint;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.semanticaction.CardSelectionAction;
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

public abstract class AbstractCardSelectionTrainer extends AbstractTrainer {

    protected abstract String getTextTrigger();
    protected boolean useNotChosen() { return false; }
    protected boolean includePassOption() { return false; }
    protected String getZoneString() { return null; }

    @Override
    public boolean appliesTo(GameState gameState, AwaitingDecision decision, String playerName) {
        if (decision.getDecisionType() != AwaitingDecisionType.CARD_SELECTION)
            return false;
        if (!decision.getText().toLowerCase().contains(getTextTrigger().toLowerCase()))
            return false;

        String requiredZone = getZoneString();
        if (requiredZone == null) {
            return true;
        }

        String zoneOfAllCards = null;
            String[] allChoices = decision.getDecisionParameters().get("cardId");
            for (String choice : allChoices) {
                for (PhysicalCard physicalCard : gameState.getAllCards()) {
                    if (physicalCard.getCardId() == Integer.parseInt(choice)) {
                        String zoneOfThisCard = physicalCard.getZone().getHumanReadable();
                        if (zoneOfAllCards == null) {
                            zoneOfAllCards = zoneOfThisCard;
                        }
                        if (!zoneOfAllCards.equals(zoneOfThisCard)) {
                            zoneOfAllCards = "zones mismatched";
                        }
                    }
                }
            }
        return requiredZone.equals(zoneOfAllCards);
    }

    @Override
    public String getAnswer(GameState gameState, AwaitingDecision decision, String playerName, RLGameStateFeatures features, ModelRegistry modelRegistry) {
        String[] minimumValues = decision.getDecisionParameters().get("min");
        int min = minimumValues == null || minimumValues.length == 0
                ? 0 : Integer.parseInt(minimumValues[0]);
        int max = Integer.parseInt(decision.getDecisionParameters().get("max")[0]);
        if (min < 0 || max < min)
            throw new IllegalArgumentException("Invalid selection range for card decision: " + min + ".." + max);
        List<String> cardIds = Arrays.stream(decision.getDecisionParameters().get("cardId")).toList();

        Classifier<double[]> model = modelRegistry.getModel(getClass());
        double[] stateVector = features.extractFeatures(gameState, decision, playerName);
        List<ScoredCard> scoredCards = new ArrayList<>();

        for (String physicalId : cardIds) {
            try {
                String blueprintId = gameState.getBlueprintId(Integer.parseInt(physicalId));
                int wounds = 0;
                for (PhysicalCard physicalCard : gameState.getInPlay()) {
                    if (physicalCard.getCardId() == Integer.parseInt(physicalId)) {
                        wounds = gameState.getWounds(physicalCard);
                    }
                }
                double[] cardVector = getCardVector(blueprintId, wounds);
                double[] extended = CardCandidateFeatures.append(stateVector, cardVector);

                double[] probs = new double[2];
                model.predict(extended, probs);
                scoredCards.add(new ScoredCard(physicalId, probs[1]));
            } catch (CardNotFoundException ignored) {

            }
        }

        scoredCards.sort(Comparator.comparingDouble(c -> -c.score));
        int maximumSelectable = Math.min(max, scoredCards.size());
        if (maximumSelectable < min)
            throw new IllegalStateException("Too few valid cards for selection decision: " + decision.getText());

        double passScore = Double.NEGATIVE_INFINITY;
        if (includePassOption() && maximumSelectable > min) {
            try {
                double[] passVector = CardCandidateFeatures.append(
                        stateVector, getCardVector(CardFeatures.PASS, 0));
                double[] probabilities = new double[2];
                model.predict(passVector, probabilities);
                passScore = probabilities[1];
            } catch (CardNotFoundException e) {
                throw new IllegalStateException("Unable to score the optional pass choice", e);
            }
        }

        List<String> chosenIds = new ArrayList<>(maximumSelectable);
        for (ScoredCard scoredCard : scoredCards) {
            if (chosenIds.size() >= maximumSelectable)
                break;
            if (chosenIds.size() < min || !includePassOption() || scoredCard.score > passScore)
                chosenIds.add(scoredCard.cardId);
        }
        return String.join(",", chosenIds);
    }

    @Override
    public boolean isStepRelevant(LearningStep step) {
        if (step.decision.getDecisionType() != AwaitingDecisionType.CARD_SELECTION)
            return false;

        if (!(step.action instanceof CardSelectionAction csa))
            return false;


        int min = Integer.parseInt(step.decision.getDecisionParameters().get("min")[0]);
        int max = Integer.parseInt(step.decision.getDecisionParameters().get("max")[0]);
        List<String> cardIds = Arrays.stream(step.decision.getDecisionParameters().get("cardId")).toList();
        if (min == max && min == cardIds.size()) {
            // Had to choose all, nothing to learn from
            return false;
        }

        if (!step.decision.getText().toLowerCase().contains(getTextTrigger().toLowerCase()))
            return false;

        String requiredZone = getZoneString();
        return requiredZone == null || requiredZone.equals(csa.getZoneString());
    }

    @Override
    public List<LabeledPoint> extractTrainingData(List<LearningStep> steps) {
        List<LabeledPoint> data = new ArrayList<>();

        for (LearningStep step : steps) {
            if (!isStepRelevant(step)) continue;

            CardSelectionAction action = (CardSelectionAction) step.action;

            if (step.reward > 0) {
                // Chosen: good
                addLabeledPoints(data, action.getChosenBlueprintIds(), action.getWoundsOnChosen(),
                        step.state, step.gameId, step.trainingWeight, 1);

                if (useNotChosen()) {
                    // Not chosen: bad
                    addLabeledPoints(data, action.getNotChosenBlueprintIds(), action.getWoundsOnNotChosen(),
                            step.state, step.gameId, step.trainingWeight, 0);
                }
            } else {
                // Chosen: bad
                addLabeledPoints(data, action.getChosenBlueprintIds(), action.getWoundsOnChosen(),
                        step.state, step.gameId, step.trainingWeight, 0);

                // Not chosen: ambiguous → skip
            }

            if (includePassOption()
                    && minimumSelectionCount(step.decision) == 0
                    && (action.getChosenBlueprintIds().isEmpty() || step.reward > 0)) {
                int label = action.getChosenBlueprintIds().isEmpty()
                        ? (step.reward > 0 ? 1 : 0)
                        : 0;
                addLabeledPoints(data, List.of(CardFeatures.PASS), List.of(0),
                        step.state, step.gameId, step.trainingWeight, label);
            }
        }

        return data;
    }

    private static int minimumSelectionCount(AwaitingDecision decision) {
        String[] minimumValues = decision.getDecisionParameters().get("min");
        return minimumValues == null || minimumValues.length == 0
                ? 0 : Integer.parseInt(minimumValues[0]);
    }

    protected void addLabeledPoints(List<LabeledPoint> data, List<String> blueprintIds, List<Integer> wounds,
                                  double[] state, String gameId, double trainingWeight, int label) {
        for (int i = 0; i < blueprintIds.size(); i++) {
            try {
                double[] cardVector = getCardVector(blueprintIds.get(i), wounds.get(i));
                double[] extended = CardCandidateFeatures.append(state, cardVector);
                data.add(new LabeledPoint(label, extended, gameId, trainingWeight));
            } catch (CardNotFoundException ignore) {
            }
        }
    }

    protected double[] getCardVector(String blueprintId, int wounds) throws CardNotFoundException {
        return CardFeatures.getCardFeatures(blueprintId, wounds);
    }
}
