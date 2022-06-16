package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.Arrays;
import java.util.Random;

import org.apache.log4j.Logger;

import com.gempukku.lotro.logic.decisions.ActionSelectionDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class ActionSelectionDecisionBot extends MakeBotDecision {
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
        LOG.trace("PARAM: actionId = " + getArrayAsString(actionId));
        LOG.trace("PARAM: blueprintId = " + getArrayAsString(blueprintId));
        LOG.trace("PARAM: actionText = " + getArrayAsString(actionText));
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
