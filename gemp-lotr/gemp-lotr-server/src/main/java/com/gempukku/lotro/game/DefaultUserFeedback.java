package com.gempukku.lotro.game;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.gempukku.lotro.communication.UserFeedback;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.ArbitraryCardsSelectionDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.decisions.IntegerAwaitingDecision;

public class DefaultUserFeedback implements UserFeedback {
    private Map<String, AwaitingDecision> _awaitingDecisionMap = new HashMap<String, AwaitingDecision>();
    private LotroGame _game;
    private Random random = new Random();

    public void setGame(LotroGame game) {
        _game = game;
    }

    public void participantDecided(String playerId) {
        _awaitingDecisionMap.remove(playerId);
        _game.getGameState().playerDecisionFinished(playerId);
    }

    public AwaitingDecision getAwaitingDecision(String playerId) {
        return _awaitingDecisionMap.get(playerId);
    }

    @Override
    public void sendAwaitingDecision(String playerId, AwaitingDecision awaitingDecision) {
        if (playerId != null && playerId.equalsIgnoreCase("bot")) {
            // Don't send, the bot will respond immediately
            if (awaitingDecision instanceof IntegerAwaitingDecision) {
                IntegerAwaitingDecision decision = (IntegerAwaitingDecision) awaitingDecision;
                String[] minParam = decision.getDecisionParameters().get("min");
                String[] maxParam = decision.getDecisionParameters().get("max");
                String[] defaultParam = decision.getDecisionParameters().get("defaultValue");
                // TODO: Pick a good value
                try {
                    int choice = 0;
                    if (minParam != null && maxParam != null && minParam.length > 0 && maxParam.length > 0) {
                        choice = (int) Math.abs((Integer.parseInt(maxParam[0]) - Integer.parseInt(minParam[0])) / 2.0);
                    }
                    participantDecided(playerId);
                    decision.decisionMade(Integer.toString(choice));
                }
                catch (DecisionResultInvalidException e) {
                    // TODO: Handle this
                    e.printStackTrace();
                }
            }
            else if (awaitingDecision instanceof ArbitraryCardsSelectionDecision) {
                ArbitraryCardsSelectionDecision decision = (ArbitraryCardsSelectionDecision) awaitingDecision;
                String[] minParam = decision.getDecisionParameters().get("min");
                String[] maxParam = decision.getDecisionParameters().get("max");
                String[] cardIdParam = decision.getDecisionParameters().get("cardId");
                String[] selectableParam = decision.getDecisionParameters().get("selectable");
                // TODO: Pick a good value
                try {
                    String choice = null;
                    if (selectableParam != null && minParam != null && maxParam != null && selectableParam.length > 0 && minParam.length > 0 && maxParam.length > 0) {
                        int cardIndex = random.nextInt(cardIdParam.length);
                        if (Boolean.parseBoolean(selectableParam[cardIndex])) {
                            choice = cardIdParam[cardIndex];
                        }
                    }
                    participantDecided(playerId);
                    decision.decisionMade(choice);
                }
                catch (DecisionResultInvalidException e) {
                    // TODO: Handle this
                    e.printStackTrace();
                }
            }
        }
        else {
            // Send to normal player
            _awaitingDecisionMap.put(playerId, awaitingDecision);
            _game.getGameState().playerDecisionStarted(playerId, awaitingDecision);
        }
    }

    @Override
    public boolean hasPendingDecisions() {
        return _awaitingDecisionMap.size() > 0;
    }

    public Set<String> getUsersPendingDecision() {
        return _awaitingDecisionMap.keySet();
    }
}
