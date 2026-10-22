package com.gempukku.lotro.bots.simulation;

import com.gempukku.lotro.bots.BotDecisionFallback;
import com.gempukku.lotro.bots.BotPlayer;
import com.gempukku.lotro.bots.TrainingEventReporter;
import com.gempukku.lotro.bots.TrainingEventReporter.Severity;
import com.gempukku.lotro.bots.rl.LearningBotPlayer;
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
        DeckPair deckPair = chooseDeckPair();
        return simulateGameInternal(bot1, bot2, deckPair.first(), deckPair.second(), false).result();
    }

    @Override
    public SimulationOutcome simulateGameWithMetrics(BotPlayer bot1, BotPlayer bot2) {
        DeckPair deckPair = chooseDeckPair();
        return simulateGameInternal(bot1, bot2, deckPair.first(), deckPair.second(), true);
    }

    public GameResult simulateGame(BotPlayer bot1, BotPlayer bot2, LotroDeck bot1Deck, LotroDeck bot2Deck) {
        return simulateGameInternal(bot1, bot2, bot1Deck, bot2Deck, false).result();
    }

    public SimulationOutcome simulateGameWithMetrics(
            BotPlayer bot1,
            BotPlayer bot2,
            LotroDeck bot1Deck,
            LotroDeck bot2Deck) {
        return simulateGameInternal(bot1, bot2, bot1Deck, bot2Deck, true);
    }

    private SimulationOutcome simulateGameInternal(
            BotPlayer bot1,
            BotPlayer bot2,
            LotroDeck bot1Deck,
            LotroDeck bot2Deck,
            boolean collectTacticalMetrics) {
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
        while (!game.isFinished()) {
            if (++decisions > MAX_DECISIONS_PER_GAME)
                throw new GameDecisionLimitException("Game exceeded " + MAX_DECISIONS_PER_GAME
                        + " bot decisions in " + format.getCode() + "; recent decisions: "
                        + (recentDecisions.isEmpty() ? "<none>" : String.join(" | ", recentDecisions)));

            AwaitingDecision bot1Decision = userFeedback.getAwaitingDecision(bot1.getName());
            AwaitingDecision bot2Decision = userFeedback.getAwaitingDecision(bot2.getName());
            if (bot1Decision != null) {
                getDecisionAndDecide(
                        bot1, bot1Decision, game, userFeedback, tacticalMetrics, decisionCycleGuard, recentDecisions);
            } else if (bot2Decision != null) {
                getDecisionAndDecide(
                        bot2, bot2Decision, game, userFeedback, tacticalMetrics, decisionCycleGuard, recentDecisions);
            } else {
                throw new IllegalStateException("Game has no pending decision and is not finished in " + format.getCode());
            }
        }

        if (bot1.getName().equals(game.getWinnerPlayerId()))
            return tacticalMetrics == null
                    ? SimulationOutcome.withoutMetrics(GameResult.P1_WON)
                    : SimulationOutcome.withMetrics(GameResult.P1_WON, tacticalMetrics.finish(game));
        if (bot2.getName().equals(game.getWinnerPlayerId()))
            return tacticalMetrics == null
                    ? SimulationOutcome.withoutMetrics(GameResult.P2_WON)
                    : SimulationOutcome.withMetrics(GameResult.P2_WON, tacticalMetrics.finish(game));
        throw new IllegalStateException("Finished game has unknown winner in " + format.getCode());
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
            ArrayDeque<String> recentDecisions) {
        String phase = game.getGameState().getCurrentPhase().toString();
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
                throw new UnresolvableDecisionCycleException(
                        "Scheduled cycle escape is no longer legal in " + format.getCode()
                        + ": " + DecisionCycleGuard.formatDecisionOutcome(
                        bot.getName(), phase, decision, escape.repeatedAnswer(), false));
            }
            decisionCycleGuard.clear();
            recordWarning("repeated-decision-cycle",
                    "Used a deferred untrained alternate answer \"" + answer + "\" to break a repeated decision cycle: "
                            + DecisionCycleGuard.formatDecisionOutcome(
                            bot.getName(), phase, decision, answer, true));
        } else if (repeatedCycle.isPresent()) {
            DecisionCycleGuard.RepeatedCycle cycle = repeatedCycle.orElseThrow();
            answer = BotDecisionFallback.alternativeAnswer(
                    game, bot.getName(), decision, cycle.repeatedAnswer());
            if (answer == null) {
                var deferred = cycle.deferredEscape();
                if (deferred.isEmpty()) {
                    throw new UnresolvableDecisionCycleException(
                            "Detected repeated bot decision cycle in " + format.getCode()
                            + " but found no distinct legal escape answer: " + cycle.describe());
                }
                answer = cycle.repeatedAnswer();
                decisionCycleGuard.clear();
                decisionCycleGuard.deferEscape(deferred.orElseThrow());
                recordWarning("repeated-decision-cycle",
                        "Current repeated decision had no alternate; scheduled an untrained alternate for the next "
                                + "cycle decision: " + cycle.describe());
            } else {
                decisionCycleGuard.clear();
                recordWarning("repeated-decision-cycle",
                        "Used an untrained alternate answer \"" + answer + "\" to break a repeated decision cycle: "
                                + cycle.describe());
            }
        } else {
            answer = bot.chooseAction(game.getGameState(), decision);
        }

        try {
            userFeedback.participantDecided(bot.getName());
            String accepted = BotDecisionFallback.decide(game, bot.getName(), decision, answer);
            if (!java.util.Objects.equals(answer, accepted)) {
                recordWarning("invalid-bot-answer-fallback",
                        bot.getName() + " used fallback \"" + accepted
                                + "\" for training decision \"" + decision.getText() + "\"");
                System.out.println(bot.getName() + " used fallback \"" + accepted
                        + "\" for training decision \"" + decision.getText() + "\"");
                if (!cycleEscape && bot instanceof LearningBotPlayer learningBot)
                    learningBot.discardLastDecision();
            }
            String summary = DecisionCycleGuard.formatDecisionOutcome(
                    bot.getName(), phase, decision, accepted, cycleEscape);
            DecisionCycleGuard.addRecentDecision(recentDecisions, summary);
            decisionCycleGuard.record(signature, decision.getDecisionType(), summary, accepted);
            game.carryOutPendingActionsUntilDecisionNeeded();
            if (tacticalMetrics != null)
                tacticalMetrics.capture(game.getGameState());
        } catch (DecisionResultInvalidException e) {
            throw new IllegalStateException(
                    "No valid bot answer for " + format.getCode() + " training decision \"" + decision.getText() + "\"", e);
        }
    }

    private void recordWarning(String type, String message) {
        if (eventReporter != null)
            eventReporter.recordEvent(Severity.WARNING, type, null, message, null);
    }
}
