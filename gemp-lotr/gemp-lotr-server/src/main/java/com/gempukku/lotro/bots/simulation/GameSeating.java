package com.gempukku.lotro.bots.simulation;

public record GameSeating(int p1SeatIndex, boolean p1StartsFirst) {
    public GameSeating {
        if (p1SeatIndex < 0 || p1SeatIndex > 1)
            throw new IllegalArgumentException("Player 1 seat index must be 0 or 1");
    }
}
