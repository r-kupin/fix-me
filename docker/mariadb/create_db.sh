#!/bin/sh

if [ ! -d "/var/lib/mysql/${DB_DEFAULT_NAME}" ]; then
    /usr/bin/mysqld --user=mysql --bootstrap <<-EOSQL
        USE mysql;
        FLUSH PRIVILEGES;
        DELETE FROM mysql.user WHERE User='';
        DROP DATABASE IF EXISTS test;
        DELETE FROM mysql.db WHERE Db='test';
        DELETE FROM mysql.user WHERE User='root' AND Host NOT IN ('localhost', '127.0.0.1', '::1');
        ALTER USER 'root'@'localhost' IDENTIFIED BY '${DB_ADMIN_PASSWORD}';

        CREATE DATABASE ${DB_DEFAULT_NAME} CHARACTER SET utf8 COLLATE utf8_general_ci;
        CREATE USER '${DB_DEFAULT_USERNAME}'@'%' IDENTIFIED BY '${DB_DEFAULT_PASSWORD}';
        GRANT ALL PRIVILEGES ON ${DB_DEFAULT_NAME}.* TO '${DB_DEFAULT_USERNAME}'@'%';
        FLUSH PRIVILEGES;

        USE
EOSQL
fi
