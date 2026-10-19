package com.gempukku.lotro.db;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

final class SqlScriptRunner {
    private SqlScriptRunner() {
    }

    static void execute(Connection connection, String resourceName) throws IOException, SQLException {
        String resourcePath = resourceName.startsWith("/") ? resourceName : "/" + resourceName;
        String script;
        try (InputStream input = SqlScriptRunner.class.getResourceAsStream(resourcePath)) {
            if (input == null)
                throw new FileNotFoundException("Database initialization resource not found: " + resourceName);
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Statement statement = connection.createStatement()) {
            for (String sql : splitStatements(script))
                statement.execute(sql);
        }
    }

    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < script.length(); i++) {
            char character = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : 0;

            if (lineComment) {
                if (character == '\n' || character == '\r') {
                    lineComment = false;
                    current.append(character);
                }
                continue;
            }
            if (blockComment) {
                if (character == '*' && next == '/') {
                    blockComment = false;
                    current.append(' ');
                    i++;
                }
                continue;
            }
            if (quote != 0) {
                current.append(character);
                if (character == '\\' && i + 1 < script.length()) {
                    current.append(script.charAt(++i));
                } else if (character == quote) {
                    if (next == quote) {
                        current.append(script.charAt(++i));
                    } else {
                        quote = 0;
                    }
                }
                continue;
            }

            if (character == '-' && next == '-') {
                lineComment = true;
                i++;
            } else if (character == '/' && next == '*') {
                blockComment = true;
                i++;
            } else if (character == '#') {
                lineComment = true;
            } else if (character == ';') {
                addStatement(statements, current);
            } else {
                current.append(character);
                if (character == '\'' || character == '"' || character == '`')
                    quote = character;
            }
        }

        if (quote != 0 || blockComment)
            throw new IllegalArgumentException("Unterminated quote or block comment in SQL initialization script");
        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String sql = current.toString().trim();
        if (!sql.isEmpty())
            statements.add(sql);
        current.setLength(0);
    }
}
