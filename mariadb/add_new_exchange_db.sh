#!/bin/sh

db_name=$1
user=$2
password=$3

if [ ! -d "/var/lib/mysql/${db_name}" ]; then
    mysql <<-EOSQL
        USE mysql;

        CREATE DATABASE ${db_name} CHARACTER SET utf8 COLLATE utf8_general_ci;
        CREATE USER '${user}'@'%' IDENTIFIED BY '${password}';
        GRANT ALL PRIVILEGES ON ${db_name}.* TO '${user}'@'%';
        FLUSH PRIVILEGES;

        USE ${db_name};
        CREATE TABLE stock
        (
            id     INT         NOT NULL AUTO_INCREMENT,
            name   VARCHAR(60) NOT NULL,
            amount INT         NOT NULL,
            UNIQUE (name),
            PRIMARY KEY (id)
        );
EOSQL
fi