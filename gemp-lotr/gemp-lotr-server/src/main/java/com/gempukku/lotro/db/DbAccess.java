package com.gempukku.lotro.db;

import com.gempukku.lotro.common.ApplicationConfiguration;
import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.DriverManagerConnectionFactory;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

public class DbAccess {
    private static Logger LOG = Logger.getLogger(DbAccess.class);
    private DataSource _dataSource;

    public DbAccess() {
        try {
            Class.forName(ApplicationConfiguration.getProperty("db.connection.class"));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Couldn't find the DB driver", e);
        }

        // Create the datasource (database connection)
        _dataSource = setupDataSource(ApplicationConfiguration.getProperty("db.connection.url"));

        // Now run the script to create the tables
        initDevDB();
    }

    public DataSource getDataSource() {
        return _dataSource;
    }

    private DataSource setupDataSource(String connectURI) {
        //
        // First, we'll create a ConnectionFactory that the
        // pool will use to create Connections.
        // We'll use the DriverManagerConnectionFactory,
        // using the connect string passed in the command line
        // arguments.
        //
        ConnectionFactory connectionFactory =
                new DriverManagerConnectionFactory(connectURI, ApplicationConfiguration.getProperty("db.connection.username"),
                        ApplicationConfiguration.getProperty("db.connection.password"));

        //
        // Now we'll need a ObjectPool that serves as the
        // actual pool of connections.
        //
        // We'll use a GenericObjectPool instance, although
        // any ObjectPool implementation will suffice.
        //
        GenericObjectPool connectionPool = new GenericObjectPool();
        connectionPool.setTestOnBorrow(true);

        //
        // Next we'll create the PoolableConnectionFactory, which wraps
        // the "real" Connections created by the ConnectionFactory with
        // the classes that implement the pooling functionality.
        //
        PoolableConnectionFactory poolableConnectionFactory =
                new PoolableConnectionFactory(connectionFactory, connectionPool, null, ApplicationConfiguration.getProperty("db.connection.validateQuery"), false, true);

        connectionPool.setFactory(poolableConnectionFactory);

        //
        // Finally, we create the PoolingDriver itself,
        // passing in the object pool we created.
        //

        return new PoolingDataSource(connectionPool);
    }

    private void initDevDB() {
        // Detect if it is H2 or MySQL connection
        if (ApplicationConfiguration.getProperty("db.connection.url").startsWith("jdbc:h2")) {
            boolean tableExists = false;
            try (Connection connection = getDataSource().getConnection()) {
                ResultSet result = connection.getMetaData().getTables(null, null, "collection", null);
                if (result.next()) {
                    tableExists = true;
                }
                if (!tableExists) {
                    result = connection.getMetaData().getTables(null, null, "COLLECTION", null);
                    if (result.next()) {
                        tableExists = true;
                    }
                }
                // If the table doesn't exists then run the script to create all tables
                if (!tableExists) {
                    // Create the tables
                    LOG.info("Start creating database tables");
                    connection.createStatement().execute("RUNSCRIPT FROM 'classpath:/database-tables-h2.sql'");
                    LOG.debug("Finished creating database tables");
                    // Insert DEV values
                    if (Boolean.parseBoolean(ApplicationConfiguration.getProperty("mode.dev"))) {
                        LOG.info("Start creating DEV data");
                        connection.createStatement().executeUpdate("INSERT INTO PLAYER (ID, NAME, PASSWORD, \"TYPE\", LAST_LOGIN_REWARD, LAST_IP, CREATE_IP, BANNED_UNTIL) VALUES(1, 'bot', '9d74932bdb6f21dc7ab21d6fc5260f474e0d538571fba7a82b74ffe47e6f9a10', 'u', 20220530, '127.0.0.1', '127.0.0.1', NULL);");
                        connection.createStatement().executeUpdate("INSERT INTO DECK (ID, PLAYER_ID, NAME, \"TYPE\", CONTENTS) VALUES(1, 1, 'Fellowship', 'Default', '2_102|1_2|1_319,1_330,1_337,3_116,1_349,1_351,1_355,3_117,1_361|3_7,3_7,3_7,3_7,1_31,1_31,1_33,1_33,3_13,3_13,1_45,1_45,1_48,1_48,1_48,1_48,1_47,1_47,1_47,1_47,3_23,3_23,3_27,3_27,1_50,1_50,1_50,1_50,2_18,2_18,1_163,1_163,1_170,1_170,1_163,1_163,2_62,2_62,1_174,1_174,1_176,1_176,1_176,1_176,1_191,1_191,1_191,1_191,1_187,1_187,1_187,1_187,2_52,1_196,1_196,1_196,1_184,1_184,2_60,2_60');");
                        LOG.debug("Finished creating DEV data");
                    }
                }
            }
            catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

}
