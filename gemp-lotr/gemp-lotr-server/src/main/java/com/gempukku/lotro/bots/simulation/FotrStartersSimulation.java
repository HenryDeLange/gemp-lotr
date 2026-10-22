package com.gempukku.lotro.bots.simulation;

import com.gempukku.lotro.bots.BotPlayer;
import com.gempukku.lotro.bots.BotDecisionFallback;
import com.gempukku.lotro.db.DeckSerialization;
import com.gempukku.lotro.game.DefaultUserFeedback;
import com.gempukku.lotro.game.LotroCardBlueprintLibrary;
import com.gempukku.lotro.game.formats.LotroFormatLibrary;
import com.gempukku.lotro.bots.rl.LearningBotPlayer;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.timing.DefaultLotroGame;
import com.gempukku.lotro.logic.vo.LotroDeck;

import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class FotrStartersSimulation implements Simulation {
    private static final int MAX_DECISIONS_PER_GAME = 10_000;

    private final LotroCardBlueprintLibrary library;
    private final LotroFormatLibrary formatLibrary;
    private final DefaultUserFeedback userFeedback;

    public FotrStartersSimulation(LotroCardBlueprintLibrary library, LotroFormatLibrary formatLibrary) {
        this.library = library;
        this.formatLibrary = formatLibrary;
        this.userFeedback = new DefaultUserFeedback();
    }


    @Override
    public GameResult simulateGame(BotPlayer bot1, BotPlayer bot2) {
        if (bot1.getName().equals(bot2.getName())) {
            throw new IllegalArgumentException("Bot names cannot be equal.");
        }

        // Make starter decks
        LotroDeck aragornStarter = DeckSerialization.buildDeckFromContents("Aragorn Starter",
                "1_290|1_2|1_320,1_327,1_340,1_346,1_349,1_351,1_355,1_358,1_361|1_365,1_365,1_92,1_94,1_94,1_97," +
                        "1_97,1_101,1_104,1_104,1_106,1_107,1_107,1_296,1_296,1_299,1_299,1_51,1_108,1_108,1_110,1_110,1_309," +
                        "1_311,1_116,1_116,1_116,1_117,1_117,1_117,1_121,1_121,1_121,1_133,1_133,1_141,1_141,1_145,1_150," +
                        "1_150,1_150,1_150,1_151,1_151,1_151,1_152,1_152,1_152,1_152,1_153,1_153,1_153,1_153,1_154,1_154," +
                        "1_154,1_157,1_157,1_158,1_158", "fotr_block", "Aragorn Starter");
        LotroDeck gandalfStarter = DeckSerialization.buildDeckFromContents("Gandalf Starter",
                "1_290|1_2|1_326,1_331,1_337,1_345,1_349,1_350,1_353,1_359,1_360|1_70,1_97,1_286,1_286,1_286,1_37," +
                        "1_37,1_364,1_364,1_12,1_12,1_296,1_296,1_299,1_76,1_76,1_76,1_51,1_51,1_78,1_78,1_78,1_78,1_304," +
                        "1_304,1_84,1_312,1_26,1_26,1_86,1_168,1_168,1_168,1_176,1_176,1_176,1_176,1_177,1_178,1_178,1_178" +
                        ",1_178,1_179,1_179,1_179,1_180,1_180,1_180,1_180,1_181,1_181,1_181,1_187,1_187,1_187,1_191," +
                        "1_191,1_191,1_196,1_196", "fotr_block", "Gandalf Starter");
        List<LotroDeck> availableDecks = List.of(aragornStarter, gandalfStarter);
        Map<String, LotroDeck> decks = new HashMap<>();
        decks.put(bot1.getName(), availableDecks.get(ThreadLocalRandom.current().nextInt(availableDecks.size())));
        decks.put(bot2.getName(), availableDecks.get(ThreadLocalRandom.current().nextInt(availableDecks.size())));

        // Init game with bots and starter decks
        DefaultLotroGame lotroGame = new DefaultLotroGame(formatLibrary.getFormat("fotr_block"), decks, userFeedback, library);
        userFeedback.setGame(lotroGame);

        lotroGame.startGame();

        // Simulate game loop
        int decisions = 0;
        DecisionCycleGuard decisionCycleGuard = new DecisionCycleGuard();
        Deque<String> recentDecisions = new ArrayDeque<>();
        while (!lotroGame.isFinished()) {
            if (++decisions > MAX_DECISIONS_PER_GAME)
                throw new IllegalStateException("Starter game exceeded " + MAX_DECISIONS_PER_GAME
                        + " bot decisions; recent decisions: "
                        + (recentDecisions.isEmpty() ? "<none>" : String.join(" | ", recentDecisions)));

            if (userFeedback.getAwaitingDecision(bot1.getName()) != null) {
                getDecisionAndDecide(bot1, lotroGame, decisionCycleGuard, recentDecisions);
            } else if (userFeedback.getAwaitingDecision(bot2.getName()) != null) {
                getDecisionAndDecide(bot2, lotroGame, decisionCycleGuard, recentDecisions);
            } else {
                throw new IllegalStateException("Starter game has no pending decision and is not finished");
            }
        }

        // Return result based on outcome
        if (lotroGame.getWinnerPlayerId().equals(bot1.getName())) {
            return GameResult.P1_WON;
        } else {
            return GameResult.P2_WON;
        }
    }

    private void getDecisionAndDecide(
            BotPlayer bot,
            DefaultLotroGame lotroGame,
            DecisionCycleGuard decisionCycleGuard,
            Deque<String> recentDecisions) {
        AwaitingDecision awaitingDecision = userFeedback.getAwaitingDecision(bot.getName());
        String phase = lotroGame.getGameState().getCurrentPhase().toString();
        String signature = DecisionCycleGuard.signature(
                bot.getName(), phase, awaitingDecision, lotroGame.getGameState());
        var deferredEscape = decisionCycleGuard.takeDeferredEscape(signature);
        var repeatedCycle = deferredEscape.isPresent()
                ? java.util.Optional.<DecisionCycleGuard.RepeatedCycle>empty()
                : decisionCycleGuard.findRepeatedCycle(signature);
        boolean cycleEscape = deferredEscape.isPresent() || repeatedCycle.isPresent();
        String action;
        if (deferredEscape.isPresent()) {
            DecisionCycleGuard.DeferredEscape escape = deferredEscape.orElseThrow();
            action = BotDecisionFallback.alternativeAnswer(
                    lotroGame, bot.getName(), awaitingDecision, escape.repeatedAnswer());
            if (action == null) {
                throw new IllegalStateException("Scheduled cycle escape is no longer legal in starter game: "
                        + DecisionCycleGuard.formatDecisionOutcome(
                        bot.getName(), phase, awaitingDecision, escape.repeatedAnswer(), false));
            }
            decisionCycleGuard.clear();
            System.out.println("Detected repeated starter-game decision cycle; using a deferred untrained alternate "
                    + "answer \"" + action + "\" to break it: "
                    + DecisionCycleGuard.formatDecisionOutcome(
                    bot.getName(), phase, awaitingDecision, action, true));
        } else if (repeatedCycle.isPresent()) {
            DecisionCycleGuard.RepeatedCycle cycle = repeatedCycle.orElseThrow();
            action = BotDecisionFallback.alternativeAnswer(
                    lotroGame, bot.getName(), awaitingDecision, cycle.repeatedAnswer());
            if (action == null) {
                var deferred = cycle.deferredEscape();
                if (deferred.isEmpty()) {
                    throw new IllegalStateException("Detected repeated starter-game decision cycle but found no distinct "
                            + "legal escape answer: " + cycle.describe());
                }
                action = cycle.repeatedAnswer();
                decisionCycleGuard.clear();
                decisionCycleGuard.deferEscape(deferred.orElseThrow());
                System.out.println("Detected repeated starter-game decision cycle; current decision has no alternate, "
                        + "so repeating it and scheduling an untrained alternate for the next cycle decision: "
                        + cycle.describe());
            } else {
                decisionCycleGuard.clear();
                System.out.println("Detected repeated starter-game decision cycle; using an untrained alternate answer \""
                        + action + "\" to break it: " + cycle.describe());
            }
        } else {
            action = bot.chooseAction(lotroGame.getGameState(), awaitingDecision);
        }

        String acceptedAction;
        try {
            userFeedback.participantDecided(bot.getName());
            acceptedAction = BotDecisionFallback.decide(lotroGame, bot.getName(), awaitingDecision, action);
        } catch (DecisionResultInvalidException e) {
            throw new IllegalStateException("No valid bot answer for training decision \"" + awaitingDecision.getText() + "\"", e);
        }

        if (!java.util.Objects.equals(action, acceptedAction)) {
            System.out.println(bot.getName() + " required fallback \"" + acceptedAction
                    + "\" for decision \"" + awaitingDecision.getText() + "\"");
            if (!cycleEscape && bot instanceof LearningBotPlayer learningBot)
                learningBot.discardLastDecision();
        }
        String summary = DecisionCycleGuard.formatDecisionOutcome(
                bot.getName(), phase, awaitingDecision, acceptedAction, cycleEscape);
        DecisionCycleGuard.addRecentDecision(recentDecisions, summary);
        decisionCycleGuard.record(signature, awaitingDecision.getDecisionType(), summary, acceptedAction);
        lotroGame.carryOutPendingActionsUntilDecisionNeeded();
    }
}
