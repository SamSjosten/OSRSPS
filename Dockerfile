# Build stage: compile with the Gradle wrapper on a JDK.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon --version
COPY src ./src
# VM 214 builds this next to the running server on about 2 GB of RAM. Gradle's defaults (a launcher, a daemon and a
# separate Kotlin compile daemon) peak around 1.8 GB and got the server OOM-killed during a deploy; one daemon that
# compiles Kotlin in-process peaks around 0.8 GB. Its heap was 95% full after a full GC at 256 MB (18k lines of main),
# 64% at 384 MB. At 45k lines 384 MB ran out ("Java heap space" in compileKotlin, 2026-11-01): up to 389 MB stays
# live after a full GC, so it is 640 MB now (61%) and a cold build peaks around 1.1-1.2 GB. Raise it again before it
# fills up. Incremental compilation is off: every image build is a full one, and its
# snapshots only cost heap. Metaspace needs about 250 MB
# here, because every image build is a cold one that also compiles the build scripts (192 MB failed with
# "OutOfMemoryError: Metaspace"); a cold build peaks around 1 GB.
ENV GRADLE_OPTS="-Xmx64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
# If memory runs out anyway, the kernel should kill this step, not the server. Build steps seem to keep the Docker
# daemon's low OOM score (in that deploy the kernel killed the 300-440 MB server four times and spared the build), so
# the step first raises its own score; the JVMs it starts inherit it.
RUN echo 1000 >/proc/$$/oom_score_adj || true; \
    ./gradlew --no-daemon installDist \
    "-Dorg.gradle.jvmargs=-Xmx640m -XX:MaxMetaspaceSize=320m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k" \
    -Pkotlin.compiler.execution.strategy=in-process -Pkotlin.incremental=false -Dorg.gradle.workers.max=1 -Dorg.gradle.parallel=false \
    -Dorg.gradle.vfs.watch=false

# Runtime stage: a JRE, the installed distribution and the data folder as a volume.
FROM eclipse-temurin:21-jre
WORKDIR /opt/osrsps
COPY --from=build /src/build/install/osrsps ./
ENV OSRSPS_DATA=/data
VOLUME /data
EXPOSE 43594 43595
ENTRYPOINT ["bin/osrsps"]
CMD ["serve"]
