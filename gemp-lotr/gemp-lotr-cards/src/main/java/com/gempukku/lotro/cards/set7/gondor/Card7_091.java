package com.gempukku.lotro.cards.set7.gondor;

import com.gempukku.lotro.common.*;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.ActivateCardAction;
import com.gempukku.lotro.logic.cardtype.AbstractCompanion;
import com.gempukku.lotro.logic.effects.AddUntilEndOfPhaseActionProxyEffect;
import com.gempukku.lotro.logic.effects.HealCharactersEffect;
import com.gempukku.lotro.logic.effects.PreventAllWoundsActionProxy;
import com.gempukku.lotro.logic.effects.SelfExertEffect;
import com.gempukku.lotro.logic.effects.choose.ChooseAndAddUntilEOPStrengthBonusEffect;
import com.gempukku.lotro.logic.effects.choose.ChooseAndExertCharactersEffect;
import com.gempukku.lotro.logic.effects.choose.ChooseAndPlayCardFromHandEffect;
import com.gempukku.lotro.logic.timing.PlayConditions;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Set: The Return of the King
 * Side: Free
 * Culture: Gondor
 * Twilight Cost: 3
 * Type: Companion • Man
 * Strength: 7
 * Vitality: 3
 * Resistance: 6
 * Signet: Frodo
 * Game Text: Ranger. Fellowship: Play a [ROHAN] Man to heal Faramir. Skirmish: Exert Faramir to make an unbound Hobbit
 * strength +2. Skirmish: Exert Gandalf to prevent all wounds to Faramir.
 */
public class Card7_091 extends AbstractCompanion {
    public Card7_091() {
        super(3, 7, 3, 6, Culture.GONDOR, Race.MAN, Signet.FRODO, "Faramir", "Wizard's Pupil", true);
        addKeyword(Keyword.RANGER);
    }

    @Override
    public List<? extends ActivateCardAction> getPhaseActionsInPlay(String playerId, LotroGame game, PhysicalCard self) {
        if (PlayConditions.canUseFPCardDuringPhase(game, Phase.FELLOWSHIP, self)
                && PlayConditions.canPlayFromHand(playerId, game, Culture.ROHAN, Race.MAN)) {
            ActivateCardAction action = new ActivateCardAction(self);
            action.appendCost(
                    new ChooseAndPlayCardFromHandEffect(playerId, game, Culture.ROHAN, Race.MAN));
            action.appendEffect(
                    new HealCharactersEffect(self, self.getOwner(), self));
            return Collections.singletonList(action);
        }
        if (PlayConditions.canUseFPCardDuringPhase(game, Phase.SKIRMISH, self)) {
            List<ActivateCardAction> actions = new LinkedList<ActivateCardAction>();

            if (PlayConditions.canSelfExert(self, game)) {
                ActivateCardAction action = new ActivateCardAction(self);
                action.setText("Make an unbound Hobbit strength +2");
                action.appendCost(
                        new SelfExertEffect(action, self));
                action.appendEffect(
                        new ChooseAndAddUntilEOPStrengthBonusEffect(action, self, playerId, 2, Race.HOBBIT, Filters.unboundCompanion));
                actions.add(action);
            }
            if (PlayConditions.canExert(self, game, Filters.gandalf)) {
                ActivateCardAction action = new ActivateCardAction(self);
                action.setText("Prevent all wounds to Faramir");
                action.appendCost(
                        new ChooseAndExertCharactersEffect(action, playerId, 1, 1, Filters.gandalf));
                action.appendEffect(
                        new AddUntilEndOfPhaseActionProxyEffect(
                                new PreventAllWoundsActionProxy(self, self)));
                actions.add(action);
            }

            return actions;
        }
        return null;
    }
}
