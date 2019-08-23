package com.gempukku.lotro.cards.set20.rohan;

import com.gempukku.lotro.common.*;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.PlayEventAction;
import com.gempukku.lotro.logic.cardtype.AbstractEvent;
import com.gempukku.lotro.logic.effects.choose.ChooseAndAddUntilEOPStrengthBonusEffect;
import com.gempukku.lotro.logic.modifiers.evaluator.CountSpottableEvaluator;

/**
 * 1
 * For Rohan!
 * Rohan	Event • Skirmish
 * Make a [Rohan] Man strength +1 for each villager you can spot.
 */
public class Card20_325 extends AbstractEvent {
    public Card20_325() {
        super(Side.FREE_PEOPLE, 1, Culture.ROHAN, "For Rohan!", Phase.SKIRMISH);
    }

    @Override
    public PlayEventAction getPlayEventCardAction(String playerId, LotroGame game, PhysicalCard self) {
        PlayEventAction action = new PlayEventAction(self);
        action.appendEffect(
                new ChooseAndAddUntilEOPStrengthBonusEffect(action, self, playerId,
                        new CountSpottableEvaluator(Keyword.VILLAGER), Culture.ROHAN, Race.MAN));
        return action;
    }
}
