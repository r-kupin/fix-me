FROM eclipse-temurin:21

RUN mkdir /opt/app
COPY target/* /opt/app

EXPOSE 8761

CMD ["java", "-jar", "/opt/app/eureka-server-1.0-SNAPSHOT.jar"]