FROM eclipse-temurin:21

RUN mkdir /opt/app
COPY target/* /opt/app

EXPOSE 5001

CMD ["java", "-jar", "/opt/app/exchange-service-1.0-SNAPSHOT.jar"]