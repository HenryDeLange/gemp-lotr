package com.gempukku.lotro.tournament;

import com.gempukku.lotro.competitive.PlayerStanding;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ChampionshipSwissPairingMechanism extends SwissPairingMechanism {

    private final Integer _targetRounds;


    public ChampionshipSwissPairingMechanism(String registryRepresentation, int targetRounds) {
        super(registryRepresentation, targetRounds);
        _targetRounds = targetRounds;
    }

    @Override
    public String getPlayOffSystem() {
        return "WC Swiss";
    }

    @Override
    public boolean isFinished(int round, Set<String> players, Set<String> droppedPlayers) {
        return round >= _targetRounds;
    }
}
