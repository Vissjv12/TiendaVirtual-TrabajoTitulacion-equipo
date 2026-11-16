# Puertos del Gateway en local y producción

## Qué hace el Compose

El archivo docker-compose.gateway.yml ejecuta un contenedor NGINX para el API Gateway. Su mapeo publica solo en loopback, con la forma 127.0.0.1:<GATEWAY_PORT>:8080; si no se define GATEWAY_PORT, el host usa 8080. El puerto 8080 dentro del contenedor es fijo; GATEWAY_PORT cambia el puerto de acceso desde el host.

La configuración del contenedor enruta al backend en los puertos 8081–8084 mediante host.docker.internal. El servicio /health confirma la disponibilidad del Gateway. El Compose no edita el NGINX instalado en el host, no instala un runtime de contenedores, no modifica unidades systemd ni configura certificados. La prueba realizada en esta revisión fue local, aislada y temporal.

## Estado observado en el servidor el 15 de noviembre de 2026

El NGINX del servidor pasa su prueba de sintaxis y escucha en los puertos 80 y 444; el HTTPS público usa :444. Las ubicaciones observadas llevan rutas conocidas directamente a servicios locales, por ejemplo productos/categorías/empresas a 8081 e inventario a 8082; no hay una ubicación general /api/ que envíe las solicitudes al Gateway. La ubicación de autenticación usa localhost:8080, así que el puerto predeterminado del Compose podría entrar en conflicto con el servicio que ya atiende ahí.

En el servidor no están instalados Docker ni Podman. Por tanto, el Gateway todavía no está ejecutándose allí y NGINX no se ha conectado a él. La reconsulta SSH posterior falló por resolución DNS, así que estas observaciones deben verificarse de nuevo antes de una ventana. La consulta de memoria reportó 5.5 GiB totales, 3.8 GiB usados y 1.7 GiB disponibles; hay 3.9 GiB de swap, con 11 MiB usados.

## Ruta prevista si se decide desplegar el Gateway

1. Confirmar con el responsable si se instalará un runtime en el servidor o se usará una unidad de servicio compatible con el entorno actual.
2. Consultar en el host que un puerto elegido esté libre. No asumir 8080; la configuración de Auth ya apunta a ese puerto. GATEWAY_PORT=8090 es solo un candidato hasta comprobarlo.
3. Publicar el contenedor únicamente en 127.0.0.1 y comprobar desde el host /health y una lectura API.
4. En el bloque HTTPS de NGINX, reemplazar las ubicaciones actuales que correspondan y enviar /api/ al puerto loopback elegido. No dejar rutas duplicadas ni exponer directamente el Gateway.
5. Ejecutar nginx -t antes de recargar NGINX y probar login, productos, categorías y las rutas que sigan habilitadas.

Esta revisión no cambió el Compose, el NGINX del servidor ni systemd. La configuración propuesta sigue siendo de despliegue; la comprobación local no demuestra que el servidor esté listo.
