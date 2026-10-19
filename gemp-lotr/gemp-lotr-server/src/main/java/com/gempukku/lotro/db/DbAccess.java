package com.gempukku.lotro.db;

import com.gempukku.lotro.common.AppConfig;
import com.gempukku.lotro.db.converters.LocalDateConverter;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.sql2o.Sql2o;
import org.sql2o.converters.Converter;
import org.sql2o.quirks.NoQuirks;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class DbAccess {
    private final DataSource _dataSource;

    public DbAccess() {
        this(Configuration.fromAppConfig());
    }

    DbAccess(Configuration configuration) {
        try {
            Class.forName(configuration.driverClassName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Couldn't find configured DB driver " + configuration.driverClassName(), e);
        }

        _dataSource = setupDataSource(configuration);
        initializeDatabase(configuration.initializationScripts());
    }

    public static final Map<Class, Converter> CustomMappers = new HashMap<>() {{
       put(LocalDate.class, new LocalDateConverter());
    }};

    public Sql2o openDB() {
        return new Sql2o(_dataSource, new NoQuirks(CustomMappers));
    }

    public DataSource getDataSource() {
        return _dataSource;
    }

    private DataSource setupDataSource(Configuration configuration) {
        Properties properties = new Properties();
        properties.putAll(configuration.connectionProperties());
        properties.setProperty("user", configuration.username());
        properties.setProperty("password", configuration.password());

        ConnectionFactory connectionFactory =
                new DriverManagerConnectionFactory(configuration.connectionUrl(), properties);

        var poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);
        poolableConnectionFactory.setDefaultAutoCommit(true);
        poolableConnectionFactory.setDefaultReadOnly(false);
        if (configuration.validationQuery() != null && !configuration.validationQuery().isBlank())
            poolableConnectionFactory.setValidationQuery(configuration.validationQuery());

        var connectionPool = new GenericObjectPool<>(poolableConnectionFactory);
        connectionPool.setTestOnBorrow(true);
        poolableConnectionFactory.setPool(connectionPool);

        return new PoolingDataSource(connectionPool);
    }

    private void initializeDatabase(List<String> scripts) {
        if (scripts.isEmpty())
            return;

        try (Connection connection = _dataSource.getConnection()) {
            for (String script : scripts) {
                try {
                    SqlScriptRunner.execute(connection, script);
                } catch (IOException | SQLException e) {
                    throw new RuntimeException("Unable to execute database initialization script " + script, e);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to connect to the configured database for initialization", e);
        }
    }

    static record Configuration(
            String driverClassName,
            String connectionUrl,
            String username,
            String password,
            String validationQuery,
            Map<String, String> connectionProperties,
            List<String> initializationScripts) {
        Configuration {
            if (driverClassName == null || driverClassName.isBlank())
                throw new IllegalArgumentException("db.connection.class is required");
            if (connectionUrl == null || connectionUrl.isBlank())
                throw new IllegalArgumentException("db.connection.url is required");

            username = username == null ? "" : username;
            password = password == null ? "" : password;
            connectionProperties = connectionProperties == null ? Map.of() : Map.copyOf(connectionProperties);
            initializationScripts = initializationScripts == null ? List.of() : List.copyOf(initializationScripts);
        }

        static Configuration fromAppConfig() {
            return new Configuration(
                    AppConfig.getProperty("db.connection.class"),
                    AppConfig.getProperty("db.connection.url"),
                    AppConfig.getProperty("db.connection.username"),
                    AppConfig.getProperty("db.connection.password"),
                    AppConfig.getProperty("db.connection.validateQuery"),
                    parseConnectionProperties(AppConfig.getProperty("db.connection.properties")),
                    parseList(AppConfig.getProperty("db.initialization.scripts")));
        }

        private static List<String> parseList(String value) {
            if (value == null || value.isBlank())
                return List.of();
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(script -> !script.isEmpty())
                    .toList();
        }

        private static Map<String, String> parseConnectionProperties(String value) {
            if (value == null || value.isBlank())
                return Map.of();

            Map<String, String> properties = new LinkedHashMap<>();
            for (String entry : value.split(",")) {
                int separator = entry.indexOf('=');
                if (separator <= 0)
                    throw new IllegalArgumentException("Invalid db.connection.properties entry: " + entry);

                String name = entry.substring(0, separator).trim();
                String propertyValue = entry.substring(separator + 1).trim();
                if (name.isEmpty())
                    throw new IllegalArgumentException("Invalid db.connection.properties entry: " + entry);
                properties.put(name, propertyValue);
            }
            return properties;
        }
    }
}
