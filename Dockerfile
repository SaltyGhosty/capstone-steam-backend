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
COPY --from=build /app/target/*.jar build.jar
# Jar extraído (lib/ aparte): la caché AOT necesita un classpath fijo de ficheros
RUN java -Djarmode=tools -jar build.jar extract --destination extracted \
    && mv extracted/*.jar extracted/app.jar && rm build.jar
WORKDIR /app/extracted

# Arranque rápido en el plan free (0,1 CPU): la JVM de Java 25 guarda en app.aot las clases ya cargadas y
# enlazadas durante un arranque de entrenamiento. Mismos flags que en ENTRYPOINT.
# El heap va fijo (384 MB = 75 % de los 512 MB del plan free): con MaxRAMPercentage, la máquina de build
# (con mucha RAM) entrenaba con un heap enorme sin punteros comprimidos y la caché no servía en el contenedor.
# -Xms128m: con el heap inicial por defecto (1/64 de 512 MB = 8 MB) la JVM no puede cargar los objetos
# archivados de la caché y no arranca.
# El entrenamiento no toca la BD: sin Flyway, sin metadatos JDBC, y sale en cuanto el contexto está listo.
ENV JAVA_FLAGS="-Xms128m -Xmx384m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
RUN java $JAVA_FLAGS -XX:AOTCacheOutput=app.aot \
      -Dspring.context.exit=onRefresh -Dspring.profiles.active=prod \
      -Dspring.flyway.enabled=false -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -Dspring.datasource.url=jdbc:postgresql://localhost:1/aot \
      -Dspring.datasource.username=aot -Dspring.datasource.password=aot \
      -DJWT_SECRET=YW90LXRyYWluaW5nLXNlY3JldC0wMTIzNDU2Nzg5YWJjZGVmMDEyMw== \
      -jar app.jar \
    && test -s app.aot

USER app
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
# Si app.aot no sirve (otra JVM), la JVM avisa y arranca igual, solo que más lenta
ENTRYPOINT ["sh", "-c", "exec java $JAVA_FLAGS -XX:AOTCache=app.aot -jar app.jar"]
