package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.Arrays;
import java.util.Random;

import org.apache.log4j.Logger;

import com.gempukku.lotro.logic.decisions.ActionSelectionDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class ActionSelectionDecisionBot implements MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(ActionSelectionDecisionBot.class);
    private Random random = new Random();

    @Override
    public String getBotChoice(AwaitingDecision awaitingDecision) {
        String actionIndex = null;
        ActionSelectionDecision decision = (ActionSelectionDecision) awaitingDecision;
        LOG.trace("TEXT: " + decision.getText());
        String[] actionId = decision.getDecisionParameters().get("actionId");
        String[] blueprintId = decision.getDecisionParameters().get("blueprintId");
        String[] actionText = decision.getDecisionParameters().get("actionText");
        LOG.trace("PARAM: actionId = " + Arrays.toString(actionId));
        LOG.trace("PARAM: blueprintId = " + Arrays.toString(blueprintId));
        LOG.trace("PARAM: actionText = " + Arrays.toString(actionText));
        if (actionId.length > 0) {
            actionIndex = Integer.toString(random.nextInt(actionId.length));
        }
        else {
            actionIndex = "";
        }
        LOG.trace("CHOICE: actionIndex = " + actionIndex);
        return actionIndex;
    }
    
}
