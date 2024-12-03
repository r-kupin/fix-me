#!/bin/sh

if [ ! -d "/var/lib/mysql/${DB_NAME}" ]; then
    /usr/bin/mysqld --user=mysql --bootstrap <<-EOSQL
        USE mysql;
        FLUSH PRIVILEGES;
        DELETE FROM mysql.user WHERE User='';
        DROP DATABASE IF EXISTS test;
        DELETE FROM mysql.db WHERE Db='test';
        ALTER USER 'root'@'localhost' IDENTIFIED BY '${DB_ADMIN_PASSWORD}';

        CREATE DATABASE ${DB_NAME} CHARACTER SET utf8 COLLATE utf8_general_ci;
        CREATE USER '${DB_USERNAME}'@'%' IDENTIFIED BY '${DB_PASSWORD}';
        GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USERNAME}'@'%';
        FLUSH PRIVILEGES;

        USE ${DB_NAME};
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
EOSQL
fi