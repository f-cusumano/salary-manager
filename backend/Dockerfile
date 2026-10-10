# syntax=docker/dockerfile:1.7
# =====================================================================================================
# Immagine del backend Salary Manager (Spring Boot 4, Java 25).
# Contesto di build: la cartella backend/ (es. "docker build -t salary-manager-backend backend").
#
#   - stage "build": compila con il Maven Wrapper; il repository Maven (~/.m2) e' una cache di BuildKit,
#     quindi le dipendenze si scaricano solo la prima volta o quando cambia il pom;
#   - stage finale: solo JRE + jar estratto a strati (dipendenze separate dal codice applicativo),
#     utente non root, database H2 nel volume /data.
#
# I test non girano nella build dell'immagine (si eseguono con "./mvnw -B verify"); per eseguirli anche qui:
#   docker build --build-arg MAVEN_BUILD_ARGS="" backend
# =====================================================================================================

ARG JDK_IMAGE=eclipse-temurin:25-jdk
ARG JRE_IMAGE=eclipse-temurin:25-jre

# -----------------------------------------------------------------------------------------------------
# Build: il jar non dipende dalla piattaforma, quindi si compila sull'architettura dell'host di build
# -----------------------------------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM ${JDK_IMAGE} AS build
WORKDIR /workspace

# Prima wrapper e pom: lo strato delle dipendenze resta valido finche' il pom non cambia
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q dependency:go-offline

# Sorgenti (i changelog Liquibase servono anche al codegen jOOQ in generate-sources)
COPY src/ src/
ARG MAVEN_BUILD_ARGS="-Dmaven.test.skip=true"
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B ${MAVEN_BUILD_ARGS} package \
    && cp target/salary-manager-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# -----------------------------------------------------------------------------------------------------
# Runtime
# -----------------------------------------------------------------------------------------------------
FROM ${JRE_IMAGE}

LABEL org.opencontainers.image.title="salary-manager-backend" \
      org.opencontainers.image.description="Salary Manager - API REST (Spring Boot, Java 25)"

# Utente di sistema senza shell ne' privilegi; /data e' l'unica cartella scrivibile (database H2)
RUN groupadd --system --gid 10001 sam \
    && useradd --system --uid 10001 --gid sam --home-dir /app --no-create-home --shell /usr/sbin/nologin sam \
    && mkdir -p /data \
    && chown sam:sam /data

WORKDIR /app

# Strati dal meno al piu' variabile: un cambio al solo codice applicativo non invalida le dipendenze.
# I file restano di root (sola lettura per l'utente dell'applicazione).
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# SAM_DB_PATH: file H2 (senza estensione) nel volume; JAVA_OPTS: opzioni della JVM, sovrascrivibili.
# SAM_SERVER_ADDRESS: nel container si ascolta su tutte le interfacce (fuori dal container il default e' 127.0.0.1);
# chi raggiunge il backend lo decidono le porte pubblicate.
# La chiave ANTHROPIC_API_KEY si passa solo a runtime (mai nell'immagine).
ENV SAM_DB_PATH=/data/salary-manager \
    SAM_SERVER_ADDRESS=0.0.0.0 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    TZ=Europe/Rome

VOLUME ["/data"]
EXPOSE 8080

USER sam

# GET /api/v1/ccnl: endpoint leggero che risponde solo con applicazione avviata e database raggiungibile
# (l'avvio comprende le migrazioni Liquibase, da qui lo start-period ampio)
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD curl -fsS -o /dev/null "http://127.0.0.1:${SAM_PORT:-8080}/api/v1/ccnl" || exit 1

# exec: la JVM riceve direttamente SIGTERM (spegnimento ordinato e chiusura del database H2)
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/application.jar \"$@\"", "salary-manager"]
