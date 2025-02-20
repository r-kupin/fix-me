FROM eclipse-temurin:21

ENV SERVER_LISTEN_PORT=8080 \
    SERVER_LISTEN_ADDR=0.0.0.0

RUN mkdir /opt/app
COPY target/* /opt/app

EXPOSE $ROUTER_BROKER_PORT $ROUTER_EXCHANGE_PORT

CMD ["java", "-jar", "/opt/app/router-service-1.0-SNAPSHOT.jar"]