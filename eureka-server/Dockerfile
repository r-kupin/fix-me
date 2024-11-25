FROM openjdk:21-alpine

WORKDIR /app

RUN apk add --no-cache bash curl

COPY target/eureka-server-*.jar eureka-server.jar

EXPOSE 8761

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/eureka-server.jar"]
