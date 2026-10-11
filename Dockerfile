FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle/wrapper ./gradle/wrapper
COPY src/main ./src/main
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon \
    && cp build/libs/*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER 1000:1000
ENTRYPOINT set -eu; \
    case "${KAKAO_MAP_JAVASCRIPT_KEY:-}" in \
      ''|*[!a-zA-Z0-9]*) echo 'Kakao map JavaScript key is missing or invalid' >&2; exit 1 ;; \
    esac; \
    mkdir -p /tmp/routy-static/Routy/js; \
    printf '{"javascriptKey":"%s"}' "$KAKAO_MAP_JAVASCRIPT_KEY" > /tmp/routy-static/Routy/js/map-config.json; \
    keytool -importcert -noprompt -alias aiven-ca -file /etc/secrets/aiven-ca.pem -keystore /tmp/aiven-truststore.p12 -storetype PKCS12 -storepass changeit >/dev/null; \
    exec java -jar /app/app.jar
