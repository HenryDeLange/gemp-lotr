package com.gempukku.lotro.bots;

import com.gempukku.lotro.bots.rl.semanticaction.ActionChoiceAction;
import com.gempukku.lotro.bots.rl.LearningStep;
import com.gempukku.lotro.bots.rl.semanticaction.SemanticActionFactory;
import com.gempukku.lotro.logic.decisions.ActionChoiceAwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertArrayEquals;

public class ActionChoiceSerializationTest {
    @Test
    public void persistsActionChoiceForTrainingRecords() {
        ActionChoiceAction action = new ActionChoiceAction("Choose a card");

        var json = action.toJson();
        assertNotNull(json);
        ActionChoiceAction restored = (ActionChoiceAction) SemanticActionFactory.fromJson(json);

        assertEquals("Choose a card", restored.getActionText());
    }

    @Test
    public void restoresActionChoiceDecisionFromTrainingRecord() {
        var decision = new ActionChoiceAwaitingDecision(
                5, "Choose a required action",
                new String[]{"0", "1"}, new String[]{"rules", "rules"},
                new String[]{"Resolve the first action", "Resolve the second action"});

        var restored = AwaitingDecisionFactory.fromJson(decision.toJson());

        assertArrayEquals(new String[]{"0", "1"}, restored.getDecisionParameters().get("actionId"));
        assertArrayEquals(new String[]{"Resolve the first action", "Resolve the second action"},
                restored.getDecisionParameters().get("actionText"));
    }

    @Test
    public void retainsGameIdForGroupedValidationSplits() {
        var decision = new ActionChoiceAwaitingDecision(
                5, "Choose a required action",
                new String[]{"0"}, new String[]{"rules"},
                new String[]{"Resolve the action"});
        LearningStep step = new LearningStep(
                new double[]{1.0}, new ActionChoiceAction("Resolve the action"), true, decision, "game-123");
        step.trainingWeight = 0.73;

        LearningStep restored = LearningStep.fromJson(step.toJson().toJSONString());

        assertEquals("game-123", restored.gameId);
        assertEquals(0.73, restored.trainingWeight, 0.0);

        var legacyJson = step.toJson();
        legacyJson.remove("trainingWeight");
        LearningStep legacyRestored = LearningStep.fromJson(legacyJson.toJSONString());
        assertEquals(1.0, legacyRestored.trainingWeight, 0.0);
    }
}
