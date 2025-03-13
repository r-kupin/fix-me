#!/bin/sh

if [ ! -d "/var/lib/mysql/${DB_STOCK_NAME}" ]; then
    /usr/bin/mysqld --user=mysql --bootstrap <<-EOSQL
        USE mysql;
        FLUSH PRIVILEGES;
        DELETE FROM mysql.user WHERE User='';
        DROP DATABASE IF EXISTS test;
        DELETE FROM mysql.db WHERE Db='test';
        ALTER USER 'root'@'localhost' IDENTIFIED BY '${DB_ADMIN_PASSWORD}';

        CREATE DATABASE ${DB_STOCK_NAME} CHARACTER SET utf8 COLLATE utf8_general_ci;
        CREATE USER '${DB_STOCK_USERNAME}'@'%' IDENTIFIED BY '${DB_STOCK_PASSWORD}';
        GRANT ALL PRIVILEGES ON ${DB_STOCK_NAME}.* TO '${DB_STOCK_USERNAME}'@'%';

        CREATE DATABASE ${DB_CLIENT_NAME} CHARACTER SET utf8 COLLATE utf8_general_ci;
        CREATE USER '${DB_CLIENT_USERNAME}'@'%' IDENTIFIED BY '${DB_CLIENT_PASSWORD}';
        GRANT ALL PRIVILEGES ON ${DB_CLIENT_NAME}.* TO '${DB_CLIENT_USERNAME}'@'%';
        FLUSH PRIVILEGES;

        USE ${DB_STOCK_NAME};
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

        USE ${DB_CLIENT_NAME};
        CREATE TABLE users
        (
            id            INT         NOT NULL AUTO_INCREMENT,
            username      VARCHAR(60) NOT NULL,
            password      VARCHAR(60) NOT NULL,
            role          VARCHAR(60) NOT NULL,
            UNIQUE (username),
            PRIMARY KEY (id)
        );
EOSQL
fi