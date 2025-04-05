FROM gradle:8-jdk17 as build
WORKDIR /machinum-releaser
COPY build.gradle build.gradle
COPY settings.gradle settings.gradle
COPY src src
COPY conf conf
RUN gradle shadowJar

FROM eclipse-temurin:17-jdk
WORKDIR /machinum-releaser
COPY --from=build /machinum-releaser/build/libs/machinum-releaser-1.0.0-all.jar app.jar
COPY conf conf
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
