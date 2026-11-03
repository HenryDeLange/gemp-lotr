package com.gempukku.lotro.async.handler;

import com.gempukku.lotro.collection.CollectionsManager;
import com.gempukku.lotro.collection.TransferDAO;
import com.gempukku.lotro.common.AppConfig;
import com.gempukku.lotro.db.PlayerDAO;
import com.gempukku.lotro.game.Player;
import com.gempukku.lotro.service.LoggedUserHolder;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PollingConfigurationTest {
    private static final String SCHEDULED_TOURNAMENT_POLLING = "scheduled.tournaments.polling.enabled";
    private static final String LEAGUE_NOTIFICATION_POLLING = "league.notifications.polling.enabled";

    @Test
    public void productionEnablesScheduledTournamentAndLeagueNotificationPolling() {
        assertTrue(AppConfig.getBooleanProperty(SCHEDULED_TOURNAMENT_POLLING));
        assertTrue(AppConfig.getBooleanProperty(LEAGUE_NOTIFICATION_POLLING));
    }

    @Test
    public void developmentDisablesScheduledTournamentAndLeagueNotificationPollingByDefault() throws Exception {
        Properties development = loadDevelopmentConfiguration();

        assertEquals("false", development.getProperty(SCHEDULED_TOURNAMENT_POLLING));
        assertEquals("false", development.getProperty(LEAGUE_NOTIFICATION_POLLING));
    }

    @Test
    public void enabledLeagueNotificationPollingAddsDeliveryHeader() {
        TransferDAO transferDAO = mock(TransferDAO.class);
        TestRequestHandler handler = new TestRequestHandler(context(transferDAO), true);
        Player player = mock(Player.class);
        when(transferDAO.hasUndeliveredLeagueNotifications(player)).thenReturn(true);

        Map<String, String> headers = handler.notificationHeaders(player);

        assertEquals("true", headers.get("Delivery-Service-League"));
        verify(transferDAO).hasUndeliveredLeagueNotifications(player);
    }

    @Test
    public void disabledLeagueNotificationPollingSkipsTheNotificationQuery() {
        TransferDAO transferDAO = mock(TransferDAO.class);
        TestRequestHandler handler = new TestRequestHandler(context(transferDAO), false);
        Player player = mock(Player.class);

        Map<String, String> headers = handler.notificationHeaders(player);

        assertFalse(headers.containsKey("Delivery-Service-League"));
        verify(transferDAO, never()).hasUndeliveredLeagueNotifications(player);
    }

    private static Map<Type, Object> context(TransferDAO transferDAO) {
        Map<Type, Object> context = new HashMap<>();
        context.put(PlayerDAO.class, mock(PlayerDAO.class));
        context.put(LoggedUserHolder.class, mock(LoggedUserHolder.class));
        context.put(TransferDAO.class, transferDAO);
        context.put(CollectionsManager.class, mock(CollectionsManager.class));
        return context;
    }

    private static Properties loadDevelopmentConfiguration() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = PollingConfigurationTest.class.getResourceAsStream("/gemp-lotr-h2.properties")) {
            assertNotNull("Missing development configuration", input);
            properties.load(input);
        }
        return properties;
    }

    private static class TestRequestHandler extends LotroServerRequestHandler {
        private TestRequestHandler(Map<Type, Object> context, boolean enabled) {
            super(context, enabled);
        }

        private Map<String, String> notificationHeaders(Player player) {
            Map<String, String> headers = new HashMap<>();
            processDeliveryServiceNotification(player, headers);
            return headers;
        }
    }
}
