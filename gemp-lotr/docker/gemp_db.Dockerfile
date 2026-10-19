FROM mariadb:11.8.9 AS MariaDB

COPY ./database_script.sql /docker-entrypoint-initdb.d
COPY ./docker/initial_user_setup.sql /docker-entrypoint-initdb.d
