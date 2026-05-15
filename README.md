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
5. Iniciar partida (`POST /api/partidas/{id}/iniciar`), o dejar que se autoarranque al llenarse una publica.
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
- `POST /api/partidas/matchmaking`
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

### Matchmaking publico

- `POST /api/partidas/matchmaking`
  - usa el jugador autenticado del token
  - busca una partida publica `WAITING` compatible por `modoArcade`
  - si existe una con hueco, mete al jugador ahi
  - si no existe, crea una nueva publica
  - devuelve:
    - `creadaNuevaPartida`
    - `partida`
    - `participacion`

Body opcional:

```json
{
  "modoArcade": true
}
```

Nota:

- si no se envia body o `modoArcade=false`, el matchmaking buscara/creara partida publica clasica.

### Otros

- `GET /api/jugadores`
- `POST /api/jugadores`
- `PATCH /api/jugadores/{id}/perfil`
- `GET /api/jugadores/{id}/amigos/perfiles`
- `GET /api/jugadores/{id}/amigos/perfiles?estado=ACEPTADO`
- `GET /api/amigos`
    - tiene campos amigo1 y amigo2, el 1 es el que envÃ­a la solicitud 
- `POST /api/amigos`
- `PATCH /api/amigos/{jugadorId}/{amigoId}/estado`
- `DELETE /api/amigos/{jugadorId}/{amigoId}`
- `DELETE /api/admin/wipe` (unico DELETE)

En jugador se distinguen ahora `skinFichas` y `skinTablero` (en lugar de un unico campo de cosmeticos).
En jugador, el campo de imagen expuesto por la API es `imagenPerfil`.
En amistades, las respuestas incluyen IDs, nombres y estado.
En amistades:

- `PATCH /api/amigos/{jugadorId}/{amigoId}/estado` sirve para aceptar o rechazar una solicitud cambiando su estado.
- `DELETE /api/amigos/{jugadorId}/{amigoId}` elimina la relacion de amistad o la solicitud.
- Tanto `PATCH` como `DELETE` aceptan los ids en cualquiera de los dos ordenes.

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

Formato actual de `eventoActual` en arcade:

- `+pieza`
- `50porcien`
- `prohibido_rojo`
- `prohibido_azul`
- `prohibido_naranja`
- `prohibido_negro`

Nota:

- Backend genera y devuelve esos valores en `eventoActual`.
- La aplicacion visual/funcional del evento puede gestionarse desde frontend.

Al iniciar (`/iniciar`):

- Se genera bolsa completa aleatoria (106 fichas).
- Se reparten 14 fichas por jugador (`manoActual`).
- Se asigna `ordenTurno`.
- Se deja `estado=RUNNING`.

Autoarranque:

- Una partida publica `WAITING` se autoarranca cuando alcanza `4` participaciones.
- Esto aplica tanto si los jugadores entran por:
  - `POST /api/partidas/matchmaking`
  - `POST /api/participaciones`
- Cuando ocurre, backend inicializa:
  - bolsa
  - manos
  - orden de turno
  - `estado=RUNNING`

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

## 10. Estado backend actualizado (mayo 2026)

### 10.1 Funcionalidades ya implementadas

- Invitaciones a partida privada:
  - `POST /api/invitaciones` (emisor por token)
  - `GET /api/invitaciones`
  - `GET /api/invitaciones?idInvitado=...`
  - `GET /api/invitaciones/invitado/{idInvitado}`
  - `DELETE /api/invitaciones/{idEmisor}/{idInvitado}/{idPartida}`
- Partida privada:
  - campo `privada` en `Partida`.
- Arcade:
  - `modoArcade` en `Partida`.
  - mercado habilitado solo en arcade.
- Seguridad:
  - contrasenas hasheadas en alta/login.
  - endpoint de cambio de contrasena:
    - `PATCH /api/jugadores/{id}/contrasena`
    - requiere token del propio jugador.
- Inactividad:
  - se contabiliza `turnosInactivo`.
  - al llegar al limite, el jugador humano se reemplaza automaticamente por bot.
- Estado de partida:
  - endpoint para pausar: `POST /api/partidas/{id}/pausar`
  - endpoint para reanudar: `POST /api/partidas/{id}/reanudar`
    - al reanudar, el backend crea automaticamente una invitacion para cada jugador humano (no bot) que no es el host
    - esos jugadores la reciben en su proximo ciclo de polling de `GET /api/invitaciones`
  - endpoint para finalizar manualmente: `POST /api/partidas/{id}/finalizar`
  - estados actuales: `WAITING | RUNNING | PAUSED | FINISHED`
