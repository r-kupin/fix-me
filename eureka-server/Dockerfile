FROM eclipse-temurin:21

RUN mkdir /opt/app
COPY target/* /opt/app

EXPOSE $EUREKA_PORT

CMD ["java", "-jar", "/opt/app/eureka-server-1.0-SNAPSHOT.jar"]