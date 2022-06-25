package com.gempukku.lotro.logic.decisions.bot.generic;

import java.lang.reflect.Field;
import java.util.Random;

import org.apache.log4j.Logger;

import com.gempukku.lotro.cards.build.field.effect.appender.DelayedAppender;
import com.gempukku.lotro.cards.build.field.effect.appender.ModifyStrength;
import com.gempukku.lotro.common.Bot;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.common.Side;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.AbstractCostToEffectAction;
import com.gempukku.lotro.logic.actions.TransferPermanentAction;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.CardActionSelectionDecision;
import com.gempukku.lotro.logic.decisions.bot.MakeBotDecision;
import com.gempukku.lotro.logic.timing.Action;
import com.gempukku.lotro.logic.timing.Effect;
import com.gempukku.lotro.logic.timing.GameStats;

public class CardActionSelectionDecisionBot extends MakeBotDecision {
    private static final Logger LOG = Logger.getLogger(CardActionSelectionDecisionBot.class);
    private Random random = new Random();

    @Override
    public String getBotChoice(LotroGame game, AwaitingDecision awaitingDecision) {
        String choice = "";
        CardActionSelectionDecision decision = (CardActionSelectionDecision) awaitingDecision;
        LOG.trace("TEXT: " + decision.getText());
        String[] actionId = decision.getDecisionParameters().get("actionId");
        String[] cardId = decision.getDecisionParameters().get("cardId");
        String[] blueprintId = decision.getDecisionParameters().get("blueprintId");
        String[] actionText = decision.getDecisionParameters().get("actionText");
        LOG.trace("PARAM: actionId = " + getArrayAsString(actionId));
        LOG.trace("PARAM: cardId = " + getArrayAsString(cardId));
        LOG.trace("PARAM: blueprintId = " + getArrayAsString(blueprintId));
        LOG.trace("PARAM: actionText = " + getArrayAsString(actionText));
        Action chosenAction = null;
        PhysicalCard chosenCard = null;
        if (actionId.length > 0) {
            choice = Integer.toString(random.nextInt(actionId.length));
            chosenAction = decision.getAction(Integer.parseInt(choice));
            chosenCard = chosenAction.getActionSource();
            LOG.trace("CARD: " + chosenCard.getBlueprintId() + " (" + chosenCard.getBlueprint().getTitle() + ")");
        }
        // If this is a Transfer action then don't do it, because otherwise the bot will end up in an infinite loop
        if (decision.getAction(Integer.parseInt(choice)).getClass().getName().equals(TransferPermanentAction.class.getName())) {
            LOG.trace("SKIP: Don't transfer equipment");
            choice = "";
            chosenAction = null;
            chosenCard = null;
        }
        else if (chosenAction != null && chosenCard != null) {
            // Handle skirmish events
            if (game.getGameState().getCurrentPhase() == Phase.SKIRMISH) {
                if (chosenAction instanceof AbstractCostToEffectAction) {
                    AbstractCostToEffectAction effectAction = (AbstractCostToEffectAction) chosenAction;
                    boolean strengthAction = false;
                    for (Effect effect : effectAction.getEffects()) {
                        if (effect.getClass().getName().startsWith(DelayedAppender.class.getName())) {
                            Field[] fields = effect.getClass().getDeclaredFields();
                            for (int i = 0; i < fields.length; i++) {
                                String name = fields[i].getName();
                                if (name.equals("this$0")) {
                                    try {
                                        fields[i].setAccessible(true);
                                        Object outer = fields[i].get(effect);
                                        if (outer.getClass().getName().startsWith(ModifyStrength.class.getName())) {
                                            strengthAction = true;
                                        }
                                    }
                                    catch (IllegalAccessException ex) {
                                        LOG.error(ex.getMessage(), ex);
                                    }
                                }
                            }
                        }
                    }
                    if (strengthAction) {
                        GameStats gameStats = game.getTurnProcedure().getGameStats();
                        if ((game.getGameState().getCurrentPlayerId().equals(Bot.BOT_NAME.getValue())
                                && gameStats.getFellowshipSkirmishStrength() > gameStats.getShadowSkirmishStrength())
                                || (!game.getGameState().getCurrentPlayerId().equals(Bot.BOT_NAME.getValue())
                                        && gameStats.getFellowshipSkirmishStrength() <= gameStats.getShadowSkirmishStrength())) {
                            // Don't play the skirmish (strength boost) event if already winning
                            LOG.trace("SKIP: Don't strength boost when already winning" + "(f" + gameStats.getFellowshipSkirmishStrength() + " vs s"
                                    + gameStats.getShadowSkirmishStrength() + ")");
                            choice = "";
                        }
                        else {
                            LOG.trace("PLAY: Currently losing, try to use " + chosenCard.getBlueprint().getTitle() + " to win "
                              + "(f" + gameStats.getFellowshipSkirmishStrength() + " vs s" + gameStats.getShadowSkirmishStrength() + ")");
                        }
                    }
                }
            }
        }
        LOG.trace("CHOICE: " + choice);
        return choice;
    }

}
