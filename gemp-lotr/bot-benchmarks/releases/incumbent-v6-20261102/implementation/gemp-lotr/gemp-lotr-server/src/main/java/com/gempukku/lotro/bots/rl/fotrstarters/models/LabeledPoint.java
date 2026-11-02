package com.gempukku.lotro.bots.rl.fotrstarters.models;

import com.gempukku.lotro.bots.rl.LearningStep;

public record LabeledPoint(int y, double[] x, String gameId, double weight) {
    public LabeledPoint {
        if (x == null)
            throw new IllegalArgumentException("Training features must not be null");
        if (!Double.isFinite(weight) || weight <= 0.0)
            throw new IllegalArgumentException("Training example weight must be finite and positive");
    }

    public LabeledPoint(int y, double[] x, String gameId) {
        this(y, x, gameId, 1.0);
    }

    public LabeledPoint(int y, double[] x) {
        this(y, x, null, 1.0);
    }

    public static LabeledPoint forStep(int y, double[] features, LearningStep step) {
        return new LabeledPoint(y, features, step.gameId, step.trainingWeight);
    }
}