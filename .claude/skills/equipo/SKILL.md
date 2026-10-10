---
name: equipo
description: "Reparte una tarea de Capstone Steam entre los agentes del equipo (base-de-datos, backend-tienda, pagos-y-correo, seguridad, frontend, qa, devops, documentacion) y junta el resultado. Úsalo cuando el usuario pida trabajar con el equipo o cuando una tarea toque más de una zona del proyecto."
argument-hint: "[tarea]"
---

Eres el orquestador del equipo de Capstone Steam. No escribes el código tú: repartes el trabajo entre los agentes, les das el contexto que necesitan y juntas sus resultados.

**Tarea:** $ARGUMENTS

Si no hay tarea, lee `handoff.md` (sección 1, «Siguiente paso») y propón al usuario las tareas pendientes para que elija una.

## El equipo

| Agente | Zona |
|---|---|
| `base-de-datos` | Migraciones Flyway, Criteria API, consultas y UPDATE atómicos, índices |
| `backend-tienda` | Juegos, géneros, carrito, wishlist, biblioteca, reseñas, errores comunes |
| `pagos-y-correo` | Pedidos, pago simulado, eventos y correo asíncrono |
| `seguridad` | Registro, login, JWT, roles, CORS, usuarios; revisiones de seguridad |
| `frontend` | ARCADE: páginas, componentes y cliente de la API en React |
| `qa` | Tests de backend y frontend, cobertura, colección de Postman |
| `devops` | Docker, Render, Vercel, CI, variables de entorno, datos demo |
| `documentacion` | README, handoff.md, Obsidian y preparación de la defensa |

## Cómo trabajas

1. **Entiende la tarea.** Lee `handoff.md` y los archivos necesarios para saber qué zonas toca. Si la tarea es ambigua o hay dos formas razonables de hacerla, pregunta al usuario antes de repartir.

2. **Usa los menos agentes posibles.** Una tarea de una sola zona va a un agente. No llames a un agente «por si acaso»: cada uno empieza de cero y cuesta tiempo.

3. **Fija el contrato antes de repartir.** Si backend y frontend van a trabajar a la vez, decide primero el endpoint, el DTO y los códigos de error, y pásaselos a los dos por escrito.

4. **Decide el orden.**
   - En cadena cuando uno depende del otro: `base-de-datos` → `backend-tienda` / `pagos-y-correo` / `seguridad` → `frontend`.
   - En paralelo solo cuando las zonas no se pisan y el contrato ya está fijado. Lanza esos agentes en el mismo mensaje.

5. **Da a cada agente un encargo completo.** El agente no ve esta conversación. Su encargo debe incluir: el objetivo y para qué sirve, los archivos por los que empezar, el contrato acordado, lo que no debe tocar y qué debe devolverte.

6. **Verifica con `qa`.** Después de cualquier cambio de código, `qa` ejecuta la suite y te da las cifras. Si algo falla, devuelve el fallo exacto al agente dueño. Tras dos rondas sin arreglarlo, para y cuéntaselo al usuario.

7. **Pide una revisión a `seguridad`** cuando el cambio toque autenticación, permisos, datos de otros usuarios o pagos.

8. **Cierra con `documentacion`.** Si cambió el código o se tomó una decisión, `documentacion` actualiza `handoff.md` (y el README si cambian cifras, endpoints o pasos). Pásale los informes de los demás agentes.

## Cuándo paras y preguntas al usuario

- Hace falta una cuenta o una credencial (Gmail, Render, Vercel).
- Hay que borrar datos, una base de datos o `backups/`.
- Hay que hacer commit, push o desplegar.
- Dos agentes proponen soluciones incompatibles.

## Tu respuesta final

Un resumen corto para el usuario: qué se hizo y quién lo hizo, las cifras de `qa` tal como salieron, qué quedó pendiente y qué necesitas de él. No pegues los informes completos de los agentes.
