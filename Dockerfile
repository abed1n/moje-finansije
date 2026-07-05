# Faza 1: build aplikacije Maven wrapperom
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# unzip je potreban Maven wrapperu (bez njega prelazi na .tar.gz
# distribuciju ciji se checksum ne poklapa sa onim u properties fajlu)
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

COPY mvnw pom.xml ./
COPY .mvn .mvn
# Windows checkout moze imati CRLF zavrsetke - normalizuj wrapper fajlove
RUN sed -i 's/\r$//' mvnw .mvn/wrapper/maven-wrapper.properties && chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B package -DskipTests

# Faza 2: runtime (JRE, non-root korisnik)
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r pfm && useradd -r -g pfm pfm \
    && mkdir -p /app/uploads \
    && chown -R pfm:pfm /app

COPY --from=build /build/target/quarkus-app/lib/ /app/lib/
COPY --from=build /build/target/quarkus-app/*.jar /app/
COPY --from=build /build/target/quarkus-app/app/ /app/app/
COPY --from=build /build/target/quarkus-app/quarkus/ /app/quarkus/

USER pfm
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
