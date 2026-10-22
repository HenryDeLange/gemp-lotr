package com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction;

import com.gempukku.lotro.bots.rl.DecisionAnswerer;
import com.gempukku.lotro.bots.random.RandomDecisionBot;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractCardActionAnswerer implements DecisionAnswerer {

    protected abstract String getTextTrigger();
    protected abstract AbstractPlayUseCardTrainer getPlayTrainer();
    protected abstract AbstractPlayUseCardTrainer getUseTrainer();
    protected abstract AbstractPlayUseCardTrainer getHealTrainer();
    protected abstract AbstractPlayUseCardTrainer getTransferTrainer();

    public List<AbstractPlayUseCardTrainer> getRoutedTrainers() {
        List<AbstractPlayUseCardTrainer> trainers = new ArrayList<>(4);
        addIfPresent(trainers, getPlayTrainer());
        addIfPresent(trainers, getUseTrainer());
        addIfPresent(trainers, getHealTrainer());
        addIfPresent(trainers, getTransferTrainer());
        return List.copyOf(trainers);
    }

    @Override
    public boolean appliesTo(GameState gameState, AwaitingDecision decision, String playerName) {
        return getPlayTrainer().appliesTo(gameState, decision, playerName) ||
                (getUseTrainer() != null && getUseTrainer().appliesTo(gameState, decision, playerName)) ||
                (getTransferTrainer() != null && getTransferTrainer().appliesTo(gameState, decision, playerName)) ||
                (getHealTrainer() != null && getHealTrainer().appliesTo(gameState, decision, playerName));
    }

    public boolean hasAvailableModel(
            GameState gameState,
            AwaitingDecision decision,
            String playerName,
            ModelRegistry modelRegistry) {
        return getRoutedTrainers().stream()
                .anyMatch(trainer -> trainer.appliesTo(gameState, decision, playerName)
                        && modelRegistry.hasModel(trainer.getClass()));
    }

    @Override
    public String getAnswer(GameState gameState, AwaitingDecision decision, String playerName, RLGameStateFeatures features, ModelRegistry modelRegistry) {
        List<AbstractPlayUseCardTrainer> trainers = getRoutedTrainers();
        List<String> answers = new ArrayList<>();
        boolean modelAvailable = false;
        for (AbstractPlayUseCardTrainer trainer : trainers) {
            if (trainer != null && trainer.appliesTo(gameState, decision, playerName)) {
                if (!modelRegistry.hasModel(trainer.getClass()))
                    continue;
                modelAvailable = true;
                answers.add(trainer.getAnswer(gameState, decision, playerName, features, modelRegistry));
            }
        }

        if (!modelAvailable)
            return new RandomDecisionBot(playerName).chooseAction(gameState, decision);
        return answers.stream().filter(s -> !s.isEmpty()).findFirst().orElse("");
    }

    private static void addIfPresent(
            List<AbstractPlayUseCardTrainer> trainers,
            AbstractPlayUseCardTrainer trainer) {
        if (trainer != null)
            trainers.add(trainer);
    }
}
