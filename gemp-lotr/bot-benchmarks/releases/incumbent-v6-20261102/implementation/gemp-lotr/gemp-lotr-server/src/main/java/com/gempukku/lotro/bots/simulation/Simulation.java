package com.gempukku.lotro.bots.simulation;

import com.gempukku.lotro.bots.BotPlayer;

public interface Simulation {
    GameResult simulateGame(BotPlayer bot1, BotPlayer bot2);

    default SimulationOutcome simulateGameWithMetrics(BotPlayer bot1, BotPlayer bot2) {
        return SimulationOutcome.withoutMetrics(simulateGame(bot1, bot2));
    }
}
