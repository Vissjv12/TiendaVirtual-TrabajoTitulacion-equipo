# Guion de presentación: Feria Digital Sigchos

## 1. Feria Digital Sigchos

Presentamos el sistema de comercio electrónico para los emprendimientos del cantón Sigchos. Explicaremos qué ofrece a clientes y emprendedores, cómo funciona internamente y cuáles son los resultados y pendientes documentados.

Fuente: Manual de Usuario e Informe Integral de Pruebas, portada.

## 2. Un espacio digital para los emprendimientos

El sistema busca dar presencia digital a los emprendimientos locales mediante un catálogo común. El visitante puede conocer la oferta y el comprador puede seguir el proceso de compra. Los emprendedores y el personal administrador disponen de herramientas según sus permisos.

Fuente: Manual de Usuario, introducción; Requerimientos técnicos, resumen ejecutivo.

## 3. ¿Quiénes utilizan el sistema?

Hay diferentes perfiles. El visitante puede consultar la tienda. El cliente accede a su cuenta y compra. El emprendedor gestiona la información de su empresa según sus permisos. El administrador tiene funciones de administración y supervisión más amplias.

Fuente: Manual de Usuario, capítulos de cliente y administración; Informe Integral de Pruebas, seguridad por roles.

## 4. Del catálogo a la compra

El recorrido comienza con la consulta y filtrado de productos. El cliente agrega lo que desea al carrito, revisa la compra, ingresa los datos necesarios y selecciona el pago. El resultado se registra con el estado correspondiente. Los pasos detallados están en el manual de usuario.

Fuente: Manual de Usuario, catálogo, carrito, checkout y medios de pago.

## 5. Medios de pago y gestión comercial

La plataforma contempla Payphone, transferencia bancaria y Deuna. Los pagos manuales dependen de un comprobante y una revisión por el responsable. Las credenciales, cuentas y condiciones de operación deben ser confirmadas por la institución antes de la entrega definitiva.

Fuente: Requerimientos técnicos, sección 8; Manual de Usuario, pagos y ventas.

## 6. Arquitectura del sistema

La interfaz usa Next.js. Nginx recibe y dirige las solicitudes hacia los servicios Java y Spring Boot. Los servicios dividen responsabilidades como autenticación, productos, inventario y ventas. MySQL mantiene los datos y Valkey actúa como caché opcional de lecturas del catálogo.

Fuente: Manual técnico, arquitectura y caché; Requerimientos técnicos, inventario.

## 7. Protección por roles y empresa

La seguridad combina controles en el acceso y verificaciones en los servicios. Para operaciones de emprendedores, el identificador de empresa y el rol ayudan a comprobar la autorización. Nginx también limita rutas y protege componentes administrativos. El informe describe las pruebas de esos controles y su alcance.

Fuente: Informe Integral de Pruebas, secciones 2 y 4; Manual técnico, API y controles de acceso.

## 8. Redis apoya lecturas del catálogo

El servicio de productos aplica Cache-Aside a lecturas concretas del catálogo y de categorías. Las claves expiran y se invalidan por generación al cambiar los datos. El inventario se obtiene por separado en tiempo real. Redis no almacena ventas, pagos, sesiones ni el contador de visitas.

Fuente: Manual técnico, capítulo 5; Informe Integral de Pruebas, Redis y alcance.

## 9. Contador de visitas

Cuando el navegador no tiene la cookie de deduplicación, el frontend envía una solicitud. Tras una respuesta correcta crea una cookie de 24 horas. El backend agrega la visita en productos.visitas_diarias. El indicador no mide personas únicas exactas porque pueden cambiar el navegador, dispositivo o cookies.

Fuente: Manual técnico, contador de visitas; Requerimientos técnicos, sección 12.

## 10. Pruebas y resultados documentados

El informe registra 43 casos exitosos en las pruebas del gateway en local. En la comparación externa de Redis se ejecutaron cuatro corridas a 10 solicitudes por segundo durante cinco minutos: 12.003 solicitudes y ningún fallo HTTP en esas corridas. El p95 promedio fue de 100,88 milisegundos con caché y 101,82 sin ella. La diferencia es pequeña frente a la variación y no demuestra una mejora práctica de latencia extremo a extremo.

Fuente: Informe Integral de Pruebas, secciones 2.2 y 5.5; REDIS-EVIDENCIA-PRODUCCION-ON-OFF-2026-11-17.md.

## 11. Operación y entrega institucional

El documento de requerimientos identifica decisiones pendientes sobre dominio, responsables, certificados, correo, pagos y acceso. La revisión del 19 de noviembre no encontró instaladas las unidades de respaldo documentadas en el servidor. Por ello deben instalarse y verificarse, incluyendo una restauración en un ambiente aislado.

Fuente: Requerimientos técnicos, secciones 4, 6, 8, 11 y 15; Manual técnico, respaldo y revisión del 19 de noviembre.

## 12. Documentación para continuar

La entrega se apoya en documentos complementarios. El manual de usuario explica los flujos desde la interfaz. El manual técnico ayuda al personal de TI a configurar y mantener el sistema. El informe de pruebas conserva las condiciones y resultados observados. Los requerimientos técnicos recogen además las decisiones para la puesta en funcionamiento.

Fuente: Manual de Usuario; Manual técnico; Informe Integral de Pruebas; Requerimientos técnicos.
