# Postman en este repositorio

## Que archivo usar segun tu cliente
- Postman Desktop/Web:
  - Importa `postman/collections/RummiPlus-API-Demo.postman_collection.json`
  - Importa `postman/environments/RummiPlus-Local.postman_environment.json`
- Recursos locales de workspace/sync:
  - Carpeta `postman/collections/**` con archivos `.request.yaml`
  - No son el formato recomendado para importar request por request en Postman Desktop.

## Orden de ejecucion recomendado
1. `1. Jugadores`
2. `2. Partidas`
3. `3. Participaciones`
4. `4. Amigos`
5. `5. Errores de Validacion (demo)`

## Errores comunes
- `409 Conflict`: estas reusando IDs que ya existen en H2 (`101`, `102`, `5001`).
  - Solucion: cambia variables del environment o limpia datos.
- `404 Not Found`: falta crear datos previos (por ejemplo jugador/partida antes de participacion).
- `400 Bad Request`: body JSON invalido, `Content-Type` incorrecto o tipos de campos incorrectos.
- `502 Bad Gateway` en `/api/bot/health` o `/api/bot/move`:
  - El backend Java esta levantado, pero no puede conectarse al servicio IA del bot.
  - Verifica que el servicio IA responda en `http://127.0.0.1:8765/api/health`.
  - Si la IA usa otro puerto/host, actualiza `bot.ia.base-url` en `application.properties`.
