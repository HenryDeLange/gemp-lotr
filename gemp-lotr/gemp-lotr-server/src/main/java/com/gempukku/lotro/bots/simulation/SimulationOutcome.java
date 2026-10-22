package com.gempukku.lotro.bots.simulation;

import java.util.Objects;
import java.util.Optional;

public record SimulationOutcome(GameResult result, Optional<GameTacticalMetrics> tacticalMetrics) {
    public SimulationOutcome {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(tacticalMetrics, "tacticalMetrics");
    }

    public static SimulationOutcome withoutMetrics(GameResult result) {
        return new SimulationOutcome(result, Optional.empty());
    }

    public static SimulationOutcome withMetrics(GameResult result, GameTacticalMetrics metrics) {
        return new SimulationOutcome(result, Optional.of(Objects.requireNonNull(metrics, "metrics")));
    }
}
