FROM eclipse-temurin:21

RUN mkdir /opt/app
COPY target/* /opt/app

EXPOSE 8080

CMD ["java", "-jar", "/opt/app/gateway-service-1.0-SNAPSHOT.jar"]