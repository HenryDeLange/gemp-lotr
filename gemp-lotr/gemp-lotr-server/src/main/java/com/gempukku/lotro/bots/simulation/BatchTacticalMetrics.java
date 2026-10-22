package com.gempukku.lotro.bots.simulation;

import java.util.Locale;

public record BatchTacticalMetrics(
        int gamesMeasured,
        int siteNineWins,
        int ringBearerCorruptionWins,
        int ringBearerDeathWins,
        int otherWins,
        long overwhelmingSkirmishes,
        long p1SitePositionTotal,
        long p2SitePositionTotal,
        long p1SitesControlledTotal,
        long p2SitesControlledTotal,
        long p1FinalBurdensTotal,
        long p2FinalBurdensTotal,
        long p1RingBearerWoundsTotal,
        long p2RingBearerWoundsTotal,
        long p1BurdensAddedTotal,
        long p2BurdensAddedTotal,
        long p1ThreatsAddedTotal,
        long p2ThreatsAddedTotal,
        long p1CompanionsKilledTotal,
        long p2CompanionsKilledTotal,
        long p1FellowshipTwilightGeneratedTotal,
        long p2FellowshipTwilightGeneratedTotal,
        long p1ShadowTwilightSpentTotal,
        long p2ShadowTwilightSpentTotal,
        long maximumTwilightTotal) {

    public static BatchTacticalMetrics empty() {
        return new BatchTacticalMetrics(0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public BatchTacticalMetrics plus(GameTacticalMetrics metrics) {
        int siteNine = siteNineWins;
        int corruption = ringBearerCorruptionWins;
        int ringBearerDeath = ringBearerDeathWins;
        int other = otherWins;
        switch (metrics.victoryReason()) {
            case SITE_NINE -> siteNine++;
            case RING_BEARER_CORRUPTION -> corruption++;
            case RING_BEARER_DEATH -> ringBearerDeath++;
            case OTHER -> other++;
        }
        return new BatchTacticalMetrics(
                gamesMeasured + 1, siteNine, corruption, ringBearerDeath, other,
                overwhelmingSkirmishes + metrics.overwhelmingSkirmishes(),
                p1SitePositionTotal + metrics.p1SitePosition(),
                p2SitePositionTotal + metrics.p2SitePosition(),
                p1SitesControlledTotal + metrics.p1SitesControlled(),
                p2SitesControlledTotal + metrics.p2SitesControlled(),
                p1FinalBurdensTotal + metrics.p1FinalBurdens(),
                p2FinalBurdensTotal + metrics.p2FinalBurdens(),
                p1RingBearerWoundsTotal + metrics.p1RingBearerWounds(),
                p2RingBearerWoundsTotal + metrics.p2RingBearerWounds(),
                p1BurdensAddedTotal + metrics.p1BurdensAdded(),
                p2BurdensAddedTotal + metrics.p2BurdensAdded(),
                p1ThreatsAddedTotal + metrics.p1ThreatsAdded(),
                p2ThreatsAddedTotal + metrics.p2ThreatsAdded(),
                p1CompanionsKilledTotal + metrics.p1CompanionsKilled(),
                p2CompanionsKilledTotal + metrics.p2CompanionsKilled(),
                p1FellowshipTwilightGeneratedTotal + metrics.p1FellowshipTwilightGenerated(),
                p2FellowshipTwilightGeneratedTotal + metrics.p2FellowshipTwilightGenerated(),
                p1ShadowTwilightSpentTotal + metrics.p1ShadowTwilightSpent(),
                p2ShadowTwilightSpentTotal + metrics.p2ShadowTwilightSpent(),
                maximumTwilightTotal + metrics.maximumTwilight());
    }

    public BatchTacticalMetrics plus(BatchTacticalMetrics other) {
        return new BatchTacticalMetrics(
                gamesMeasured + other.gamesMeasured,
                siteNineWins + other.siteNineWins,
                ringBearerCorruptionWins + other.ringBearerCorruptionWins,
                ringBearerDeathWins + other.ringBearerDeathWins,
                otherWins + other.otherWins,
                overwhelmingSkirmishes + other.overwhelmingSkirmishes,
                p1SitePositionTotal + other.p1SitePositionTotal,
                p2SitePositionTotal + other.p2SitePositionTotal,
                p1SitesControlledTotal + other.p1SitesControlledTotal,
                p2SitesControlledTotal + other.p2SitesControlledTotal,
                p1FinalBurdensTotal + other.p1FinalBurdensTotal,
                p2FinalBurdensTotal + other.p2FinalBurdensTotal,
                p1RingBearerWoundsTotal + other.p1RingBearerWoundsTotal,
                p2RingBearerWoundsTotal + other.p2RingBearerWoundsTotal,
                p1BurdensAddedTotal + other.p1BurdensAddedTotal,
                p2BurdensAddedTotal + other.p2BurdensAddedTotal,
                p1ThreatsAddedTotal + other.p1ThreatsAddedTotal,
                p2ThreatsAddedTotal + other.p2ThreatsAddedTotal,
                p1CompanionsKilledTotal + other.p1CompanionsKilledTotal,
                p2CompanionsKilledTotal + other.p2CompanionsKilledTotal,
                p1FellowshipTwilightGeneratedTotal + other.p1FellowshipTwilightGeneratedTotal,
                p2FellowshipTwilightGeneratedTotal + other.p2FellowshipTwilightGeneratedTotal,
                p1ShadowTwilightSpentTotal + other.p1ShadowTwilightSpentTotal,
                p2ShadowTwilightSpentTotal + other.p2ShadowTwilightSpentTotal,
                maximumTwilightTotal + other.maximumTwilightTotal);
    }

    public String compactSummary() {
        if (gamesMeasured == 0)
            return "no tactical samples";
        return String.format(Locale.ROOT,
                "wins(site9/corrupt/RB-death/other)=%d/%d/%d/%d; overwhelm=%d; "
                        + "avg sites P1/P2=%.1f/%.1f; "
                        +                         "controlled=%.1f/%.1f; final burdens=%.1f/%.1f; RB wounds=%.1f/%.1f; "
                                + "burdens+=%.1f/%.1f; threats+=%.1f/%.1f; "
                        + "FP twilight+=%.1f/%.1f; Shadow twilight spent=%.1f/%.1f",
                siteNineWins, ringBearerCorruptionWins, ringBearerDeathWins, otherWins, overwhelmingSkirmishes,
                average(p1SitePositionTotal), average(p2SitePositionTotal),
                average(p1SitesControlledTotal), average(p2SitesControlledTotal),
                average(p1FinalBurdensTotal), average(p2FinalBurdensTotal),
                average(p1RingBearerWoundsTotal), average(p2RingBearerWoundsTotal),
                average(p1BurdensAddedTotal), average(p2BurdensAddedTotal),
                average(p1ThreatsAddedTotal), average(p2ThreatsAddedTotal),
                average(p1FellowshipTwilightGeneratedTotal), average(p2FellowshipTwilightGeneratedTotal),
                average(p1ShadowTwilightSpentTotal), average(p2ShadowTwilightSpentTotal));
    }

    private double average(long total) {
        return total / (double) gamesMeasured;
    }
}
