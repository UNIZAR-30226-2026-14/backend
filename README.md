# RummiPlus Backend - Guia para Frontend

## 1. Base URL

- `http://localhost:8080`

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

## 5. Auth (nuevo)

- `POST /api/auth/login`
  - Body: `{ "nombre": "...", "contrasena": "..." }`
  - Devuelve: `token`, `expiraEn`, `jugador`
- `GET /api/auth/me`
- `POST /api/auth/logout`

Para endpoints de turno/jugada:

- Header obligatorio: `Authorization: Bearer <token>`

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
- `POST /api/partidas/{id}/jugar`

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
- `GET /api/amigos`
- `POST /api/amigos`
- `PATCH /api/amigos/{jugadorId}/{amigoId}/estado`
- `DELETE /api/admin/wipe` (unico DELETE)

## 7. Estado de partida (nuevo)

Campos nuevos en `Partida`:

- `estado`: `WAITING | RUNNING | FINISHED`
- `ganadorId`
- `puntuacionFinal` (json string con resumen)
- `turnoInicio`

Al iniciar (`/iniciar`):

- Se genera bolsa completa aleatoria (106 fichas).
- Se reparten 14 fichas por jugador (`manoActual`).
- Se asigna `ordenTurno`.
- Se deja `estado=RUNNING`.

Fin de partida:

- Cuando un jugador se queda sin fichas al jugar.
- Se guarda `ganadorId` y `puntuacionFinal`.
- Se actualizan stats de jugador.
- `estado=FINISHED`.

## 8. Formato de acciones de turno

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

Reglas validadas:

- Cada grupo tiene minimo 3 fichas.
- Debe ser terna/cuarteto valido o escalera valida.
- Las fichas deben estar en la mano del jugador.

## 9. Nota importante

- Se ha eliminado la API legacy `/api/games`.
- Usad solo flujo `partidas + participaciones + auth`.
