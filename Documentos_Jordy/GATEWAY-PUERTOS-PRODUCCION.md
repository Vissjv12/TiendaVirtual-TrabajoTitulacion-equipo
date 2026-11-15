# Puertos del Gateway en producción

## Mapa de tráfico

- El navegador entra por `https://tienda.gadmsigchos.gob.ec:444`, que es el puerto HTTPS público observado en el servidor.
- El Nginx frontal del servidor conserva TLS y reenvía las rutas `/api/` al Gateway en `http://127.0.0.1:8080`.
- El Gateway escucha en `8080` de forma interna. `docker-compose.gateway.yml` lo publica solo en loopback para que no quede expuesto directamente a Internet.
- Para desarrollo local se conserva `http://localhost:8080`.

## Variables del frontend

Copiar `.env.production.example` a `.env.production` antes de compilar el frontend. `NEXT_PUBLIC_GATEWAY_URL` se incorpora al bundle del navegador; `GATEWAY_INTERNAL_URL` configura los rewrites del servidor Next.js. No usar `localhost` como URL pública del navegador en producción.

## Nginx frontal

Dentro del `server` HTTPS que escucha en `444`, la ruta API debe apuntar al Gateway local:

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Revisar la configuración vigente y reemplazar la ubicación `/api/` existente; no crear dos bloques `location` iguales. Mantener el Gateway accesible solo desde el host y verificar `/health` local y una ruta `/api/` a través de HTTPS antes de aceptar el despliegue.

## Límite del cambio

Esta corrección alinea las URLs y el mapeo de puertos del repositorio. No instala Docker ni cambia `systemd` o Nginx en el servidor. Las salidas previas indicaron que el servidor opera con `systemd` y no tenía Docker/Podman disponibles; por eso `docker-compose.gateway.yml` no podrá ejecutarse allí hasta que exista un runtime autorizado o se prepare una unidad de servicio compatible. No ejecutar en producción el Compose general que crea otro contenedor MySQL.
