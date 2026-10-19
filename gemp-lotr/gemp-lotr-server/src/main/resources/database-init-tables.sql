-- Idempotent baseline schema shared by MariaDB and H2 in MySQL mode.

CREATE TABLE IF NOT EXISTS `application_lock` (
    `lock_name` VARCHAR(100) NOT NULL,
    PRIMARY KEY (`lock_name`)
);

INSERT IGNORE INTO `application_lock` (`lock_name`) VALUES ('patch-note-announcements');

CREATE TABLE IF NOT EXISTS `collection` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `player_id` INT NOT NULL,
    `collection` MEDIUMBLOB,
    `type` VARCHAR(45) NOT NULL,
    `extra_info` VARCHAR(5000),
    PRIMARY KEY (`id`),
    UNIQUE KEY `collection_player_type` (`player_id`, `type`),
    KEY `player_collection` (`player_id`, `type`)
);

CREATE TABLE IF NOT EXISTS `collection_entries` (
    `collection_id` INT NOT NULL,
    `quantity` INT DEFAULT 0,
    `product_type` VARCHAR(50) NOT NULL,
    `product_variant` VARCHAR(50),
    `product` VARCHAR(50) NOT NULL,
    `source` VARCHAR(100) NOT NULL,
    `created_date` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `modified_date` TIMESTAMP,
    `notes` VARCHAR(100),
    PRIMARY KEY (`collection_id`, `product`)
);

CREATE TABLE IF NOT EXISTS `deck` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `player_id` INT NOT NULL,
    `name` VARCHAR(100) NOT NULL DEFAULT 'Default',
    `type` VARCHAR(45) NOT NULL DEFAULT 'Default',
    `target_format` VARCHAR(50) NOT NULL DEFAULT 'Anything Goes',
    `contents` TEXT NOT NULL,
    `notes` VARCHAR(5000) NOT NULL DEFAULT '',
    PRIMARY KEY (`id`),
    UNIQUE KEY `player_deck` (`player_id`, `name`),
    KEY `deck_player_id` (`player_id`)
);

CREATE TABLE IF NOT EXISTS `game_history` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `winner` VARCHAR(45) NOT NULL,
    `winnerid` INT NOT NULL,
    `loser` VARCHAR(45) NOT NULL,
    `loserid` INT NOT NULL,
    `win_reason` VARCHAR(255) NOT NULL,
    `lose_reason` VARCHAR(255) NOT NULL,
    `win_recording_id` VARCHAR(45) NOT NULL,
    `lose_recording_id` VARCHAR(45) NOT NULL,
    `format_name` VARCHAR(45) NOT NULL,
    `tournament` VARCHAR(255) NOT NULL,
    `winner_deck_name` VARCHAR(255) NOT NULL,
    `loser_deck_name` VARCHAR(255) NOT NULL,
    `start_date` TIMESTAMP NOT NULL,
    `end_date` TIMESTAMP NOT NULL,
    `replay_version` INT NOT NULL,
    PRIMARY KEY (`id`),
    KEY `game_history_end_date` (`end_date`)
);

CREATE TABLE IF NOT EXISTS `ignores` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `playername` VARCHAR(30) NOT NULL,
    `ignoredname` VARCHAR(30) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `player_ignores` (`playername`)
);

CREATE TABLE IF NOT EXISTS `ip_ban` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `ip` VARCHAR(255) NOT NULL,
    `prefix` INT NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `league` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(45) NOT NULL,
    `code` BIGINT NOT NULL,
    `type` VARCHAR(45) NOT NULL,
    `parameters` TEXT NOT NULL,
    `start_date` TIMESTAMP NOT NULL,
    `end_date` TIMESTAMP NOT NULL,
    `status` INT NOT NULL,
    `cost` INT NOT NULL DEFAULT 0,
    `schedule_id` INT,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `league_match` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `league_type` VARCHAR(45) NOT NULL,
    `season_type` VARCHAR(45) NOT NULL,
    `winner` VARCHAR(45) NOT NULL,
    `loser` VARCHAR(45) NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `league_participation` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `league_type` VARCHAR(45) NOT NULL,
    `player_name` VARCHAR(45) NOT NULL,
    `join_ip` VARCHAR(45),
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `league_schedule` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(45) NOT NULL,
    `league_type` VARCHAR(45) NOT NULL,
    `template` TEXT NOT NULL,
    `events` TEXT NOT NULL,
    `name_pattern` VARCHAR(255) NOT NULL DEFAULT '{series} - {event}',
    `next_event_date` DATE NOT NULL,
    `next_event_index` INT NOT NULL DEFAULT 0,
    `interval_months` DECIMAL(6,3) NOT NULL DEFAULT 1.000,
    `lead_days` INT NOT NULL DEFAULT 7,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `last_created_league_id` INT,
    `last_run` TIMESTAMP,
    `last_error` VARCHAR(1000),
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `player` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(30) NOT NULL,
    `password` VARCHAR(64) NOT NULL,
    `type` VARCHAR(5) NOT NULL DEFAULT 'u',
    `last_login_reward` INT,
    `last_ip` VARCHAR(45),
    `create_ip` VARCHAR(45),
    `banned_until` NUMERIC(20,0),
    PRIMARY KEY (`id`),
    UNIQUE KEY `player_name_unique` (`name`)
);

