# Oleada 1 — reglas comunes para los constructores

Diseño: `docs/superpowers/specs/2026-10-19-cierre-y-extras-design.md`. Cuatro bloques en paralelo, cada uno en su propio worktree y su propia rama, partiendo de `main` (`07f4dc3`):

| Bloque | Plan | Migración asignada |
|---|---|---|
| B · Reseñas estilo Steam | `2026-10-19-bloque-b-resenas.md` | **V8** |
| E · Amigos y perfil | `2026-10-19-bloque-e-amigos.md` | **V9** |
| F · Refresh token | `2026-10-19-bloque-f-refresh-token.md` | **V10** |
| G · Portadas en Cloudinary | `2026-10-19-bloque-g-cloudinary.md` | ninguna |

La sesión principal fusiona en `main` en este orden: B → E → F → G, y resuelve los conflictos.

## Reglas (todas obligatorias)

1. **Git:** un commit por tarea del plan, mensaje corto en español. **Nunca** `Co-Authored-By`, «Generated with» ni ninguna atribución a Claude. No hagas push, no cambies de rama, no fusiones.
2. **TDD:** primero el test, verlo fallar por la razón esperada, luego el código, verlo pasar.
3. **Entorno (PowerShell):**
   - Antes de Maven: `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"`.
   - Test suelto: `.\mvnw.cmd -q test "-Dtest=NombreTest" "-Djacoco.skip=true"`.
   - Al final del bloque: `.\mvnw.cmd clean verify` (BUILD SUCCESS y «coverage checks met»). Usa siempre `clean` en el `verify` final: VS Code deja `.class` sucios en `target/`.
   - Frontend: en tu worktree no hay `node_modules`. Antes del primer test: `cd frontend; npm ci`. Luego `npm test` y `npm run build`.
4. **Base de datos:** los tests usan H2 en memoria. **Nunca** arranques la app contra PostgreSQL ni toques `.env`: las migraciones de los cuatro bloques se aplicarán a la BD real solo después de fusionar.
5. **No toques:** `handoff.md`, `README.md`, `frontend/README.md`, `postman/`, `equipo-agentes/`, `.claude/`, `docs/` ni la bóveda de Obsidian. La documentación y Postman los hace la sesión principal tras fusionar. `scripts/` solo si tu plan lo dice.
6. **Migración:** usa solo el número asignado. `FlywayMigrationTest` comprueba la última versión. Cambia el test para que compruebe tu número (renómbralo `appliesAllMigrationsUpToV<N>`). El conflicto lo resuelve la fusión.
7. **i18n:**
   - **Backend:** claves nuevas en `messages.properties` (es), `messages_en.properties` y `messages_it.properties`, al **final** de cada archivo, bajo un comentario `# Bloque X`. El apóstrofo se escribe `’`.
   - **Frontend:** claves nuevas en `frontend/src/i18n/messages.ts`, al **final** de cada uno de los tres idiomas, bajo `// Bloque X`. `i18n.test.tsx` exige las mismas claves en los tres.
   - Todo texto visible va en los tres idiomas.
8. **Errores de la API:** subclases de `common/exception/ApiException` (`BadRequestException`, `ConflictException`, `ForbiddenException`, `NotFoundException`) con **claves** de mensaje, nunca texto fijo. Salen como `ProblemDetail` traducido.
9. **Estilo:** sigue el código vecino.
   - Paquetes por funcionalidad, DTOs como `record` con `static from(...)`.
   - Comentarios en español, cortos.
   - Tests de integración con `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional` (import de `AutoConfigureMockMvc`: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`).
   - Frontend: tests con `mockFetch` y `renderRoutes` de `frontend/src/test/`.
10. **Informe final** (tu último mensaje):
    - lista de commits (hash y mensaje);
    - nº de tests de backend y de frontend, y cobertura de `clean verify`;
    - cada desviación del plan, con el motivo;
    - lo que no pudiste hacer.
