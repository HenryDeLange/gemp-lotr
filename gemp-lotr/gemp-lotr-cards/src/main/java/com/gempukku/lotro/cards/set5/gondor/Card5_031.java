package com.gempukku.lotro.cards.set5.gondor;

import com.gempukku.lotro.common.*;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.ActivateCardAction;
import com.gempukku.lotro.logic.cardtype.AbstractCompanion;
import com.gempukku.lotro.logic.effects.HealCharactersEffect;
import com.gempukku.lotro.logic.effects.choose.ChooseAndAssignMinionToCompanionEffect;
import com.gempukku.lotro.logic.modifiers.Modifier;
import com.gempukku.lotro.logic.modifiers.TwilightCostModifier;
import com.gempukku.lotro.logic.modifiers.condition.PhaseCondition;
import com.gempukku.lotro.logic.timing.PlayConditions;

import java.util.Collections;
import java.util.List;

/**
 * Set: Battle of Helm's Deep
 * Side: Free
 * Culture: Gondor
 * Twilight Cost: 3
 * Type: Companion • Man
 * Strength: 7
 * Vitality: 3
 * Resistance: 6
 * Game Text: Knight. The twilight cost of each other knight in your starting fellowship is -1.
 * Assignment: Assign Alcarin to a minion bearing a [GONDOR] fortification to heal Alcarin.
 */
public class Card5_031 extends AbstractCompanion {
    public Card5_031() {
        super(3, 7, 3, 6, Culture.GONDOR, Race.MAN, null, "Alcarin", "Warrior of Lamedon", true);
        addKeyword(Keyword.KNIGHT);
    }

    @Override
    public List<? extends Modifier> getInPlayModifiers(LotroGame game, PhysicalCard self) {
        return Collections.singletonList(
                new TwilightCostModifier(self,
                        Filters.and(
                                Filters.not(self),
                                Keyword.KNIGHT
                        ), new PhaseCondition(Phase.PLAY_STARTING_FELLOWSHIP), -1));
    }

    @Override
    public List<? extends ActivateCardAction> getPhaseActionsInPlay(final String playerId, final LotroGame game, final PhysicalCard self) {
        if (PlayConditions.canUseFPCardDuringPhase(game, Phase.ASSIGNMENT, self)
                && PlayConditions.canSpot(game, self, Filters.assignableToSkirmishAgainst(Side.FREE_PEOPLE, Filters.and(CardType.MINION, Filters.hasAttached(Culture.GONDOR, Keyword.FORTIFICATION))))) {
            final ActivateCardAction action = new ActivateCardAction(self);
            action.appendCost(
                    new ChooseAndAssignMinionToCompanionEffect(action, playerId, self, CardType.MINION, Filters.hasAttached(Culture.GONDOR, Keyword.FORTIFICATION)));
            action.appendEffect(
                    new HealCharactersEffect(self, self.getOwner(), self));
            return Collections.singletonList(action);
        }
        return null;
    }
}