- Robo:
  - `POST /api/partidas/{id}/solo-robar` acepta `cantidadRobar` opcional.
  - respuesta incluye `fichaRobada` y `fichasRobadas`.
- Partida con fichas por jugador:
  - `PartidaDTO` incluye `fichasPorJugador` (`Map<idJugador, fichasActuales>`).

### 10.2 Invitaciones + partidas a medias en una sola consulta (opcion B)

- Endpoint:
  - `GET /api/invitaciones?idInvitado={id}&includeInProgress=true`
- Devuelve objeto combinado:
  - `invitaciones`: invitaciones recibidas del jugador.
  - `partidasEnCurso`: partidas donde participa y estan en `RUNNING` o `PAUSED`.

#### Flujo completo: reanudar partida con notificacion a jugadores

Este flujo describe como el host reanuda una partida y como los demas jugadores se enteran sin WebSockets.

**Paso 1 — Host reanuda la partida:**

```
POST /api/partidas/42/reanudar
Authorization: Bearer <token-host>
```

Respuesta: partida con `estado: RUNNING`.

Efecto en backend: se crea automaticamente una invitacion (en tabla `INVITACION_PARTIDA`) para cada jugador humano que no es el host.

**Paso 2 — Los demas jugadores estan haciendo polling desde el menu principal:**

```
GET /api/invitaciones?idInvitado=7&includeInProgress=true
```

Respuesta:

```json
{
  "invitaciones": [
    {
      "idEmisor": 3,
      "nombreEmisor": "Alice",
      "idInvitado": 7,
      "idPartida": 42,
      "fechaEnvio": "2026-05-15T10:30:00"
    }
  ],
  "partidasEnCurso": [...]
}
```

**Paso 3 — Frontend detecta la invitacion y muestra el modal "Alice ha reanudado la partida. ¿Volver a jugar?"**

El jugador acepta (o el frontend redirige automaticamente si la invitacion es a una partida donde ya tiene participacion).

**Paso 4 — El jugador vuelve a la partida:**

```
GET /api/partidas/42
```

El estado ya es `RUNNING`, el jugador ve su mano y el tablero actual.

**Paso 5 (opcional) — Borrar la invitacion una vez procesada:**

```
DELETE /api/invitaciones/{idHost}/{idJugador}/42
```

Notas:
- Si la invitacion ya existia (partida pausada y reanudada varias veces), el backend la ignora y no crea duplicados.
- El frontend solo necesita hacer polling de `GET /api/invitaciones` (que ya hacia para invitaciones normales). No se requiere ningun mecanismo adicional.

### 10.3 Modo arcade: codificacion de fichas (alineada con IA)

Formato base de ficha:

- `COLOR + VALOR(2 digitos) + SUFIJOS`

Ejemplos:

- normal: `R07`, `B12`
- dorada: `R07D`
- arcoiris: `O08A`

Reglas de formato:

- Color: `R`, `B`, `O`, `K`
- Valor: `01` a `13`
- Sufijos arcade permitidos: `A` (arcoiris), `D` (dorada)
- Orden canÃ³nico al serializar: `A`, `D`

Notas:

- Las fichas arcoiris (`A`) se tratan como flexibles en validacion de combinaciones arcade.
- Joker clasico sigue siendo `J*`.

### 10.4 Integraciones nuevas de mercado y objetos arcade

Resumen:

- No se ha cambiado el esquema de base de datos para esta parte.
- Se reutilizan:
  - `Partida.mercado`
  - `Participacion.habilidadesActuales`
- El mercado personal de cada jugador en arcade ahora muestra `3` objetos.
- Precios actuales:
  - `MIDAS_TOUCH` cuesta `3`
  - el resto de objetos cuestan `6`
- Los codigos de objeto actuales son:
  - `GUARDIAN_ANGEL`
  - `CRYSTAL_BALL`
  - `MIDAS_TOUCH`
  - `PLUS_FOUR`
  - `SWAP_ON_FAIL`
  - `WHITE_GLOVE`
  - `SMOKE_BOMB`
  - `CHILI_PEPPER`
  - `GLASS_CEILING`

Endpoints nuevos o ampliados:

- `GET /api/partidas/{id}/mercado`
  - devuelve el mercado personal del jugador autenticado.
  - incluye:
    - `monedasJugador` 
    - `objetosMercado`
    - `habilidadesCompradas`
    - `efectosActivos`

- `POST /api/partidas/{id}/mercado/comprar`
  - compra un objeto del mercado personal del jugador autenticado.

