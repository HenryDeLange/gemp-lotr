package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.Random;

import org.apache.log4j.Logger;

import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.TransferPermanentAction;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.CardActionSelectionDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class CardActionSelectionDecisionBot extends MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(CardActionSelectionDecisionBot.class);
    private Random random = new Random();

    @Override
    public String getBotChoice(LotroGame game, AwaitingDecision awaitingDecision) {
        String choice = "";
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
            choice = Integer.toString(random.nextInt(actionId.length));
            PhysicalCard card = decision.getAction(Integer.parseInt(choice)).getActionSource();
            LOG.trace("CARD: " + card.getBlueprintId() + " (" + card.getBlueprint().getTitle() + ")");
        }
        // If this is a Transfer action then don't do it, because otherwise the bot will end up in an infinite loop
        if (decision.getAction(Integer.parseInt(choice)).getClass().getName().equals(TransferPermanentAction.class.getName())) {
            LOG.trace("SKIP: Don't transfer equipment");
            choice = "";
        }
        LOG.trace("CHOICE: " + choice);
        return choice;
    }
    
}
