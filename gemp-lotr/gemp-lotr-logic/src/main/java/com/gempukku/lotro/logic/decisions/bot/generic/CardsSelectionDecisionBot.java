package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import com.gempukku.lotro.common.Bot;
import com.gempukku.lotro.common.Keyword;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.CardsSelectionDecision;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class CardsSelectionDecisionBot extends MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(CardsSelectionDecisionBot.class);
    private Random random = new Random();

    @Override
    public String getBotChoice(LotroGame game, AwaitingDecision awaitingDecision) {
        String choice = null;
        CardsSelectionDecision decision = (CardsSelectionDecision) awaitingDecision;
        LOG.trace(" TEXT: " + decision.getText());
        String[] min = decision.getDecisionParameters().get("min");
        String[] max = decision.getDecisionParameters().get("max");
        String[] cardIds = decision.getDecisionParameters().get("cardId");
        LOG.trace(" PARAM: min = " + getArrayAsString(min));
        LOG.trace(" PARAM: max = " + getArrayAsString(max));
        LOG.trace(" PARAM: cardId = " + getArrayAsString(cardIds));
        int minValue = Integer.parseInt(min[0]);
        int maxValue = Integer.parseInt(max[0]);
        int numberOfCardsToSelect = random.nextInt((maxValue + 1) - minValue) + minValue;
        if (numberOfCardsToSelect > 0) {
            // Handle wound selection
            if (cardIds.length > 0) {
                try {
                    Boolean isOwnCard = null;
                    List<Target> targets = new ArrayList<>(cardIds.length);
                    if (decision.getText().equals("Choose cards to wound")) {
                        PhysicalCard card = decision.getSelectedCardById(Integer.parseInt(cardIds[0]));
                        isOwnCard = card.getOwner().equals(Bot.BOT_NAME.getValue());
                    }
                    else if (decision.getText().startsWith("Choose a character to assign an archery wound to")) {
                        isOwnCard = true;
                    }
                    else if (decision.getText().startsWith("Choose a minion to assign an archery wound to")) {
                        isOwnCard = true;
                    }
                    if (isOwnCard != null) {
                        for (String cardId : cardIds) {
                            int tempCardId = Integer.parseInt(cardId);
                            PhysicalCard card = decision.getSelectedCardById(tempCardId);
                            targets.add(new Target(tempCardId, card.getBlueprint().getTitle(), 
                                    game.getModifiersQuerying().getStrength(game, card),
                                    game.getModifiersQuerying().getVitality(game, card), 
                                    game.getModifiersQuerying().getKeywordCount(game, card, Keyword.DAMAGE)));
                        }
                        // Choose the target to wound
                        Comparator<Target> sort = new Comparator<Target>() {
                            @Override
                            public int compare(Target o1, Target o2) {
                                int result = -1 * Integer.compare(o1.vitality, o2.vitality);
                                if (result == 0) {
                                    result = -1 * Integer.compare(o1.strength, o2.strength);
                                }
                                if (result == 0) {
                                    result = -1 * Integer.compare(o1.damage, o2.damage);
                                }
                                return result;
                            }
                        };
                        Collections.sort(targets, sort);
                        if (isOwnCard) {
                            // TODO: Allow wounding the ring bearer if it will avoid another companion dieing
                            for (int i = targets.size() - 1; i >= 0; i--) {
                                Target target = targets.get(i);
                                if (game.getGameState().getRingBearer(Bot.BOT_NAME.getValue()).getCardId() == target.cardId) {
                                    targets.remove(i);
                                    LOG.trace(" DAMAGE: Avoid wounding the ring bearer " + target.title);
                                    break;
                                }
                            }
                            List<Target> tempTargets = new ArrayList<>(targets);
                            for (int i = tempTargets.size() - 1; i >= 0; i--) {
                                if (tempTargets.get(i).vitality == 1)
                                    tempTargets.remove(i);
                            }
                            Target target;
                            if (!tempTargets.isEmpty())
                                target = tempTargets.get(tempTargets.size() - 1);
                            else
                                target = targets.get(targets.size() - 1);
                            choice = Integer.toString(target.cardId);
                            LOG.trace("DAMAGE: Weakest own card that won't die, or sacrifice the weakest if all will die "
                                    + "(" + target.title + " v" + target.vitality + ")");
                        }
                        else {
                            // TODO: Be smarter if the total number of wounds are known
                            List<Target> tempTargets = new ArrayList<>(targets);
                            for (int i = tempTargets.size() - 1; i >= 0; i--) {
                                if (tempTargets.get(i).vitality > 1)
                                    tempTargets.remove(i);
                            }
                            Target target;
                            if (!tempTargets.isEmpty())
                                target = tempTargets.get(0);
                            else
                                target = targets.get(0);
                            choice = Integer.toString(target.cardId);
                            LOG.trace(" DAMAGE: Strongest enemy card that will die, or the strongest if all won't die "
                                    + "(" + target.title + " v" + target.vitality + ")");
                        }
                    }
                }
                catch (DecisionResultInvalidException | NumberFormatException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
            // Handle non-wound selection
            if (choice == null) {
                List<Integer> selectableCardIndexes = new ArrayList<>(cardIds.length);
                for (int i = 0; i < cardIds.length; i++)
                    selectableCardIndexes.add(i);
                for (int selectCount = 0; selectCount < numberOfCardsToSelect; selectCount++) {
                    int selectedCardIndex = selectableCardIndexes.remove(random.nextInt(selectableCardIndexes.size()));
                    if (selectCount == 0) {
                        choice = cardIds[selectedCardIndex];
                    }
                    else {
                        choice += "," + cardIds[selectedCardIndex];
                    }
                }
            }
        }
        else {
            choice = "";
        }
        LOG.trace("CHOICE: " + choice);
        return choice;
    }

    private class Target {
        private int cardId;
        private String title;
        private int strength;
        private int vitality;
        private int damage;

        private Target(int cardId, String title, int strength, int vitality, int damage) {
            this.cardId = cardId;
            this.title = title;
            this.strength = strength;
            this.vitality = vitality;
            this.damage = damage;
        }
    }

}
