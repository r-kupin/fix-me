FROM openjdk:21-alpine

WORKDIR /app

RUN apk add --no-cache bash curl

COPY target/broker-service-*.jar broker-service.jar

EXPOSE 8080 5000

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/broker-service.jar"]
