package com.gempukku.lotro.logic.timing.results;

import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.logic.effects.KillEffect;
import com.gempukku.lotro.logic.timing.EffectResult;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ForEachKilledResult extends EffectResult {
    private final PhysicalCard _killedCard;

    private final Set<PhysicalCard> _killers;
    private final KillEffect.Cause _cause;

    public ForEachKilledResult(PhysicalCard killedCard, Collection<? extends PhysicalCard> killers, KillEffect.Cause cause) {
        super(EffectResult.Type.FOR_EACH_KILLED);
        _killedCard = killedCard;
        _killers = new HashSet<>(killers);
        _cause = cause;
    }

    public PhysicalCard getKilledCard() {
        return _killedCard;
    }

    public Set<PhysicalCard> getKillers() {
        return _killers;
    }

    public KillEffect.Cause getCause() {
        return _cause;
    }
}
