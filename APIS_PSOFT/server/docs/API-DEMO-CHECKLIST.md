# API Demo Checklist

## 1) Preparacion
- Arranca backend Spring Boot en `http://localhost:8080`.
- Importa la coleccion:
  - `server/docs/postman/RummiPlus-API-Demo.postman_collection.json`

## 2) Orden recomendado de ejecucion
1. Carpeta `1. Jugadores`
2. Carpeta `2. Amigos`
3. Carpeta `3. Partidas`
4. Carpeta `4. Errores de Validacion (demo)`

## 3) Nota importante para amigos
- En `Crear Relacion de Amistad`, copia el `id` devuelto.
- Pega ese valor en la variable `amistadId` de Postman.
- Ejecuta `Actualizar Estado de Amistad (usa id devuelto)`.

## 4) Que demuestra cada bloque
- `Jugadores`: endpoint HTTP en controller, datos de entrada a service, persistencia y salida DTO.
- `Amigos`: validacion de reglas de dominio (duplicados, no autoamistad), consulta filtrada por jugador.
- `Partidas`: flujo de creacion y actualizacion de estado de partida.
- `Errores`: validaciones `@Valid` + `GlobalExceptionHandler` con respuesta estructurada.

## 5) Checklist de exposicion (profesores)
1. Mostrar endpoint de controller.
2. Mostrar metodo en service que ejecuta la logica.
3. Mostrar repo/mapper usado.
4. Ejecutar request y enseñar JSON de respuesta DTO.
5. Ejecutar caso invalido y enseñar error `400` estructurado.
