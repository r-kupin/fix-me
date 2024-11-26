FROM eclipse-temurin:21

RUN mkdir /opt/app
COPY target/* /opt/app

EXPOSE 5001 5000

CMD ["java", "-jar", "/opt/app/router-service-1.0-SNAPSHOT.jar"]