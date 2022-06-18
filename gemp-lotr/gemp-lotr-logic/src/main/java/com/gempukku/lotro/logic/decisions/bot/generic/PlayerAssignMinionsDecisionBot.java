package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import com.gempukku.lotro.common.Bot;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.PlayerAssignMinionsDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class PlayerAssignMinionsDecisionBot extends MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(PlayerAssignMinionsDecisionBot.class);

    // TODO: Need to consider defender+1 freeCharacters
    // TODO: Need to maximize assignment on the most wins, not just by strength
    // TODO: When overwhelm is certain, then assign to weakest companion

    @Override
    public String getBotChoice(LotroGame game, AwaitingDecision awaitingDecision) {
        StringBuilder assignment = new StringBuilder();
        PlayerAssignMinionsDecision decision = (PlayerAssignMinionsDecision) awaitingDecision;
        LOG.trace("TEXT: " + decision.getText());
        String[] freeCharacters = decision.getDecisionParameters().get("freeCharacters");
        String[] minions = decision.getDecisionParameters().get("minions");
        LOG.trace("PARAM: freeCharacters = " + getArrayAsString(freeCharacters));
        LOG.trace("PARAM: minions = " + getArrayAsString(minions));
        // Sort the characters in order of strength (highest first)
        List<PhysicalCard> freeCharacterCards = new ArrayList<>(decision.getCopyOfPhysicalFreeCharacterCards());
        Collections.sort(freeCharacterCards, (o1, o2) -> -1 * Integer.compare(o1.getBlueprint().getStrength(), o2.getBlueprint().getStrength()));
        List<PhysicalCard> minionCards = new ArrayList<>(decision.getCopyOfPhysicalMinionCards());
        Collections.sort(minionCards, (o1, o2) -> -1 * Integer.compare(o1.getBlueprint().getStrength(), o2.getBlueprint().getStrength()));
        // If the ring bearer need to fight then choose the weakest minion
        if (minions.length >= freeCharacters.length) {
            assignment.append(freeCharacterCards.remove(0).getCardId()).append(" ").append(minionCards.remove(0).getCardId());
        }
        // Assign the rest of the minions based on strength
        for (PhysicalCard freeCharacterCard : freeCharacterCards) {
            if (freeCharacterCard.getBlueprintId().equals(game.getGameState().getRingBearer(Bot.BOT_NAME.getValue()).getBlueprintId())) {
                LOG.trace("CONDITION: Don't assign a minion to the ring bearer [" + freeCharacterCard.getBlueprint().getTitle() + "]");
            }
            else {
                if (assignment.length() > 0) {
                    assignment.append(",");
                }
                assignment.append(freeCharacterCard.getCardId()).append(" ").append(minionCards.remove(0).getCardId());
            }
            if (minionCards.isEmpty()) {
                break;
            }
        }
        String choice = assignment.toString();
        LOG.trace("CHOICE: " + choice + " (Comma separated array of CardIds: 'Com Min,Com Min')");
        return choice;
    }

}
