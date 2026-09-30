# Imagen del backend (Render u otra plataforma con Docker).
# Build:  docker build -t capstone-steam-api .
# Run:    docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod -e DB_URL=... -e DB_USER=... \
#                    -e DB_PASSWORD=... -e JWT_SECRET=... capstone-steam-api

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
# Primero solo el wrapper y el pom: las dependencias quedan en caché mientras no cambie el pom
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
# Los tests ya corren en CI (.github/workflows/ci.yml)
RUN ./mvnw -q -B package -DskipTests -Djacoco.skip=true

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/target/*.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
