FROM alpine:latest

ARG DB_ADMIN_PASSWORD
ARG DB_DEFAULT_USERNAME
ARG DB_DEFAULT_PASSWORD
ARG DB_DEFAULT_NAME

RUN apk update && apk add --no-cache mariadb mariadb-client

COPY docker.cnf /etc/my.cnf.d/
RUN sed -i "s|skip-networking|skip-networking=0|g" /etc/my.cnf.d/mariadb-server.cnf

RUN mkdir -p /var/run/mysqld && \
     chown -R mysql:mysql /var/run/mysqld

RUN mysql_install_db --user=mysql --datadir=/var/lib/mysql

COPY create_db.sh .
RUN sh create_db.sh && rm -f /*.sh

COPY init.sql /docker-entrypoint-initdb.d/

EXPOSE 3306

USER mysql

CMD ["/usr/bin/mysqld"]