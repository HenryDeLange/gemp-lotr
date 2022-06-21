package com.gempukku.lotro.logic.decisions.bot.specific;

import org.apache.log4j.Logger;

import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;
import com.gempukku.lotro.logic.decisions.bot.generic.IntegerAwaitingDecisionBot;

public class ForEachYouSpotDecisionBot extends MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(ForEachYouSpotDecisionBot.class);
    private final IntegerAwaitingDecisionBot integerAwaitingDecisionBot = new IntegerAwaitingDecisionBot();

    @Override
    public String getBotChoice(LotroGame game, AwaitingDecision awaitingDecision) {
        LOG.trace("DELEGATE TO: IntegerAwaitingDecisionBot");
        return integerAwaitingDecisionBot.getBotChoice(game, awaitingDecision);
    }
    
}