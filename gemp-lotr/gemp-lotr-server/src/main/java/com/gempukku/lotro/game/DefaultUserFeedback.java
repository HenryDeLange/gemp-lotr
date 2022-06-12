package com.gempukku.lotro.game;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.gempukku.lotro.communication.UserFeedback;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.ActionSelectionDecision;
import com.gempukku.lotro.logic.decisions.ArbitraryCardsSelectionDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.CardActionSelectionDecision;
import com.gempukku.lotro.logic.decisions.CardsSelectionDecision;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.decisions.ForEachBurdenYouSpotDecision;
import com.gempukku.lotro.logic.decisions.ForEachTwilightTokenYouSpotDecision;
import com.gempukku.lotro.logic.decisions.ForEachYouSpotDecision;
import com.gempukku.lotro.logic.decisions.IntegerAwaitingDecision;
import com.gempukku.lotro.logic.decisions.MultipleChoiceAwaitingDecision;
import com.gempukku.lotro.logic.decisions.PlayerAssignMinionsDecision;
import com.gempukku.lotro.logic.decisions.YesNoDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;
import com.gempukku.lotro.logic.decisions.bot.generic.ActionSelectionDecisionBot;
import com.gempukku.lotro.logic.decisions.bot.generic.ArbitraryCardsSelectionDecisionBot;
import com.gempukku.lotro.logic.decisions.bot.generic.CardActionSelectionDecisionBot;
import com.gempukku.lotro.logic.decisions.bot.generic.CardsSelectionDecisionBot;
import com.gempukku.lotro.logic.decisions.bot.generic.IntegerAwaitingDecisionBot;
import com.gempukku.lotro.logic.decisions.bot.generic.MultipleChoiceAwaitingDecisionBot;
import com.gempukku.lotro.logic.decisions.bot.generic.PlayerAssignMinionsDecisionBot;
import com.gempukku.lotro.logic.decisions.bot.specific.ForEachBurdenYouSpotDecisionBot;
import com.gempukku.lotro.logic.decisions.bot.specific.ForEachTwilightTokenYouSpotDecisionBot;
import com.gempukku.lotro.logic.decisions.bot.specific.ForEachYouSpotDecisionBot;
import com.gempukku.lotro.logic.decisions.bot.specific.YesNoDecisionBot;
import com.gempukku.lotro.logic.timing.DefaultLotroGame;

public class DefaultUserFeedback implements UserFeedback {
    private static final Logger LOG = Logger.getLogger(DefaultUserFeedback.class);
    
    private Map<String, AwaitingDecision> _awaitingDecisionMap = new HashMap<String, AwaitingDecision>();
    private LotroGame _game;
    private Map<String, MakeBotDecision> _botDecisionMap = new HashMap<>();

    public DefaultUserFeedback() {
        // Generic Decisions
        _botDecisionMap.put(ActionSelectionDecision.class.getName(), new ActionSelectionDecisionBot());
        _botDecisionMap.put(ArbitraryCardsSelectionDecision.class.getName(), new ArbitraryCardsSelectionDecisionBot());
        _botDecisionMap.put(CardActionSelectionDecision.class.getName(), new CardActionSelectionDecisionBot());
        _botDecisionMap.put(CardsSelectionDecision.class.getName(), new CardsSelectionDecisionBot());
        _botDecisionMap.put(IntegerAwaitingDecision.class.getName(), new IntegerAwaitingDecisionBot());
        _botDecisionMap.put(MultipleChoiceAwaitingDecision.class.getName(), new MultipleChoiceAwaitingDecisionBot());
        _botDecisionMap.put(PlayerAssignMinionsDecision.class.getName(), new PlayerAssignMinionsDecisionBot());
        // Specific Decisions
        _botDecisionMap.put(ForEachBurdenYouSpotDecision.class.getName(), new ForEachBurdenYouSpotDecisionBot());
        _botDecisionMap.put(ForEachTwilightTokenYouSpotDecision.class.getName(), new ForEachTwilightTokenYouSpotDecisionBot());
        _botDecisionMap.put(ForEachYouSpotDecision.class.getName(), new ForEachYouSpotDecisionBot());
        _botDecisionMap.put(YesNoDecision.class.getName(), new YesNoDecisionBot());
    }

    public void setGame(LotroGame game) {
        _game = game;
    }

    public void participantDecided(String playerId) {
        LOG.trace("[" + playerId + "] participantDecided");
        _awaitingDecisionMap.remove(playerId);
        _game.getGameState().playerDecisionFinished(playerId);
    }

    public AwaitingDecision getAwaitingDecision(String playerId) {
        return _awaitingDecisionMap.get(playerId);
    }

    @Override
    public void sendAwaitingDecision(String playerId, AwaitingDecision awaitingDecision) {
        LOG.trace("[" + playerId + "] sendAwaitingDecision ********** " + awaitingDecision.getClass().getName().replace("com.gempukku.lotro.logic.", "..") + " **********");
        if (playerId != null && playerId.equalsIgnoreCase("bot")) {
            // Don't send, the bot will respond immediately
            handleBotDecision(playerId, awaitingDecision);
        }
        else {
            // Send to normal player
            _awaitingDecisionMap.put(playerId, awaitingDecision);
            _game.getGameState().playerDecisionStarted(playerId, awaitingDecision);
        }
    }

    private void handleBotDecision(String playerId, AwaitingDecision awaitingDecision) {
        // Let the bot make a choice
        String choice = _botDecisionMap.get(getOriginalClass(awaitingDecision).getName()).getBotChoice(awaitingDecision);
        // Make the decision based on the bot's choice
        try {
            participantDecided(playerId);
            LOG.trace("[" + playerId + "] handleBotDecision : decisionMade(choice) = " + choice);
            awaitingDecision.decisionMade(choice);
            if (_game instanceof DefaultLotroGame) {
                ((DefaultLotroGame) _game).carryOutPendingActionsUntilDecisionNeeded();
            }
        }
        catch (DecisionResultInvalidException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private Class<?> getOriginalClass(AwaitingDecision awaitingDecision) {
        Class<?> cls = awaitingDecision.getClass();
        if (cls.isAnonymousClass()) {
            return cls.getInterfaces().length == 0 ? cls.getSuperclass() : cls.getInterfaces()[0];
        }
        else {
            return cls;
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
