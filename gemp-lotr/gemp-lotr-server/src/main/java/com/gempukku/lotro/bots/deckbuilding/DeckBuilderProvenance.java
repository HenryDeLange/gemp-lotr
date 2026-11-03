package com.gempukku.lotro.bots.deckbuilding;

public record DeckBuilderProvenance(
        String playingPolicySha256,
        String referencePoolSha256,
        String referenceManifestSha256) {
    public DeckBuilderProvenance {
        requireValue(playingPolicySha256, "Playing-policy fingerprint");
        requireValue(referencePoolSha256, "Reference-pool fingerprint");
        if (referenceManifestSha256 != null)
            requireValue(referenceManifestSha256, "Reference-manifest fingerprint");
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " must not be blank");
    }
}
