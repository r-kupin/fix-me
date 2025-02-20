FROM alpine:latest

ARG DB_STOCK_NAME
ARG DB_STOCK_USERNAME
ARG DB_STOCK_PASSWORD

ARG DB_CLIENT_NAME
ARG DB_CLIENT_USERNAME
ARG DB_CLIENT_PASSWORD

ARG DB_ADMIN_PASSWORD

RUN apk update && apk add --no-cache mariadb mariadb-client

COPY docker.cnf /etc/my.cnf.d/
RUN sed -i "s|skip-networking|skip-networking=0|g" /etc/my.cnf.d/mariadb-server.cnf

RUN mkdir -p /var/run/mysqld && \
     chown -R mysql:mysql /var/run/mysqld

RUN mysql_install_db --user=mysql --datadir=/var/lib/mysql

COPY create_db.sh .
COPY add_new_exchange_db.sh .
RUN sh create_db.sh && rm -f create_db.sh

EXPOSE $DB_PORT

USER mysql

CMD ["/usr/bin/mariadbd"]