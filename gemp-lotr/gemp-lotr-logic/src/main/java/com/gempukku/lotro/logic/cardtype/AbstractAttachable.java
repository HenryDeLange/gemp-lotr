package com.gempukku.lotro.logic.cardtype;

import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Culture;
import com.gempukku.lotro.common.PossessionClass;
import com.gempukku.lotro.common.Side;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.timing.Action;
import com.gempukku.lotro.logic.timing.Effect;
import com.gempukku.lotro.logic.timing.EffectResult;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class AbstractAttachable extends AbstractLotroCardBlueprint {
    private PossessionClass _possessionClass;

    public AbstractAttachable(Side side, CardType cardType, int twilight, Culture culture, PossessionClass possessionClass, String name) {
        this(side, cardType, twilight, culture, possessionClass, name, null, false);
    }

    public AbstractAttachable(Side side, CardType cardType, int twilight, Culture culture, PossessionClass possessionClass, String name, String subTitle, boolean unique) {
        super(twilight, side, cardType, culture, name, subTitle, unique);
        _possessionClass = possessionClass;
    }

    @Override
    public Set<PossessionClass> getPossessionClasses() {
        if (_possessionClass != null)
            return Collections.singleton(_possessionClass);
        return null;
    }
}