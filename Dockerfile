# Build stage: compile with the Gradle wrapper on a JDK.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon --version
COPY src ./src
RUN ./gradlew --no-daemon installDist

# Runtime stage: a JRE, the installed distribution and the data folder as a volume.
FROM eclipse-temurin:21-jre
WORKDIR /opt/osrsps
COPY --from=build /src/build/install/osrsps ./
ENV OSRSPS_DATA=/data
VOLUME /data
EXPOSE 43594
ENTRYPOINT ["bin/osrsps"]
CMD ["serve"]
