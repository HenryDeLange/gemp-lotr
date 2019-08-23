package com.gempukku.lotro.cards.set20.moria;

import com.gempukku.lotro.common.Culture;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.common.Race;
import com.gempukku.lotro.common.Side;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.PlayEventAction;
import com.gempukku.lotro.logic.cardtype.AbstractEvent;
import com.gempukku.lotro.logic.effects.RevealCardsFromYourHandEffect;
import com.gempukku.lotro.logic.effects.choose.ChooseAndPlayCardFromHandEffect;
import com.gempukku.lotro.logic.effects.choose.ChooseCardsFromHandEffect;

import java.util.Collection;

/**
 * 0
 * Power and Terror
 * Moria	Event • Shadow
 * Reveal any number of [Moria] Goblins from your hand to play the Balrog.
 * It's twilight cost is -2 for each Goblin revealed.
 */
public class Card20_278 extends AbstractEvent {
    public Card20_278() {
        super(Side.SHADOW, 0, Culture.MORIA, "Power and Terror", Phase.SHADOW);
    }

    @Override
    public boolean checkPlayRequirements(LotroGame game, PhysicalCard self) {
        return Filters.filter(game.getGameState().getHand(self.getOwner()), game, Filters.balrog,
                Filters.playable(game, -2 * Filters.filter(game.getGameState().getHand(self.getOwner()), game, Culture.MORIA, Race.GOBLIN).size())).size() > 0;
    }

    @Override
    public PlayEventAction getPlayEventCardAction(final String playerId, final LotroGame game, final PhysicalCard self) {
        final PlayEventAction action = new PlayEventAction(self);
        action.appendCost(
                new ChooseCardsFromHandEffect(playerId, 0, Integer.MAX_VALUE, Culture.MORIA, Race.GOBLIN) {
                    @Override
                    protected void cardsSelected(LotroGame game, Collection<PhysicalCard> selectedCards) {
                        action.appendEffect(
                                new RevealCardsFromYourHandEffect(self, playerId, selectedCards));
                        int cardsRevealed = selectedCards.size();
                        action.appendEffect(
                                new ChooseAndPlayCardFromHandEffect(playerId, game, -2 * cardsRevealed, Filters.balrog));
                    }
                });
        return action;
    }
}
