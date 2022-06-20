package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.log4j.Logger;

import com.gempukku.lotro.common.Bot;
import com.gempukku.lotro.common.Keyword;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.PlayerAssignMinionsDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class PlayerAssignMinionsDecisionBot extends MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(PlayerAssignMinionsDecisionBot.class);

    // TODO: Need to consider defender+1 freeCharacters

    @Override
    public String getBotChoice(LotroGame game, AwaitingDecision awaitingDecision) {
        StringBuilder assignment = new StringBuilder();
        PlayerAssignMinionsDecision decision = (PlayerAssignMinionsDecision) awaitingDecision;
        LOG.trace(" TEXT: " + decision.getText());
        String[] freeCharacters = decision.getDecisionParameters().get("freeCharacters");
        String[] minions = decision.getDecisionParameters().get("minions");
        LOG.trace(" PARAM: freeCharacters = " + getArrayAsString(freeCharacters));
        LOG.trace(" PARAM: minions = " + getArrayAsString(minions));
        // Get the relevant info and sort in order of strength and vitality (highest first)
        List<Skirmisher> fpSkirmishers = new ArrayList<>(freeCharacters.length);
        List<Skirmisher> mnSkirmishers = new ArrayList<>(minions.length);
        for (PhysicalCard card : decision.getCopyOfPhysicalFreeCharacterCards()) {
            fpSkirmishers.add(new Skirmisher(card.getCardId(), card.getBlueprintId(), 
                    card.getBlueprint().getTitle(), 
                    game.getModifiersQuerying().getStrength(game, card),
                    game.getModifiersQuerying().getVitality(game, card),
                    game.getModifiersQuerying().getKeywordCount(game, card, Keyword.DAMAGE),
                    game.getModifiersQuerying().getOverwhelmMultiplier(game, card)));
        }
        for (PhysicalCard card : decision.getCopyOfPhysicalMinionCards()) {
            mnSkirmishers.add(new Skirmisher(card.getCardId(), card.getBlueprintId(),
                    card.getBlueprint().getTitle(), 
                    game.getModifiersQuerying().getStrength(game, card),
                    game.getModifiersQuerying().getVitality(game, card),
                    game.getModifiersQuerying().getKeywordCount(game, card, Keyword.DAMAGE),
                    game.getModifiersQuerying().getOverwhelmMultiplier(game, card)));
        }
        Comparator<Skirmisher> sort = new Comparator<Skirmisher>() {
            @Override
            public int compare(Skirmisher o1, Skirmisher o2) {
                int result = -1 * Integer.compare(o1.strength, o2.strength);
                if (result == 0) {
                    result = -1 * Integer.compare(o1.vitality, o2.vitality);
                }
                return result;
            }
        };
        Collections.sort(fpSkirmishers, sort);
        Collections.sort(mnSkirmishers, sort);
        // Handle  the ring bearer first
        for (int i = 0; i < fpSkirmishers.size(); i++) {
            Skirmisher fpCard = fpSkirmishers.get(i);
            if (fpCard.blueprintId.equals(game.getGameState().getRingBearer(Bot.BOT_NAME.getValue()).getBlueprintId())) {
                // Remove the ring bearer from the list of fighters
                Skirmisher ringBearer = fpSkirmishers.remove(i);
                // If the ring bearer need to fight then choose the weakest minion
                if (minions.length >= freeCharacters.length) {
                    assign(assignment, ringBearer, mnSkirmishers.remove(mnSkirmishers.size() - 1));
                    LOG.trace(" ASSIGN: Assigned the weakest minion to the ring bearer");
                }
                break;
            }
        }
        // Assign the rest of the minions based on strength
        for (int mnIndex = 0; mnIndex < mnSkirmishers.size(); mnIndex++) {
            // Check if there are free peoples characters left to assign
            if (fpSkirmishers.isEmpty()) {
                LOG.trace(" SWARMED: There are " + (mnSkirmishers.size() - mnIndex) + " more minions than free peoples characters");
                break;
            }
            // Choose the free peoples characters to fight the minion
            Skirmisher mnSkirmisher = mnSkirmishers.get(mnIndex);
            List<Skirmisher> tempFpSkirmishers = new ArrayList<>(fpSkirmishers);
            // Remove all free peoples characters that will be killed by this minion
            for (int fpIndex = tempFpSkirmishers.size() -1; fpIndex >= 0; fpIndex--) {
                Skirmisher fpSkirmisher = tempFpSkirmishers.get(fpIndex);
                if (mightDie(fpSkirmisher, mnSkirmisher))
                    tempFpSkirmishers.remove(fpIndex);
            }
            if (tempFpSkirmishers.isEmpty()) {
                // This minion might kill all free peoples characters, sacrifice the weakest one
                LOG.trace(" SACRIFICE: Assigning the weakest free peoples character");
                assign(assignment, fpSkirmishers.remove(fpSkirmishers.size() - 1), mnSkirmisher);
            }
            else {
                // Assign the free people character if it is winning
                boolean wasAssigned = false;
                for (Skirmisher fpSkirmisher : tempFpSkirmishers) {
                    if (fpSkirmisher.strength > mnSkirmisher.strength) {
                        assign(assignment, fpSkirmishers.remove(fpSkirmishers.indexOf(fpSkirmisher)), mnSkirmisher);
                        wasAssigned = true;
                        break;
                    }
                }
                if (!wasAssigned) {
                    // All free people characters that aren't killed still lose to this minion, sacrifice the weakest one
                    assign(assignment, fpSkirmishers.remove(fpSkirmishers.indexOf(tempFpSkirmishers.get(tempFpSkirmishers.size() - 1))), mnSkirmisher);
                }
            }
        }
        String choice = assignment.toString();
        LOG.trace("CHOICE: " + choice + " ('FrP Min,FrP Min')");
        return choice;
    }

    private void assign(StringBuilder assignment, Skirmisher freePeoplesCharacter, Skirmisher minion) {
        if (assignment.length() > 0) {
            assignment.append(",");
        }
        assignment.append(freePeoplesCharacter.cardId).append(" ").append(minion.cardId);
        LOG.trace("ASSIGN: " + freePeoplesCharacter.title + " (s" + freePeoplesCharacter.strength + ",d" + freePeoplesCharacter.damage + ",v" + freePeoplesCharacter.vitality + ")"
                + " + " + minion.title + " (s" + minion.strength + ",d" + minion.damage + ",v" + minion.vitality + ") ");
    }

    private boolean mightDie(Skirmisher fpSkirmisher, Skirmisher mnSkirmisher) {
        return fpSkirmisher.vitality <= mnSkirmisher.damage
                || fpSkirmisher.strength * fpSkirmisher.overwhelmMultiplier < mnSkirmisher.strength;
    }

    private class Skirmisher {
        private int cardId;
        private String blueprintId;
        private String title;
        private int strength;
        private int vitality;
        private int damage;
        private int overwhelmMultiplier;

        private Skirmisher(int cardId, String blueprintId, String title, int strength, int vitality, int damage, int overwhelmMultiplier) {
            this.cardId = cardId;
            this.blueprintId = blueprintId;
            this.title = title;
            this.strength = strength;
            this.vitality = vitality;
            this.damage = damage;
            this.overwhelmMultiplier = overwhelmMultiplier;
        }
    }

}
