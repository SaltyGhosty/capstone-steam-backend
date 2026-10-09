# Equipo de agentes de Capstone Steam

Esta carpeta contiene el equipo listo para instalar en Claude Code: ocho agentes y el comando `/equipo`, que los coordina.

## Instalar

Claude Code solo lee los agentes desde la carpeta `.claude` del proyecto. Desde la raíz del proyecto, en PowerShell:

```powershell
Copy-Item -Recurse -Force .\equipo-agentes\agents, .\equipo-agentes\skills -Destination .\.claude\
```

Después cierra y vuelve a abrir Claude Code en esta carpeta (hace falta la primera vez, porque `.claude\agents` no existía).

Cuando compruebes que funciona, puedes borrar `equipo-agentes`.

## Usar

- Todo el equipo, con un orquestador que reparte la tarea: `/equipo Probar el envío real de correos con Gmail`
- Un agente concreto: `@agent-frontend añade un filtro por estudio en /browse`
- Ver los agentes instalados: `/agents`

## Quién hace qué

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

Cada archivo de `agents/` es texto: puedes abrirlo y cambiar las instrucciones de ese agente.