- `POST /api/partidas/{id}/mercado/usar`
  - usa un objeto ya comprado.
  - el `idJugador` no se manda en body: se obtiene del token.

Body base para usar objeto:

```json
{
  "codigoObjeto": "MIDAS_TOUCH"
}
```

Campos posibles del body:

- `codigoObjeto`: obligatorio.
- `idJugadorObjetivo`: obligatorio si el objeto afecta a otro jugador.
- `codigoObjetoObjetivo`: se usa en la confirmacion de `WHITE_GLOVE`.
- `fichaPropia`: se usa en la confirmacion de `SWAP_ON_FAIL`.
- `fichaObjetivo`: se usa en la confirmacion de `SWAP_ON_FAIL`.

Respuesta de uso:

- `consumido`
- `bloqueadoPorGuardianAngel`
- `mensaje`
- `manoActual`
- `habilidadesCompradas`
- `efectosActivos`
- `fichasObjetivoVisibles`
- `habilidadesObjetivoVisibles`
- `efectosActivosObjetivo`

Reparto de responsabilidades backend / frontend:

- Objetos con logica funcional real en backend:
  - `CRYSTAL_BALL`
  - `MIDAS_TOUCH`
  - `SWAP_ON_FAIL`
  - `WHITE_GLOVE`

- Objetos que backend registra y expone como efecto, para que frontend los interprete:
  - `GUARDIAN_ANGEL`
  - `PLUS_FOUR`
  - `SMOKE_BOMB`
  - `CHILI_PEPPER`
  - `GLASS_CEILING`

Notas de comportamiento:

- `SWAP_ON_FAIL` y `WHITE_GLOVE` funcionan en dos pasos:
  - primero preview
  - luego confirmacion
- `CRYSTAL_BALL`
  - devuelve las fichas y objetos del jugador objetivo.
- `MIDAS_TOUCH`
  - modifica directamente la mano del actor en backend.
- `GUARDIAN_ANGEL`, `PLUS_FOUR`, `SMOKE_BOMB`, `CHILI_PEPPER` y `GLASS_CEILING`
  - se consumen al usarse
  - se guardan/exponen en `efectosActivos`
  - frontend decide la reaccion visual o funcional a partir de esa informacion
- En participaciones, frontend puede leer ya separados:
  - `habilidadesCompradas`
  - `efectosActivos`

#### Ejemplo de flujo: objeto sobre uno mismo

Caso: `MIDAS_TOUCH`

1. Front consulta inventario/mercado:
   - `GET /api/partidas/15/mercado`
2. Front detecta que el jugador tiene `MIDAS_TOUCH` en `habilidadesCompradas`.
3. Front lanza:

```json
POST /api/partidas/15/mercado/usar
{
  "codigoObjeto": "MIDAS_TOUCH"
}
```

4. Backend consume el objeto y devuelve la mano actualizada en `manoActual`.
5. Front refresca la mano local y elimina el objeto del inventario visual.

#### Ejemplo de flujo: objeto sobre otro jugador

Caso: `PLUS_FOUR`

Request:

```json
POST /api/partidas/15/mercado/usar
{
  "codigoObjeto": "PLUS_FOUR",
  "idJugadorObjetivo": 22
}
```

Resultado esperado:

- backend consume el objeto
- backend anade `PLUS_FOUR` a `efectosActivos` del objetivo
- la respuesta incluye:
  - `consumido=true`
  - `idJugadorObjetivo=22`
  - `efectosActivosObjetivo`
- frontend usa esa informacion para avisar al jugador afectado y aplicar el comportamiento visual o funcional que corresponda

#### Ejemplo de flujo: objeto con preview y confirmacion

Caso: `WHITE_GLOVE`

Paso 1, preview:

```json
POST /api/partidas/15/mercado/usar
{
  "codigoObjeto": "WHITE_GLOVE",
  "idJugadorObjetivo": 22
}
```

Frontend recibe:

- `consumido=false`
- `habilidadesObjetivoVisibles=[...]`

Paso 2, confirmacion:

```json
POST /api/partidas/15/mercado/usar
{
  "codigoObjeto": "WHITE_GLOVE",
  "idJugadorObjetivo": 22,
  "codigoObjetoObjetivo": "SMOKE_BOMB"
}
```

Resultado esperado:

- backend roba exactamente ese objeto al objetivo
- `consumido=true`
- el inventario del actor se actualiza en `habilidadesCompradas`
- frontend actualiza su inventario visual con la respuesta
