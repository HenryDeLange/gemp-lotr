package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.Arrays;

import org.apache.log4j.Logger;

import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.PlayerAssignMinionsDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class PlayerAssignMinionsDecisionBot implements MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(PlayerAssignMinionsDecisionBot.class);

    @Override
    public String getBotChoice(AwaitingDecision awaitingDecision) {
        String commaArrayOfCharacterSpaceMinion = null;
        PlayerAssignMinionsDecision decision = (PlayerAssignMinionsDecision) awaitingDecision;
        LOG.trace("TEXT: " + decision.getText());
        String[] freeCharacters = decision.getDecisionParameters().get("freeCharacters");
        String[] minions = decision.getDecisionParameters().get("minions");
        LOG.trace("PARAM: freeCharacters = " + Arrays.toString(freeCharacters));
        LOG.trace("PARAM: minions = " + Arrays.toString(minions));
        commaArrayOfCharacterSpaceMinion = "";
        int freeCharacterIndex = 0;
        for (int minionIndex = 0; minionIndex < minions.length; minionIndex++) {
            // Don't assign to the ring-bearer
            if (freeCharacterIndex < freeCharacters.length) {
                PhysicalCard card = decision.getPhysicalFreeCharacterCard(Integer.parseInt(freeCharacters[freeCharacterIndex]));
                if (card.getBlueprint().getTitle().equals("Frodo")) {
                    LOG.trace("SKIP: Don't assign a minion to Frodo");
                    freeCharacterIndex++;
                    continue;
                }
                if (commaArrayOfCharacterSpaceMinion.isEmpty()) {
                    commaArrayOfCharacterSpaceMinion += freeCharacters[freeCharacterIndex++] + " " + minions[minionIndex];
                }
                else {
                    commaArrayOfCharacterSpaceMinion += "," + freeCharacters[freeCharacterIndex++] + " " + minions[minionIndex];
                }
            }
        }
        LOG.trace("CHOICE: commaArrayOfCharacterSpaceMinion = " + commaArrayOfCharacterSpaceMinion);
        return commaArrayOfCharacterSpaceMinion;
    }

}
