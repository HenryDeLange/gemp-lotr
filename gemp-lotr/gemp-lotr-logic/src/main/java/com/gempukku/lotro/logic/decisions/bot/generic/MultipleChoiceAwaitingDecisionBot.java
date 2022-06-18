package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.Random;

import org.apache.log4j.Logger;

import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.MultipleChoiceAwaitingDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;

public class MultipleChoiceAwaitingDecisionBot extends MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(MultipleChoiceAwaitingDecisionBot.class);
    private Random random = new Random();

    @Override
    public String getBotChoice(LotroGame game, AwaitingDecision awaitingDecision) {
        String choiceIndex = null;
        MultipleChoiceAwaitingDecision decision = (MultipleChoiceAwaitingDecision) awaitingDecision;
        LOG.trace("TEXT: " + decision.getText());
        String[] results = decision.getDecisionParameters().get("results");
        LOG.trace("PARAM: results = " + getArrayAsString(results));
        if (results.length > 0) {
            choiceIndex = Integer.toString(random.nextInt(results.length));
        }
        else {
            choiceIndex = "";
        }
        LOG.trace("CHOICE: choiceIndex = " + choiceIndex);
        return choiceIndex;
    }

}
