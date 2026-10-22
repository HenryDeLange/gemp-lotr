package com.gempukku.lotro.bots.simulation;

import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.logic.timing.DefaultLotroGame;
import com.gempukku.lotro.logic.timing.GameStats;

import java.util.Locale;

final class GameTacticalMetricsAccumulator {
    private final String p1;
    private final String p2;
    private final PlayerMetrics p1Metrics = new PlayerMetrics();
    private final PlayerMetrics p2Metrics = new PlayerMetrics();
    private Snapshot previous;
    private int maximumTwilight;
    private int overwhelmingSkirmishes;

    GameTacticalMetricsAccumulator(String p1, String p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    void capture(GameState gameState) {
        Snapshot current = snapshot(gameState);
        maximumTwilight = Math.max(maximumTwilight, current.twilight());
        if (previous != null) {
            accumulatePlayerChanges(p1Metrics, previous.p1Burdens(), current.p1Burdens(),
                    previous.p1Threats(), current.p1Threats(),
                    previous.p1DeadCompanions(), current.p1DeadCompanions());
            accumulatePlayerChanges(p2Metrics, previous.p2Burdens(), current.p2Burdens(),
                    previous.p2Threats(), current.p2Threats(),
                    previous.p2DeadCompanions(), current.p2DeadCompanions());
            accumulateTwilightChanges(previous, current);
            if (!previous.fpOverwhelmed() && current.fpOverwhelmed())
                overwhelmingSkirmishes++;
        }
        previous = current;
    }

    GameTacticalMetrics finish(DefaultLotroGame game) {
        GameState gameState = game.getGameState();
        capture(gameState);

        int p1Position = gameState.getPlayerPosition(p1);
        int p2Position = gameState.getPlayerPosition(p2);
        int p1BurdenCount = gameState.getPlayerBurdens(p1);
        int p2BurdenCount = gameState.getPlayerBurdens(p2);
        PhysicalCard p1RingBearer = gameState.getRingBearer(p1);
        PhysicalCard p2RingBearer = gameState.getRingBearer(p2);
        int p1Wounds = p1RingBearer == null ? 0 : gameState.getWounds(p1RingBearer);
        int p2Wounds = p2RingBearer == null ? 0 : gameState.getWounds(p2RingBearer);

        return new GameTacticalMetrics(
                victoryReason(game, p1Position, p2Position),
                p1Position,
                p2Position,
                countControlledSites(gameState, p1),
                countControlledSites(gameState, p2),
                p1BurdenCount,
                p2BurdenCount,
                p1Metrics.burdensAdded,
                p2Metrics.burdensAdded,
                p1Metrics.burdensRemoved,
                p2Metrics.burdensRemoved,
                gameState.getPlayerThreats(p1),
                gameState.getPlayerThreats(p2),
                p1Metrics.threatsAdded,
                p2Metrics.threatsAdded,
                p1Metrics.threatsRemoved,
                p2Metrics.threatsRemoved,
                p1Wounds,
                p2Wounds,
                p1Metrics.companionsKilled,
                p2Metrics.companionsKilled,
                p1Metrics.fellowshipTwilightGenerated,
                p2Metrics.fellowshipTwilightGenerated,
                p1Metrics.shadowTwilightSpent,
                p2Metrics.shadowTwilightSpent,
                overwhelmingSkirmishes,
                maximumTwilight);
    }

    private void accumulatePlayerChanges(
            PlayerMetrics metrics,
            int previousBurdens,
            int currentBurdens,
            int previousThreats,
            int currentThreats,
            int previousDeadCompanions,
            int currentDeadCompanions) {
        metrics.burdensAdded += positiveDelta(currentBurdens, previousBurdens);
        metrics.burdensRemoved += positiveDelta(previousBurdens, currentBurdens);
        metrics.threatsAdded += positiveDelta(currentThreats, previousThreats);
        metrics.threatsRemoved += positiveDelta(previousThreats, currentThreats);
        metrics.companionsKilled += positiveDelta(currentDeadCompanions, previousDeadCompanions);
    }

    private void accumulateTwilightChanges(Snapshot previous, Snapshot current) {
        int delta = current.twilight() - previous.twilight();
        if (delta == 0 || previous.currentPlayerId() == null)
            return;

        PlayerMetrics activePlayer = metricsFor(previous.currentPlayerId());
        if (activePlayer == null)
            return;
        if (delta > 0 && previous.phase() == Phase.FELLOWSHIP)
            activePlayer.fellowshipTwilightGenerated += delta;
        else if (delta < 0 && previous.phase() == Phase.SHADOW)
            activePlayer.shadowTwilightSpent += -delta;
    }

    private Snapshot snapshot(GameState gameState) {
        GameStats stats = gameState.getMostRecentGameStats();
        return new Snapshot(
                gameState.getCurrentPhase(),
                gameState.getCurrentPlayerId(),
                gameState.getTwilightPool(),
                gameState.getPlayerBurdens(p1),
                gameState.getPlayerBurdens(p2),
                gameState.getPlayerThreats(p1),
                gameState.getPlayerThreats(p2),
                countDeadCompanions(gameState, p1),
                countDeadCompanions(gameState, p2),
                stats != null && stats.isFpOverwhelmed());
    }

    private PlayerMetrics metricsFor(String playerId) {
        if (p1.equals(playerId))
            return p1Metrics;
        if (p2.equals(playerId))
            return p2Metrics;
        return null;
    }

    private static int positiveDelta(int current, int previous) {
        return Math.max(0, current - previous);
    }

    private static int countControlledSites(GameState gameState, String playerId) {
        int count = 0;
        for (int siteNumber = 1; siteNumber <= 9; siteNumber++) {
            PhysicalCard site = gameState.getSite(siteNumber);
            if (site != null && playerId.equals(site.getCardController()))
                count++;
        }
        return count;
    }

    private static int countDeadCompanions(GameState gameState, String playerId) {
        return (int) gameState.getDeadPile(playerId).stream()
                .filter(card -> card.getBlueprint().getCardType() == CardType.COMPANION)
                .count();
    }

    private GameTacticalMetrics.VictoryReason victoryReason(
            DefaultLotroGame game,
            int p1Position,
            int p2Position) {
        String winner = game.getWinnerPlayerId();
        String loser = winner.equals(p1) ? p2 : p1;
        String lossReason = game.getLoserReasons().getOrDefault(loser, "").toLowerCase(Locale.ROOT);
        if (lossReason.contains("corrupt"))
            return GameTacticalMetrics.VictoryReason.RING_BEARER_CORRUPTION;
        if (lossReason.contains("ring") && lossReason.contains("dead"))
            return GameTacticalMetrics.VictoryReason.RING_BEARER_DEATH;
        int winnerPosition = winner.equals(p1) ? p1Position : p2Position;
        String winnerReason = game.getWinnerReason() == null
                ? ""
                : game.getWinnerReason().toLowerCase(Locale.ROOT);
        if (winnerPosition >= 9 || winnerReason.contains("site 9"))
            return GameTacticalMetrics.VictoryReason.SITE_NINE;
        return GameTacticalMetrics.VictoryReason.OTHER;
    }

    private record Snapshot(
            Phase phase,
            String currentPlayerId,
            int twilight,
            int p1Burdens,
            int p2Burdens,
            int p1Threats,
            int p2Threats,
            int p1DeadCompanions,
            int p2DeadCompanions,
            boolean fpOverwhelmed) {
    }

    private static final class PlayerMetrics {
        private int burdensAdded;
        private int burdensRemoved;
        private int threatsAdded;
        private int threatsRemoved;
        private int companionsKilled;
        private int fellowshipTwilightGenerated;
        private int shadowTwilightSpent;
    }
}
