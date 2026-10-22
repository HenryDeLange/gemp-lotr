package com.gempukku.lotro.bots.simulation;

public record GameTacticalMetrics(
        VictoryReason victoryReason,
        int p1SitePosition,
        int p2SitePosition,
        int p1SitesControlled,
        int p2SitesControlled,
        int p1FinalBurdens,
        int p2FinalBurdens,
        int p1BurdensAdded,
        int p2BurdensAdded,
        int p1BurdensRemoved,
        int p2BurdensRemoved,
        int p1FinalThreats,
        int p2FinalThreats,
        int p1ThreatsAdded,
        int p2ThreatsAdded,
        int p1ThreatsRemoved,
        int p2ThreatsRemoved,
        int p1RingBearerWounds,
        int p2RingBearerWounds,
        int p1CompanionsKilled,
        int p2CompanionsKilled,
        int p1FellowshipTwilightGenerated,
        int p2FellowshipTwilightGenerated,
        int p1ShadowTwilightSpent,
        int p2ShadowTwilightSpent,
        int overwhelmingSkirmishes,
        int maximumTwilight) {

    public enum VictoryReason {
        SITE_NINE,
        RING_BEARER_CORRUPTION,
        RING_BEARER_DEATH,
        OTHER
    }
}
