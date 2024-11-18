FROM mariadb:latest

COPY scripts /docker-entrypoint-initdb.d/

ENV MARIADB_ROOT_PASSWORD=mainpass

ENV MARIADB_USER=stock_service
ENV MARIADB_PASSWORD=stock_service_pass
ENV MARIADB_DATABASE=stock_db

ENV PORT 3306
EXPOSE 3306