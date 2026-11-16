# Registro de correcciones posteriores T07, T08 y T09

## Cambios

1. Página de creación de categorías: la comprobación de administrador se realiza después del montaje mediante `useEffect`, y el retorno condicional (`return null`) se ubica **después de todos los hooks**, inmediatamente antes del JSX. Así el HTML del servidor y el primer render del cliente coinciden y React ejecuta siempre los mismos hooks.
2. Se añadió un límite Nginx específico de 10 solicitudes por minuto, con ráfaga de 5, para login y recuperación de contraseña. El resto de la API de autenticación conserva su límite general.
3. Se reemplazaron los comodines CORS de demo, msvc-producto y msvc-ventas por la variable `APP_CORS_ALLOWED_ORIGINS`. El valor local predeterminado es `http://localhost:3000`.
4. Los cuatro microservicios usan `server.forward-headers-strategy=native` (ver revisión, punto B).
5. `tienda.prod.conf` ya no emite cabeceras CORS en ninguna ruta (hallazgo 5 de la revisión).

## Revisión de las correcciones (2026-11-16)

**A. Hooks de categorías: la primera versión no corregía el problema.** El `return null` seguía antes de los `useState` del formulario (`next lint`: `react-hooks/rules-of-hooks` en las líneas 37, 43 y 44). Además empeoraba el caso del administrador: el primer render (antes de `useEffect`) devolvía `null` tras 4 hooks y el siguiente ejecutaba 7, por lo que React lanzaba *"Rendered more hooks than during the previous render"* y la página no abría. Se movió la guarda debajo de todos los hooks. También se escaparon las comillas de la línea de ejemplo (`react/no-unescaped-entities`, error previo que hace fallar `next build`). `next lint` sobre el archivo: sin errores.

**B. CORS sin comodín rompía producción sin la variable.** Spring trata una petición como CORS cuando el `Origin` no coincide con esquema, host y puerto de la petición recibida. Detrás de NGINX el servicio recibe `http://tienda.gadmsigchos.gob.ec:444` mientras el navegador envía `Origin: https://tienda.gadmsigchos.gob.ec:444`; el esquema no coincide, la petición se evalúa como CORS y, con el valor por defecto `http://localhost:3000`, todo POST/PUT desde el navegador (incluido el login) recibiría `403 Invalid CORS request`. Antes funcionaba solo gracias al comodín.

Corrección: `server.forward-headers-strategy=native` en demo, producto, inventario y ventas. Tomcat toma el esquema y el host de `X-Forwarded-Proto` / `X-Forwarded-Host` (que NGINX ya envía), **solo si la conexión viene de un proxy interno** (127.0.0.1 o redes privadas, incluida la red de Docker). La petición del mismo origen deja de considerarse CORS y no hace falta agregar el dominio público a `APP_CORS_ALLOWED_ORIGINS`. `APP_CORS_ALLOWED_ORIGINS` queda documentada en `.env.example`.

**C. Límites de autenticación (NGINX):** correctos en ambos archivos. `location = /api/auth/login` (coincidencia exacta) y la regex de `recuperar-password` tienen prioridad sobre el prefijo `/api/auth`; cubren `/solicitar`, `/verificar` y `/restablecer`. En producción, al no definir `add_header` propios, heredan las cabeceras de seguridad del bloque `server`. Efecto a considerar: 10 intentos por minuto **por IP**; si muchos usuarios salen por la misma IP pública (oficina con NAT), pueden recibir 429 en horas de alta concurrencia.

**D. Hallazgo 5, CORS en `tienda.prod.conf`:** producción es mismo origen, por lo que se eliminó el `map $prod_cors_origin` (aceptaba cualquier subdominio de `gadmsigchos.gob.ec` con credenciales) y las cabeceras CORS de `/api/auth`. Todas las rutas siguen ocultando las cabeceras CORS de los microservicios. Al quitar sus `add_header`, `/api/auth` hereda las cabeceras de seguridad del bloque `server` (antes las duplicaba). El Gateway Docker (`nginx.conf`) conserva su CORS porque en local el frontend (:3000) y el Gateway están en orígenes distintos.

## Validación

- `next lint` sobre la página de categorías: sin errores.
- `npx tsc --noEmit`: correcto. `npm test`: 41/41.
- msvc-inventario 48/48, msvc-ventas 71/71, demo 73/74 y msvc-producto 44/45. La prueba que falla en demo y en producto es la que arranca el contexto completo y requiere MySQL (*Communications link failure*); fallaba igual antes de estos cambios.
- `git diff --check`: correcto.
- No se ejecutó `nginx -t` (no hay NGINX en el equipo de desarrollo).

## Pendiente operativo

- Ejecutar `nginx -t` en el servidor y en el contenedor del Gateway.
- Confirmar que los puertos 8081 a 8084 no estén expuestos externamente (escucha en 127.0.0.1 o firewall). `forward-headers-strategy=native` solo confía en proxies internos, pero un puerto expuesto igualmente se salta el rate limiting y los bloqueos del Gateway.
- Tras desplegar, probar el login desde `https://tienda.gadmsigchos.gob.ec:444` y revisar que no haya `403 Invalid CORS request`.
- `APP_CORS_ALLOWED_ORIGINS` solo debe ampliarse si algún cliente legítimo usa un origen distinto (por ejemplo, un túnel de pruebas).
