package com.gempukku.lotro.bots.rl.fotrstarters.models.integerchoice;

import java.util.Arrays;

public final class IntegerOptionFeatures {
    public static final int FEATURE_COUNT = 6;

    private IntegerOptionFeatures() {
    }

    public static double[] append(double[] state, int value, int minimum, int maximum) {
        double range = Math.max(1.0, (double) maximum - minimum);
        double scale = Math.max(1.0, Math.max(Math.abs((double) minimum), Math.abs((double) maximum)));
        double[] result = Arrays.copyOf(state, state.length + FEATURE_COUNT);
        int offset = state.length;
        result[offset++] = (value - (double) minimum) / range;
        result[offset++] = value / scale;
        result[offset++] = minimum / scale;
        result[offset++] = maximum / scale;
        result[offset++] = value == minimum ? 1.0 : 0.0;
        result[offset] = value == maximum ? 1.0 : 0.0;
        return result;
    }
}
