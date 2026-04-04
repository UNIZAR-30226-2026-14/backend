# RummiPlus Backend - Guia para Frontend

Guia rapida y actualizada para consumir la API desde frontend.

## 1. Flujo para frontend

1. Levantar PostgreSQL.
2. Levantar Spring Boot.
3. Probar con Postman.
4. Consumir endpoints desde frontend (`fetch`/axios).

El frontend se conecta a `http://localhost:8080`.

## 2. Arranque rapido

### 2.1 Base de datos (PostgreSQL)

Desde `APIS_PSOFT`:

```powershell
docker compose up -d
```

Credenciales esperadas por la API:

- Host: `localhost`
- Puerto: `5432`
- DB: `rummiplus`
- Usuario: `admin`
- Password: `admin123`

### 2.2 Backend (Spring Boot)

Desde `APIS_PSOFT/server`:

```powershell
.\mvnw.cmd clean spring-boot:run
```

## 3. Coleccion Postman

Importar:

- `postman/collections/RummiPlus-API-Demo.postman_collection.json`

Variable clave:

- `baseUrl = http://localhost:8080`

## 4. Cambios importantes de logica de juego

### 4.1 Inicio real de partida

Nuevo endpoint:

- `POST /api/partidas/{id}/iniciar`

Al iniciar:

- Se crea bolsa aleatoria de Rummikub original (106 fichas).
- Se reparten 14 fichas por jugador (guardadas en `participacion.manoActual`).
- `participacion.fichasActuales` se actualiza a `14`.
- `partida.bolsa` guarda solo fichas no jugadas (ni mesa ni manos).
- `partida.conjuntoMesa` empieza vacio.

### 4.2 Turnos y timeout

Nuevo endpoint para frontend (fin de turno):

- `GET /api/partidas/{id}/siguiente-turno`

Reglas aplicadas:

- 4 slots de turno maximo (`0..3`).
- Si faltan jugadores, se saltan slots vacios.
- Timeout de turno automatico: 60 segundos.

### 4.3 Campos nuevos utiles para frontend

En `Partida`:

- `turnoInicio` (timestamp inicio del turno actual)

En `Participacion`:

- `manoActual` (CSV de fichas en mano)
- `ordenTurno` (slot de turno)

## 5. Endpoints principales

### Jugadores

- `GET /api/jugadores`
- `POST /api/jugadores`
- `PATCH /api/jugadores/{id}/perfil`

### Partidas

- `GET /api/partidas`
- `GET /api/partidas/{id}`
- `POST /api/partidas`
- `PUT /api/partidas/{id}`
- `POST /api/partidas/{id}/iniciar`
- `GET /api/partidas/{id}/siguiente-turno`

### Participaciones

- `GET /api/participaciones?partidaId={id}`
- `POST /api/participaciones`
- `PUT /api/participaciones/{idJugador}/{idPartida}`

### Amigos

- `GET /api/amigos?jugadorId={id}`
- `POST /api/amigos`
- `PATCH /api/amigos/{jugador1Id}/{jugador2Id}/estado`

### Limpieza total de datos (solo demo/dev)

- `DELETE /api/admin/wipe`

Este es el unico endpoint `DELETE` disponible.

## 6. Orden recomendado de pruebas (coleccion actual)

1. `1. Jugadores`
2. `2. Partidas` (crear partida con `corriendo=false`)
3. `3. Participaciones`
4. `4. Amigos`
5. `7. Logica Inicio y Turnos`
6. `8. Limpieza Base (Demo)` (opcional al final)
7. `5. Errores de Validacion (demo)`

Nota importante:

- La carpeta `6. Flujo 4 Movimientos` es de demo manual y puede sobrescribir campos como `bolsa`.  
Para validar la logica nueva, usa la carpeta `7. Logica Inicio y Turnos`.

## 7. Ejemplo rapido frontend

```javascript
const start = await fetch("http://localhost:8080/api/partidas/5001/iniciar", {
  method: "POST"
});
const partida = await start.json();
console.log(partida.turno, partida.turnoInicio);
```

Fin de turno:

```javascript
await fetch("http://localhost:8080/api/partidas/5001/siguiente-turno");
```

## 8. Problemas comunes

### 8.1 No conecta a PostgreSQL

Verifica contenedor/servicio y credenciales (`admin/admin123`).

### 8.2 Error de puerto 8080 ocupado

```powershell
Get-NetTCPConnection -LocalPort 8080 | Select-Object OwningProcess,State
Stop-Process -Id <PID> -Force
```

### 8.3 Bot IA (opcional)

Solo si usais bot:

- `GET /api/bot/health`
- `POST /api/bot/move`

Debe estar levantado el servicio IA en `http://127.0.0.1:8765`.


