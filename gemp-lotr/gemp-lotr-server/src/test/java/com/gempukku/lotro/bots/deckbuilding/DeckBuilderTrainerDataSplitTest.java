package com.gempukku.lotro.bots.deckbuilding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckBuilderTrainerDataSplitTest {
    @Test
    void keepsEveryDeckFingerprintOnOnlyOneSideOfTheHoldout() {
        String validationDeck = null;
        String trainingDeck = null;
        for (int index = 0; index < 1000 && (validationDeck == null || trainingDeck == null); index++) {
            String fingerprint = "deck-" + index;
            if (Math.floorMod(fingerprint.hashCode(), 5) == 0)
                validationDeck = fingerprint;
            else
                trainingDeck = fingerprint;
        }

        List<DeckBuilderTrainer.DeckTrainingExample> examples = new ArrayList<>();
        examples.add(new DeckBuilderTrainer.DeckTrainingExample(validationDeck, new double[]{1}, true));
        examples.add(new DeckBuilderTrainer.DeckTrainingExample(validationDeck, new double[]{2}, false));
        examples.add(new DeckBuilderTrainer.DeckTrainingExample(trainingDeck, new double[]{3}, true));
        examples.add(new DeckBuilderTrainer.DeckTrainingExample(trainingDeck, new double[]{4}, false));

        DeckBuilderTrainer.TrainingSplit split = DeckBuilderTrainer.splitByDeckFingerprint(examples);
        Set<String> trainingFingerprints = fingerprints(split.training());
        Set<String> validationFingerprints = fingerprints(split.validation());

        assertEquals(Set.of(trainingDeck), trainingFingerprints);
        assertEquals(Set.of(validationDeck), validationFingerprints);
        assertTrue(java.util.Collections.disjoint(trainingFingerprints, validationFingerprints));
    }

    private static Set<String> fingerprints(List<DeckBuilderTrainer.DeckTrainingExample> examples) {
        Set<String> fingerprints = new HashSet<>();
        examples.forEach(example -> fingerprints.add(example.deckKey()));
        return fingerprints;
    }
}
