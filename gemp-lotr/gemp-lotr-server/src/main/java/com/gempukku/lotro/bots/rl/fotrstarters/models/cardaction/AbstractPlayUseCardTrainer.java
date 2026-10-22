package com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.AbstractTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.CardCandidateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.LabeledPoint;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.rl.semanticaction.CardActionChoiceAction;
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

public abstract class AbstractPlayUseCardTrainer extends AbstractTrainer {
    private static final String PLAY = "Play";
    private static final String USE = "Use";
    private static final String HEAL = "Heal";
    private static final String TRANSFER = "Transfer";

    protected String getAllowedActionText() {
        if (isPlayTrainer()) {
            return PLAY;
        } else if (isUseTrainer()) {
            return USE;
        } else if (isHealTrainer()) {
            return HEAL;
        } else if (isTransferTrainer()) {
            return TRANSFER;
        } else {
            throw new IllegalStateException("Trainer is of unknown type.");
        }
    }

    protected abstract String getTextTrigger();
    protected abstract boolean isPlayTrainer();
    protected abstract boolean isUseTrainer();
    protected abstract boolean isTransferTrainer();
    protected abstract boolean isHealTrainer();

    @Override
    public boolean appliesTo(GameState gameState, AwaitingDecision decision, String playerName) {
        String[] actionIds = decision.getDecisionParameters().get("actionId");
        if (actionIds == null || actionIds.length == 0) {
            // No actions available: degenerate decision
            return false;
        }

        if (decision.getDecisionType() != AwaitingDecisionType.CARD_ACTION_CHOICE)
            return false;

        if (!decision.getText().toLowerCase().contains(getTextTrigger().toLowerCase())) {
            return false;
        }

        return Arrays.stream(decision.getDecisionParameters().get("actionText")).anyMatch(s -> s.startsWith(getAllowedActionText()));
    }

    @Override
    public boolean isStepRelevant(LearningStep step) {
        if (step.decision.getDecisionType() != AwaitingDecisionType.CARD_ACTION_CHOICE)
            return false;

        if (!(step.action instanceof CardActionChoiceAction caca))
            return false;

        String[] actionIds = step.decision.getDecisionParameters().get("actionId");
        if (actionIds == null || actionIds.length == 0) {
            // No actions available: degenerate decision
            return false;
        }

        if (!step.decision.getText().toLowerCase().contains(getTextTrigger().toLowerCase())) {
            return false;
        }

        return Arrays.stream(step.decision.getDecisionParameters().get("actionText")).anyMatch(s -> s.startsWith(getAllowedActionText())) && (caca.getActionText() == null || caca.getActionText().startsWith(getAllowedActionText()));
    }

    @Override
    public String getAnswer(GameState gameState, AwaitingDecision decision, String playerName, RLGameStateFeatures features, ModelRegistry modelRegistry) {
        List<String> cardIds = Arrays.stream(decision.getDecisionParameters().get("cardId")).toList();
        List<String> actions = Arrays.stream(decision.getDecisionParameters().get("actionText")).toList();

        Classifier<double[]> model = modelRegistry.getModel(getClass());
        double[] stateVector = features.extractFeatures(gameState, decision, playerName);
        List<ScoredAction> scoredActions = new ArrayList<>();

        for (int actionIndex = 0; actionIndex < cardIds.size(); actionIndex++) {
            String physicalId = cardIds.get(actionIndex);
            try {
                if (!actions.get(actionIndex).startsWith(getAllowedActionText())) {
                    continue;
                }

                String blueprintId = CardActionChoiceAction.resolveSourceBlueprint(gameState, decision, actionIndex);
                if (blueprintId == null) {
                    continue;
                }
                int wounds = 0;
                for (PhysicalCard physicalCard : gameState.getInPlay()) {
                    if (physicalCard.getCardId() == Integer.parseInt(physicalId)) {
                        wounds = gameState.getWounds(physicalCard);
                    }
                }
                double[] cardVector = getCardFeatures(blueprintId, wounds, gameState, physicalId);
                if (cardVector == null) {
                    continue;
                }
                double[] extended = CardCandidateFeatures.append(stateVector, cardVector);

                double[] probs = new double[2];
                model.predict(extended, probs);
                scoredActions.add(new ScoredAction(actionIndex, probs[1]));
            } catch (CardNotFoundException ignored) {

            }
        }

        // Add pass option
        try {
            double[] cardVector = getPassCardFeatures();
            double[] extended = CardCandidateFeatures.append(stateVector, cardVector);

            double[] probs = new double[2];
            model.predict(extended, probs);
            scoredActions.add(new ScoredAction(-1, probs[1]));
        } catch (CardNotFoundException e) {
            e.printStackTrace();
        }

        scoredActions.sort(Comparator.comparingDouble(ScoredAction::score).reversed());
        int chosenIndex = scoredActions.get(0).actionIndex();
        return chosenIndex < 0 ? "" : String.valueOf(chosenIndex);
    }



    @Override
    public List<LabeledPoint> extractTrainingData(List<LearningStep> steps) {
        List<LabeledPoint> data = new ArrayList<>();

        for (LearningStep step : steps) {
            if (!isStepRelevant(step)) continue;

            CardActionChoiceAction action = (CardActionChoiceAction) step.action;
            int label = step.reward > 0 ? 1 : 0;

            if (action.getActionText() == null) {
                addLabeledPoints(data, CardFeatures.PASS, action, step.state, step.gameId,
                        step.trainingWeight, label);
            } else if (action.getSourceBlueprint() != null) {
                addLabeledPoints(data, action.getSourceBlueprint(), action, step.state, step.gameId,
                        step.trainingWeight, label);
            }
        }

        return data;
    }

    protected void addLabeledPoints(List<LabeledPoint> data, String blueprintId, CardActionChoiceAction action,
                                    double[] state, String gameId, double trainingWeight, int label) {
        try {
            double[] cardVector = getCardFeatures(blueprintId, action);
            double[] extended = CardCandidateFeatures.append(state, cardVector);
            data.add(new LabeledPoint(label, extended, gameId, trainingWeight));
        } catch (CardNotFoundException ignore) {
        }

    }

    protected double[] getPassCardFeatures() throws CardNotFoundException {
        return CardFeatures.getCardFeatures(CardFeatures.PASS, 0);
    }

    protected double[] getCardFeatures(String blueprintId, CardActionChoiceAction action) throws CardNotFoundException {
        return CardFeatures.getCardFeatures(blueprintId, action.getWoundsOnSource());
    }

    protected double[] getCardFeatures(String blueprintId, int wounds, GameState gameState, String physicalId) throws CardNotFoundException {
        return CardFeatures.getCardFeatures(blueprintId, wounds);
    }

    private record ScoredAction(int actionIndex, double score) {
    }
}
