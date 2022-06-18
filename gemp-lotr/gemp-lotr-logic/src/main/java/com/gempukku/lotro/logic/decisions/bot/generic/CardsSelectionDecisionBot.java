package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.CardsSelectionDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class CardsSelectionDecisionBot extends MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(CardsSelectionDecisionBot.class);
    private Random random = new Random();

    @Override
    public String getBotChoice(LotroGame game, AwaitingDecision awaitingDecision) {
        String commaArrayOfSelectedCardIndexes = null;
        CardsSelectionDecision decision = (CardsSelectionDecision) awaitingDecision;
        LOG.trace("TEXT: " + decision.getText());
        String[] min = decision.getDecisionParameters().get("min");
        String[] max = decision.getDecisionParameters().get("max");
        String[] cardId = decision.getDecisionParameters().get("cardId");
        LOG.trace("PARAM: min = " + getArrayAsString(min));
        LOG.trace("PARAM: max = " + getArrayAsString(max));
        LOG.trace("PARAM: cardId = " + getArrayAsString(cardId));
        int minValue = Integer.parseInt(min[0]);
        int maxValue = Integer.parseInt(max[0]);
        int numberOfCardsToSelect = random.nextInt((maxValue + 1) - minValue) + minValue;
        if (numberOfCardsToSelect > 0) {
            List<Integer> selectableCardIndexes = new ArrayList<>(cardId.length);
            for (int i = 0; i < cardId.length; i++)
                selectableCardIndexes.add(i);
            for (int selectCount = 0; selectCount < numberOfCardsToSelect; selectCount++) {
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
        LOG.trace("CHOICE: commaArrayOfSelectedCardIndexes = " + commaArrayOfSelectedCardIndexes);
        return commaArrayOfSelectedCardIndexes;
    }

}
