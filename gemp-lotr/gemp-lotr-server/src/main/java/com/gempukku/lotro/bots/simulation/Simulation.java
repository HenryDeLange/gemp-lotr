package com.gempukku.lotro.bots.simulation;

import com.gempukku.lotro.bots.BotPlayer;
import com.gempukku.lotro.bots.rl.LearningBotPlayer;

import java.util.List;

public interface Simulation {
    GameResult simulateGame(BotPlayer bot1, BotPlayer bot2);

    default void beginGame(String gameId, BotPlayer bot1, BotPlayer bot2) {
        if (bot1 instanceof LearningBotPlayer learningBot1)
            learningBot1.beginEpisode(gameId, List.of());
        if (bot2 instanceof LearningBotPlayer learningBot2)
            learningBot2.beginEpisode(gameId, List.of());
    }

    default SimulationOutcome simulateGameWithMetrics(BotPlayer bot1, BotPlayer bot2) {
        return SimulationOutcome.withoutMetrics(simulateGame(bot1, bot2));
    }
}
