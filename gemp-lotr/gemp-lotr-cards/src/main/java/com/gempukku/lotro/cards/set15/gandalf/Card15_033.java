package com.gempukku.lotro.cards.set15.gandalf;

import com.gempukku.lotro.common.*;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.game.state.Skirmish;
import com.gempukku.lotro.logic.actions.PlayEventAction;
import com.gempukku.lotro.logic.cardtype.AbstractEvent;
import com.gempukku.lotro.logic.modifiers.evaluator.Evaluator;
import com.gempukku.lotro.logic.timing.PlayConditions;
import com.gempukku.lotro.logic.timing.UnrespondableEffect;

/**
 * Set: The Hunters
 * Side: Free
 * Culture: Gandalf
 * Twilight Cost: 2
 * Type: Event • Skirmish
 * Game Text: Spell. Spot Gandalf and an unbound companion skirmishing a minion to use Gandalf’s resistance to resolve
 * that skirmish instead of that companion’s strength.
 */
public class Card15_033 extends AbstractEvent {
    public Card15_033() {
        super(Side.FREE_PEOPLE, 2, Culture.GANDALF, "One Last Surprise", Phase.SKIRMISH);
        addKeyword(Keyword.SPELL);
    }

    @Override
    public boolean checkPlayRequirements(LotroGame game, PhysicalCard self) {
        return PlayConditions.canSpot(game, Filters.gandalf)
                && PlayConditions.canSpot(game, Filters.unboundCompanion, Filters.inSkirmishAgainst(CardType.MINION));
    }

    @Override
    public PlayEventAction getPlayEventCardAction(String playerId, LotroGame game, PhysicalCard self) {
        PlayEventAction action = new PlayEventAction(self);
        final PhysicalCard gandalf = Filters.findFirstActive(game, Filters.gandalf);
        action.appendEffect(
                new UnrespondableEffect() {
                    @Override
                    protected void doPlayEffect(LotroGame game) {
                        final Skirmish skirmish = game.getGameState().getSkirmish();
                        if (skirmish != null) {
                            skirmish.setFpStrengthOverrideEvaluator(
                                    new Evaluator() {
                                        @Override
                                        public int evaluateExpression(LotroGame game, PhysicalCard cardAffected) {
                                            return game.getModifiersQuerying().getResistance(game, gandalf);
                                        }
                                    });
                        }
                    }
                });
        return action;
    }
}
