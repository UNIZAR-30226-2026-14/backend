# RummiPlus Backend - Guia para Frontend

Este documento explica, paso a paso, como conectaros al backend aunque no tengais experiencia en backend.

## 1. Uso del backend (consumidores de la API)

Si trabajas en frontend (web o escritorio), tu flujo es este:

1. Levantar PostgreSQL con Docker.
2. Levantar Spring Boot (API REST).
3. Probar que responde con Postman.
4. Consumir endpoints desde frontend con `fetch`/axios.

El frontend **solo** habla con el backend en `http://localhost:8080`.  
El backend se encarga de base de datos y logica.

## 2. Arranque rapido

### 2.1 Levantar base de datos (PostgreSQL)

Desde la carpeta `APIS_PSOFT`:

```powershell
docker compose up -d
```

Esto levanta PostgreSQL con:

- Host: `localhost`
- Puerto: `5432`
- DB: `rummiplus`
- Usuario: `admin`
- Password: `admin123`

### 2.2 Levantar backend (Spring Boot)

Desde `APIS_PSOFT/server`:

```powershell
.\mvnw.cmd clean spring-boot:run
```

Si todo va bien, en terminal veras:

- `Tomcat started on port 8080`
- `Started ServerApplication`

## 3. Importar pruebas en Postman

Importa esta coleccion (JSON):

- `postman/collections/RummiPlus-API-Demo.postman_collection.json`

Variable importante:

- `baseUrl = http://localhost:8080`

## 4. Orden recomendado de pruebas

Ejecutar en este orden:

1. `1. Jugadores`
2. `2. Partidas`
3. `3. Participaciones`
4. `4. Amigos`
5. `6. Flujo 4 Movimientos`
6. `5. Errores de Validacion (demo)`

Notas:

- Si `Crear Jugador 1/2` devuelve `409`, significa que ya existe ese id (comportamiento correcto).
- En Partidas ya existe `conjuntoMesa` en create/update.

## 5. Endpoints principales para frontend

### Jugadores

- `GET /api/jugadores`
- `POST /api/jugadores`
- `PATCH /api/jugadores/{id}/perfil`

### Partidas

- `GET /api/partidas`
- `GET /api/partidas/{id}`
- `POST /api/partidas`
- `PUT /api/partidas/{id}`

`Partida` incluye:

- `idPartida`
- `turno`
- `fecha`
- `mercado`
- `bolsa`
- `conjuntoMesa`
- `corriendo`

### Participaciones

- `GET /api/participaciones?partidaId={id}`
- `POST /api/participaciones`
- `PUT /api/participaciones/{idJugador}/{idPartida}`

### Amigos

- `GET /api/amigos?jugadorId={id}`
- `POST /api/amigos`
- `PATCH /api/amigos/{jugador1Id}/{jugador2Id}/estado`

## 6. Ejemplo rapido desde frontend

```javascript
const res = await fetch("http://localhost:8080/api/partidas/5001");
const data = await res.json();
console.log(data);
```

Ejemplo update de partida con `conjuntoMesa`:

```javascript
await fetch("http://localhost:8080/api/partidas/5001", {
  method: "PUT",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    turno: 2,
    fecha: "2026-03-31",
    mercado: "obj3,obj4",
    bolsa: "f4,f5,f6",
    conjuntoMesa: "[B01,B02,B03]|[R05,R06,R07]|[O08,O09,O10]",
    corriendo: true
  })
});
```

## 7. Errores comunes y solucion

### Error: `Port 8080 was already in use`

Hay otra app en 8080.

```powershell
Get-NetTCPConnection -LocalPort 8080 | Select-Object OwningProcess,State
Stop-Process -Id <PID> -Force
```

### Error: no conecta a PostgreSQL

Comprobar contenedor:

```powershell
docker ps
```

Debe aparecer `rummiplus-postgres` con `0.0.0.0:5432->5432`.

### Error 400 / 409 en Postman

- `400`: payload invalido (campos faltantes/formato mal).
- `409`: conflicto de negocio (duplicado, etc.).

## 8. (Opcional) IA del bot

Solo necesario si vais a usar endpoints de bot:

- `GET /api/bot/health`
- `POST /api/bot/move`

El servicio Python de IA debe estar levantado en `http://127.0.0.1:8765`.

