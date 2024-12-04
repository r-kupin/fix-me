FROM eclipse-temurin:21

ENV SERVER_LISTEN_PORT=8080 \
    SERVER_LISTEN_ADDR=0.0.0.0

RUN mkdir /opt/app
COPY target/* /opt/app

EXPOSE $SERVER_LISTEN_PORT $ROUTER_BROKER_PORT

CMD ["java", "-jar", "/opt/app/broker-service-1.0-SNAPSHOT.jar"]
