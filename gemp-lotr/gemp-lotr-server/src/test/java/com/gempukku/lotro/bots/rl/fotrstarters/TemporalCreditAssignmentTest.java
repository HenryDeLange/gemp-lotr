package com.gempukku.lotro.bots.rl.fotrstarters;

import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.semanticaction.ActionChoiceAction;
import com.gempukku.lotro.logic.decisions.ActionChoiceAwaitingDecision;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TemporalCreditAssignmentTest {
    @Test
    public void terminalRewardGivesMoreWeightToDecisionsNearTheOutcome() {
        ActionChoiceAwaitingDecision decision = new ActionChoiceAwaitingDecision(
                1, "Choose an action", new String[]{"0"}, new String[]{"rules"}, new String[]{"Pass"});
        List<LearningStep> steps = List.of(
                new LearningStep(new double[]{1}, new ActionChoiceAction("Pass"), true, decision, "game-1"),
                new LearningStep(new double[]{2}, new ActionChoiceAction("Pass"), true, decision, "game-1"),
                new LearningStep(new double[]{3}, new ActionChoiceAction("Pass"), true, decision, "game-1"));
        steps.get(0).trainingWeight = 0.5;

        FotrStarterBot.assignTerminalRewardCredit(steps, 1.0);

        assertEquals(1.0, steps.get(0).reward, 0.0);
        assertEquals(0.5 * Math.pow(0.995, 2), steps.get(0).trainingWeight, 0.0);
        assertEquals(0.995, steps.get(1).trainingWeight, 0.0);
        assertEquals(1.0, steps.get(2).trainingWeight, 0.0);
    }
}
