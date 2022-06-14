package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.Arrays;

import org.apache.log4j.Logger;

import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.CardActionSelectionDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class CardActionSelectionDecisionBot implements MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(CardActionSelectionDecisionBot.class);

    @Override
    public String getBotChoice(AwaitingDecision awaitingDecision) {
        String actionIndex = null;
        CardActionSelectionDecision decision = (CardActionSelectionDecision) awaitingDecision;
        LOG.trace("TEXT: " + decision.getText());
        String[] actionId = decision.getDecisionParameters().get("actionId");
        String[] cardId = decision.getDecisionParameters().get("cardId");
        String[] blueprintId = decision.getDecisionParameters().get("blueprintId");
        String[] actionText = decision.getDecisionParameters().get("actionText");
        LOG.trace("PARAM: actionId = " + Arrays.toString(actionId));
        LOG.trace("PARAM: cardId = " + Arrays.toString(cardId));
        LOG.trace("PARAM: blueprintId = " + Arrays.toString(blueprintId));
        LOG.trace("PARAM: actionText = " + Arrays.toString(actionText));
        if (actionId.length > 0) {
            actionIndex = "0";
        }
        else {
            actionIndex = "";
        }
        // If this is a Transfer action then don't do it, because otherwise the bot will end up in an infinite loop
        if (decision.getCopyOfActions().get(Integer.parseInt(actionIndex)).getClass().getName().contains("TransferPermanentAction")) {
            LOG.trace("SKIP: Don't transfer equipment");
// // TODO: Test whether this works (also maybe choose random actions instead of always 0) - does nnot fix the loop...
//             decision.removeAction(decision.getCopyOfActions().get(Integer.parseInt(actionIndex)));
            actionIndex = "";
        }
        LOG.trace("CHOICE: actionIndex = " + actionIndex);
        return actionIndex;
    }
    
}
