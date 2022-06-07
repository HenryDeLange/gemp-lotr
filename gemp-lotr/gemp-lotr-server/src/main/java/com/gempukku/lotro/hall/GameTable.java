package com.gempukku.lotro.hall;

import com.gempukku.lotro.game.LotroGameMediator;
import com.gempukku.lotro.game.LotroGameParticipant;

import java.util.*;

public class GameTable {
    private GameSettings gameSettings;
    private Map<String, LotroGameParticipant> players = new HashMap<String, LotroGameParticipant>();

    private LotroGameMediator lotroGameMediator;
    private int capacity;
    private String tableId;

    public GameTable(GameSettings gameSettings, String tableId) {
        this.gameSettings = gameSettings;
        this.tableId = tableId;
        this.capacity = gameSettings.getLotroFormat().getAdventure().isSolo() ? 1 : 2;
    }

    public void startGame(LotroGameMediator lotroGameMediator) {
        this.lotroGameMediator = lotroGameMediator;
    }

    public LotroGameMediator getLotroGameMediator() {
        return lotroGameMediator;
    }

    public boolean wasGameStarted() {
        return lotroGameMediator != null;
    }

    public boolean tableIsFull() {
        return players.size() == capacity;
    }

    public boolean addPlayer(LotroGameParticipant player) {
        players.put(player.getPlayerId(), player);
        return tableIsFull();
    }

    public boolean removePlayer(String playerId) {
        players.remove(playerId);
        return players.size() == 0;
    }

    public boolean hasPlayer(String playerId) {
        return players.containsKey(playerId);
    }

    public List<String> getPlayerNames() {
        return new LinkedList<String>(players.keySet());
    }

    public Set<LotroGameParticipant> getPlayers() {
        return Collections.unmodifiableSet(new HashSet<LotroGameParticipant>(players.values()));
    }

    public GameSettings getGameSettings() {
        return gameSettings;
    }

    public String getTableId() {
        return tableId;
    }
}
