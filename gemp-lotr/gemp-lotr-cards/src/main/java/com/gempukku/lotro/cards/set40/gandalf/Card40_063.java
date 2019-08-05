package com.gempukku.lotro.cards.set40.gandalf;

import com.gempukku.lotro.cards.AbstractAlly;
import com.gempukku.lotro.cards.PlayConditions;
import com.gempukku.lotro.cards.effects.SelfExertEffect;
import com.gempukku.lotro.cards.effects.choose.ChooseAndDiscardCardsFromPlayEffect;
import com.gempukku.lotro.common.*;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.ActivateCardAction;
import com.gempukku.lotro.logic.timing.Action;

import java.util.Collections;
import java.util.List;

/**
 * Title: *Albert Dreary, Entertainer from Bree
 * Set: Second Edition
 * Side: Free
 * Culture: Gandalf
 * Twilight Cost: 1
 * Type: Ally - Man - Bree
 * Strength: 3
 * Vitality: 3
 * Card Number: 1R63
 * Game Text: To play, spot Gandalf.
 * Maneuver: Exert Albert Dreary to discard an [ISENGARD] or [MORIA] condition.
 */
public class Card40_063 extends AbstractAlly{
    public Card40_063() {
        super(1, Block.SECOND_ED, 0, 3, 3, Race.MAN, Culture.GANDALF, "Albert Dreary",
                "Entertainer from Bree", true);
        addKeyword(Keyword.BREE);
    }

    @Override
    public boolean checkPlayRequirements(String playerId, LotroGame game, PhysicalCard self, int withTwilightRemoved, int twilightModifier, boolean ignoreRoamingPenalty, boolean ignoreCheckingDeadPile) {
        return super.checkPlayRequirements(playerId, game, self, withTwilightRemoved, twilightModifier, ignoreRoamingPenalty, ignoreCheckingDeadPile)
                && PlayConditions.canSpot(game, Filters.gandalf);
    }

    @Override
    protected List<? extends Action> getExtraInPlayPhaseActions(String playerId, LotroGame game, PhysicalCard self) {
        if (PlayConditions.canUseFPCardDuringPhase(game, Phase.MANEUVER, self)
                && PlayConditions.canSelfExert(self, game)) {
            ActivateCardAction action = new ActivateCardAction(self);
            action.appendCost(
                    new SelfExertEffect(action, self));
            action.appendEffect(
                    new ChooseAndDiscardCardsFromPlayEffect(action, playerId, 1, 1,
                            CardType.CONDITION, Filters.or(Culture.ISENGARD, Culture.MORIA)));
            return Collections.singletonList(action);
        }
        return null;
    }
}