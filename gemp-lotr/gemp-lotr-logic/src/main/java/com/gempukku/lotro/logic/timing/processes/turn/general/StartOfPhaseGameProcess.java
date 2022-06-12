package com.gempukku.lotro.logic.timing.processes.turn.general;

import org.apache.log4j.Logger;

import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.game.state.actions.DefaultActionsEnvironment;
import com.gempukku.lotro.logic.actions.SystemQueueAction;
import com.gempukku.lotro.logic.effects.TriggeringResultEffect;
import com.gempukku.lotro.logic.modifiers.ModifiersLogic;
import com.gempukku.lotro.logic.timing.AbstractSuccessfulEffect;
import com.gempukku.lotro.logic.timing.processes.GameProcess;
import com.gempukku.lotro.logic.timing.results.StartOfPhaseResult;

public class StartOfPhaseGameProcess implements GameProcess {
    private static final Logger LOG = Logger.getLogger(StartOfPhaseGameProcess.class);

    private Phase _phase;
    private String _playerId;
    private GameProcess _followingGameProcess;

    public StartOfPhaseGameProcess(Phase phase, GameProcess followingGameProcess) {
        _phase = phase;
        _followingGameProcess = followingGameProcess;
    }

    public StartOfPhaseGameProcess(Phase phase, String playerId, GameProcess followingGameProcess) {
        _phase = phase;
        _playerId = playerId;
        _followingGameProcess = followingGameProcess;
    }

    @Override
    public void process(LotroGame game) {
        LOG.trace("[" + _playerId + "] <<<<<<<<<< Starting " + _phase + " phase >>>>>>>>>>");
        game.getGameState().setCurrentPhase(_phase);
        SystemQueueAction action = new SystemQueueAction();
        action.setText("Start of " + _phase + " phase");
        action.appendEffect(
                new TriggeringResultEffect(null, new StartOfPhaseResult(_phase, _playerId), "Start of " + _phase + " phase"));
        action.appendEffect(
                new AbstractSuccessfulEffect() {
                    @Override
                    public String getText(LotroGame game) {
                        return null;
                    }

                    @Override
                    public Type getType() {
                        return null;
                    }

                    @Override
                    public void playEffect(LotroGame game) {
                        ((ModifiersLogic) game.getModifiersEnvironment()).signalStartOfPhase(_phase);
                        ((DefaultActionsEnvironment) game.getActionsEnvironment()).signalStartOfPhase(_phase);
                    }
                });

        game.getActionsEnvironment().addActionToStack(action);
    }

    @Override
    public GameProcess getNextProcess() {
        return _followingGameProcess;
    }
}
