# Oleada 2 — reglas comunes para los constructores

Diseño: `docs/superpowers/specs/2026-10-19-cierre-y-extras-design.md`. Dos bloques en paralelo, cada uno en su propio worktree y su propia rama:

| Bloque | Plan | Migración |
|---|---|---|
| C · Catálogo a ~60 juegos | `2026-10-19-bloque-c-catalogo.md` | ninguna |
| D · Chatbot recomendador | `2026-10-19-bloque-d-chatbot.md` | ninguna |

La sesión principal fusiona en `main` en este orden: C → D.

## Reglas

Las de `2026-10-19-oleada-1-comun.md` siguen valiendo todas, con estos cambios:

1. **Base de la rama.** En la oleada 1 los worktrees salieron de un commit viejo (`0578f9e`). Antes de nada:
   - ejecuta `git log -1 --format=%h` y `git merge-base --is-ancestor <commit que te da la sesión principal> HEAD`;
   - si no lo contiene, ejecuta `git merge --ff-only main`. Nunca uses `reset --hard`.
2. **Estado actual del esquema:** V1 a V10, y `FlywayMigrationTest` termina en `appliesAllMigrationsUpToV10`. Esta oleada no añade migraciones.
3. **Sesión con refresh token:** el token de acceso dura **15 min** (bloque F). Cualquier script que haga muchas llamadas tiene que contar con eso; ver el plan C.
4. **i18n:** las claves nuevas van bajo `# Bloque C` / `// Bloque C` o `# Bloque D` / `// Bloque D`.
5. **Saltos de línea:** `.gitattributes` manda `*.cmd` en CRLF. Si `git status` muestra `deploy/local/*.cmd` modificados sin que los toques, no los incluyas en tus commits.
6. **Informe final:** el mismo de la oleada 1, más la **rama** y el **worktree**.
