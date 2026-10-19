package com.gempukku.lotro.db;

import com.gempukku.lotro.collection.CollectionSerializer;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class H2DatabaseInitializationTest {
    @Test
    public void initializesCurrentSchemaForH2() throws Exception {
        String url = "jdbc:h2:mem:h2-schema-test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "NON_KEYWORDS=TYPE,START,END,COLLECTION,NAME,PASSWORD,STATUS,FORMAT,ROUND,UNTIL;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS GEMP_DB\\;SET SCHEMA GEMP_DB";
        DbAccess dbAccess = createH2DbAccess(url);

        try (Connection connection = dbAccess.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM gemp_db.prize_placeholder")) {
            assertTrue(result.next());
        }

        DbPlayerDAO playerDAO = new DbPlayerDAO(dbAccess, new DbCollectionDAO(dbAccess, new CollectionSerializer()));
        var devPlayer = playerDAO.getPlayer("dev");
        assertNotNull(devPlayer);
        assertNotNull(playerDAO.loginUser("dev", "dev"));
        try (Connection connection = dbAccess.getDataSource().getConnection();
             var statement = connection.prepareStatement(
                     "SELECT target_format, contents FROM deck WHERE player_id = ? AND name = ?")) {
            statement.setInt(1, devPlayer.getId());
            statement.setString(2, "fotr_block Elf-Moria");
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("fotr_block", result.getString("target_format"));
                assertTrue(result.getString("contents").startsWith("1_290|1_2|"));
            }
        }

        createH2DbAccess(url);
        try (Connection connection = dbAccess.getDataSource().getConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM deck WHERE player_id = ? AND name = ?")) {
            statement.setInt(1, devPlayer.getId());
            statement.setString(2, "fotr_block Elf-Moria");
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }

        assertTrue(new DbPrizePlaceholderDAO(dbAccess).getUnresolved().isEmpty());
        assertTrue(new DbTransferDAO(dbAccess).getPastAnnouncements().isEmpty());
        assertTrue(new DbLeagueScheduleDAO(dbAccess).getAllSchedules().isEmpty());
        assertTrue(playerDAO.registerBot("~h2-initialization-test"));

        DbTransferDAO transferDAO = new DbTransferDAO(dbAccess);
        ZonedDateTime now = ZonedDateTime.now();
        String marker = "<!-- gemp-patchnote:h2-test -->";
        assertTrue(transferDAO.addServerAnnouncementIfAbsent(marker, "H2 test", marker, now, now.plusDays(1)) > 0);
        assertEquals(-1, transferDAO.addServerAnnouncementIfAbsent(marker, "H2 test", marker, now, now.plusDays(1)));
    }

    private static DbAccess createH2DbAccess(String url) {
        return new DbAccess(new DbAccess.Configuration(
                "org.h2.Driver", url, "sa", "", "SELECT 1",
                Map.of(), List.of("database-init-tables.sql", "database-init-dev-h2.sql")));
    }
}
