package com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.AbstractTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.CardCandidateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.LabeledPoint;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.semanticaction.ChooseFromArbitraryCardsAction;
import com.gempukku.lotro.game.CardNotFoundException;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import smile.classification.Classifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public abstract class AbstractArbitraryCardsTrainer extends AbstractTrainer {

    protected String getTextTrigger() {
        return null;
    }

    protected boolean useNotChosen() { return false; }

    protected double getMinimumOptionScore() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public boolean appliesTo(GameState gameState, AwaitingDecision decision, String playerName) {
        return matchesDecision(decision);
    }

    protected boolean matchesDecision(AwaitingDecision decision) {
        if (decision.getDecisionType() != AwaitingDecisionType.ARBITRARY_CARDS)
            return false;
        String textTrigger = getTextTrigger();
        return textTrigger == null
                || decision.getText().toLowerCase().contains(textTrigger.toLowerCase());
    }

    @Override
    public String getAnswer(GameState gameState, AwaitingDecision decision, String playerName, RLGameStateFeatures features, ModelRegistry modelRegistry) {
        int max = Integer.parseInt(decision.getDecisionParameters().get("max")[0]);
        String[] minimumValues = decision.getDecisionParameters().get("min");
        int min = minimumValues == null || minimumValues.length == 0
                ? 0 : Integer.parseInt(minimumValues[0]);
        if (min < 0 || max < min)
            throw new IllegalArgumentException("Invalid selection range for arbitrary-card decision: " + min + ".." + max);

        List<String> selectableIds = new ArrayList<>();
        List<String> selectableBlueprints = new ArrayList<>();
        List<String> cardIds = Arrays.stream(decision.getDecisionParameters().get("cardId")).toList();
        List<String> blueprints = Arrays.stream(decision.getDecisionParameters().get("blueprintId")).toList();
        List<String> selectable = Arrays.stream(decision.getDecisionParameters().get("selectable")).toList();

        for (int i = 0; i < cardIds.size() && i < selectable.size(); i++) {
            if (Boolean.parseBoolean(selectable.get(i))) {
                selectableIds.add(cardIds.get(i));
                selectableBlueprints.add(blueprints.get(i));
            }
        }

        Classifier<double[]> model = modelRegistry.getModel(getClass());
        double[] stateVector = features.extractFeatures(gameState, decision, playerName);
        List<ScoredCard> scoredCards = new ArrayList<>();

        for (int i = 0; i < selectableBlueprints.size(); i++) {
            try {
                String blueprintId = selectableBlueprints.get(i);
                double[] extended = getCandidateVector(stateVector, blueprintId);

                double[] probs = new double[2];
                model.predict(getModelInputVector(model, extended), probs);
                scoredCards.add(new ScoredCard(selectableIds.get(i), probs[1]));
            } catch (CardNotFoundException ignored) {

            }
        }

        scoredCards.sort(Comparator.comparingDouble(c -> -c.score));
        int maximumSelectable = Math.min(max, scoredCards.size());
        if (maximumSelectable < min)
            throw new IllegalStateException("Too few valid cards for arbitrary-card decision: " + decision.getText());

        List<String> chosenIds = new ArrayList<>(maximumSelectable);
        for (ScoredCard scoredCard : scoredCards) {
            if (chosenIds.size() >= maximumSelectable)
                break;
            if (chosenIds.size() < min || scoredCard.score >= getMinimumOptionScore())
                chosenIds.add(scoredCard.cardId);
        }
        return String.join(",", chosenIds);
    }

    protected double[] getCandidateVector(double[] state, String blueprintId) throws CardNotFoundException {
        return CardCandidateFeatures.append(state, getCardVector(blueprintId));
    }

    @Override
    protected double[] getModelInputVector(Classifier<double[]> model, double[] candidateVector) {
        return candidateVector;
    }

    @Override
    public boolean isStepRelevant(LearningStep step) {
        return step.action instanceof ChooseFromArbitraryCardsAction
                && matchesDecision(step.decision);
    }

    @Override
    public List<LabeledPoint> extractTrainingData(List<LearningStep> steps) {
        List<LabeledPoint> data = new ArrayList<>();

        for (LearningStep step : steps) {
            if (!isStepRelevant(step)) continue;

            ChooseFromArbitraryCardsAction action = (ChooseFromArbitraryCardsAction) step.action;

            if (step.reward > 0) {
                // Chosen: good
                addLabeledPoints(data, action.getChosenBlueprintIds(), step.state, step.gameId,
                        step.trainingWeight, 1);

                if (useNotChosen()) {
                    // Not chosen: bad
                    addLabeledPoints(data, action.getNotChosenBlueprintIds(), step.state, step.gameId,
                            step.trainingWeight, 0);
                }
            } else {
                // Chosen: bad
                addLabeledPoints(data, action.getChosenBlueprintIds(), step.state, step.gameId,
                        step.trainingWeight, 0);

                // Not chosen: ambiguous → skip
            }
        }

        return data;
    }

    protected void addLabeledPoints(List<LabeledPoint> data, List<String> blueprintIds,
                                    double[] state, String gameId, double trainingWeight, int label) {
        for (String blueprintId : blueprintIds) {
            try {
                double[] extended = getCandidateVector(state, blueprintId);
                data.add(new LabeledPoint(label, extended, gameId, trainingWeight));
            } catch (CardNotFoundException ignore) {
                System.out.println(ignore.getMessage());
            }
        }
    }

    protected double[] getCardVector(String blueprintId) throws CardNotFoundException {
        return CardFeatures.getCardFeatures(blueprintId, 0);
    }
}
