package com.gempukku.lotro.bots.simulation;

import com.gempukku.lotro.bots.BotPlayer;
import com.gempukku.lotro.bots.rl.LearningBotPlayer;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class SimpleBatchSimulationRunnerTest {
    @Test
    public void claimedGamesAreSimulatedExactlyOnceAcrossWorkers() throws Exception {
        int totalGames = 100;
        AtomicInteger nextGame = new AtomicInteger();
        AtomicInteger simulatedGames = new AtomicInteger();
        AtomicInteger completedCallbacks = new AtomicInteger();
        Simulation simulation = (bot1, bot2) -> {
            simulatedGames.incrementAndGet();
            return GameResult.P1_WON;
        };
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<SimulationStats>> workers = new ArrayList<>();

        try {
            for (int worker = 0; worker < 4; worker++) {
                SimpleBatchSimulationRunner runner = new SimpleBatchSimulationRunner(
                        simulation, bot("one-" + worker), bot("two-" + worker),
                        totalGames, completedCallbacks::incrementAndGet);
                workers.add(executor.submit(
                        () -> runner.runClaimedGames(() -> nextGame.getAndIncrement() < totalGames)));
            }

            SimulationStats aggregate = new SimulationStats(0, 0, 0);
            for (Future<SimulationStats> worker : workers)
                aggregate = aggregate.plus(worker.get());

            assertEquals(totalGames, aggregate.getTotalGames());
            assertEquals(totalGames, aggregate.getBot1Wins());
            assertEquals(0, aggregate.getBot2Wins());
            assertEquals(totalGames, simulatedGames.get());
            assertEquals(totalGames, completedCallbacks.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void decisionLimitSkipsGameAndAbortsPartialLearningEpisodes() {
        AtomicInteger simulatedGames = new AtomicInteger();
        AtomicInteger completedCallbacks = new AtomicInteger();
        Simulation simulation = (bot1, bot2) -> {
            if (simulatedGames.getAndIncrement() == 0)
                throw new MovieBlockSimulation.GameDecisionLimitException("decision limit reached");
            return GameResult.P1_WON;
        };
        TestLearningBot bot1 = new TestLearningBot("one");
        TestLearningBot bot2 = new TestLearningBot("two");
        SimpleBatchSimulationRunner runner = new SimpleBatchSimulationRunner(
                simulation, bot1, bot2, 2, completedCallbacks::incrementAndGet);

        SimulationStats stats = runner.run();

        assertEquals(2, simulatedGames.get());
        assertEquals(2, completedCallbacks.get());
        assertEquals(1, stats.getTotalGames());
        assertEquals(1, stats.getSkippedGames());
        assertEquals(1, stats.getBot1Wins());
        assertEquals(1, bot1.abortedEpisodes);
        assertEquals(1, bot2.abortedEpisodes);
        assertEquals(1, bot1.completedEpisodes);
        assertEquals(1, bot2.completedEpisodes);
    }

    @Test
    public void unresolvableDecisionCycleSkipsGameAndAbortsPartialLearningEpisodes() {
        AtomicInteger simulatedGames = new AtomicInteger();
        AtomicInteger completedCallbacks = new AtomicInteger();
        Simulation simulation = (bot1, bot2) -> {
            if (simulatedGames.getAndIncrement() == 0)
                throw new MovieBlockSimulation.UnresolvableDecisionCycleException("no legal escape");
            return GameResult.P1_WON;
        };
        TestLearningBot bot1 = new TestLearningBot("one");
        TestLearningBot bot2 = new TestLearningBot("two");
        SimpleBatchSimulationRunner runner = new SimpleBatchSimulationRunner(
                simulation, bot1, bot2, 2, completedCallbacks::incrementAndGet);

        SimulationStats stats = runner.run();

        assertEquals(2, simulatedGames.get());
        assertEquals(2, completedCallbacks.get());
        assertEquals(1, stats.getTotalGames());
        assertEquals(1, stats.getSkippedGames());
        assertEquals(1, stats.getBot1Wins());
        assertEquals(1, bot1.abortedEpisodes);
        assertEquals(1, bot2.abortedEpisodes);
        assertEquals(1, bot1.completedEpisodes);
        assertEquals(1, bot2.completedEpisodes);
    }

    private static BotPlayer bot(String name) {
        return new BotPlayer() {
            @Override
            public String chooseAction(GameState gameState, AwaitingDecision awaitingDecision) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    private static final class TestLearningBot implements LearningBotPlayer {
        private final String name;
        private int abortedEpisodes;
        private int completedEpisodes;

        private TestLearningBot(String name) {
            this.name = name;
        }

        @Override
        public String chooseAction(GameState gameState, AwaitingDecision awaitingDecision) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void observe(GameState gameState, AwaitingDecision decision, String playerId, String chosenAction,
                            double reward, boolean terminal) {
        }

        @Override
        public void beginEpisode(String gameId) {
        }

        @Override
        public void endEpisode(double reward) {
            completedEpisodes++;
        }

        @Override
        public void abortEpisode() {
            abortedEpisodes++;
        }

        @Override
        public void discardLastDecision() {
        }
    }
}
