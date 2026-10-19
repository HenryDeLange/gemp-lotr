INSERT INTO `player` (`name`, `password`, `type`, `create_ip`)
SELECT 'dev', 'ef260e9aa3c673af240d17a2660480361a8e081d1ffeca2a5ed0e3219fc18567', 'u', '127.0.0.1'
WHERE NOT EXISTS (SELECT 1 FROM `player` WHERE `name` = 'dev');

UPDATE `player`
SET `password` = 'ef260e9aa3c673af240d17a2660480361a8e081d1ffeca2a5ed0e3219fc18567'
WHERE `name` = 'dev' AND (`password` IS NULL OR `password` = '');

INSERT INTO `deck` (`player_id`, `name`, `target_format`, `contents`, `notes`)
SELECT `player`.`id`, 'fotr_block Elf-Moria', 'fotr_block',
       '1_290|1_2|1_320,1_334,1_341,2_118,1_349,1_350,1_355,1_357,1_362|3_7,1_48,1_48,1_48,1_50,1_53,1_53,3_13,3_13,1_45,1_45,1_163,2_49,2_49,2_60,2_60,2_60,2_60,1_176,1_176,1_176,1_176,1_178,1_178,1_184,1_184,2_67,1_191,1_191,2_52,1_31,1_31,1_33,1_33,1_41,1_41,1_47,1_47,1_190,3_23,3_23,3_27,3_27,1_32,1_32,1_37,1_167,1_171,1_187,1_187,1_187,1_187,1_201,3_9,2_18,2_18,2_18,1_175,1_192,1_196',
       ''
FROM `player`
WHERE `player`.`name` = 'dev'
  AND NOT EXISTS (
      SELECT 1 FROM `deck`
      WHERE `deck`.`player_id` = `player`.`id` AND `deck`.`name` = 'fotr_block Elf-Moria'
  );
