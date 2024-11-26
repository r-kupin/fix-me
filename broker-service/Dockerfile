FROM eclipse-temurin:21

RUN mkdir /opt/app
COPY target/* /opt/app

EXPOSE 8080 5000

CMD ["java", "-jar", "/opt/app/broker-service-1.0-SNAPSHOT.jar"]
