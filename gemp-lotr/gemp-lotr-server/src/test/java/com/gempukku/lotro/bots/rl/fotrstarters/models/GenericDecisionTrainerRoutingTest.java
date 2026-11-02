package com.gempukku.lotro.bots.rl.fotrstarters.models;

import com.gempukku.lotro.bots.rl.fotrstarters.models.cardaction.GenericCardActionChoiceTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.arbitrarycards.GenericArbitraryCardsTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.integerchoice.BurdenTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.integerchoice.GenericIntegerTrainer;
import com.gempukku.lotro.bots.rl.fotrstarters.models.integerchoice.IntegerOptionFeatures;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GenericDecisionTrainerRoutingTest {
    @Test
    public void genericIntegerTrainerHandlesBoundedIntegerChoicesButNotBids() {
        GenericIntegerTrainer trainer = new GenericIntegerTrainer();

        assertTrue(trainer.appliesTo(null, integerDecision("Choose X"), "bot"));
        assertFalse(trainer.appliesTo(null, integerDecision("Choose a number of burdens to bid"), "bot"));
        assertFalse(trainer.appliesTo(null, integerDecision("Choose how many to spot"), "bot"));
        assertTrue(new BurdenTrainer().appliesTo(
                null, integerDecision("Choose a number of burdens to bid"), "bot"));
    }

    @Test
    public void genericCardActionTrainerHandlesUnclassifiedCardActions() {
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getDecisionType()).thenReturn(AwaitingDecisionType.CARD_ACTION_CHOICE);
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "actionId", new String[]{"0"},
                "actionText", new String[]{"Stack a besieger"}));

        assertTrue(new GenericCardActionChoiceTrainer().appliesTo(null, decision, "bot"));
    }

    @Test
    public void genericArbitraryCardsTrainerHandlesUnclassifiedCardChoices() {
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getDecisionType()).thenReturn(AwaitingDecisionType.ARBITRARY_CARDS);
        when(decision.getText()).thenReturn("Choose cards from deck");
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "cardId", new String[]{"temp0"},
                "blueprintId", new String[]{"8_37"},
                "selectable", new String[]{"true"},
                "min", new String[]{"0"},
                "max", new String[]{"1"}));

        assertTrue(new GenericArbitraryCardsTrainer().appliesTo(null, decision, "bot"));
    }

    @Test
    public void passGateDoesNotTreatZeroCardOrNonCardActionChoicesAsPassable() {
        GenericCardActionChoiceTrainer passGate = new GenericCardActionChoiceTrainer();
        AwaitingDecision optionalZeroCardSelection = mock(AwaitingDecision.class);
        when(optionalZeroCardSelection.getDecisionType()).thenReturn(AwaitingDecisionType.ARBITRARY_CARDS);
        when(optionalZeroCardSelection.getDecisionParameters()).thenReturn(Map.of(
                "cardId", new String[]{"temp0"},
                "selectable", new String[]{"true"},
                "min", new String[]{"0"},
                "max", new String[]{"1"}));
        AwaitingDecision mandatoryChoice = mock(AwaitingDecision.class);
        when(mandatoryChoice.getDecisionType()).thenReturn(AwaitingDecisionType.ACTION_CHOICE);
        when(mandatoryChoice.getDecisionParameters()).thenReturn(Map.of(
                "actionId", new String[]{"mandatory-action"},
                "actionText", new String[]{"Resolve mandatory action"}));

        assertFalse(passGate.appliesTo(null, optionalZeroCardSelection, "bot"));
        assertFalse(passGate.appliesTo(null, mandatoryChoice, "bot"));
        assertFalse(passGate.prefersPass(optionalZeroCardSelection, new double[0], new ModelRegistry()));
        assertFalse(passGate.prefersPass(mandatoryChoice, new double[0], new ModelRegistry()));
    }

    @Test
    public void integerOptionFeaturesAppendAStableRangeEncoding() {
        assertArrayEquals(
                new double[]{2.0, 0.5, 2.0 / 3.0, 1.0 / 3.0, 1.0, 0.0, 0.0},
                IntegerOptionFeatures.append(new double[]{2.0}, 2, 1, 3),
                0.0);
    }

    private static AwaitingDecision integerDecision(String text) {
        AwaitingDecision decision = mock(AwaitingDecision.class);
        when(decision.getDecisionType()).thenReturn(AwaitingDecisionType.INTEGER);
        when(decision.getText()).thenReturn(text);
        when(decision.getDecisionParameters()).thenReturn(Map.of(
                "min", new String[]{"0"},
                "max", new String[]{"3"}));
        return decision;
    }
}
