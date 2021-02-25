package com.gempukku.lotro.db;

import com.gempukku.lotro.db.vo.LeagueMatchResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class DbLeagueMatchDAO implements LeagueMatchDAO {
    private DbAccess _dbAccess;

    public DbLeagueMatchDAO(DbAccess dbAccess) {
        _dbAccess = dbAccess;
    }

    @Override
    public Collection<LeagueMatchResult> getLeagueMatches(String leagueId) {
        try {
            try (Connection conn = _dbAccess.getDataSource().getConnection()) {
                try (PreparedStatement statement = conn.prepareStatement("select winner, loser, season_type from league_match where league_type=?")) {
                    statement.setString(1, leagueId);
                    try (ResultSet rs = statement.executeQuery()) {
                        Set<LeagueMatchResult> result = new HashSet<LeagueMatchResult>();
                        while (rs.next()) {
                            String winner = rs.getString(1);
                            String loser = rs.getString(2);
                            String serie = rs.getString(3);

                            result.add(new LeagueMatchResult(serie, winner, loser));
                        }
                        return result;
                    }
                }
            }
        } catch (SQLException exp) {
            throw new RuntimeException(exp);
        }
    }

    @Override
    public void addPlayedMatch(String leagueId, String serieId, String winner, String loser) {
        try {
            try (Connection conn = _dbAccess.getDataSource().getConnection()) {
                try (PreparedStatement statement = conn.prepareStatement("insert into league_match (league_type, season_type, winner, loser) values (?, ?, ?, ?)")) {
                    statement.setString(1, leagueId);
                    statement.setString(2, serieId);
                    statement.setString(3, winner);
                    statement.setString(4, loser);
                    statement.execute();
                }
            }
        } catch (SQLException exp) {
            throw new RuntimeException(exp);
        }
    }
}
