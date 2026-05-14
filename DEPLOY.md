# Despliegue (Render + Supabase + IA)

Guía mínima para **producción** y para no romper nada al tocar configuración.

## Qué va dónde

| Pieza | Dónde vive | Quién lo configura |
|--------|------------|----------------------|
| API + SPA estática | [Render](https://render.com) (servicio Web) | Variables de entorno en el panel de Render |
| PostgreSQL | [Supabase](https://supabase.com) | Proyecto Supabase → cadena de conexión |
| Bot (Python) | Render (otro Web Service) | URL pública del servicio IA |

**No hace falta** subir secretos ni URLs de producción en `application.properties` en Git: en Render se definen como variables de entorno y Spring las lee al arrancar.

## Variables de entorno en Render (backend)

Obligatorias / habituales:

| Variable | Descripción |
|----------|-------------|
| `DATABASE_URL` | URI de Postgres (Supabase suele dar `postgresql://...` o `postgres://...`). El backend la normaliza a JDBC al arrancar (`DatabaseUrlEnvironmentPostProcessor`). |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Solo si tu cadena **no** lleva usuario y contraseña embebidos; si vienen en la URI, el post-procesador puede rellenarlos solo. |
| `PORT` | Render la inyecta sola; equivale a `server.port`. |
| `SERVER_SSL_ENABLED` | Poner `false` en la nube (TLS lo termina el proxy de Render). En local con HTTPS directo suele ir a `true` vía `application.properties`. |
| `STATIC_PUBLIC_PATH` | En Docker ya va a `/app/public/`; en Render, si el JAR sirve estáticos desde otra ruta, ajustar aquí. |
| `BOT_IA_BASE_URL` | URL base del servicio de IA **sin** barra final, p. ej. `https://ia-xxxx.onrender.com`. Sustituye el default `http://127.0.0.1:8765`. |
| `BOT_IA_TIMEOUT_MS` | Opcional; en gratis conviene algo alto (p. ej. `30000`) por cold start del servicio IA. |

Si falta `DATABASE_URL` o es inválida, la app **no arranca** o no conecta a la BD: eso no se “arregla” cambiando solo el `application.properties` en el repo sin definir la variable en Render.

## ¿Puedo cambiar puerto y URL en `application.properties` sin joder producción?

- **Producción (Render)** no lee “el último commit” de esas líneas como única fuente de verdad: manda lo que tengas puesto en **Environment** del servicio. Los placeholders del tipo `${PORT:8080}` y `${DATABASE_URL}` están pensados para eso.
- Si **commiteas** valores fijos (puerto, URL de BD, URL de IA) en el repo, el siguiente deploy **sí** usará esos defaults **salvo** que Render siga sobreescribiendo con variables de entorno (las env vars tienen prioridad sobre el valor por defecto del placeholder **solo si están definidas**).
- **Recomendación:** dejar `application.properties` como plantilla con `${...}` y defaults solo para **desarrollo local**; en Render configurar todo lo sensible por el panel.

## Front (Vite) y API en producción

En el host de producción (`rummiplus.onrender.com`), el front usa **mismo origen** que la API (`/api/...`). No depende de un “puerto raro” en el navegador: es HTTPS en el 443 estándar.

En **local**, el front usa `VITE_API_BASE_URL` (ver `.env.development.example` en `frontend-web`) para apuntar al backend (p. ej. `https://localhost:8443`).

## Servicio IA (Render)

- Arranque típico: `python -m rummiplus.server --host 0.0.0.0 --port $PORT`
- Health check: `GET /api/health`

## Comprobar despliegue

1. Web: `https://<tu-servicio>.onrender.com/`
2. API (ejemplo): `GET https://<tu-servicio>.onrender.com/api/...` (lo que expongáis)
3. IA: `GET https://<tu-servicio-ia>.onrender.com/api/health`

## Plan gratuito Render

Tras ~15 min sin tráfico el servicio **hiberna**; la primera petición puede tardar. No es fallo de puerto ni de URL: es cold start.
