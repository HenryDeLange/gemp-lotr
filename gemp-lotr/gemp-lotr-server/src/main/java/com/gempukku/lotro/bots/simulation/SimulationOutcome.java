package com.gempukku.lotro.bots.simulation;

import java.util.Objects;
import java.util.Optional;

public record SimulationOutcome(
        GameResult result,
        Optional<GameTacticalMetrics> tacticalMetrics,
        Optional<GameSeating> seating) {
    public SimulationOutcome(GameResult result, Optional<GameTacticalMetrics> tacticalMetrics) {
        this(result, tacticalMetrics, tacticalMetrics.map(metrics ->
                new GameSeating(metrics.p1SeatIndex(), metrics.p1StartsFirst())));
    }

    public SimulationOutcome {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(tacticalMetrics, "tacticalMetrics");
        Objects.requireNonNull(seating, "seating");
    }

    public static SimulationOutcome withoutMetrics(GameResult result) {
        return new SimulationOutcome(result, Optional.empty(), Optional.empty());
    }

    public static SimulationOutcome withMetrics(GameResult result, GameTacticalMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics");
        return new SimulationOutcome(result, Optional.of(metrics),
                Optional.of(new GameSeating(metrics.p1SeatIndex(), metrics.p1StartsFirst())));
    }

    public static SimulationOutcome withSeating(GameResult result, GameSeating seating) {
        return new SimulationOutcome(result, Optional.empty(), Optional.of(Objects.requireNonNull(seating, "seating")));
    }
}
