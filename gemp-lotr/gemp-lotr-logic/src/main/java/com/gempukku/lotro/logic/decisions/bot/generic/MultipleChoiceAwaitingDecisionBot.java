package com.gempukku.lotro.logic.decisions.bot.generic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import com.gempukku.lotro.common.Bot;
import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.MultipleChoiceAwaitingDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;
import com.gempukku.lotro.logic.timing.processes.turn.regroup.FellowshipPlayerChoosesToMoveOrStayGameProcess;

public class MultipleChoiceAwaitingDecisionBot extends MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(MultipleChoiceAwaitingDecisionBot.class);
    private Random random = new Random();

    @Override
    public String getBotChoice(LotroGame game, AwaitingDecision awaitingDecision) {
        String choice = "";
        MultipleChoiceAwaitingDecision decision = (MultipleChoiceAwaitingDecision) awaitingDecision;
        LOG.trace("TEXT: " + decision.getText());
        String[] results = decision.getDecisionParameters().get("results");
        LOG.trace("PARAM: results = " + getArrayAsString(results));
        if (game.getGameState().getCurrentPhase() == Phase.REGROUP
                && decision.getClass().getName().contains(FellowshipPlayerChoosesToMoveOrStayGameProcess.class.getName())) {
            // Count the number of free companions
            List<String> players = new ArrayList<>(game.getGameState().getPlayerOrder().getAllPlayers());
            players.remove(Bot.BOT_NAME.getValue());
            String opponent = players.get(0);
            int twilightPool = game.getGameState().getTwilightPool();
            List<PhysicalCard> inPlayCompanions = new ArrayList<>();
            List<PhysicalCard> inPlayMinions = new ArrayList<>();
            for (PhysicalCard card : game.getGameState().getInPlay()) {
                if (card.getBlueprint().getCardType() == CardType.MINION && card.getOwner().equals(opponent))
                    inPlayMinions.add(card);
                else if (card.getBlueprint().getCardType() == CardType.COMPANION && card.getOwner().equals(Bot.BOT_NAME.getValue()))
                    inPlayCompanions.add(card);
            }
            int minionCount = inPlayMinions.size();
            int companionCount = inPlayCompanions.size();
            // Assuming the average minion costs 3, then only move if it is likely that there will be less minions than companions
            int opponentHandSize = 8; // At this stage the shadow player already reconciled their hand
            int potentialNewMinions = (int) Math.round(Math.min(twilightPool / 3.0, opponentHandSize / 3.0 * 2.0));
            // TODO: Also consider the number of near-death companions and how many minions are likely to win
            // TODO: Also consider defender+1
            // TODO: Also consider sanctuaries
            if (companionCount - 1 > minionCount + potentialNewMinions) {
                choice = "0"; // Move
            }
            else 
                choice = "1"; // Stay
            LOG.trace("MOVE/STAY: companionCount = " + companionCount);
            LOG.trace("MOVE/STAY: twilightPool = " + twilightPool);
            LOG.trace("MOVE/STAY: opponentHandSize = " + opponentHandSize);
            LOG.trace("MOVE/STAY: minionCount = " + minionCount);
            LOG.trace("MOVE/STAY: potentialNewMinions = " + potentialNewMinions);
        }
        else if (results.length > 0) {
            choice = Integer.toString(random.nextInt(results.length));
        }
        LOG.trace("CHOICE: " + choice + " (" + results[Integer.parseInt(choice)] + ")");
        return choice;
    }

}
