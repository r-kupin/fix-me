FROM openjdk:21-alpine

WORKDIR /app

RUN apk add --no-cache bash curl

COPY target/gateway-service-*.jar gateway-service.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/gateway-service.jar"]
