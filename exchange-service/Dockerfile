FROM openjdk:21-alpine

WORKDIR /app

RUN apk add --no-cache bash curl

COPY target/exchange-service-*.jar exchange-service.jar

EXPOSE 5001

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/exchange-service.jar"]
