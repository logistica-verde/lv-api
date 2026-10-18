FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
COPY contracts contracts
RUN ./mvnw -B -q -DskipTests package && cp target/lv-api-*.jar app.jar

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 --create-home lv
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER lv
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
