package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.Arrays;

import org.apache.log4j.Logger;

import com.gempukku.lotro.logic.decisions.ActionSelectionDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class ActionSelectionDecisionBot implements MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(ActionSelectionDecisionBot.class);

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
            actionIndex = "0";
        }
        else {
            actionIndex = "";
        }
        LOG.trace("CHOICE: actionIndex = " + actionIndex);
        return actionIndex;
    }
    
}
