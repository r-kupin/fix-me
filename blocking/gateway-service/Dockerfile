FROM eclipse-temurin:21

RUN mkdir /opt/app
COPY target/* /opt/app

EXPOSE $GATEWAY_PORT

CMD ["java", "-jar", "/opt/app/gateway-service-1.0-SNAPSHOT.jar"]