package com.gempukku.lotro.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.gempukku.lotro.common.Bot;
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
import com.gempukku.lotro.logic.timing.Action;
import com.gempukku.lotro.logic.timing.DefaultLotroGame;
import com.gempukku.lotro.logic.timing.processes.GameProcess;

public class DefaultUserFeedback implements UserFeedback {
    private static final Logger LOG = Logger.getLogger(DefaultUserFeedback.class);
    
    private Map<String, AwaitingDecision> _awaitingDecisionMap = new HashMap<String, AwaitingDecision>();
    private LotroGame _game;
    private Map<String, MakeBotDecision> _botDecisionMap = new HashMap<>();
    private List<String> _botActionHistory = new ArrayList<>();

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
        LOG.trace(" [" + playerId + "] participantDecided");
        _awaitingDecisionMap.remove(playerId);
        _game.getGameState().playerDecisionFinished(playerId);
    }

    public AwaitingDecision getAwaitingDecision(String playerId) {
        return _awaitingDecisionMap.get(playerId);
    }

    @Override
    public void sendAwaitingDecision(String playerId, AwaitingDecision awaitingDecision) {
        LOG.trace(" [" + playerId + "] *************** Start New Decision ***************");
        LOG.trace(" [" + playerId + "] sendAwaitingDecision -> " + awaitingDecision.getClass().getName().replace("com.gempukku.lotro.logic.", ".."));
        if (_game.isBotGame() && playerId != null && playerId.equalsIgnoreCase(Bot.BOT_NAME.getValue())) {
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
        if (!_botActionHistory.isEmpty()) {
            LOG.trace(" [" + playerId + "] handleBotDecision : Previously played actions " + Arrays.toString(_botActionHistory.toArray()));
        }
        // Let the bot make a choice
        String decisionType = getOriginalClass(awaitingDecision).getName();
        String choice = null;
        if (decisionType.equals(CardActionSelectionDecision.class.getName())) {
            CardActionSelectionDecision decision = (CardActionSelectionDecision) awaitingDecision;
            // If the bot already performed the action then remove it from the available actions list
            // (thus for now the bot will only be allowed to do an action once per phase, to prevent infinite loops or even more silly choices)
            for (String prevAction : _botActionHistory) {
                if (decision.hasActions()) {
                    for (Action action : decision.getCopyOfActions()) {
                        if (action.getActionSource().getBlueprintId().equals(prevAction)) {
                            LOG.trace("[" + playerId + "] handleBotDecision : REMOVE action from decision because it was used before = " + action.getText(_game));
                            decision.removeAction(action);
                            break;
                        }
                    }
                }
            }
            // If there are valid action remaining, then let the bot make a choice
            if (decision.hasActions()) {
                choice = _botDecisionMap.get(decisionType).getBotChoice(decision);
            }
            else {
                LOG.trace("[" + playerId + "] handleBotDecision : SKIP because there are no actions to perform");
            }
            // Keep track of performed actions
            if (choice != null && !choice.trim().isEmpty()) {
                _botActionHistory.add(decision.getAction(Integer.parseInt(choice)).getActionSource().getBlueprintId());
            }
        }
        else {
            choice = _botDecisionMap.get(decisionType).getBotChoice(awaitingDecision);
        }
        // Make the decision based on the bot's choice
        try {
            _game.getGameState().playerDecisionStarted(playerId, awaitingDecision);
            participantDecided(playerId);
            if (choice == null) {
                choice = "";
            }
            LOG.trace("[" + playerId + "] handleBotDecision : decisionMade(choice) = " + choice);
            awaitingDecision.decisionMade(choice);
            if (_game instanceof DefaultLotroGame) {
                DefaultLotroGame defaultLotroGame = (DefaultLotroGame) _game;
                if (defaultLotroGame.getTurnProcedure().getGameProcess().getNextProcess() == null) {
                    defaultLotroGame.carryOutPendingActionsUntilDecisionNeeded();
                }
                else {
                    LOG.trace("[" + playerId + "] handleBotDecision : Continue to next GameProcess -> " 
                            + defaultLotroGame.getTurnProcedure().getGameProcess().getNextProcess().getClass().getName()
                                    .replace("com.gempukku.lotro.logic.", ".."));
                }
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

    /**
     * Don't use, only added for the bot to use
     */
    @Deprecated
    @Override
    public void resetBotActionHistory() {
        LOG.trace("< CLEAR the bot action history >");
        _botActionHistory.clear();
    }

    @Override
    public boolean hasPendingDecisions() {
        return _awaitingDecisionMap.size() > 0;
    }

    public Set<String> getUsersPendingDecision() {
        return _awaitingDecisionMap.keySet();
    }
}
