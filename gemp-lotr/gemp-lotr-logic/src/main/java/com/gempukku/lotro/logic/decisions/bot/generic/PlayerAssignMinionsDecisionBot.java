package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.Arrays;

import org.apache.log4j.Logger;

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
        for (int minionIndex = 0; minionIndex < minions.length; minionIndex++) {
            int freeCharacterIndex = minionIndex + 1; // Don't assign to the ring-bearer
            if (freeCharacterIndex < freeCharacters.length) {
                if (minionIndex == 0) {
                    commaArrayOfCharacterSpaceMinion += freeCharacters[freeCharacterIndex] + " " + minions[minionIndex];
                }
                else {
                    commaArrayOfCharacterSpaceMinion += "," + freeCharacters[freeCharacterIndex] + " " + minions[minionIndex];
                }
            }
        }
        LOG.trace("CHOICE: commaArrayOfCharacterSpaceMinion = " + commaArrayOfCharacterSpaceMinion);
        return commaArrayOfCharacterSpaceMinion;
    }

}
