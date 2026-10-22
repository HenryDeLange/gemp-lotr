package com.gempukku.lotro.bots.rl.fotrstarters.models;

import java.util.Arrays;
import java.util.List;

public final class DecisionOptionFeatures {
    private static final int TOKEN_BUCKETS = 32;
    private static final List<String> OPTION_MARKERS = List.of(
            "yes", "no", "first", "second", "top", "bottom", "draw", "discard",
            "heal", "remove", "shadow", "companion", "ring", "wound", "burden",
            "threat", "twilight", "strength", "damage", "archery", "overwhelm",
            "exert", "move", "site", "control", "card", "proceed", "back",
            "cancel", "play", "use", "inspect", "order", "done", "keep", "wear");

    private DecisionOptionFeatures() {
    }

    public static double[] append(double[] state, String option, int index, int optionCount) {
        double[] result = Arrays.copyOf(state, state.length + 2 + OPTION_MARKERS.size() + TOKEN_BUCKETS);
        int offset = state.length;
        result[offset++] = optionCount < 2 ? 0.0 : (double) index / (optionCount - 1);
        result[offset++] = optionCount;

        String normalized = option == null ? "" : option.toLowerCase();
        for (String marker : OPTION_MARKERS)
            result[offset++] = normalized.contains(marker) ? 1.0 : 0.0;
        for (String token : normalized.split("[^a-z0-9]+")) {
            if (!token.isEmpty())
                result[offset + Math.floorMod(token.hashCode(), TOKEN_BUCKETS)] = 1.0;
        }
        return result;
    }
}
