package com.gempukku.lotro.async.handler;

import com.gempukku.lotro.async.HttpProcessingException;
import com.gempukku.lotro.async.ResponseWriter;
import com.gempukku.lotro.common.AppConfig;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

public class WebRequestHandlerTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder(new File("target"));

    @Test
    public void servesFilesFromRelativeWebRoot() throws Exception {
        File relativeRoot = temporaryFolder.newFolder("web").getCanonicalFile();
        File index = new File(relativeRoot, "index.html");
        Files.writeString(index.toPath(), "ok");

        Path relativePath = Path.of("").toAbsolutePath().relativize(relativeRoot.toPath());
        ResponseWriter responseWriter = mock(ResponseWriter.class);

        new WebRequestHandler(relativePath + File.separator).handleRequest(
                "", new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"),
                Collections.emptyMap(), responseWriter, "127.0.0.1");

        verify(responseWriter).writeFile(eq(index), anyMap());
    }

    @Test
    public void servesHallDiscordPanelWithProductionDefault() throws Exception {
        assertEquals("true", AppConfig.getProperty("hall.discord.panel.enabled"));

        File hall = createHallPage("production");
        ResponseWriter responseWriter = mock(ResponseWriter.class);
        new WebRequestHandler(hall.getParentFile().getPath())
                .handleRequest("hall.html", new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"),
                        Collections.emptyMap(), responseWriter, "127.0.0.1");

        verify(responseWriter).writeFile(eq(hall), argThat(headers ->
                headers.get("etag").endsWith("-discord-true\"")));
        verify(responseWriter, never()).writeByteResponse(org.mockito.ArgumentMatchers.any(), anyMap());
    }

    @Test
    public void omitsHallDiscordPanelWhenDisabledInDevelopmentConfig() throws Exception {
        Properties developmentConfig = loadProperties("/gemp-lotr-h2.properties");
        assertEquals("false", developmentConfig.getProperty("hall.discord.panel.enabled"));

        File hall = createHallPage("development");
        ResponseWriter responseWriter = mock(ResponseWriter.class);
        new WebRequestHandler(hall.getParentFile().getPath(), developmentConfig.getProperty("hall.discord.panel.enabled"))
                .handleRequest("hall.html", new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"),
                        Collections.emptyMap(), responseWriter, "127.0.0.1");

        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(responseWriter).writeByteResponse(body.capture(), argThat(headers ->
                "text/html; charset=UTF-8".equals(headers.get("content-type"))
                        && headers.get("etag").endsWith("-discord-false\"")));
        String html = new String(body.getValue(), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(html.contains("discord-button"));
        assertTrue(html.contains("wiki-button"));
        verify(responseWriter, never()).writeFile(eq(hall), anyMap());
    }

    @Test
    public void allowsDevelopmentConfigurationToEnableHallDiscordPanel() throws Exception {
        File hall = createHallPage("enabled");
        ResponseWriter responseWriter = mock(ResponseWriter.class);
        new WebRequestHandler(hall.getParentFile().getPath(), "true")
                .handleRequest("hall.html", new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"),
                        Collections.emptyMap(), responseWriter, "127.0.0.1");

        verify(responseWriter).writeFile(eq(hall), argThat(headers ->
                headers.get("etag").endsWith("-discord-true\"")));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsInvalidHallDiscordPanelSetting() {
        new WebRequestHandler("target", "sometimes");
    }

    @Test
    public void rejectsPathTraversal() throws Exception {
        ResponseWriter responseWriter = mock(ResponseWriter.class);

        try {
            new WebRequestHandler("target").handleRequest(
                    "../outside.html", new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"),
                    Collections.emptyMap(), responseWriter, "127.0.0.1");
        } catch (HttpProcessingException exception) {
            assertEquals(403, exception.getStatus());
            return;
        }

        throw new AssertionError("Path traversal must be rejected");
    }

    private File createHallPage(String name) throws Exception {
        File webRoot = temporaryFolder.newFolder(name).getCanonicalFile();
        File hall = new File(webRoot, "hall.html").getCanonicalFile();
        Files.writeString(hall.toPath(), "<html><body>"
                + "<a id=\"wiki-button\">Wiki</a>"
                + "<!-- HALL_DISCORD_PANEL_START -->"
                + "<a id=\"discord-button\">Discord</a>"
                + "<!-- HALL_DISCORD_PANEL_END -->"
                + "</body></html>");
        return hall;
    }

    private Properties loadProperties(String resource) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull("Missing test configuration resource " + resource, input);
            properties.load(input);
        }
        return properties;
    }
}
