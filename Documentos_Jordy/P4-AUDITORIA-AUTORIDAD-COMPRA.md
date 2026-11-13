# P4 - Auditoria de autoridad de compra

Fecha: 2026-11-13
Estado: P4 cerrado en codigo y pruebas locales; pendiente unicamente de configurar y validar la URL real de auth en el entorno de despliegue.

## Objetivo

Evitar que un cliente autenticado pueda alterar precio, empresa o costo de envio manipulando la peticion HTTP. Se conservan las reglas actuales: impuesto del 15%, estados, metodos de pago, flujo de pagos e inventario.

## Hallazgo original

`VentaApplicationService` construia los productos con `nombreProducto` y `precioUnitario` enviados por el navegador. `VentaMapper` usaba esos datos para calcular subtotal, impuesto y total. Ademas, `VentaMapper` aceptaba `costoEnvio` del cliente y `emprendedorId` del cliente, con fallback fijo a `1L`.

## Correccion implementada

- Ventas consulta `ProductoClient` por cada `productoId`.
- El precio y nombre usados en el detalle provienen de `msvc-producto`; los valores enviados por el navegador se ignoran.
- El DTO interno de producto incorpora la empresa. La venta rechaza items de empresas distintas y usa la empresa consultada, no el `emprendedorId` recibido.
- Ventas consulta el costo de envio mediante `ConfiguracionPagosClient` al endpoint propietario de configuracion en `msvc-auth-service`/`demo`.
- El `costoEnvio` del navegador se ignora. El servicio propietario conserva la tarifa configurada y su fallback vigente de `$5.00`.
- `VentaMapper` mantiene el calculo de subtotal, impuesto del 15%, envio y total.
- No se cambiaron estados, metodos de pago, Stripe, inventario ni Redis.
- Se agrego `app.msvc-auth-service.url=http://localhost:8084` como valor local predeterminado; en despliegue debe apuntar al servicio equivalente.
- Los dos Docker Compose ahora exigen `MSVC_AUTH_SERVICE_URL` y lo inyectan como `APP_MSVC_AUTH_SERVICE_URL`; no se invento una direccion porque el compose actual no incluye el servicio auth.
- Los errores Feign y genericos ya no devuelven URLs, puertos ni mensajes internos. El detalle queda solamente en el log del servidor.
- Se configuraron timeouts Feign de 3 segundos para conexion y 5 segundos para lectura.
- `facturas/` quedo excluido del control de versiones.

## Compatibilidad

`precioUnitario` sigue aceptandose en el request para no romper de inmediato al frontend existente, pero ya no es una fuente de autoridad. La migracion futura puede eliminarlo del contrato y dejar solo `productoId` y `cantidad`.

La politica de producto activo no se agrego en esta etapa para no cambiar reglas comerciales actuales sin confirmacion. La validacion de stock atomica queda para la auditoria especifica de `msvc-inventario`.

## Validacion

- Compilacion Maven de `msvc-ventas`: correcta.
- La suite completa de integracion se ejecuto correctamente.
- La prueba `test14_ignoraImportesManipuladosPorCliente` pasa: envia precio `$0.01`, envio `0` y empresa `9999`, pero conserva precio oficial, tarifa `$5.00` y empresa consultada.
- No se ejecutaron ventas contra produccion ni se modifico ninguna base real.

## Validaciones que no requieren un pago real

El flujo de pago no es necesario para comprobar esta correccion. La prueba
anti-manipulacion usa H2, mocks de Productos y ConfiguracionPagos, y verifica el
calculo y los datos persistidos sin llamar a Payphone, Stripe ni a una base de
produccion.

Las siguientes comprobaciones quedan pendientes de infraestructura, no de logica
de codigo:

- probar conectividad desde el contenedor de Ventas hacia auth y Productos;
- apagar temporalmente un servicio en un entorno local o staging y comprobar la
  respuesta 503 generica;
- revisar logs del contenedor durante un fallo controlado;
- confirmar con el administrador el valor real de `MSVC_AUTH_SERVICE_URL`.

Para despliegue, el `.env` del servidor debe contener, por ejemplo:

```dotenv
MSVC_AUTH_SERVICE_URL=http://HOST_O_SERVICIO_AUTH:8084
```

Dentro de Docker no debe usarse `localhost:8084` salvo que auth se ejecute en el
mismo contenedor de Ventas, lo cual no corresponde al diseño actual.

## Pendientes fuera del alcance local

- Confirmar `MSVC_AUTH_SERVICE_URL` con el administrador.
- Probar conectividad entre contenedores en staging o produccion.
- Ejecutar una prueba de error controlada con un microservicio no disponible.
- Revisar los logs del contenedor durante esa prueba, sin exponerlos al cliente.

## Cierre P4

P4 se considera cerrado para avanzar al trabajo local de P5. No se requiere
ejecutar un pago real para validar esta etapa: la autoridad de precios, empresa,
envio y calculos fue comprobada con pruebas aisladas y la suite completa de
Ventas.

## Prompt de continuacion

> Completa P4 sin cambiar las reglas comerciales actuales. Corrige los tests de msvc-ventas, agrega una prueba que intente alterar precio, empresa y costo de envio, y verifica que se usan los valores devueltos por msvc-producto y el servicio de configuracion. Ejecuta la suite completa, revisa el diff y no ejecutes escrituras en produccion, no integres Redis y no elimines Stripe.
