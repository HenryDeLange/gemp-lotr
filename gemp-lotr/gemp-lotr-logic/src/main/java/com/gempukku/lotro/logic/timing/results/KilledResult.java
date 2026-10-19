package com.gempukku.lotro.logic.timing.results;

import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.logic.effects.KillEffect;
import com.gempukku.lotro.logic.timing.EffectResult;

import java.util.Set;

public class KilledResult extends EffectResult {
    private final Set<PhysicalCard> _killedCards;

    private final Set<PhysicalCard> _killers;
    private final KillEffect.Cause _cause;

    public KilledResult(Set<PhysicalCard> killedCards, Set<PhysicalCard> killers, KillEffect.Cause cause) {
        super(EffectResult.Type.ANY_NUMBER_KILLED);
        _killedCards = killedCards;
        _killers = killers;
        _cause = cause;
    }

    public Set<PhysicalCard> getKilledCards() {
        return _killedCards;
    }

    public Set<PhysicalCard> getKillers() {
        return _killers;
    }

    public KillEffect.Cause getCause() {
        return _cause;
    }
}
