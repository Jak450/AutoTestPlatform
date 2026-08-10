FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backed/AI_Study_Notes/pom.xml .
RUN mvn -q dependency:go-offline
COPY backed/AI_Study_Notes/src ./src
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /opt/autotest
COPY --from=build /build/target/*.jar app.jar
COPY AutoTest_fronted/dist /opt/autotest/dist
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar app.jar --spring.profiles.active=prod"]
