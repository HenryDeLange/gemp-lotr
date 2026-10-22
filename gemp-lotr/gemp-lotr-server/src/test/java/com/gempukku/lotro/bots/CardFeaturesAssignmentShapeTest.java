package com.gempukku.lotro.bots;

import com.gempukku.lotro.at.AbstractAtTest;
import com.gempukku.lotro.bots.rl.fotrstarters.CardFeatures;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CardFeaturesAssignmentShapeTest extends AbstractAtTest {
    @Test
    public void passAndAssignedCharacterSlotsHaveTheSameFeatureLength() throws Exception {
        CardFeatures.init(_cardLibrary);

        double[] emptyAssignment = CardFeatures.getAssignmentFeatures(
                Map.of(), Map.of(), 0, 0);
        double[] oneCompanion = CardFeatures.getAssignmentFeatures(
                Map.of("1_365", List.of()), Map.of("1_365", 0), 0, 0);

        assertEquals(emptyAssignment.length, oneCompanion.length);
    }
}