CREATE TABLE IF NOT EXISTS `scheduled_tournament` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `tournament_id` VARCHAR(45) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `format` VARCHAR(45) NOT NULL,
    `start_date` TIMESTAMP NOT NULL,
    `type` VARCHAR(45),
    `parameters` TEXT,
    `started` BOOLEAN NOT NULL DEFAULT FALSE,
    `cost` INT NOT NULL DEFAULT 0,
    `playoff` VARCHAR(45),
    `tiebreaker` VARCHAR(45),
    `prizes` VARCHAR(45),
    `minimum_players` INT,
    `manual_kickoff` BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `tournament` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `tournament_id` VARCHAR(255) NOT NULL,
    `name` VARCHAR(255),
    `start_date` TIMESTAMP NOT NULL,
    `type` VARCHAR(45),
    `parameters` TEXT,
    `stage` VARCHAR(45),
    `round` INT,
    `manual_kickoff` BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `tournament_match` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `tournament_id` VARCHAR(255) NOT NULL,
    `round` INT NOT NULL,
    `player_one` VARCHAR(45) NOT NULL,
    `player_two` VARCHAR(45) NOT NULL,
    `winner` VARCHAR(45),
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `tournament_player` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `tournament_id` VARCHAR(255) NOT NULL,
    `player` VARCHAR(30) NOT NULL,
    `deck_name` VARCHAR(100) NOT NULL,
    `deck` TEXT NOT NULL,
    `dropped` BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `pending_tournament_queue` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `scheduled_tournament_id` INT NOT NULL,
    `player_id` INT NOT NULL,
    `deck_name` VARCHAR(45) NOT NULL,
    `deck` TEXT NOT NULL,
    `dropped` BOOLEAN NOT NULL DEFAULT FALSE,
    `checked_in` BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `transfer` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `notify` BOOLEAN NOT NULL DEFAULT FALSE,
    `player` VARCHAR(45) NOT NULL,
    `reason` VARCHAR(255) NOT NULL,
    `collection` VARCHAR(255) NOT NULL,
    `currency` INT NOT NULL DEFAULT 0,
    `contents` TEXT NOT NULL,
    `message` TEXT,
    `date_recorded` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `direction` VARCHAR(45) NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `announcements` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(255) NOT NULL,
    `content` TEXT NOT NULL,
    `start` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `until` TIMESTAMP NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `prize_placeholder` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `label` VARCHAR(255) NOT NULL,
    `count` INT NOT NULL DEFAULT 1,
    `event_kind` VARCHAR(20) NOT NULL,
    `event_id` VARCHAR(45),
    `event_name` VARCHAR(255),
    `tier_index` INT,
    `created` TIMESTAMP NOT NULL,
    `created_by` VARCHAR(45),
    `resolved_blueprint` VARCHAR(45),
    `resolved_on` TIMESTAMP,
    `resolved_by` VARCHAR(45),
    `notes` VARCHAR(1000),
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `prize_award` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `event_kind` VARCHAR(20) NOT NULL,
    `event_id` VARCHAR(45) NOT NULL,
    `event_name` VARCHAR(255),
    `tier_index` INT,
    `tier_label` VARCHAR(255),
    `player` VARCHAR(45) NOT NULL,
    `items` TEXT NOT NULL,
    `awarded_on` TIMESTAMP NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `rtmd_comparison_vote` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `blueprint_a` VARCHAR(20) NOT NULL,
    `blueprint_b` VARCHAR(20) NOT NULL,
    `winner` VARCHAR(20) NOT NULL,
    `ip_address` VARCHAR(45) NOT NULL,
    `voted_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `rtmd_idea_submission` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `idea_text` VARCHAR(250) NOT NULL,
    `ip_address` VARCHAR(45) NOT NULL,
    `submitted_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `upvotes` INT NOT NULL DEFAULT 0,
    `downvotes` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `rtmd_idea_vote` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `submission_id` INT NOT NULL,
    `ip_address` VARCHAR(45) NOT NULL,
    `vote` TINYINT NOT NULL,
    `voted_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_idea_vote` (`submission_id`, `ip_address`)
);
