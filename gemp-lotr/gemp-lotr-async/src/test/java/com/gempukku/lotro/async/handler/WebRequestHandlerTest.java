package com.gempukku.lotro.async.handler;

import com.gempukku.lotro.async.HttpProcessingException;
import com.gempukku.lotro.async.ResponseWriter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}
