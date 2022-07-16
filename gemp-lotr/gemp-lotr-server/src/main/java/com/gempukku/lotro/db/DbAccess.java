package com.gempukku.lotro.db;

import com.gempukku.lotro.common.ApplicationConfiguration;
import com.gempukku.lotro.common.Bot;

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
    private static final Logger LOG = Logger.getLogger(DbAccess.class);
    private DataSource _dataSource;

    public DbAccess() {
        try {
            Class.forName(ApplicationConfiguration.getProperty("db.connection.class"));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Couldn't find the DB driver", e);
        }

        // Create the datasource (database connection)
        _dataSource = setupDataSource(ApplicationConfiguration.getProperty("db.connection.url"));

        if (ApplicationConfiguration.getProperty("mode.dev").equals("true")) {
            // Now run the script to create the tables
            initDevDB();
            // Setup the bot player
            setupBotPlayer();
        }
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
                    connection.createStatement().execute("RUNSCRIPT FROM 'classpath:/database-init-tables-h2.sql'");
                    LOG.debug("Finished creating database tables");
                    // Insert DEV values
                    if (Boolean.parseBoolean(ApplicationConfiguration.getProperty("mode.dev"))) {
                        LOG.info("Start creating dev and test players");
                        // Add 'dev' and 'test' users
                        connection.createStatement().executeUpdate("INSERT INTO PLAYER (ID, NAME, PASSWORD, \"TYPE\", LAST_LOGIN_REWARD, LAST_IP, CREATE_IP, BANNED_UNTIL) " +
                                "VALUES(-1, 'dev', 'ef260e9aa3c673af240d17a2660480361a8e081d1ffeca2a5ed0e3219fc18567', 'u', 20220530, '127.0.0.1', '127.0.0.1', NULL);");
                        connection.createStatement().executeUpdate("INSERT INTO PLAYER (ID, NAME, PASSWORD, \"TYPE\", LAST_LOGIN_REWARD, LAST_IP, CREATE_IP, BANNED_UNTIL) " +
                                "VALUES(-2, 'test', '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08', 'u', 20220530, '127.0.0.1', '127.0.0.1', NULL);");
                        // Add some decks to the 'dev'
                        connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                                "VALUES(-1, 'fotr_block Elf-Moria', '1_290|1_2|1_320,1_334,1_341,2_118,1_349,1_350,1_355,1_357,1_362|3_7,1_48,1_48,1_48,1_50,1_53,1_53,3_13,3_13,1_45,1_45,1_163,2_49,2_49,2_60,2_60,2_60,2_60,1_176,1_176,1_176,1_176,1_178,1_178,1_184,1_184,2_67,1_191,1_191,2_52,1_31,1_31,1_33,1_33,1_41,1_41,1_47,1_47,1_190,3_23,3_23,3_27,3_27,1_32,1_32,1_37,1_167,1_171,1_187,1_187,1_187,1_187,1_201,3_9,2_18,2_18,2_18,1_175,1_192,1_196');");
                        connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                                "VALUES(-1, 'fotr_block ElfGondor-Isengard', '1_290|1_1|1_324,1_331,3_119,2_119,1_349,1_351,1_355,3_117,1_360|1_30,1_365,1_365,1_365,3_122,3_122,3_121,1_33,2_32,1_90,1_94,1_95,1_33,1_112,3_21,3_21,1_31,1_31,1_47,1_47,3_9,3_19,1_100,1_49,1_114,1_66,2_18,1_93,1_117,1_117,1_127,1_131,1_131,1_153,1_153,1_158,1_158,1_158,1_158,1_156,1_156,1_156,1_156,1_148,1_148,1_148,2_46,2_46,1_133,1_133,1_159,3_71,3_71,1_160,1_160,1_121,1_121,1_139,1_139,1_128');");
                        connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                                "VALUES(-1, 'ttt_block Ent-Dunland', '4_301|4_1|4_323,4_334,4_342,4_343,4_348,6_118,4_356,4_358,4_361|6_27,6_27,6_33,6_35,6_35,6_32,6_32,6_32,6_28,6_28,6_28,6_29,6_29,6_29,6_29,6_26,6_26,6_26,6_26,6_24,6_24,6_34,6_34,6_34,6_113,4_308,4_308,4_322,6_112,4_314,6_2,6_2,4_11,4_11,4_25,4_25,4_25,4_25,6_4,6_4,4_20,4_20,4_24,4_24,4_24,4_40,4_40,4_22,4_22,4_36,4_36,4_19,4_29,4_29,4_31,6_7,4_5,4_5,4_5,5_3,4_37,4_37,4_33,4_19,4_19,6_35,4_103,4_107,4_103,6_111');");
                        connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                                "VALUES(-1, 'ttt_block Rohan-Easterling', '4_301|1_2|4_326,4_336,4_339,4_344,4_349,4_354,4_357,4_363,6_119|4_265,4_265,4_265,4_265,4_267,4_263,4_268,4_274,4_287,4_287,4_287,4_287,4_288,4_288,4_288,4_288,5_89,5_89,5_91,5_91,4_282,4_289,4_297,5_94,5_94,6_97,6_78,4_225,4_228,4_228,4_228,4_228,4_226,4_226,4_226,4_226,4_230,4_230,4_224,4_224,4_224,4_224,4_229,4_229,4_229,4_229,6_79,6_79,6_79,4_239,4_239,4_259,4_259,4_259,4_240,5_88,5_88,5_88,5_88,6_79');");
                        connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                                "VALUES(-1, 'king_block Gondor-Nazgul', '7_317|1_2|7_334,7_337,8_117,8_118,7_344,7_347,7_353,7_358,7_361|8_41,8_38,8_38,8_38,8_41,8_43,8_43,8_43,8_45,8_45,8_45,8_45,8_47,8_47,8_47,8_48,8_48,8_48,8_46,8_46,8_46,8_46,1_102,1_102,1_102,8_33,8_33,10_30,10_30,10_30,8_80,8_80,10_67,10_67,10_67,7_219,7_219,8_84,8_81,8_81,10_70,10_70,8_79,7_196,7_187,7_199,7_199,7_199,7_199,7_203,7_203,10_53,10_53,10_53,10_53,8_71,8_71,10_64,8_68,8_67');");
                        connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                                "VALUES(-1, 'king_block Elf-GollumSauron', '7_317|7_2|7_332,7_336,7_339,7_343,7_344,7_350,7_355,7_357,7_360|7_16,7_16,10_7,10_7,10_8,10_8,10_9,10_9,10_11,8_10,8_10,7_58,7_58,8_25,8_25,7_264,7_282,7_284,7_288,7_288,7_288,7_289,7_289,7_289,7_290,7_290,7_291,7_291,7_292,7_292,7_17,7_17,7_18,7_18,8_30,8_30,7_265,8_9,1_37,1_37,1_37,1_37,7_29,7_29,7_29,7_29,7_30,7_30,7_266,7_22,7_22,7_28,7_28,8_22,7_269,7_269,7_283,7_310,7_310,10_103');");
                        connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                                "VALUES(-2, 'fotr_block Elf-Moria', '1_290|1_2|1_320,1_334,1_341,2_118,1_349,1_350,1_355,1_357,1_362|3_7,1_48,1_48,1_48,1_50,1_53,1_53,3_13,3_13,1_45,1_45,1_163,2_49,2_49,2_60,2_60,2_60,2_60,1_176,1_176,1_176,1_176,1_178,1_178,1_184,1_184,2_67,1_191,1_191,2_52,1_31,1_31,1_33,1_33,1_41,1_41,1_47,1_47,1_190,3_23,3_23,3_27,3_27,1_32,1_32,1_37,1_167,1_171,1_187,1_187,1_187,1_187,1_201,3_9,2_18,2_18,2_18,1_175,1_192,1_196');");
                        connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                                "VALUES(-2, 'fotr_block ElfGondor-Isengard', '1_290|1_1|1_324,1_331,3_119,2_119,1_349,1_351,1_355,3_117,1_360|1_30,1_365,1_365,1_365,3_122,3_122,3_121,1_33,2_32,1_90,1_94,1_95,1_33,1_112,3_21,3_21,1_31,1_31,1_47,1_47,3_9,3_19,1_100,1_49,1_114,1_66,2_18,1_93,1_117,1_117,1_127,1_131,1_131,1_153,1_153,1_158,1_158,1_158,1_158,1_156,1_156,1_156,1_156,1_148,1_148,1_148,2_46,2_46,1_133,1_133,1_159,3_71,3_71,1_160,1_160,1_121,1_121,1_139,1_139,1_128');");
                        connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                                "VALUES(-2, 'ttt_block Ent-Dunland', '4_301|4_1|4_323,4_334,4_342,4_343,4_348,6_118,4_356,4_358,4_361|6_27,6_27,6_33,6_35,6_35,6_32,6_32,6_32,6_28,6_28,6_28,6_29,6_29,6_29,6_29,6_26,6_26,6_26,6_26,6_24,6_24,6_34,6_34,6_34,6_113,4_308,4_308,4_322,6_112,4_314,6_2,6_2,4_11,4_11,4_25,4_25,4_25,4_25,6_4,6_4,4_20,4_20,4_24,4_24,4_24,4_40,4_40,4_22,4_22,4_36,4_36,4_19,4_29,4_29,4_31,6_7,4_5,4_5,4_5,5_3,4_37,4_37,4_33,4_19,4_19,6_35,4_103,4_107,4_103,6_111');");
                        connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                                "VALUES(-2, 'ttt_block Rohan-Easterling', '4_301|1_2|4_326,4_336,4_339,4_344,4_349,4_354,4_357,4_363,6_119|4_265,4_265,4_265,4_265,4_267,4_263,4_268,4_274,4_287,4_287,4_287,4_287,4_288,4_288,4_288,4_288,5_89,5_89,5_91,5_91,4_282,4_289,4_297,5_94,5_94,6_97,6_78,4_225,4_228,4_228,4_228,4_228,4_226,4_226,4_226,4_226,4_230,4_230,4_224,4_224,4_224,4_224,4_229,4_229,4_229,4_229,6_79,6_79,6_79,4_239,4_239,4_259,4_259,4_259,4_240,5_88,5_88,5_88,5_88,6_79');");
                        connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                                "VALUES(-2, 'king_block Gondor-Nazgul', '7_317|1_2|7_334,7_337,8_117,8_118,7_344,7_347,7_353,7_358,7_361|8_41,8_38,8_38,8_38,8_41,8_43,8_43,8_43,8_45,8_45,8_45,8_45,8_47,8_47,8_47,8_48,8_48,8_48,8_46,8_46,8_46,8_46,1_102,1_102,1_102,8_33,8_33,10_30,10_30,10_30,8_80,8_80,10_67,10_67,10_67,7_219,7_219,8_84,8_81,8_81,10_70,10_70,8_79,7_196,7_187,7_199,7_199,7_199,7_199,7_203,7_203,10_53,10_53,10_53,10_53,8_71,8_71,10_64,8_68,8_67');");
                        connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                                "VALUES(-2, 'king_block Elf-GollumSauron', '7_317|7_2|7_332,7_336,7_339,7_343,7_344,7_350,7_355,7_357,7_360|7_16,7_16,10_7,10_7,10_8,10_8,10_9,10_9,10_11,8_10,8_10,7_58,7_58,8_25,8_25,7_264,7_282,7_284,7_288,7_288,7_288,7_289,7_289,7_289,7_290,7_290,7_291,7_291,7_292,7_292,7_17,7_17,7_18,7_18,8_30,8_30,7_265,8_9,1_37,1_37,1_37,1_37,7_29,7_29,7_29,7_29,7_30,7_30,7_266,7_22,7_22,7_28,7_28,8_22,7_269,7_269,7_283,7_310,7_310,10_103');");
                        LOG.debug("Finished creating DEV data");
                    }
                }
            }
            catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private void setupBotPlayer() {
        // Detect if it is H2 or MySQL connection
        if (ApplicationConfiguration.getProperty("db.connection.url").startsWith("jdbc:h2")) {
            try (Connection connection = getDataSource().getConnection()) {
                if (!connection.createStatement().executeQuery("SELECT ID FROM PLAYER WHERE \"TYPE\"='" + Bot.BOT_DB_TYPE.getValue() + "'").next()) {
                    LOG.info("Creating bot player");
                    // Create the bot player (username=BOT, password=bot)
                    connection.createStatement().executeUpdate("INSERT INTO PLAYER (NAME, PASSWORD, \"TYPE\", LAST_LOGIN_REWARD, LAST_IP, CREATE_IP, BANNED_UNTIL) " +
                            "VALUES('" + Bot.BOT_NAME.getValue() + "', '9d74932bdb6f21dc7ab21d6fc5260f474e0d538571fba7a82b74ffe47e6f9a10', '" + Bot.BOT_DB_TYPE.getValue() + "', 20220530, '127.0.0.1', '127.0.0.1', NULL);");
                    // Add some initial decks
                    ResultSet resultSet = connection.createStatement().executeQuery("SELECT ID FROM PLAYER WHERE \"TYPE\"='" + Bot.BOT_DB_TYPE.getValue() + "'");
                    resultSet.next();
                    int botID = resultSet.getInt("ID");
                    // See lotrFormats.json for the deck name prefixes:
                    //     - fotr_block
                    //     - pc_fotr_block
                    //     - ttt_block
                    //     - towers_standard
                    //     - ts_reflections
                    //     - king_block
                    //     - rotk_sta
                    //     - multipath
                    //     - movie
                    //     - pc_movie
                    //     - standard
                    //     - open
                    //     - expanded
                    //     - pc_expanded
                    //     - pre-hunters_expanded
                    //     - french
                    //     - rev_tow_sta
                    //     - war_block
                    //     - war_standard
                    //     - hunter_block
                    //     - hobbit_tsr
                    //     - hobbit_tcb
                    //     - test_pc_fotr_block
                    //     - test_pc_movie
                    //     - test_pc_expanded
                    connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                            "VALUES(" + botID + ", 'fotr_block Elf-Moria', '1_290|1_2|1_320,1_334,1_341,2_118,1_349,1_350,1_355,1_357,1_362|3_7,1_48,1_48,1_48,1_50,1_53,1_53,3_13,3_13,1_45,1_45,1_163,2_49,2_49,2_60,2_60,2_60,2_60,1_176,1_176,1_176,1_176,1_178,1_178,1_184,1_184,2_67,1_191,1_191,2_52,1_31,1_31,1_33,1_33,1_41,1_41,1_47,1_47,1_190,3_23,3_23,3_27,3_27,1_32,1_32,1_37,1_167,1_171,1_187,1_187,1_187,1_187,1_201,3_9,2_18,2_18,2_18,1_175,1_192,1_196');");
                    connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                            "VALUES(" + botID + ", 'fotr_block ElfGondor-Isengard', '1_290|1_1|1_324,1_331,3_119,2_119,1_349,1_351,1_355,3_117,1_360|1_30,1_365,1_365,1_365,3_122,3_122,3_121,1_33,2_32,1_90,1_94,1_95,1_33,1_112,3_21,3_21,1_31,1_31,1_47,1_47,3_9,3_19,1_100,1_49,1_114,1_66,2_18,1_93,1_117,1_117,1_127,1_131,1_131,1_153,1_153,1_158,1_158,1_158,1_158,1_156,1_156,1_156,1_156,1_148,1_148,1_148,2_46,2_46,1_133,1_133,1_159,3_71,3_71,1_160,1_160,1_121,1_121,1_139,1_139,1_128');");
                    connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                            "VALUES(" + botID + ", 'ttt_block Ent-Dunland', '4_301|4_1|4_323,4_334,4_342,4_343,4_348,6_118,4_356,4_358,4_361|6_27,6_27,6_33,6_35,6_35,6_32,6_32,6_32,6_28,6_28,6_28,6_29,6_29,6_29,6_29,6_26,6_26,6_26,6_26,6_24,6_24,6_34,6_34,6_34,6_113,4_308,4_308,4_322,6_112,4_314,6_2,6_2,4_11,4_11,4_25,4_25,4_25,4_25,6_4,6_4,4_20,4_20,4_24,4_24,4_24,4_40,4_40,4_22,4_22,4_36,4_36,4_19,4_29,4_29,4_31,6_7,4_5,4_5,4_5,5_3,4_37,4_37,4_33,4_19,4_19,6_35,4_103,4_107,4_103,6_111');");
                    connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                            "VALUES(" + botID + ", 'ttt_block Rohan-Easterling', '4_301|1_2|4_326,4_336,4_339,4_344,4_349,4_354,4_357,4_363,6_119|4_265,4_265,4_265,4_265,4_267,4_263,4_268,4_274,4_287,4_287,4_287,4_287,4_288,4_288,4_288,4_288,5_89,5_89,5_91,5_91,4_282,4_289,4_297,5_94,5_94,6_97,6_78,4_225,4_228,4_228,4_228,4_228,4_226,4_226,4_226,4_226,4_230,4_230,4_224,4_224,4_224,4_224,4_229,4_229,4_229,4_229,6_79,6_79,6_79,4_239,4_239,4_259,4_259,4_259,4_240,5_88,5_88,5_88,5_88,6_79');");
                    connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                            "VALUES(" + botID + ", 'king_block Gondor-Nazgul', '7_317|1_2|7_334,7_337,8_117,8_118,7_344,7_347,7_353,7_358,7_361|8_41,8_38,8_38,8_38,8_41,8_43,8_43,8_43,8_45,8_45,8_45,8_45,8_47,8_47,8_47,8_48,8_48,8_48,8_46,8_46,8_46,8_46,1_102,1_102,1_102,8_33,8_33,10_30,10_30,10_30,8_80,8_80,10_67,10_67,10_67,7_219,7_219,8_84,8_81,8_81,10_70,10_70,8_79,7_196,7_187,7_199,7_199,7_199,7_199,7_203,7_203,10_53,10_53,10_53,10_53,8_71,8_71,10_64,8_68,8_67');");
                    connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                            "VALUES(" + botID + ", 'king_block Elf-GollumSauron', '7_317|7_2|7_332,7_336,7_339,7_343,7_344,7_350,7_355,7_357,7_360|7_16,7_16,10_7,10_7,10_8,10_8,10_9,10_9,10_11,8_10,8_10,7_58,7_58,8_25,8_25,7_264,7_282,7_284,7_288,7_288,7_288,7_289,7_289,7_289,7_290,7_290,7_291,7_291,7_292,7_292,7_17,7_17,7_18,7_18,8_30,8_30,7_265,8_9,1_37,1_37,1_37,1_37,7_29,7_29,7_29,7_29,7_30,7_30,7_266,7_22,7_22,7_28,7_28,8_22,7_269,7_269,7_283,7_310,7_310,10_103');");
                    connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                            "VALUES(" + botID + ", 'multipath 1', '1_290|1_2|1_319,1_330,1_337,3_116,1_349,1_351,1_355,3_117,1_361|3_7,3_7,3_7,1_48,1_48,1_48,1_50,1_50,1_50,3_13,3_13,1_45,1_45,1_163,2_60,2_60,1_176,1_176,1_176,1_176,1_184,1_184,1_191,1_191,1_191,1_191,2_52,1_31,1_31,1_33,1_33,1_47,1_47,3_23,3_23,3_27,3_27,1_187,1_187,1_187,1_187,2_18,2_18,1_196,1_196,3_9,1_32,1_32,1_37,1_163,2_49,2_49,2_60,2_60,2_67,1_190,1_167,1_167,1_201,1_53');");
                    connection.createStatement().executeUpdate("INSERT INTO DECK (PLAYER_ID, NAME, CONTENTS) " +
                            "VALUES(" + botID + ", 'multipath 2', '1_290|1_2|1_319,1_330,1_337,3_116,1_349,1_351,1_355,3_117,1_361|3_7,3_7,3_7,1_48,1_48,1_48,1_50,1_50,1_50,3_13,3_13,1_45,1_45,1_163,2_60,2_60,1_176,1_176,1_176,1_176,1_184,1_184,1_191,1_191,1_191,1_191,2_52,1_31,1_31,1_33,1_33,1_47,1_47,3_23,3_23,3_27,3_27,1_187,1_187,1_187,1_187,2_18,2_18,1_196,1_196,3_9,1_32,1_32,1_37,1_163,2_49,2_49,2_60,2_60,2_67,1_190,1_167,1_167,1_201,1_53');");
                }
            }
            catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

}
