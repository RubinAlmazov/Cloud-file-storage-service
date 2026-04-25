FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY MyTinyParser/pom.xml MyTinyParser/pom.xml
COPY MyTinyParser/src MyTinyParser/src
RUN cd MyTinyParser && mvn clean install -DskipTests -q

COPY CloudFileStorage/pom.xml CloudFileStorage/pom.xml
COPY CloudFileStorage/src CloudFileStorage/src
RUN cd CloudFileStorage && mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/CloudFileStorage/target/CloudFileStorage-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
