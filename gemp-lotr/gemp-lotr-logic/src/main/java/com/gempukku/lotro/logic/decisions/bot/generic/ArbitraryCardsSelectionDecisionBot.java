package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;

import com.gempukku.lotro.common.Bot;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.ArbitraryCardsSelectionDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class ArbitraryCardsSelectionDecisionBot extends MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(ArbitraryCardsSelectionDecisionBot.class);
    private Random random = new Random();

    @Override
    public String getBotChoice(LotroGame game, AwaitingDecision awaitingDecision) {
        ArbitraryCardsSelectionDecision decision = (ArbitraryCardsSelectionDecision) awaitingDecision;
        LOG.trace(" TEXT: " + decision.getText());
        String[] min = decision.getDecisionParameters().get("min");
        String[] max = decision.getDecisionParameters().get("max");
        String[] cardId = decision.getDecisionParameters().get("cardId");
        String[] blueprintId = decision.getDecisionParameters().get("blueprintId");
        String[] selectable = decision.getDecisionParameters().get("selectable");
        LOG.trace(" PARAM: min = " + getArrayAsString(min));
        LOG.trace(" PARAM: max = " + getArrayAsString(max));
        LOG.trace(" PARAM: cardId = " + getArrayAsString(cardId));
        LOG.trace(" PARAM: blueprintId = " + getArrayAsString(blueprintId));
        LOG.trace(" PARAM: selectable = " + getArrayAsString(selectable));
        String choice = "";
        if (game.getGameState().getCurrentPhase() == Phase.PLAY_STARTING_FELLOWSHIP) {
            List<? extends PhysicalCard> deck = game.getGameState().getDeck(Bot.BOT_NAME.getValue());
            List<Selectable> selectableCards = new ArrayList<>(cardId.length);
            Set<String> uniqueCardIds = new HashSet<>(cardId.length);
            for (int i = 0; i < cardId.length; i++) {
                String tempCardId = cardId[i];
                if (!uniqueCardIds.contains(tempCardId)) {
                    uniqueCardIds.add(tempCardId);
                    PhysicalCard card = decision.getPhysicalCardByIndex(i);
                    int cost = card.getBlueprint().getTwilightCost();
                    if (cost <= 4) {
                        int count = 0;
                        for (PhysicalCard deckCard : deck) {
                            if (deckCard.getBlueprintId().equals(card.getBlueprintId()))
                                count++;
                        }
                        selectableCards.add(new Selectable(tempCardId, card.getBlueprint().getTitle(), 
                                card.getBlueprint().getStrength(), card.getBlueprint().getVitality(), cost, count));
                    }
                }
            }
            // TODO: How to handle cards like Eomer and Elite Rider, where Eomer is stronger but needs to be played second
            if (selectableCards.size() > 1) {
                // Play as many companions as possible (thus start with the cheapest)
                // Assume less copies of the card in the deck means it is more likely to be in the starting fellowship
                Collections.sort(selectableCards, ((o1, o2) -> {
                    int result = Integer.compare(o1.cost, o2.cost);
                    if (result == 0)
                        result = Integer.compare(o1.copies, o2.copies);
                    if (result == 0)
                        result = -1 * Integer.compare(o1.strength, o2.strength);
                    if (result == 0)
                        result = -1 * Integer.compare(o1.vitality, o2.vitality);
                    return result;
                }));

            }
            // Select the card
            Selectable selectected = selectableCards.get(0);
            choice = selectected.cardId;
            LOG.trace(" FELLOWSHIP: " + selectected.title 
                + " (c" + selectected.cost + ", d" + selectected.copies + ", s" + selectected.strength + ", v" + selectected.vitality + ")");
        }
        else {
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
                            choice = cardId[selectedCardIndex];
                        }
                        else {
                            choice += "," + cardId[selectedCardIndex];
                        }
                    }
                }
            }
        }
        LOG.trace("CHOICE: " + choice);
        return choice;
    }

    private class Selectable {
        private String cardId;
        private String title;
        private int strength;
        private int vitality;
        private int cost;
        private int copies;

        private Selectable(String cardId, String title, int strength, int vitality, int cost, int copies) {
            this.cardId = cardId;
            this.title = title;
            this.strength = strength;
            this.vitality = vitality;
            this.cost = cost;
            this.copies = copies;
        }
    }
    
}
