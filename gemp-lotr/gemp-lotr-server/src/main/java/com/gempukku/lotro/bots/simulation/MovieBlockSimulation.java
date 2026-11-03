package com.gempukku.lotro.bots.simulation;

import com.gempukku.lotro.bots.AssignmentLegality;
import com.gempukku.lotro.bots.BotDecisionFallback;
import com.gempukku.lotro.bots.BotPlayer;
import com.gempukku.lotro.bots.TrainingEventReporter;
import com.gempukku.lotro.bots.TrainingEventReporter.Severity;
import com.gempukku.lotro.bots.TrainingDiagnosticTrace;
import com.gempukku.lotro.bots.deckbuilding.DeckBuilderTrainer;
import com.gempukku.lotro.bots.rl.LearningBotPlayer;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.db.DeckSerialization;
import com.gempukku.lotro.game.DefaultUserFeedback;
import com.gempukku.lotro.game.LotroCardBlueprintLibrary;
import com.gempukku.lotro.game.LotroFormat;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.timing.DefaultLotroGame;
import com.gempukku.lotro.logic.vo.LotroDeck;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public class MovieBlockSimulation implements Simulation {
    private static final int MAX_DECISIONS_PER_GAME = 10_000;

    private final LotroFormat format;
    private final List<LotroDeck> availableDecks;
    private final LotroCardBlueprintLibrary library;
    private final Supplier<DeckPair> deckPairSupplier;
    private final TrainingEventReporter eventReporter;
    private DeckPair preparedDeckPair;

    public MovieBlockSimulation(LotroFormat format, List<LotroDeck> availableDecks, LotroCardBlueprintLibrary library) {
        this(format, availableDecks, library, null);
    }

    public MovieBlockSimulation(
            LotroFormat format,
            List<LotroDeck> availableDecks,
            LotroCardBlueprintLibrary library,
            TrainingEventReporter eventReporter) {
        if (availableDecks.isEmpty())
            throw new IllegalArgumentException("At least one legal bot deck is required");
        this.format = format;
        this.availableDecks = List.copyOf(availableDecks);
        this.library = library;
        this.deckPairSupplier = null;
        this.eventReporter = eventReporter;
    }

    public MovieBlockSimulation(
            LotroFormat format,
            LotroCardBlueprintLibrary library,
            Supplier<DeckPair> deckPairSupplier) {
        this(format, library, deckPairSupplier, null);
    }

    public MovieBlockSimulation(
            LotroFormat format,
            LotroCardBlueprintLibrary library,
            Supplier<DeckPair> deckPairSupplier,
            TrainingEventReporter eventReporter) {
        this.format = format;
        this.availableDecks = List.of();
        this.library = library;
        this.deckPairSupplier = Objects.requireNonNull(deckPairSupplier, "deckPairSupplier");
        this.eventReporter = eventReporter;
    }

    @Override
    public GameResult simulateGame(BotPlayer bot1, BotPlayer bot2) {
        DeckPair deckPair = takeDeckPair();
        return simulateGameInternal(bot1, bot2, deckPair.first(), deckPair.second(), false, false).result();
    }

    @Override
    public SimulationOutcome simulateGameWithMetrics(BotPlayer bot1, BotPlayer bot2) {
        DeckPair deckPair = takeDeckPair();
        return simulateGameInternal(bot1, bot2, deckPair.first(), deckPair.second(), true, false);
    }

    @Override
    public void beginGame(String gameId, BotPlayer bot1, BotPlayer bot2) {
        DeckPair deckPair = chooseDeckPair();
        preparedDeckPair = deckPair;
        List<String> deckFingerprints = List.of(
                DeckBuilderTrainer.fingerprint(format.getCode(), deckPair.first()),
                DeckBuilderTrainer.fingerprint(format.getCode(), deckPair.second()));
        if (bot1 instanceof LearningBotPlayer learningBot1)
            learningBot1.beginEpisode(gameId, deckFingerprints);
        if (bot2 instanceof LearningBotPlayer learningBot2)
            learningBot2.beginEpisode(gameId, deckFingerprints);
    }

    public GameResult simulateGame(BotPlayer bot1, BotPlayer bot2, LotroDeck bot1Deck, LotroDeck bot2Deck) {
        return simulateGameInternal(bot1, bot2, bot1Deck, bot2Deck, false, false).result();
    }

    public SimulationOutcome simulateGameWithMetrics(
            BotPlayer bot1,
            BotPlayer bot2,
            LotroDeck bot1Deck,
            LotroDeck bot2Deck) {
        return simulateGameInternal(bot1, bot2, bot1Deck, bot2Deck, true, false);
    }

    public SimulationOutcome simulateGameWithSeating(
            BotPlayer bot1,
            BotPlayer bot2,
            LotroDeck bot1Deck,
            LotroDeck bot2Deck) {
        return simulateGameInternal(bot1, bot2, bot1Deck, bot2Deck, false, true);
    }

    private SimulationOutcome simulateGameInternal(
            BotPlayer bot1,
            BotPlayer bot2,
            LotroDeck bot1Deck,
            LotroDeck bot2Deck,
            boolean collectTacticalMetrics,
            boolean collectSeating) {
        if (bot1.getName().equals(bot2.getName()))
            throw new IllegalArgumentException("Bot names cannot be equal.");

        Map<String, LotroDeck> decks = new HashMap<>();
        decks.put(bot1.getName(), copyDeck(bot1Deck));
        decks.put(bot2.getName(), copyDeck(bot2Deck));

        DefaultUserFeedback userFeedback = new DefaultUserFeedback();
        DefaultLotroGame game = new DefaultLotroGame(format, decks, userFeedback, library);
        userFeedback.setGame(game);
        GameTacticalMetricsAccumulator tacticalMetrics = collectTacticalMetrics
                ? new GameTacticalMetricsAccumulator(bot1.getName(), bot2.getName())
                : null;
        game.startGame();
        if (tacticalMetrics != null)
            tacticalMetrics.capture(game.getGameState());

        int decisions = 0;
        ArrayDeque<String> recentDecisions = new ArrayDeque<>();
        DecisionCycleGuard decisionCycleGuard = new DecisionCycleGuard();
        AssignmentLegality assignmentLegality = new AssignmentLegality(game);
        while (!game.isFinished()) {
            if (++decisions > MAX_DECISIONS_PER_GAME)
                throw new GameDecisionLimitException("Game exceeded " + MAX_DECISIONS_PER_GAME
                        + " bot decisions in " + format.getCode() + "; recent decisions: "
                        + (recentDecisions.isEmpty() ? "<none>" : String.join(" | ", recentDecisions)));

            AwaitingDecision bot1Decision = userFeedback.getAwaitingDecision(bot1.getName());
            AwaitingDecision bot2Decision = userFeedback.getAwaitingDecision(bot2.getName());
            if (bot1Decision != null) {
                getDecisionAndDecide(
                        bot1, bot1Decision, game, userFeedback, tacticalMetrics, decisionCycleGuard, recentDecisions,
                        assignmentLegality);
            } else if (bot2Decision != null) {
                getDecisionAndDecide(
                        bot2, bot2Decision, game, userFeedback, tacticalMetrics, decisionCycleGuard, recentDecisions,
                        assignmentLegality);
            } else {
                throw new IllegalStateException("Game has no pending decision and is not finished in " + format.getCode());
            }
        }

        if (bot1.getName().equals(game.getWinnerPlayerId()))
            return completedOutcome(GameResult.P1_WON, game, bot1, bot2, tacticalMetrics, collectSeating);
        if (bot2.getName().equals(game.getWinnerPlayerId()))
            return completedOutcome(GameResult.P2_WON, game, bot1, bot2, tacticalMetrics, collectSeating);
        throw new IllegalStateException("Finished game has unknown winner in " + format.getCode());
    }

    private SimulationOutcome completedOutcome(
            GameResult result,
            DefaultLotroGame game,
            BotPlayer bot1,
            BotPlayer bot2,
            GameTacticalMetricsAccumulator tacticalMetrics,
            boolean collectSeating) {
        if (tacticalMetrics != null)
            return SimulationOutcome.withMetrics(result, tacticalMetrics.finish(game));
        if (collectSeating)
            return SimulationOutcome.withSeating(result, actualSeating(game, bot1, bot2));
        return SimulationOutcome.withoutMetrics(result);
    }

    private static GameSeating actualSeating(DefaultLotroGame game, BotPlayer bot1, BotPlayer bot2) {
        var gameState = game.getGameState();
        var seatingOrder = gameState.getPlayerOrder().getAllPlayers();
        int p1SeatIndex = seatingOrder.indexOf(bot1.getName());
        String firstPlayer = gameState.getFirstPlayerId();
        if (seatingOrder.size() != 2 || p1SeatIndex < 0
                || !bot1.getName().equals(firstPlayer) && !bot2.getName().equals(firstPlayer))
            throw new IllegalStateException("Completed game has invalid actual seating data");
        return new GameSeating(p1SeatIndex, bot1.getName().equals(firstPlayer));
    }

    public static final class GameDecisionLimitException extends IllegalStateException {
        public GameDecisionLimitException(String message) {
            super(message);
        }
    }

    public static final class UnresolvableDecisionCycleException extends IllegalStateException {
        public UnresolvableDecisionCycleException(String message) {
            super(message);
        }
    }

    private LotroDeck chooseDeck() {
        return availableDecks.get(ThreadLocalRandom.current().nextInt(availableDecks.size()));
    }

    private DeckPair chooseDeckPair() {
        return deckPairSupplier == null
                ? new DeckPair(chooseDeck(), chooseDeck())
                : Objects.requireNonNull(deckPairSupplier.get(), "deckPairSupplier returned null");
    }

    private DeckPair takeDeckPair() {
        DeckPair deckPair = preparedDeckPair;
        preparedDeckPair = null;
        return deckPair == null ? chooseDeckPair() : deckPair;
    }

    public record DeckPair(LotroDeck first, LotroDeck second) {
        public DeckPair {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
        }
    }

    private static LotroDeck copyDeck(LotroDeck source) {
        return DeckSerialization.buildDeckFromContents(
                source.getDeckName(),
                DeckSerialization.buildContentsFromDeck(source),
                source.getTargetFormat(),
                source.getNotes());
    }

    private void getDecisionAndDecide(
            BotPlayer bot,
            AwaitingDecision decision,
            DefaultLotroGame game,
            DefaultUserFeedback userFeedback,
            GameTacticalMetricsAccumulator tacticalMetrics,
            DecisionCycleGuard decisionCycleGuard,
            ArrayDeque<String> recentDecisions,
            AssignmentLegality assignmentLegality) {
        Phase currentPhase = game.getGameState().getCurrentPhase();
        String phase = currentPhase.toString();
        String signature = DecisionCycleGuard.signature(bot.getName(), phase, decision, game.getGameState());
        var deferredEscape = decisionCycleGuard.takeDeferredEscape(signature);
        var repeatedCycle = deferredEscape.isPresent()
                ? java.util.Optional.<DecisionCycleGuard.RepeatedCycle>empty()
                : decisionCycleGuard.findRepeatedCycle(signature);
        boolean cycleEscape = deferredEscape.isPresent() || repeatedCycle.isPresent();
        String answer;
        if (deferredEscape.isPresent()) {
            DecisionCycleGuard.DeferredEscape escape = deferredEscape.orElseThrow();
            answer = BotDecisionFallback.alternativeAnswer(
                    game, bot.getName(), decision, escape.repeatedAnswer());
            if (answer == null) {
                recordDiagnosticTrace(TrainingDiagnosticTrace.create(
                        "unresolvable-cycle", format.getCode(), phase, decision, signature,
                        escape.repeatedAnswer(), null, 0, List.of()));
                throw new UnresolvableDecisionCycleException(
                        "Scheduled cycle escape is no longer legal in " + format.getCode()
                                + " during " + phase + " for " + decision.getDecisionType());
            }
            decisionCycleGuard.clear();
            recordCycleRecovery(escape.repeatedHistoryDecisions(),
                    "A repeated decision was escaped in " + format.getCode()
                            + " during " + phase + " for " + decision.getDecisionType() + ".");
        } else if (repeatedCycle.isPresent()) {
            DecisionCycleGuard.RepeatedCycle cycle = repeatedCycle.orElseThrow();
            answer = BotDecisionFallback.alternativeAnswer(
                    game, bot.getName(), decision, cycle.repeatedAnswer());
            if (answer == null) {
                var deferred = cycle.deferredEscape();
                if (deferred.isEmpty()) {
                    recordDiagnosticTrace(TrainingDiagnosticTrace.create(
                            "unresolvable-cycle", format.getCode(), phase, decision, signature,
                            cycle.repeatedAnswer(), null, cycle.length(), cycle.diagnosticPattern()));
                    throw new UnresolvableDecisionCycleException(
                            "Detected repeated bot decision cycle in " + format.getCode()
                                    + " during " + phase + " for " + decision.getDecisionType()
                                    + " with no legal escape (cycle length " + cycle.length() + ")");
                }
                answer = cycle.repeatedAnswer();
                decisionCycleGuard.clear();
                decisionCycleGuard.deferEscape(deferred.orElseThrow());
                recordWarning("deferred-decision-cycle-escape-scheduled",
                        "A repeated decision had no immediate escape in " + format.getCode()
                                + " during " + phase + "; an alternate was scheduled.");
            } else {
                decisionCycleGuard.clear();
                recordCycleRecovery(cycle.repeatedHistoryDecisions(),
                        "A repeated decision was escaped in " + format.getCode()
                                + " during " + phase + " for " + decision.getDecisionType() + ".");
            }
        } else {
            answer = bot.chooseAction(game.getGameState(), decision, assignmentLegality);
        }

        try {
            userFeedback.participantDecided(bot.getName());
            String accepted = BotDecisionFallback.decide(game, bot.getName(), decision, answer);
            if (!java.util.Objects.equals(answer, accepted)) {
                recordDiagnosticTrace(TrainingDiagnosticTrace.create(
                        "invalid-answer-fallback", format.getCode(), phase, decision, signature,
                        answer, accepted, 0, List.of()));
                recordWarning("invalid-bot-answer-fallback",
                        "An invalid bot answer was replaced with a legal fallback in " + format.getCode()
                                + " during " + phase + " for " + decision.getDecisionType() + ".");
                System.out.println("Invalid bot answer replaced with a legal fallback in " + format.getCode()
                        + " during " + phase + " for " + decision.getDecisionType() + ".");
                if (!cycleEscape && bot instanceof LearningBotPlayer learningBot)
                    learningBot.discardLastDecision();
            }
            String summary = DecisionCycleGuard.formatDecisionOutcome(
                    phase, decision, accepted, cycleEscape);
            DecisionCycleGuard.addRecentDecision(recentDecisions, summary);
            decisionCycleGuard.record(signature, decision.getDecisionType(), summary, accepted);
            if (tacticalMetrics != null)
                tacticalMetrics.recordDecision(currentPhase, decision, accepted);
            game.carryOutPendingActionsUntilDecisionNeeded();
            if (tacticalMetrics != null)
                tacticalMetrics.capture(game.getGameState());
        } catch (DecisionResultInvalidException e) {
            recordDiagnosticTrace(TrainingDiagnosticTrace.create(
                    "invalid-answer-failure", format.getCode(), phase, decision, signature,
                    answer, null, 0, List.of()));
            throw new IllegalStateException("No valid bot answer for " + format.getCode()
                    + " during " + phase + " for " + decision.getDecisionType(), e);
        }
    }

    private void recordWarning(String type, String message) {
        if (eventReporter != null)
            eventReporter.recordEvent(Severity.WARNING, type, null, message, null);
    }

    private void recordCycleRecovery(int repeatedHistoryDecisions, String message) {
        if (eventReporter != null)
            eventReporter.recordCycleRecovery(repeatedHistoryDecisions, message);
    }

    private void recordDiagnosticTrace(TrainingDiagnosticTrace trace) {
        if (eventReporter != null)
            eventReporter.recordDiagnosticTrace(trace);
    }
}
