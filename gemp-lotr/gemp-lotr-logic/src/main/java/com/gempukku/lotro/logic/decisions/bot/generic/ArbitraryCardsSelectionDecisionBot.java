package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.ArbitraryCardsSelectionDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class ArbitraryCardsSelectionDecisionBot extends MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(ArbitraryCardsSelectionDecisionBot.class);
    private Random random = new Random();

    @Override
    public String getBotChoice(LotroGame game, AwaitingDecision awaitingDecision) {
        String commaArrayOfSelectedCardIndexes = null;
        ArbitraryCardsSelectionDecision decision = (ArbitraryCardsSelectionDecision) awaitingDecision;
        LOG.trace("TEXT: " + decision.getText());
        String[] min = decision.getDecisionParameters().get("min");
        String[] max = decision.getDecisionParameters().get("max");
        String[] cardId = decision.getDecisionParameters().get("cardId");
        String[] blueprintId = decision.getDecisionParameters().get("blueprintId");
        String[] selectable = decision.getDecisionParameters().get("selectable");
        LOG.trace("PARAM: min = " + getArrayAsString(min));
        LOG.trace("PARAM: max = " + getArrayAsString(max));
        LOG.trace("PARAM: cardId = " + getArrayAsString(cardId));
        LOG.trace("PARAM: blueprintId = " + getArrayAsString(blueprintId));
        LOG.trace("PARAM: selectable = " + getArrayAsString(selectable));
        if (selectable != null && selectable.length > 0) {
            List<Integer> selectableCardIndexes = new ArrayList<>();
            for (int cardIndex = 0; cardIndex < cardId.length; cardIndex++) {
                if (Boolean.parseBoolean(selectable[cardIndex])) {
                    selectableCardIndexes.add(cardIndex);
                }
            }
            if (!selectableCardIndexes.isEmpty()) {
                int minValue = Integer.parseInt(min[0]);
                int maxValue = Integer.parseInt(max[0]);
                int numberOfCardsToSelect = random.nextInt((maxValue + 1) - minValue) + minValue;
                if (awaitingDecision.getClass().getName().contains("PlayerPlaysStartingFellowshipGameProcess")) {
                    numberOfCardsToSelect = maxValue;
                }
                for (int selectCount = 0; selectCount < Math.min(numberOfCardsToSelect, selectableCardIndexes.size()); selectCount++) {
                    int selectedCardIndex = selectableCardIndexes.remove(random.nextInt(selectableCardIndexes.size()));
                    if (selectCount == 0) {
                        commaArrayOfSelectedCardIndexes = cardId[selectedCardIndex];
                    }
                    else {
                        commaArrayOfSelectedCardIndexes += "," + cardId[selectedCardIndex];
                    }
                }
            }
            else {
                commaArrayOfSelectedCardIndexes = "";
            }
        }
        else {
            commaArrayOfSelectedCardIndexes = "";
        }
        LOG.trace("CHOICE: commaArrayOfSelectedCardIndexes = " + commaArrayOfSelectedCardIndexes);
        return commaArrayOfSelectedCardIndexes;
    }
    
}
