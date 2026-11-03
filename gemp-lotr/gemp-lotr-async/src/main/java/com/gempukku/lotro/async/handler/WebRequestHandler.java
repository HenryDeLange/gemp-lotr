package com.gempukku.lotro.async.handler;

import com.gempukku.lotro.async.HttpProcessingException;
import com.gempukku.lotro.async.ResponseWriter;
import com.gempukku.lotro.common.AppConfig;
import io.netty.handler.codec.http.HttpHeaderNames;

import io.netty.handler.codec.http.HttpRequest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class WebRequestHandler implements UriRequestHandler {
    private static final String HALL_DISCORD_ENABLED_PROPERTY = "hall.discord.panel.enabled";
    private static final String HALL_DISCORD_PANEL_START = "<!-- HALL_DISCORD_PANEL_START -->";
    private static final String HALL_DISCORD_PANEL_END = "<!-- HALL_DISCORD_PANEL_END -->";

    private final String _root;
    private final boolean _hallDiscordPanelEnabled;

    public WebRequestHandler() {
        this(AppConfig.getWebPath());
    }

    public WebRequestHandler(String root) {
        this(root, AppConfig.getProperty(HALL_DISCORD_ENABLED_PROPERTY));
    }

    WebRequestHandler(String root, String hallDiscordPanelSetting) {
        _root = root;
        if ("true".equalsIgnoreCase(hallDiscordPanelSetting)) {
            _hallDiscordPanelEnabled = true;
        } else if ("false".equalsIgnoreCase(hallDiscordPanelSetting)) {
            _hallDiscordPanelEnabled = false;
        } else {
            throw new IllegalStateException("Configuration property " + HALL_DISCORD_ENABLED_PROPERTY
                    + " must be set to true or false");
        }
    }

    @Override
    public void handleRequest(String uri, HttpRequest request, Map<Type, Object> context, ResponseWriter responseWriter, String remoteIp) throws Exception {
        if (uri.equals(""))
            uri = "index.html";

        uri = uri.replace('/', File.separatorChar);

        if ((uri.contains(".."))
                || uri.contains(File.separator + ".")
                || uri.startsWith(".") || uri.endsWith("."))
            throw new HttpProcessingException(403);

        File root = new File(_root).getCanonicalFile();
        File file = new File(root, uri).getCanonicalFile();
        Path rootPath = root.toPath();
        if (!file.toPath().startsWith(rootPath))
            throw new HttpProcessingException(403);

        if (!file.exists())
            throw new HttpProcessingException(404);

        boolean hallHtml = uri.equals("hall.html");
        final String etag = "\"" + file.lastModified()
                + (hallHtml ? "-discord-" + _hallDiscordPanelEnabled : "") + "\"";

        if (clientHasCurrentVersion(request, etag))
            throw new HttpProcessingException(304);

        if (hallHtml && !_hallDiscordPanelEnabled && file.isFile()) {
            String html = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            int panelStart = html.indexOf(HALL_DISCORD_PANEL_START);
            int panelEnd = html.indexOf(HALL_DISCORD_PANEL_END, panelStart + HALL_DISCORD_PANEL_START.length());
            if (panelStart < 0 || panelEnd < 0)
                throw new IllegalStateException("Hall Discord panel markers are missing or out of order");
            html = html.substring(0, panelStart) + html.substring(panelEnd + HALL_DISCORD_PANEL_END.length());
            responseWriter.writeByteResponse(html.getBytes(StandardCharsets.UTF_8), Map.of(
                    HttpHeaderNames.ETAG.toString(), etag,
                    HttpHeaderNames.CONTENT_TYPE.toString(), "text/html; charset=UTF-8"));
            return;
        }

        responseWriter.writeFile(file, Collections.singletonMap(HttpHeaderNames.ETAG.toString(), etag));
    }

    private boolean clientHasCurrentVersion(HttpRequest request, String etag) {
        String ifNoneMatch = request.headers().get(HttpHeaderNames.IF_NONE_MATCH);
        if (ifNoneMatch != null) {
            String[] clientKnownVersions = ifNoneMatch.split(",");
            for (String clientKnownVersion : clientKnownVersions) {
                if (clientKnownVersion.trim().equals(etag))
                    return true;
            }
        }
        return false;
    }
}
