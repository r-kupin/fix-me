#!/bin/sh

if [ ! -d "/var/lib/mysql/${DB_DEFAULT_NAME}" ]; then
    /usr/bin/mysqld --user=mysql --bootstrap <<-EOSQL
        USE mysql;
        FLUSH PRIVILEGES;
        DELETE FROM mysql.user WHERE User='';
        DROP DATABASE IF EXISTS test;
        DELETE FROM mysql.db WHERE Db='test';
        ALTER USER 'root'@'localhost' IDENTIFIED BY '${DB_ADMIN_PASSWORD}';

        CREATE DATABASE ${DB_DEFAULT_NAME} CHARACTER SET utf8 COLLATE utf8_general_ci;
        CREATE USER '${DB_DEFAULT_USERNAME}'@'%' IDENTIFIED BY '${DB_DEFAULT_PASSWORD}';
        GRANT ALL PRIVILEGES ON ${DB_DEFAULT_NAME}.* TO '${DB_DEFAULT_USERNAME}'@'%';
        FLUSH PRIVILEGES;

        USE ${DB_DEFAULT_NAME};
        CREATE TABLE stock
        (
            id     INT         NOT NULL AUTO_INCREMENT,
            name   VARCHAR(60) NOT NULL,
            amount INT         NOT NULL,
            UNIQUE (name),
            PRIMARY KEY (id)
        );
        insert into stock (id, name, amount)
        values (1, 'TEST1', 1);
        insert into stock (id, name, amount)
        values (2, 'TEST2', 2);

        USE mysql;
        CREATE DATABASE ${DB_ADDITIONAL_NAME} CHARACTER SET utf8 COLLATE utf8_general_ci;
        CREATE USER '${DB_ADDITIONAL_USERNAME}'@'%' IDENTIFIED BY '${DB_ADDITIONAL_PASSWORD}';
        GRANT ALL PRIVILEGES ON ${DB_ADDITIONAL_NAME}.* TO '${DB_ADDITIONAL_USERNAME}'@'%';
        FLUSH PRIVILEGES;

        USE ${DB_ADDITIONAL_NAME};
        CREATE TABLE stock
        (
            id     INT         NOT NULL AUTO_INCREMENT,
            name   VARCHAR(60) NOT NULL,
            amount INT         NOT NULL,
            UNIQUE (name),
            PRIMARY KEY (id)
        );
        insert into stock (id, name, amount)
        values (1, 'TEST3', 3);
        insert into stock (id, name, amount)
        values (2, 'TEST4', 4);
EOSQL
fi