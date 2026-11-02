package com.gempukku.lotro.bots.simulation;

import java.util.Objects;

public class SimulationStats {
    private final int bot1Wins;
    private final int bot2Wins;
    private final int totalGames;
    private final int skippedGames;
    private final BatchTacticalMetrics tacticalMetrics;

    public SimulationStats(int bot1Wins, int bot2Wins, int totalGames) {
        this(bot1Wins, bot2Wins, totalGames, BatchTacticalMetrics.empty(), 0);
    }

    public SimulationStats(
            int bot1Wins,
            int bot2Wins,
            int totalGames,
            BatchTacticalMetrics tacticalMetrics) {
        this(bot1Wins, bot2Wins, totalGames, tacticalMetrics, 0);
    }

    public SimulationStats(
            int bot1Wins,
            int bot2Wins,
            int totalGames,
            BatchTacticalMetrics tacticalMetrics,
            int skippedGames) {
        this.bot1Wins = bot1Wins;
        this.bot2Wins = bot2Wins;
        this.totalGames = totalGames;
        this.skippedGames = skippedGames;
        this.tacticalMetrics = Objects.requireNonNull(tacticalMetrics, "tacticalMetrics");
    }

    public double getBot1WinRate() {
        return totalGames == 0 ? 0.0 : bot1Wins * 100.0 / totalGames;
    }

    public double getBot2WinRate() {
        return totalGames == 0 ? 0.0 : bot2Wins * 100.0 / totalGames;
    }

    public int getBot1Wins() {
        return bot1Wins;
    }

    public int getBot2Wins() {
        return bot2Wins;
    }

    public int getTotalGames() {
        return totalGames;
    }

    public int getSkippedGames() {
        return skippedGames;
    }

    public BatchTacticalMetrics getTacticalMetrics() {
        return tacticalMetrics;
    }

    public SimulationStats plus(SimulationStats other) {
        return new SimulationStats(
                bot1Wins + other.bot1Wins,
                bot2Wins + other.bot2Wins,
                totalGames + other.totalGames,
                tacticalMetrics.plus(other.tacticalMetrics),
                skippedGames + other.skippedGames);
    }

    @Override
    public String toString() {
        return String.format("Bot1: %.1f%%, Bot2: %.1f%%; %d skipped; %s",
                getBot1WinRate(), getBot2WinRate(), skippedGames, tacticalMetrics.compactSummary());
    }
}
