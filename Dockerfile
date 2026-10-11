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
ENTRYPOINT ["sh", "-c", "keytool -importcert -noprompt -alias aiven-ca -file /etc/secrets/aiven-ca.pem -keystore /tmp/aiven-truststore.p12 -storetype PKCS12 -storepass changeit >/dev/null && exec java -jar /app/app.jar"]
