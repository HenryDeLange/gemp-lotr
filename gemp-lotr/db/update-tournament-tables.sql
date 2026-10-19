

SELECT *
FROM tournament t 
ORDER BY id DESC


SELECT *
FROM tournament_player tp 
INNER JOIN tournament t 
	ON t.tournament_id = tp.tournament_id 
ORDER BY tp.ID DESC

DROP TABLE IF EXISTS `pending_tournament_queue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
 SET character_set_client = utf8mb4 ;
CREATE TABLE `pending_tournament_queue` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `scheduled_tournament_id` int(11) NOT NULL,
  `player_id` int(11) NOT NULL,
  `deck_name` varchar(45) COLLATE utf8_bin NOT NULL,
  `deck` text COLLATE utf8_bin NOT NULL,
  `dropped` BIT DEFAULT 0 NOT NULL,
  `checked_in` BIT DEFAULT 0 NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_st_id` (`scheduled_tournament_id`),
  CONSTRAINT `fk_st_id` FOREIGN KEY (`scheduled_tournament_id`) REFERENCES `scheduled_tournament` (`id`),
  KEY `fk_player_id` (`player_id`),
  CONSTRAINT `fk_player_id` FOREIGN KEY (`player_id`) REFERENCES `player` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;



ALTER TABLE gemp_db.scheduled_tournament  
ADD COLUMN start_date DATETIME NOT NULL DEFAULT now() AFTER `start`;

UPDATE scheduled_tournament 
SET start_date = from_unixtime(floor(`start`/1000));

ALTER TABLE gemp_db.scheduled_tournament 
DROP COLUMN `start`;

ALTER TABLE gemp_db.scheduled_tournament  
ADD COLUMN manual_kickoff BIT NOT NULL DEFAULT 0 AFTER `minimum_players`;

ALTER TABLE gemp_db.scheduled_tournament  
ADD COLUMN tiebreaker VARCHAR(45) NOT NULL DEFAULT 'owr' AFTER `playoff`;

ALTER TABLE gemp_db.scheduled_tournament 
DROP COLUMN `cost`;

ALTER TABLE gemp_db.scheduled_tournament  
ADD COLUMN cost INT NOT NULL DEFAULT 0 AFTER `start_date`;


ALTER TABLE gemp_db.scheduled_tournament  
ADD COLUMN started2 BIT NOT NULL DEFAULT 0 AFTER `started`;

UPDATE scheduled_tournament 
SET started2 = started;

ALTER TABLE gemp_db.scheduled_tournament 
DROP COLUMN `started`;

ALTER TABLE gemp_db.scheduled_tournament 
RENAME COLUMN started2 TO started;




ALTER TABLE gemp_db.tournament  
ADD COLUMN start_date DATETIME NOT NULL DEFAULT now() AFTER `start`;

UPDATE tournament 
SET start_date = from_unixtime(floor(`start`/1000));

ALTER TABLE gemp_db.tournament 
DROP COLUMN `start`;

ALTER TABLE gemp_db.tournament  
ADD COLUMN manual_kickoff BIT NOT NULL DEFAULT 0 AFTER `round`;




ALTER TABLE gemp_db.tournament_match  
ADD COLUMN round2 INT NOT NULL DEFAULT 0 AFTER `round`;

UPDATE tournament_match 
SET round2 = `round`;

ALTER TABLE gemp_db.tournament_match 
DROP COLUMN `round`;

ALTER TABLE gemp_db.tournament_match 
RENAME COLUMN round2 TO `round`;



