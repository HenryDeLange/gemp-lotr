package com.gempukku.lotro.bots;

import com.gempukku.lotro.at.AbstractAtTest;
import com.gempukku.lotro.bots.TrainingDiagnosticTrace;
import com.gempukku.lotro.bots.TrainingEventReporter;
import com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer;
import com.gempukku.lotro.bots.random.RandomDecisionBot;
import com.gempukku.lotro.bots.rl.LearningBotPlayer;
import com.gempukku.lotro.bots.rl.RLGameStateFeatures;
import com.gempukku.lotro.bots.rl.ReplayBuffer;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.FotrStarterBot;
import com.gempukku.lotro.bots.rl.fotrstarters.FotrStartersLearningStepsPersistence;
import com.gempukku.lotro.bots.rl.fotrstarters.MovieTrilogyRLGameStateFeatures;
import com.gempukku.lotro.bots.rl.fotrstarters.models.FotrStarterTrainerCatalog;
import com.gempukku.lotro.bots.rl.fotrstarters.models.ModelRegistry;
import com.gempukku.lotro.bots.simulation.BatchTacticalMetrics;
import com.gempukku.lotro.bots.simulation.GameResult;
import com.gempukku.lotro.bots.simulation.FotrStartersSimulation;
import com.gempukku.lotro.bots.simulation.MovieBlockSimulation;
import com.gempukku.lotro.bots.simulation.SimulationOutcome;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MovieBlockSimulationTest extends AbstractAtTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void completedGameProducesEquivalentSharedAndPerTrainerExamples() throws Exception {
        CardFeatures.init(_cardLibrary);
        var format = _formatLibrary.getFormat("fotr_block");
        var decks = MovieBlockDeckFactory.createBotDecks("fotr_block", format, _cardLibrary, 2, 5678L);
        var simulation = new MovieBlockSimulation(format, decks, _cardLibrary);
        var buffer = new ReplayBuffer(100_000);
        var bot = new FotrStarterBot(new MovieTrilogyRLGameStateFeatures("fotr_block"),
                "~corpus-test", new ModelRegistry(), buffer);
        var random = new RandomDecisionBot("~corpus-opponent");
        simulation.beginGame("corpus-game", bot, random);
        GameResult result = simulation.simulateGame(bot, random);
        bot.endEpisode(result == GameResult.P1_WON ? 1 : -1);
        var steps = buffer.drain();
        assertFalse(steps.isEmpty());
        assertTrue(steps.stream().allMatch(step -> step.deckFingerprints.size() == 2));
        AtomicReference<FotrStartersLearningStepsPersistence.AppendStats> legacyStats = new AtomicReference<>();
        AtomicReference<FotrStartersLearningStepsPersistence.AppendStats> sharedStats = new AtomicReference<>();
        var legacy = new FotrStartersLearningStepsPersistence(
                temporaryFolder.newFolder("legacy").toPath(), ignored -> { }, legacyStats::set, false);
        var shared = new FotrStartersLearningStepsPersistence(
                temporaryFolder.newFolder("shared").toPath(), ignored -> { }, sharedStats::set, false,
                Set.of(), FotrStartersLearningStepsPersistence.DataLayout.SHARED_V1);
        legacy.save(steps);
        shared.save(steps);
        assertEquals(legacyStats.get().trainerRecords(), sharedStats.get().trainerRecords());
        System.out.printf("Shared corpus single-game comparison: %d decisions, %d memberships, "
                        + "%d shared records; %d per-trainer bytes vs %d shared bytes%n",
                steps.size(), sharedStats.get().trainerRecords(), sharedStats.get().storedRecords(),
                legacyStats.get().appendedBytes(), sharedStats.get().appendedBytes());
        for (var trainer : FotrStarterTrainerCatalog.createAll()) {
            assertEquals(trainer.getClass().getName(),
                    legacy.load(trainer).stream().map(step -> step.toJson().toJSONString()).toList(),
                    shared.load(trainer).stream().map(step -> step.toJson().toJSONString()).toList());
        }
    }

    @Test
    public void completesBackToBackGamesWithBasePolicyInAllMovieBlocks() {
        CardFeatures.init(_cardLibrary);

        for (String formatCode : List.of("fotr_block", "ttt_block", "king_block", "movie")) {
            var format = _formatLibrary.getFormat(formatCode);
            var decks = MovieBlockDeckFactory.createBotDecks(formatCode, format, _cardLibrary, 2, 5678L);
            RLGameStateFeatures features = new MovieTrilogyRLGameStateFeatures(formatCode);
            var simulation = new MovieBlockSimulation(format, decks, _cardLibrary);

            for (int gameIndex = 0; gameIndex < 2; gameIndex++) {
                String gameSuffix = formatCode + "-" + gameIndex;
                var bot = new FotrStarterBot(features, "~model-less-" + gameSuffix, new ModelRegistry(), null);
                var random = new RandomDecisionBot("~random-" + gameSuffix);

                SimulationOutcome outcome = simulation.simulateGameWithMetrics(bot, random);
                GameResult result = outcome.result();
                assertNotNull(formatCode + " game result", result);
                var metrics = outcome.tacticalMetrics().orElseThrow();
                assertTrue(formatCode + " must record an actual seat",
                        metrics.p1SeatIndex() == 0 || metrics.p1SeatIndex() == 1);
                assertTrue(formatCode + " must record decisions", metrics.decisionMetrics().decisions() > 0);
                var batch = BatchTacticalMetrics.empty().plus(metrics).decisionMetrics();
                assertTrue(formatCode + " must aggregate the game", batch.gamesMeasured() == 1
                        && batch.getP95Decisions() == metrics.decisionMetrics().decisions());
            }
        }
    }

    @Test
    public void bothSimulatorsProvideTheActualGamesAssignmentRules() {
        var format = _formatLibrary.getFormat("fotr_block");
        var decks = MovieBlockDeckFactory.createBotDecks("fotr_block", format, _cardLibrary, 2, 5678L);
        var movieSimulation = new MovieBlockSimulation(format, decks, _cardLibrary);
        var starterSimulation = new FotrStartersSimulation(_cardLibrary, _formatLibrary);

        assertNotNull(movieSimulation.simulateGame(
                new CheckedRulesBot("~movie-one"), new CheckedRulesBot("~movie-two")));
        assertNotNull(starterSimulation.simulateGame(
                new CheckedRulesBot("~starter-one"), new CheckedRulesBot("~starter-two")));
    }

    @Test
    public void batchEpisodeReceivesBothDeckFingerprintsBeforeRecordingDecisions() {
        var format = _formatLibrary.getFormat("fotr_block");
        var decks = MovieBlockDeckFactory.createBotDecks("fotr_block", format, _cardLibrary, 2, 5678L);
        var pair = new MovieBlockSimulation.DeckPair(decks.get(0), decks.get(1));
        var simulation = new MovieBlockSimulation(format, _cardLibrary, () -> pair);
        var bot1 = new RecordingLearningBot("~recording-one");
        var bot2 = new RecordingLearningBot("~recording-two");
        String gameId = "provenance-test";

        simulation.beginGame(gameId, bot1, bot2);

        List<String> expected = List.of(
                DeckBuilderTrainer.fingerprint("fotr_block", pair.first()),
                DeckBuilderTrainer.fingerprint("fotr_block", pair.second()));
        assertEquals(gameId, bot1.gameId);
        assertEquals(gameId, bot2.gameId);
        assertEquals(expected, bot1.deckFingerprints);
        assertEquals(expected, bot2.deckFingerprints);
    }

    @Test
    public void invalidAnswerProducesARedactedTrainingTrace() {
        var format = _formatLibrary.getFormat("king_block");
        var deck = MovieBlockDeckFactory.createBotDecks("king_block", format, _cardLibrary, 1, 5678L).getFirst();
        List<TrainingDiagnosticTrace> traces = new ArrayList<>();
        TrainingEventReporter reporter = new TrainingEventReporter() {
            @Override
            public void recordEvent(Severity severity, String type, String model, String message, Throwable failure) {
            }

            @Override
            public void recordDiagnosticTrace(TrainingDiagnosticTrace trace) {
                traces.add(trace);
            }
        };
        var simulation = new MovieBlockSimulation(format, _cardLibrary,
                () -> new MovieBlockSimulation.DeckPair(deck, deck), reporter);
        var invalidOnce = new InvalidOnceBot("~invalid-once");
        var random = new RandomDecisionBot("~trace-random");

        assertNotNull(simulation.simulateGame(invalidOnce, random));

        TrainingDiagnosticTrace trace = traces.stream()
                .filter(item -> item.kind().equals("invalid-answer-fallback"))
                .findFirst()
                .orElseThrow();
        assertEquals("king_block", trace.format());
        assertFalse(com.alibaba.fastjson2.JSON.toJSONString(trace).contains("private-invalid-answer"));
    }

    private static final class CheckedRulesBot implements BotPlayer {
        private final RandomDecisionBot delegate;

        private CheckedRulesBot(String name) {
            delegate = new RandomDecisionBot(name);
        }

        @Override
        public String chooseAction(GameState gameState, AwaitingDecision decision) {
            throw new AssertionError("Simulator did not provide assignment legality");
        }

        @Override
        public String chooseAction(GameState gameState, AwaitingDecision decision, AssignmentLegality legality) {
            assertTrue("Assignment legality must reference the running game", legality.isChecked());
            return delegate.chooseAction(gameState, decision, legality);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }
    }

    private static final class RecordingLearningBot implements LearningBotPlayer {
        private final RandomDecisionBot delegate;
        private String gameId;
        private List<String> deckFingerprints = List.of();

        private RecordingLearningBot(String name) {
            delegate = new RandomDecisionBot(name);
        }

        @Override
        public String chooseAction(GameState gameState, AwaitingDecision decision) {
            return delegate.chooseAction(gameState, decision);
        }

        @Override
        public String chooseAction(GameState gameState, AwaitingDecision decision, AssignmentLegality legality) {
            return delegate.chooseAction(gameState, decision, legality);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public void observe(GameState gameState, AwaitingDecision decision, String playerId,
                            String chosenAction, double reward, boolean terminal) {
        }

        @Override
        public void beginEpisode(String gameId) {
            this.gameId = gameId;
            deckFingerprints = List.of();
        }

        @Override
        public void beginEpisode(String gameId, List<String> deckFingerprints) {
            this.gameId = gameId;
            this.deckFingerprints = List.copyOf(deckFingerprints);
        }

        @Override
        public void endEpisode(double reward) {
        }

        @Override
        public void abortEpisode() {
        }

        @Override
        public void discardLastDecision() {
        }
    }

    private static final class InvalidOnceBot implements BotPlayer {
        private final RandomDecisionBot delegate;
        private boolean invalidAnswerPending = true;

        private InvalidOnceBot(String name) {
            delegate = new RandomDecisionBot(name);
        }

        @Override
        public String chooseAction(GameState gameState, AwaitingDecision decision) {
            if (invalidAnswerPending) {
                invalidAnswerPending = false;
                return "private-invalid-answer";
            }
            return delegate.chooseAction(gameState, decision);
        }

        @Override
        public String chooseAction(
                GameState gameState,
                AwaitingDecision decision,
                AssignmentLegality assignmentLegality) {
            if (invalidAnswerPending) {
                invalidAnswerPending = false;
                return "private-invalid-answer";
            }
            return delegate.chooseAction(gameState, decision, assignmentLegality);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }
    }
}
