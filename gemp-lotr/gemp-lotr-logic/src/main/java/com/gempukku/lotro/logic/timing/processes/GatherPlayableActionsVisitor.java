package com.gempukku.lotro.logic.timing.processes;

import com.gempukku.lotro.game.CompletePhysicalCardVisitor;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.timing.Action;

import java.util.LinkedList;
import java.util.List;

public class GatherPlayableActionsVisitor extends CompletePhysicalCardVisitor {
    private LotroGame _game;
    private String _playerId;

    private List<Action> _actions = new LinkedList<Action>();

    public GatherPlayableActionsVisitor(LotroGame game, String playerId) {
        _game = game;
        _playerId = playerId;
    }

    @Override
    protected void doVisitPhysicalCard(PhysicalCard physicalCard) {
        List<? extends Action> list = physicalCard.getBlueprint().getPhaseActions(_playerId, _game, physicalCard);
        if (list != null) {
            for (Action action : list) {
                if (action != null)
                    _actions.add(action);
                else
                    System.out.println("Null action from: " + physicalCard.getBlueprint().getName());
            }
        }
    }

    public List<? extends Action> getActions() {
        return _actions;
    }
}
