package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.Arrays;
import java.util.Random;

import org.apache.log4j.Logger;

import com.gempukku.lotro.logic.actions.TransferPermanentAction;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.CardActionSelectionDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class CardActionSelectionDecisionBot extends MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(CardActionSelectionDecisionBot.class);
    private Random random = new Random();

    @Override
    public String getBotChoice(AwaitingDecision awaitingDecision) {
        String actionIndex = null;
        CardActionSelectionDecision decision = (CardActionSelectionDecision) awaitingDecision;
        LOG.trace("TEXT: " + decision.getText());
        String[] actionId = decision.getDecisionParameters().get("actionId");
        String[] cardId = decision.getDecisionParameters().get("cardId");
        String[] blueprintId = decision.getDecisionParameters().get("blueprintId");
        String[] actionText = decision.getDecisionParameters().get("actionText");
        LOG.trace("PARAM: actionId = " + getArrayAsString(actionId));
        LOG.trace("PARAM: cardId = " + getArrayAsString(cardId));
        LOG.trace("PARAM: blueprintId = " + getArrayAsString(blueprintId));
        LOG.trace("PARAM: actionText = " + getArrayAsString(actionText));
        // TODO: Try to use GameState -> xxxSkirmishStrength, etc. to aviod doing actions when already winning a skirmish
        if (actionId.length > 0) {
            actionIndex = Integer.toString(random.nextInt(actionId.length));
            LOG.trace("CARD: " + decision.getAction(Integer.parseInt(actionIndex)).getActionSource().getBlueprintId());
        }
        else {
            actionIndex = "";
        }
        // If this is a Transfer action then don't do it, because otherwise the bot will end up in an infinite loop
        if (decision.getAction(Integer.parseInt(actionIndex)).getClass().getName().equals(TransferPermanentAction.class.getName())) {
            LOG.trace("SKIP: Don't transfer equipment");
            actionIndex = "";
        }
        LOG.trace("CHOICE: actionIndex = " + actionIndex);
        return actionIndex;
    }
    
}
