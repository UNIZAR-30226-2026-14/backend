# API Demo Checklist

## 1) Preparacion
- Arranca backend Spring Boot en `http://localhost:8080`.
- Importa la coleccion:
  - `server/docs/postman/RummiPlus-API-Demo.postman_collection.json`

## 2) Orden recomendado de ejecucion
1. Carpeta `1. Jugadores`
2. Carpeta `2. Partidas`
3. Carpeta `3. Participaciones`
4. Carpeta `4. Amigos`
5. Carpeta `5. Errores de Validacion (demo)`

## 4) Que demuestra cada bloque
- `Jugadores`: endpoint HTTP en controller, datos de entrada a service, persistencia y salida DTO.
- `Partidas`: flujo de creacion y actualizacion de estado de partida.
- `Participaciones`: relacion muchos-a-muchos `Jugador-Partida` con PK compuesta y estado por jugador.
- `Amigos`: validacion de reglas de dominio (duplicados, no autoamistad), consulta filtrada por jugador con PK compuesta.
- `Errores`: validaciones `@Valid` + `GlobalExceptionHandler` con respuesta estructurada.

## 5) Checklist de exposicion (profesores)
1. Mostrar endpoint de controller.
2. Mostrar metodo en service que ejecuta la logica.
3. Mostrar repo/mapper usado.
4. Ejecutar request y enseñar JSON de respuesta DTO.
5. Ejecutar caso invalido y enseñar error `400` estructurado.
