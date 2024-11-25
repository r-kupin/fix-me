FROM openjdk:21-alpine

WORKDIR /app

RUN apk add --no-cache bash curl

COPY target/router-service-*.jar router-service.jar

EXPOSE 5000 5001

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/router-service.jar"]
