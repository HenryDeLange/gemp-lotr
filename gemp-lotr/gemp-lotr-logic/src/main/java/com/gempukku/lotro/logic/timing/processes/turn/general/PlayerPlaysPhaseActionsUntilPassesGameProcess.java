package com.gempukku.lotro.logic.timing.processes.turn.general;

import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.decisions.ActionsSelectionDecision;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.timing.Action;
import com.gempukku.lotro.logic.timing.processes.GameProcess;
import com.gempukku.lotro.logic.timing.processes.GatherPlayableActionsVisitor;

import java.util.LinkedList;
import java.util.List;

public class PlayerPlaysPhaseActionsUntilPassesGameProcess implements GameProcess {
    private LotroGame _game;
    private String _playerId;
    private GameProcess _followingGameProcess;

    private GameProcess _nextProcess;

    public PlayerPlaysPhaseActionsUntilPassesGameProcess(LotroGame game, String playerId, GameProcess followingGameProcess) {
        _game = game;
        _playerId = playerId;
        _followingGameProcess = followingGameProcess;
    }

    @Override
    public void process() {
        GatherPlayableActionsVisitor visitor = new GatherPlayableActionsVisitor(_game, _playerId);
        _game.getGameState().iterateActivableCards(_playerId, visitor);

        List<Action> playableActions = new LinkedList<Action>();
        for (Action action : visitor.getActions())
            if (_game.getModifiersQuerying().canPlayAction(_game.getGameState(), action))
                playableActions.add(action);

        _game.getUserFeedback().sendAwaitingDecision(_playerId,
                new ActionsSelectionDecision(1, "Choose action to play or press DONE", playableActions) {
                    @Override
                    public void decisionMade(String result) throws DecisionResultInvalidException {
                        Action action = getSelectedAction(result);
                        if (action != null) {
                            _nextProcess = new PlayerPlaysPhaseActionsUntilPassesGameProcess(_game, _playerId, _followingGameProcess);
                            _game.getActionsEnvironment().addActionToStack(action);
                        } else
                            _nextProcess = _followingGameProcess;
                    }
                });
    }

    @Override
    public GameProcess getNextProcess() {
        return _nextProcess;
    }
}
