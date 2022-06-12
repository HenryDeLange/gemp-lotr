package com.gempukku.lotro.logic.decisions.bot.specific;

import org.apache.log4j.Logger;

import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;
import com.gempukku.lotro.logic.decisions.bot.generic.MultipleChoiceAwaitingDecisionBot;

public class YesNoDecisionBot implements MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(YesNoDecisionBot.class);
    private final MultipleChoiceAwaitingDecisionBot multipleChoiceAwaitingDecisionBot = new MultipleChoiceAwaitingDecisionBot();

    @Override
    public String getBotChoice(AwaitingDecision awaitingDecision) {
        LOG.trace("DELEGATE TO: MultipleChoiceAwaitingDecisionBot");
        return multipleChoiceAwaitingDecisionBot.getBotChoice(awaitingDecision);
    }
    
}
