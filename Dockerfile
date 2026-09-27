# Parameterized build for all Ameso backend services
# docker build --build-arg SERVICE=Ameso.IncidentService -t incident-service .
# SERVICE maps to main class ameso.<service lowercased>.Application (e.g. ameso.incidentservice.Application)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src/ src/
RUN mvn -q -B package dependency:copy-dependencies -DincludeScope=runtime -DskipTests

FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
COPY --from=build /src/target/dependency lib/
COPY --from=build /src/target/classes classes/
ENV SERVICE=${SERVICE}
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -cp 'classes:lib/*' \"$(echo $SERVICE | tr 'A-Z' 'a-z').Application\""]
