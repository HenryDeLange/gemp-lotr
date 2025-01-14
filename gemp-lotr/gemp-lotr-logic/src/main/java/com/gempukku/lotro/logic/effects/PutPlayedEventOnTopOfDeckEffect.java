package com.gempukku.lotro.logic.effects;

import com.gempukku.lotro.common.Zone;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.actions.PlayEventAction;
import com.gempukku.lotro.logic.timing.AbstractEffect;

import java.util.Collections;

public class PutPlayedEventOnTopOfDeckEffect extends AbstractEffect {
    private PlayEventAction _action;

    public PutPlayedEventOnTopOfDeckEffect(PlayEventAction action) {
        _action = action;
    }

    @Override
    public String getText(LotroGame game) {
        return "Put " + GameUtils.getFullName(_action.getEventPlayed()) + " on top of your deck";
    }

    @Override
    public Type getType() {
        return null;
    }

    @Override
    public boolean isPlayableInFull(LotroGame game) {
        Zone zone = _action.getEventPlayed().getZone();
        return zone == Zone.VOID || zone == Zone.VOID_FROM_HAND;
    }

    @Override
    protected FullEffectResult playEffectReturningResult(LotroGame game) {
        if (isPlayableInFull(game)) {
            PhysicalCard eventPlayed = _action.getEventPlayed();
            game.getGameState().sendMessage(_action.getPerformingPlayer() + " puts " + GameUtils.getCardLink(eventPlayed) + " on top of their deck");
            game.getGameState().removeCardsFromZone(eventPlayed.getOwner(), Collections.singletonList(eventPlayed));
            game.getGameState().putCardOnTopOfDeck(eventPlayed);
            return new FullEffectResult(true);
        }
        return new FullEffectResult(false);
    }
}
