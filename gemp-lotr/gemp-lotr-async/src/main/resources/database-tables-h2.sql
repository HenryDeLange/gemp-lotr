

-- TODO: Still need to add the Primary Keys, Indexes, etc.




-- PUBLIC.COLLECTION definition

-- Drop table

-- DROP TABLE PUBLIC.COLLECTION;

CREATE TABLE PUBLIC.COLLECTION (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	PLAYER_ID INT NOT NULL,
	COLLECTION BINARY VARYING(1048576),
	"TYPE" CHARACTER VARYING(45)
);


-- PUBLIC.DECK definition

-- Drop table

-- DROP TABLE PUBLIC.DECK;

CREATE TABLE PUBLIC.DECK (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	PLAYER_ID INT NOT NULL,
	NAME CHARACTER VARYING(45),
	"TYPE" CHARACTER VARYING(45),
	CONTENTS CHARACTER VARYING(65535)
);


-- PUBLIC.GAME_HISTORY definition

-- Drop table

-- DROP TABLE PUBLIC.GAME_HISTORY;

CREATE TABLE PUBLIC.GAME_HISTORY (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	WINNER CHARACTER VARYING(45),
	LOSER CHARACTER VARYING(45),
	WIN_REASON CHARACTER VARYING(255),
	LOSE_REASON CHARACTER VARYING(255),
	WIN_RECORDING_ID CHARACTER VARYING(45),
	LOSE_RECORDING_ID CHARACTER VARYING(45),
	START_DATE NUMERIC(20,0),
	END_DATE NUMERIC(20,0),
	FORMAT_NAME CHARACTER VARYING(45),
	WINNER_DECK_NAME CHARACTER VARYING(255),
	LOSER_DECK_NAME CHARACTER VARYING(255),
	TOURNAMENT CHARACTER VARYING(255)
);


-- PUBLIC.IGNORES definition

-- Drop table

-- DROP TABLE PUBLIC.IGNORES;

CREATE TABLE PUBLIC.IGNORES (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	PLAYERNAME CHARACTER VARYING(10),
	IGNOREDNAME CHARACTER VARYING(10)
);


-- PUBLIC.IP_BAN definition

-- Drop table

-- DROP TABLE PUBLIC.IP_BAN;

CREATE TABLE PUBLIC.IP_BAN (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	IP CHARACTER VARYING(255),
	PREFIX INT NOT NULL
);


-- PUBLIC.LEAGUE definition

-- Drop table

-- DROP TABLE PUBLIC.LEAGUE;

CREATE TABLE PUBLIC.LEAGUE (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	NAME CHARACTER VARYING(45),
	"TYPE" CHARACTER VARYING(45),
	CLASS CHARACTER VARYING(255),
	PARAMETERS CHARACTER VARYING(255),
	"START" INT NOT NULL,
	"END" INT NOT NULL,
	STATUS INT NOT NULL,
	COST INT NOT NULL
);


-- PUBLIC.LEAGUE_MATCH definition

-- Drop table

-- DROP TABLE PUBLIC.LEAGUE_MATCH;

CREATE TABLE PUBLIC.LEAGUE_MATCH (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	LEAGUE_TYPE CHARACTER VARYING(45),
	SEASON_TYPE CHARACTER VARYING(45),
	WINNER CHARACTER VARYING(45),
	LOSER CHARACTER VARYING(45)
);


-- PUBLIC.LEAGUE_PARTICIPATION definition

-- Drop table

-- DROP TABLE PUBLIC.LEAGUE_PARTICIPATION;

CREATE TABLE PUBLIC.LEAGUE_PARTICIPATION (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	LEAGUE_TYPE CHARACTER VARYING(45),
	PLAYER_NAME CHARACTER VARYING(45),
	JOIN_IP CHARACTER VARYING(45)
);


-- PUBLIC.PLAYER definition

-- Drop table

-- DROP TABLE PUBLIC.PLAYER;

CREATE TABLE PUBLIC.PLAYER (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	NAME CHARACTER VARYING(10),
	PASSWORD CHARACTER VARYING(64),
	"TYPE" CHARACTER VARYING(5),
	LAST_LOGIN_REWARD INT,
	LAST_IP CHARACTER VARYING(45),
	CREATE_IP CHARACTER VARYING(45),
	BANNED_UNTIL NUMERIC(20,0)
);


-- PUBLIC.SCHEDULED_TOURNAMENT definition

-- Drop table

-- DROP TABLE PUBLIC.SCHEDULED_TOURNAMENT;

CREATE TABLE PUBLIC.SCHEDULED_TOURNAMENT (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	TOURNAMENT_ID CHARACTER VARYING(45),
	NAME CHARACTER VARYING(255),
	FORMAT CHARACTER VARYING(45),
	"START" NUMERIC(20,0),
	COST NUMERIC(10,0),
	PLAYOFF CHARACTER VARYING(45),
	PRIZES CHARACTER VARYING(45),
	MINIMUM_PLAYERS NUMERIC(3,0),
	STARTED NUMERIC(1,0)
);


-- PUBLIC.TOURNAMENT definition

-- Drop table

-- DROP TABLE PUBLIC.TOURNAMENT;

CREATE TABLE PUBLIC.TOURNAMENT (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	TOURNAMENT_ID CHARACTER VARYING(255),
	"START" NUMERIC(20,0),
	DRAFT_TYPE CHARACTER VARYING(45),
	NAME CHARACTER VARYING(255),
	FORMAT CHARACTER VARYING(255),
	COLLECTION CHARACTER VARYING(255),
	STAGE CHARACTER VARYING(45),
	PAIRING CHARACTER VARYING(45),
	ROUND INT,
	PRIZES CHARACTER VARYING(45)
);


-- PUBLIC.TOURNAMENT_MATCH definition

-- Drop table

-- DROP TABLE PUBLIC.TOURNAMENT_MATCH;

CREATE TABLE PUBLIC.TOURNAMENT_MATCH (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	TOURNAMENT_ID CHARACTER VARYING(255),
	ROUND NUMERIC(2,0),
	PLAYER_ONE CHARACTER VARYING(45),
	PLAYER_TWO CHARACTER VARYING(45),
	WINNER CHARACTER VARYING(45)
);


-- PUBLIC.TOURNAMENT_PLAYER definition

-- Drop table

-- DROP TABLE PUBLIC.TOURNAMENT_PLAYER;

CREATE TABLE PUBLIC.TOURNAMENT_PLAYER (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	TOURNAMENT_ID CHARACTER VARYING(255),
	PLAYER CHARACTER VARYING(10),
	DECK_NAME CHARACTER VARYING(45),
	DECK CHARACTER VARYING(65535),
	DROPPED BINARY(1)
);


-- PUBLIC.TRANSFER definition

-- Drop table

-- DROP TABLE PUBLIC.TRANSFER;

CREATE TABLE PUBLIC.TRANSFER (
	ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
	NOTIFY INT NOT NULL,
	PLAYER CHARACTER VARYING(45),
	REASON CHARACTER VARYING(255),
	NAME CHARACTER VARYING(255),
	CURRENCY INT NOT NULL,
	COLLECTION CHARACTER VARYING(65535),
	TRANSFER_DATE NUMERIC(20,0),
	DIRECTION CHARACTER VARYING(45)
);