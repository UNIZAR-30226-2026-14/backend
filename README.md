# RummiPlus Backend - Guia para Frontend

## 1. Base URL

- `https://localhost:8443`

Nota:

- La API ahora arranca con HTTPS directo en Spring Boot.
- El certificado es local/autofirmado, asi que navegador y Postman pueden avisar la primera vez.

## 2. Arranque rapido (API + BD Docker)

### 2.1 Levantar PostgreSQL en Docker

Desde la carpeta `APIS_PSOFT`:

```powershell
docker compose up -d
```

Comprobar que esta arriba:

```powershell
docker ps --filter "name=rummiplus-postgres"
```

### 2.2 Cargar `crear.sql` en la BD Docker

Desde la raiz del repo (`backend`):

```powershell
Get-Content -Raw ".\crear.sql" | docker exec -i rummiplus-postgres psql -U admin -d rummiplus
```

### 2.3 Levantar API (Spring Boot)

Desde `APIS_PSOFT/server`:

```powershell
.\mvnw.cmd spring-boot:run
```

La API quedara accesible en:

```text
https://localhost:8443
```

## 3. Conexion DBeaver (tu base local/docker)

En nueva conexion PostgreSQL, usa:

1. Host: `127.0.0.1` (o `localhost`)
2. Port: `5432`
3. Database: `rummiplus`
4. Username: `admin`
5. Password: `admin123`
6. SSL: desactivado (si te lo pide)

Luego pulsa `Test Connection` y `Finish`.

## 4. Flujo recomendado frontend

1. Crear jugadores (`POST /api/jugadores`) (No hace falta poner id).
2. Hacer login (`POST /api/auth/login`) y guardar `token`.
3. Crear partida (`POST /api/partidas`) con `corriendo=false` (No hace falta poner id).
4. Crear participaciones (`POST /api/participaciones`).
5. Iniciar partida (`POST /api/partidas/{id}/iniciar`).
6. Loop de turno con endpoints de juego (`jugar`, `robar`, `pasar`).
7. Si faltan jugadores al iniciar, backend completa con bots hasta 4.
8. Si alguien sale durante la partida (`POST /api/partidas/{id}/salir`), backend lo reemplaza por bot.

## 5. Auth (nuevo)

- `POST /api/auth/login`
  - Body: `{ "nombre": "...", "contrasena": "..." }`
  - Devuelve: `token`, `expiraEn`, `jugador`
- `GET /api/auth/me`
- `POST /api/auth/logout`

Para endpoints de turno/jugada:

- Header obligatorio: `Authorization: Bearer <token>`

## 5.1 Cambio de HTTP a HTTPS

Antes:

```text
http://localhost:8080
```

Ahora:

```text
https://localhost:8443
```

Las peticiones JSON no cambian; solo cambia el esquema (`https`) y el puerto (`8443`).

## 6. Endpoints de juego

### Partidas

- `GET /api/partidas`
- `GET /api/partidas/{id}`
- `POST /api/partidas`
- `PUT /api/partidas/{id}`
- `POST /api/partidas/{id}/iniciar`
- `GET /api/partidas/{id}/turno-actual` (solo lectura)
- `POST /api/partidas/{id}/siguiente-turno` (fin de turno)
- `POST /api/partidas/{id}/pasar`
- `POST /api/partidas/{id}/robar`
- `POST /api/partidas/{id}/solo-robar` (roba sin avanzar turno)
- `POST /api/partidas/{id}/jugar`
- `POST /api/partidas/{id}/jugar-avanzado`
- `POST /api/partidas/{id}/salir`

### Participaciones

- `GET /api/participaciones`
- `GET /api/participaciones?partidaId={id}`
- `GET /api/participaciones?jugadorId={id}`
- `GET /api/participaciones/{idJugador}/{idPartida}` (nuevo, directo)
- `POST /api/participaciones`
- `PUT /api/participaciones/{idJugador}/{idPartida}`

### Otros

- `GET /api/jugadores`
- `POST /api/jugadores`
- `PATCH /api/jugadores/{id}/perfil`
- `GET /api/jugadores/{id}/amigos/perfiles`
- `GET /api/jugadores/{id}/amigos/perfiles?estado=ACEPTADO`
- `GET /api/amigos`
    - tiene campos amigo1 y amigo2, el 1 es el que envía la solicitud 
- `POST /api/amigos`
- `PATCH /api/amigos/{jugadorId}/{amigoId}/estado`
- `DELETE /api/admin/wipe` (unico DELETE)

En jugador se distinguen ahora `skinFichas` y `skinTablero` (en lugar de un unico campo de cosmeticos).
En jugador, el campo de imagen expuesto por la API es `imagenPerfil`.
En amistades, las respuestas incluyen IDs, nombres y estado.

## 7. Estado de partida (nuevo)

Campos nuevos en `Partida`:

- `estado`: `WAITING | RUNNING | FINISHED`
- `ganadorId`
- `puntuacionFinal` (diccionario con resumen)
- `turnoInicio`
- `modoArcade`
- `eventoActual`

Comportamiento de modos:

- `modoArcade=false`: mercado deshabilitado y `eventoActual` vacio.
- `modoArcade=true`: mercado habilitado y `eventoActual` se usa en partida.

Al iniciar (`/iniciar`):

- Se genera bolsa completa aleatoria (106 fichas).
- Se reparten 14 fichas por jugador (`manoActual`).
- Se asigna `ordenTurno`.
- Se deja `estado=RUNNING`.

Fin de partida:

- Cuando un jugador se queda sin fichas al jugar.
- Se guarda `ganadorId` y `puntuacionFinal`. En la API se devuelve como diccionario.
- Se actualizan stats de jugador.
- `estado=FINISHED`.

## 8. Formato de acciones de turno

En participaciones ahora tambien se expone:

- `jugadorImagenPerfil`
- `turnosInactivo`

### Pasar / Siguiente turno / Robar

Body:

```json
{
  "idJugador": 1
}
```

### Jugar

Body:

```json
{
  "idJugador": 1,
  "grupos": [
    ["R3", "B3", "O3"],
    ["K7", "K8", "K9"]
  ]
}
```

`POST /api/partidas/{id}/robar` ahora devuelve ademas `fichaRobada` en la respuesta.
`POST /api/partidas/{id}/solo-robar` devuelve `fichaRobada` pero mantiene el mismo `turno`.

Reglas validadas:

- Cada grupo tiene minimo 3 fichas.
- Debe ser terna/cuarteto valido o escalera valida.
- Las fichas deben estar en la mano del jugador.

### Jugar avanzado (extender/reorganizar tablero)

Body (extend_meld):

```json
{
  "idJugador": 1,
  "moveType": "extend_meld",
  "extendIndex": 0,
  "extensionTiles": ["R10"]
}
```

Body (replace_board):

```json
{
  "idJugador": 1,
  "moveType": "replace_board",
  "newBoard": [
    ["R3", "B3", "O3"],
    ["K7", "K8", "K9"]
  ]
}
```

## 9. Nota importante

- Se ha eliminado la API legacy `/api/games`.
- Usad solo flujo `partidas + participaciones + auth`.
