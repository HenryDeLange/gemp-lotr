package com.gempukku.lotro.bots.simulation;

import com.gempukku.lotro.bots.BotPlayer;
import com.gempukku.lotro.bots.TrainingEventReporter;
import com.gempukku.lotro.bots.TrainingEventReporter.Severity;
import com.gempukku.lotro.bots.rl.LearningBotPlayer;

import java.util.UUID;
import java.util.function.BooleanSupplier;

public class SimpleBatchSimulationRunner implements SimulationRunner{
    private final Simulation simulation;
    private final BotPlayer bot1;
    private final BotPlayer bot2;
    private final int numGames;
    private final Runnable gameCompleted;
    private final TrainingEventReporter eventReporter;

    public SimpleBatchSimulationRunner(Simulation simulation, BotPlayer bot1, BotPlayer bot2, int numGames) {
        this(simulation, bot1, bot2, numGames, () -> {
        });
    }

    public SimpleBatchSimulationRunner(
            Simulation simulation,
            BotPlayer bot1,
            BotPlayer bot2,
            int numGames,
            Runnable gameCompleted) {
        this(simulation, bot1, bot2, numGames, gameCompleted, null);
    }

    public SimpleBatchSimulationRunner(
            Simulation simulation,
            BotPlayer bot1,
            BotPlayer bot2,
            int numGames,
            Runnable gameCompleted,
            TrainingEventReporter eventReporter) {
        this.simulation = simulation;
        this.bot1 = bot1;
        this.bot2 = bot2;
        this.numGames = numGames;
        this.gameCompleted = gameCompleted;
        this.eventReporter = eventReporter;
    }

    @Override
    public SimulationStats run() {
        return runGames(() -> true);
    }

    public SimulationStats runClaimedGames(BooleanSupplier claimNextGame) {
        return runGames(claimNextGame);
    }

    private SimulationStats runGames(BooleanSupplier claimNextGame) {
        int bot1Wins = 0;
        int gamesPlayed = 0;
        int gamesAttempted = 0;
        int skippedGames = 0;
        BatchTacticalMetrics tacticalMetrics = BatchTacticalMetrics.empty();

        while (gamesAttempted < numGames && claimNextGame.getAsBoolean()) {
            gamesAttempted++;
            String gameId = UUID.randomUUID().toString();
            if (bot1 instanceof LearningBotPlayer learningBot1)
                learningBot1.beginEpisode(gameId);
            if (bot2 instanceof LearningBotPlayer learningBot2)
                learningBot2.beginEpisode(gameId);
            SimulationOutcome outcome;
            try {
                outcome = simulation.simulateGameWithMetrics(bot1, bot2);
            } catch (MovieBlockSimulation.GameDecisionLimitException
                     | MovieBlockSimulation.UnresolvableDecisionCycleException e) {
                abortEpisode(bot1);
                abortEpisode(bot2);
                skippedGames++;
                String failureType = e instanceof MovieBlockSimulation.UnresolvableDecisionCycleException
                        ? "unresolvable-decision-cycle"
                        : "game-decision-limit";
                String reason = e instanceof MovieBlockSimulation.UnresolvableDecisionCycleException
                        ? "an unresolvable decision cycle"
                        : "the decision limit";
                if (eventReporter != null)
                    eventReporter.recordEvent(Severity.ERROR, failureType, null,
                            "Skipped game " + gameId + " after " + reason + " ("
                                    + bot1.getName() + " vs " + bot2.getName() + ").", e);
                System.err.printf("Skipping game %s after %s (%s vs %s); full failure follows.%n",
                        gameId, reason, bot1.getName(), bot2.getName());
                e.printStackTrace(System.err);
                gameCompleted.run();
                continue;
            }
            GameResult result = outcome.result();
            if (outcome.tacticalMetrics().isPresent())
                tacticalMetrics = tacticalMetrics.plus(outcome.tacticalMetrics().orElseThrow());
            if (result == GameResult.P1_WON) {
                bot1Wins++;
            }
            double bot1Reward = result == GameResult.P1_WON ? 10.0 : 0.0;
            double bot2Reward = 10.0 - bot1Reward;
            if (bot1 instanceof LearningBotPlayer) {
                ((LearningBotPlayer) bot1).endEpisode(bot1Reward);
            }
            if (bot2 instanceof LearningBotPlayer) {
                ((LearningBotPlayer) bot2).endEpisode(bot2Reward);
            }
            gameCompleted.run();
            gamesPlayed++;
        }

        return new SimulationStats(bot1Wins, gamesPlayed - bot1Wins, gamesPlayed, tacticalMetrics, skippedGames);
    }

    private static void abortEpisode(BotPlayer bot) {
        if (bot instanceof LearningBotPlayer learningBot)
            learningBot.abortEpisode();
    }
}
