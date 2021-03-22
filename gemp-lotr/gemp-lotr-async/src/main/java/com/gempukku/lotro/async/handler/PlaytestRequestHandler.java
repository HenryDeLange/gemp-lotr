package com.gempukku.lotro.async.handler;

import com.gempukku.lotro.DateUtils;
import com.gempukku.lotro.async.HttpProcessingException;
import com.gempukku.lotro.async.ResponseWriter;
import com.gempukku.lotro.cache.CacheManager;
import com.gempukku.lotro.collection.CollectionsManager;
import com.gempukku.lotro.common.ApplicationConfiguration;
import com.gempukku.lotro.db.LeagueDAO;
import com.gempukku.lotro.db.PlayerDAO;
import com.gempukku.lotro.db.vo.CollectionType;
import com.gempukku.lotro.draft2.SoloDraftDefinitions;
import com.gempukku.lotro.game.CardCollection;
import com.gempukku.lotro.game.CardSets;
import com.gempukku.lotro.game.LotroCardBlueprintLibrary;
import com.gempukku.lotro.game.Player;
import com.gempukku.lotro.game.formats.LotroFormatLibrary;
import com.gempukku.lotro.hall.HallServer;
import com.gempukku.lotro.league.*;
import com.gempukku.lotro.service.AdminService;
import com.gempukku.lotro.tournament.TournamentService;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;

public class PlaytestRequestHandler extends LotroServerRequestHandler implements UriRequestHandler {

    private PlayerDAO _playerDAO;

    public PlaytestRequestHandler(Map<Type, Object> context) {
        super(context);
        _playerDAO = extractObject(context, PlayerDAO.class);

    }

    @Override
    public void handleRequest(String uri, HttpRequest request, Map<Type, Object> context, ResponseWriter responseWriter, String remoteIp) throws Exception {
        if (uri.equals("/addTesterFlag") && request.getMethod() == HttpMethod.POST) {
            addTesterFlag(request, responseWriter);
        } else if (uri.equals("/removeTesterFlag") && request.getMethod() == HttpMethod.POST) {
            removeTesterFlag(request, responseWriter);
        } else if (uri.equals("/getTesterFlag") && request.getMethod() == HttpMethod.GET) {
            getTesterFlag(request, responseWriter);
        } else {
            throw new HttpProcessingException(404);
        }
    }

    private void addTesterFlag(HttpRequest request, ResponseWriter responseWriter) throws Exception {
        HttpPostRequestDecoder postDecoder = new HttpPostRequestDecoder(request);
        try {
            Player player = getResourceOwnerSafely(request, null);

            _playerDAO.addPlayerFlag(player.getName(), "t");

            responseWriter.writeHtmlResponse("OK");

        } finally {
            postDecoder.destroy();
        }
    }

    private void removeTesterFlag(HttpRequest request, ResponseWriter responseWriter) throws Exception {
        HttpPostRequestDecoder postDecoder = new HttpPostRequestDecoder(request);
        try {
            Player player = getResourceOwnerSafely(request, null);

            _playerDAO.removePlayerFlag(player.getName(), "t");

            responseWriter.writeHtmlResponse("OK");

        } finally {
            postDecoder.destroy();
        }
    }

    private void getTesterFlag(HttpRequest request, ResponseWriter responseWriter) throws Exception {
        HttpPostRequestDecoder postDecoder = new HttpPostRequestDecoder(request);
        try {
            Player player = getResourceOwnerSafely(request, null);

            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document doc = documentBuilder.newDocument();
            Element hasTester = doc.createElement("hasTester");

            hasTester.setAttribute("result", String.valueOf(player.getType().contains("t")));

            responseWriter.writeXmlResponse(doc);

        } finally {
            postDecoder.destroy();
        }
    }

}
