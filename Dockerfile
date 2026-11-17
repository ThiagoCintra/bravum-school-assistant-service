# Build somente quando autorizado. Nao publica nem executa Actions.
FROM --platform=$BUILDPLATFORM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY . .
RUN mvn -B -Dmaven.test.skip=true package
FROM --platform=$TARGETPLATFORM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar
EXPOSE 8088
ENTRYPOINT ["java","-jar","/app/app.jar"]
