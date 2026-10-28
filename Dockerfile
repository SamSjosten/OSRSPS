# Build stage: compile with the Gradle wrapper on a JDK. VM 214 builds this next to the live server and can be ballooned
# down to 2 GB, so the build keeps to one small JVM: the Kotlin compiler runs inside Gradle's (no compile daemon), with a
# 448 MB heap, the serial collector and the quick JIT only. About 800 MB at its peak, against 1.4 GB with the defaults.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
ENV GRADLE_OPTS="-Xmx64m -XX:+UseSerialGC"
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon --version
COPY src ./src
RUN ./gradlew --no-daemon -Dorg.gradle.workers.max=1 -Pkotlin.compiler.execution.strategy=in-process \
    "-Dorg.gradle.jvmargs=-Xmx448m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m" \
    installDist

# Runtime stage: a JRE, the installed distribution and the data folder as a volume.
FROM eclipse-temurin:21-jre
WORKDIR /opt/osrsps
COPY --from=build /src/build/install/osrsps ./
ENV OSRSPS_DATA=/data
VOLUME /data
EXPOSE 43594 43595
ENTRYPOINT ["bin/osrsps"]
CMD ["serve"]
