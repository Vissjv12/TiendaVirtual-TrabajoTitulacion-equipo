# Estado de avance: cronograma, plan de etapas y plan de cambios

| Dato | Valor |
|---|---|
| Fecha | 2026-11-15 |
| Rama evaluada | `integration/version-estable-deuna-pulido` (commit `432f92b`), cargada en local |
| Método | Contraste entre documentación de planificación, registros de avance y código. **No se ejecutó el sistema.** |
| Documento complementario | `REVISION-CODIGO-INTEGRACION-A15-A25-Y-PULIDO.md` (hallazgos de código C-xx, A-xx, M-xx y B-xx, y guía de pruebas manuales) |

**Fuentes consultadas**

| Tipo | Documento |
|---|---|
| Cronograma | `Cronograma_mejoras_sistema.xlsx` (7 semanas, A-01 a A-25, hitos H-01 a H-08) |
| Plan de etapas y ramas | `Plan_de_trabajo_etapas_y_ramas.docx`, `PLAN-IMPLEMENTACION-SISTEMA-V2 (1).docx` |
| Plan de cambios propio | `PLAN-PAQUETE-PRE-REDIS-Y-MEJORAS.md` (P0–P8), `CIERRE-ETAPA-P4-P7-Y-ENTREGA-BASE.md` |
| Plan rumbo a producción | `REVISION-FLUJO-SEGURIDAD-Y-TAREAS.md` (decisiones D-01 a D-06, hallazgos H-01 a H-17, tareas T00–T15) |
| Registros de avance | `REGISTRO-ETAPA-1-A01-A06.md`, `REGISTRO-SEGURIDAD-A08-A10.md`, `REGISTRO-ETAPA-4-GATEWAY-A11-A14.md`, `RESUMEN-ESTADO-PROYECTO-Y-PLAN-CONTINUACION.md`, `HALLAZGOS-REVISION-SERVIDOR-PRODUCCION.md` y los registros de las ramas de los compañeros |

> **Nota sobre la numeración:** los hitos del cronograma (H-01 a H-08) y los hallazgos de seguridad del plan T00–T15 (también H-01 a H-17) comparten prefijo. En este documento, los hitos se citan como **hito H-0X** y los hallazgos como **hallazgo H-XX**.

---

## 1. Conclusiones principales

1. **El código va adelantado respecto al calendario, pero los hitos no están cerrados.** Las 25 actividades del cronograma tienen código o documento, cuando según el calendario solo correspondían las de las semanas 1 y 2. Sin embargo, de los 8 hitos, solo el hito H-01 está cumplido. El resto tiene validaciones, autorizaciones o pruebas pendientes.
2. **El plan rumbo a producción (T00–T15) está mayormente sin ejecutar.** De 13 tareas previas al despliegue: 3 están hechas (T03, T04, T12), 1 es informativa (T00), 2 son parciales (T07, T08) y 7 no se han hecho (T01, T02, T05, T06, T09, T10, T11, T13).
3. **Hay cambios que contradicen decisiones ya tomadas por el equipo:** Stripe (D-01), la edición de productos por el administrador (D-03), los permisos sobre categorías y empresas (T07) y el orden de Redis (D-06). Deben resolverse antes de seguir corrigiendo.
4. **Hay una regresión funcional nueva.** Desde A-24, el cierre automático de ventas pagadas con PayPhone devuelve 403 y la venta queda `PENDIENTE` sin aviso. Esto convierte a T05 en prioritaria.
5. **La premisa de T10 cambió.** El reconocimiento del servidor (T00) muestra que producción corre con servicios **systemd, no con Docker**. Hay que decidir si se migra a Docker o se despliega sobre systemd.
6. **El flujo de ramas del plan no se aplicó.** No existen `etapa/5..7`, `develop` está desactualizado, no hay `release/corte-*` y no hay evidencia de PR con revisión cruzada.

---

## 2. Avance según el cronograma

### 2.1 Ubicación en el calendario

El cronograma no fija fechas. Se toma como inicio el **2026-11-03**, fecha de los primeros commits A-02 y A-04. Con ese supuesto, al 2026-11-15 el proyecto cierra la **semana 2 de 7**, y la semana 7 terminaría hacia el 2026-12-20. `RESUMEN-ESTADO-PROYECTO-Y-PLAN-CONTINUACION.md` §8 ya aceptó que el calendario es una guía de orden y que las fechas se replanifican por etapa.

| Semana | Planificado | Ejecutado (fecha de commits) |
|---|---|---|
| 1 | A-01, A-02, inicio de A-08 | A-01 a A-06 (3–4 nov), A-08 a A-10 documentales (6 nov) |
| 2 | A-03, A-06, A-08, A-09 | Gateway A-11 a A-14 (8–9 nov), P4–P7 (13 nov), Redis, paginación, A-24/A-25 y pulido (14–15 nov) |
| 3 a 7 | A-04 a A-25, validación integral y despliegue gradual | — |

### 2.2 Estado por actividad

Leyenda:
- **Hecho:** implementado y con evidencia.
- **Hecho (local):** con evidencia solo en local.
- **Parcial:** falta una parte del resultado esperado.
- **Pendiente.**

| ID | Actividad | Estado | Evidencia y pendiente |
|---|---|---|---|
| A-01 | Inventariar recursos y permisos | Hecho | `A01-INVENTARIO-RECURSOS-Y-PERMISOS.md` |
| A-02 | Respaldo y ambiente de pruebas | Hecho (local) | Respaldo restaurable y ambiente aislado (`docker-compose.test.yml`). El respaldo de **producción** es T01 y está pendiente. |
| A-03 | Autorización por empresa en backend | Parcial | Productos, ventas y salida de inventario validan la empresa. Las escrituras generales de inventario no lo hacen (revisión A-01). |
| A-04 | Frontend según alcance autorizado | Hecho | Manejo de 401 y 403; botón "Gestionar" por empresa. |
| A-05 | Pruebas de aislamiento horizontal | Parcial | Pruebas unitarias. La prueba por HTTP con dos emprendedores reales sigue pendiente (`REGISTRO-ETAPA-1-A01-A06.md`). |
| A-06 | Proteger rutas del panel administrativo | Hecho | Layout de `/administrador` protegido; las rutas internas se corrigieron en el pulido. |
| A-07 | Validar autorización contra la API | Hecho (local) | `pruebas-seguridad-a07.ps1`. |
| A-08 | Retirar secretos versionados | Hecho | Variables en `.env`; `.env.example` sin valores. |
| A-09 | Rotar credenciales comprometidas | Pendiente | Preparada en documentos; requiere autorización (es T15). |
| A-10 | Actualizar hashes de contraseñas | Pendiente | Preparada en documentos, sin ejecución irreversible. |
| A-11 | Diseñar estrategia de gateway | Hecho | `A11-ESTRATEGIA-Y-DISENO-GATEWAY.md` |
| A-12 | Implementar gateway en pruebas | Hecho (local) | NGINX en `docker-compose.gateway.yml`. |
| A-13 | Migrar frontend al gateway | Hecho | El frontend usa `GATEWAY_BASE_URL`. |
| A-14 | Validar seguridad y disponibilidad del gateway | Parcial | 43/43 en su momento. El script quedó **desactualizado** tras A-24 (espera 200 sin token en `/api/ventas`). Puertos de producción pendientes (T09). |
| A-15 | Evaluar alternativas de arquitectura | Hecho | Piloto de lectura; stock y carrito fuera de Redis. |
| A-16 | Diseñar modelo Redis | Hecho | Claves, TTL e invalidación definidos. |
| A-17 | Piloto Redis | Hecho (local) | Desactivado por defecto. No cubre la vitrina paginada (revisión M-01). |
| A-18 | Evaluar migración amplia | Parcial | Mediciones k6 solo locales; prueba institucional y aprobación de campos públicos pendientes. |
| A-19 | Contrato de paginación | Hecho | `A19-CONTRATO-PAGINACION.md`; implementación idéntica en los 4 servicios. |
| A-20 | Paginación backend | Hecho | 18 endpoints `/paginado`. |
| A-21 | Paginación frontend | Hecho | DataTable `lazy` en 17 vistas. |
| A-22 | Validar rendimiento paginado | Parcial | Solo pruebas unitarias; siguen los listados sin paginar. |
| A-23 | Precios sin iniciar sesión | Hecho | Falta el DTO público de empresa sin RUC ni contacto (hallazgo H-15). |
| A-24 | Exigir autenticación para comprar | Hecho | Resource Server JWT en Ventas. Ver la regresión de PayPhone en §5. |
| A-25 | Validar separación pública y privada | Parcial | Pruebas con mocks; falta la validación directa contra la API y el gateway. |

### 2.3 Estado de los hitos

| Hito | Previsto | Condición del cronograma | Estado | Qué falta para cerrarlo |
|---|---|---|---|---|
| H-01 Seguridad inicial | Semana 1 | Respaldo restaurable y matriz de permisos aprobada | **Cumplido (local)** | — |
| H-02 Aislamiento y roles | Semana 3 | Emprendedores y clientes no acceden a recursos ajenos | **Abierto** | A-05 por HTTP; autorización por propiedad en todas las escrituras de inventario; restringir los campos de empresa que edita el emprendedor. |
| H-03 Configuración segura | Semana 3 | Sin secretos operativos; credenciales de prueba funcionando | **Abierto** | A-09 y A-10 (requieren autorización). |
| H-04 Gateway operativo | Semana 5 | Rutas por gateway; puertos internos no expuestos | **Parcial** | T09 (puertos, rate limiting, bloqueos) y actualizar el script A-14. |
| H-05 Consultas optimizadas | Semana 5 | Listados paginados con límites | **Casi cumplido** | Decidir qué hacer con los listados antiguos sin paginar. |
| H-06 Decisión Redis | Semana 6 | Evidencia del piloto y recomendación | **Parcial** | Prueba institucional, decisión sobre la vitrina paginada y aprobación de campos. |
| H-07 Catálogo y compra | Semana 6 | Precios públicos y compra protegida | **Casi cumplido** | Corregir el cierre de PayPhone (T05). |
| H-08 Liberación | Semana 7 | Seguridad, regresión y rendimiento aprobados | **No iniciado** | Regresión integral (P8), rama `release`, plan de reversión. |

---

## 3. Avance del plan de cambios propio (P0–P8)

| Paso | Estado |
|---|---|
| P0 Congelar base y línea base | Hecho |
| P1 Configuración y referencias obsoletas a Stripe | Hecho en su alcance. El retiro completo de Stripe es T06 y está pendiente. |
| P2 Caracteres especiales | Hecho; queda verificar en producción (`CIERRE-ETAPA-P4-P7`, §6). |
| P3 Precios públicos | Hecho (equivale a A-23 y T12). |
| P4 Autoridad del servidor en la compra | Hecho; se conserva en la rama nueva. |
| P5 Endurecimiento de errores y logs | Hecho; se conserva en la rama nueva. |
| P6 Línea base sin Redis | Hecho |
| P7 Puerta de entrada para Redis | Hecho; los compañeros avanzaron A-17 y A-18 sobre esa base. |
| **P8 Regresión y entrega de etapa** | **Pendiente.** Estaba previsto ejecutarlo "cuando los compañeros terminen sus cambios", y esa condición se cumple con la rama cargada. |

---

## 4. Avance del plan rumbo a producción (T00–T15)

| Tarea | Prioridad | Estado | Situación en el código (`432f92b`) |
|---|---|---|---|
| T00 Reconocimiento del servidor | Bloqueante | **Hecho (informativo)** | `HALLAZGOS-REVISION-SERVIDOR-PRODUCCION.md`: AlmaLinux con servicios systemd (Nginx, MySQL, Eureka y los 4 microservicios); Docker no instalado. Hay hallazgos de SSL, SSH y firewall pendientes de autorización. |
| T01 Respaldo de producción | Bloqueante | **Pendiente** | No existen `scripts/prod/backup.sh`, `restore-verify.sh` ni la guía de respaldo. |
| T02 PayPhone privado | Crítica | **Pendiente** | `SecurityConfig.java:48` (Auth) mantiene `permitAll` sobre `/payphone/*`; no existe `GET /api/ventas/{id}/payphone-config`. |
| T03 Precio y envío en el servidor | Crítica | **Hecho** | P4. |
| T04 Seguridad en Ventas (A-24) | Crítica | **Hecho** | Matriz de `@PreAuthorize`; Camunda y BPM solo para ADMIN. |
| T05 Confirmación del pago en el servidor | Crítica | **Pendiente, con regresión** | El frontend sigue llamando a `/completar` desde el cliente (`cartService.ts:541`, `ventasService.ts:224`). Ver §5. |
| T06 Retirar Stripe y pago simulado | Alta | **Pendiente, en sentido contrario** | Siguen `StripeController`, `stripeService.ts`, `StripeCheckoutForm.tsx`, la ruta `/api/stripe` del gateway y `procesarPagoSimulado` en `pagoService.ts`. La rama nueva reforzó la seguridad de Stripe. |
| T07 Autorización de producto e inventario | Alta | **Parcial, con contradicción** | Hecho: `salida-lote` y la edición directa del stock validan propiedad. Pendiente: las demás escrituras de inventario, `POST /api/movimientos` y `GET /api/movimientos/**` (público). Contradicción: T07 pide categorías y empresas solo para ADMIN, pero el emprendedor puede crear categorías y editar su empresa. |
| T08 Superficie y logs | Alta | **Parcial** | Hecho: `show-sql=false`, el conteo de `ROLE_EMP`, y los logs de PayPhone ya no imprimen el token. Pendiente: Swagger y `/api-docs` siguen públicos (no existe `springdoc.api-docs.enabled`) y quedan 10 `@CrossOrigin`. |
| T09 Gateway de producción y rate limiting | Alta | **Pendiente** | `gateway/nginx.conf` no tiene `limit_req`, cabeceras de seguridad, ni bloqueo de `/engine-rest`, `/camunda` o `/swagger-ui`. |
| T10 Despliegue Docker de producción | Bloqueante para desplegar | **Pendiente; premisa a revisar** | No existen el `Dockerfile` de Auth ni `docker-compose.prod.yml`. El servidor real no usa Docker (T00). |
| T11 Vincular emprendedor y empresa | Alta (negocio) | **Pendiente** | No hay endpoints ni pantalla de vinculación. |
| T12 Precios públicos (A-23) | Alta (negocio) | **Hecho, salvo un punto** | Falta el DTO público sin RUC, email ni teléfono de la empresa (hallazgo H-15; revisión M-03). |
| T13 Admin sin edición de productos | Alta (negocio) | **Pendiente, en sentido contrario** | El pulido habilitó al administrador para crear productos y gestionarlos todos. |
| T14 Cifrado de credenciales PayPhone | Media (futuro) | Pendiente previsto | Después de T00 y T01. |
| T15 Rotación de credenciales (A-09) | Media (futuro) | Pendiente previsto | Después del despliegue, con autorización. |

### Estado de los hallazgos de seguridad H-01 a H-17

| Resueltos | Parciales | Abiertos |
|---|---|---|
| H-02 (precio y envío), H-04 (Ventas sin autenticación), H-05 (Camunda y BPM), H-16 (conteo de roles) | H-03 (aprobar y rechazar pagos protegidos, pero el cierre PayPhone no se hace en el servidor), H-07 (escrituras autenticadas, no solo admin), H-08 (salida y stock protegidos, resto no), H-12 (sin tokens en logs; falta suprimir `console.log` en producción) | H-01 (PayPhone público), H-06 (puertos), H-09 (movimientos públicos), H-10 (Swagger), H-11 (rate limiting), H-13 (pago simulado), H-14 (`@CrossOrigin`), H-15 (datos de empresa), H-17 (token en texto plano) |

---

## 5. Regresión detectada: cierre de ventas pagadas con PayPhone

- **Antes de A-24:** después de confirmar el pago, `payphone-confirmacion/page.tsx:85` llamaba a `cartService.completarVenta`, que ejecuta `PUT /api/ventas/{id}/completar` con el token del cliente. Ventas no tenía seguridad, así que la venta pasaba a `COMPLETADA`. Era inseguro (hallazgo H-03), pero funcionaba.
- **Después de A-24:** `/completar` exige `ROLE_ADMIN` o el emprendedor dueño, y el cliente recibe 403. El frontend lo trata como aviso (`cartService.ts:541-560`) e informa "Venta creada exitosamente (estado pendiente)".
- **Efecto:** la compra pagada con PayPhone queda `PENDIENTE`, sin pago registrado como aprobado y sin descuento de stock. Solo se cierra si el emprendedor la completa a mano, y nada le indica que debe hacerlo.
- **Solución prevista:** T05. Confirmar con PayPhone desde el servidor (estado, monto e identificador de la venta), completar la venta en el backend de forma idempotente y quitar las llamadas a `/completar` del frontend.

---

## 6. Contradicciones con decisiones del equipo

| Decisión o tarea | Qué se acordó | Qué hay en la rama | Qué hay que decidir |
|---|---|---|---|
| D-01 / T06 | Stripe no se usará; retirarlo junto con el pago simulado. | Stripe se mantiene y se le añadió `@PreAuthorize`; el pago simulado sigue. | Confirmar D-01. Si se mantiene, ejecutar T06; los hallazgos de Stripe de la revisión (A-03, A-04, M-06) desaparecen con el retiro. |
| D-03 / T13 | El administrador no modifica productos (alcance pendiente de confirmar, opción b recomendada). | El administrador crea productos y ve "Gestionar" en todos (`REGISTRO-PULIDO-PRE-INTEGRACION.md` §1.5 y §1.6). | Confirmar el alcance de D-03 y si se revierte el cambio del pulido. |
| T07 | Categorías y empresas: escrituras solo para ADMIN. | El emprendedor crea categorías globales y edita su empresa, incluidos `activo` y RUC (revisión A-02). | Decidir si el emprendedor puede editar su perfil de empresa y con qué campos, y si puede crear categorías. |
| D-06 | Redis en una rama nueva **después** de estabilizar y desplegar. | Redis A-17 y A-18 integrado antes del despliegue. | Aceptar como desviación (es compatible porque está desactivado por defecto) y mantenerlo apagado en el primer despliegue. |
| T10 | Despliegue de producción con Docker Compose. | El servidor usa systemd (T00). | Elegir entre migrar a Docker o desplegar con systemd y adaptar T10. |
| Flujo de ramas (plan §11–13) | `feature → etapa → develop → release`, con PR revisado y evidencia. | Cadena lineal de ramas `feature` e `integration`; no hay `etapa/5..7`, `develop` actualizado ni `release`. | Definir la rama base oficial de la siguiente fase y abrir `release/*` para la validación. |

---

## 7. Plan de continuación recomendado

### Fase 0: Decisiones (sin código)

1. Confirmar D-01 (Stripe), D-03 (administrador y productos), los permisos del emprendedor (T07) y el modelo de despliegue (T10: Docker o systemd).
2. Declarar `integration/version-estable-deuna-pulido` como base de la siguiente fase y registrar como desviaciones aceptadas las de §6 que se mantengan.

### Fase 1: Bloqueantes de seguridad y flujo de pago

| Orden | Tarea | Relación |
|---|---|---|
| 1 | T05: confirmación de PayPhone en el servidor | Corrige la regresión de §5 y el hallazgo H-03. |
| 2 | Resolución de QR Deuna y datos bancarios solo por la empresa correcta | Revisión C-01. |
| 3 | T02: configuración de PayPhone privada | Hallazgo H-01; revisión C-02. |
| 4 | T07 completo: propiedad en todas las escrituras de inventario y movimientos, campos de empresa restringidos, movimientos no públicos | Hallazgos H-08 y H-09; revisión A-01, A-02 y M-05. |
| 5 | T06: retiro de Stripe y del pago simulado (si se confirma D-01) | Hallazgo H-13; revisión A-03, A-04 y M-06. |
| 6 | Facturas generadas después del commit, no en `new Thread` | Revisión A-05. |

### Fase 2: Preparación de producción

| Orden | Tarea |
|---|---|
| 7 | T01: respaldo y restauración verificables |
| 8 | T08 pendiente: Swagger desactivado en producción, retirar `@CrossOrigin`, suprimir `console.log` |
| 9 | T09: rate limiting, cabeceras de seguridad, bloqueos en el gateway y exposición de puertos |
| 10 | T10 adaptado al modelo de despliegue elegido |

### Fase 3: Cierre de etapa

| Orden | Tarea |
|---|---|
| 11 | Actualizar `pruebas-gateway-a14.ps1` con los casos de A-24 (cliente, emprendedor, admin) |
| 12 | Ejecutar la guía de pruebas manuales de `REVISION-CODIGO-INTEGRACION-A15-A25-Y-PULIDO.md` §5 y la prueba A-05 por HTTP |
| 13 | P8 / hito H-08: regresión integral, rama `release/*` y plan de reversión |
| 14 | Despliegue; después T11, T13 (según decisión), T14, T15 (A-09) y A-10 |

### Pendientes de autorización externa

- A-09 y A-10 (rotación de credenciales y hashes).
- Acciones sobre el servidor: SSL, SSH, firewall y prueba institucional de Redis, según `HALLAZGOS-REVISION-SERVIDOR-PRODUCCION.md`.
- Ventana de despliegue y responsable del rollback.

---

## 8. Limitaciones de este análisis

- No se levantó el sistema. Los estados "hecho" se basan en código y en evidencias registradas por el equipo; los marcados "(local)" no tienen validación en producción.
- La ubicación en el calendario depende del supuesto de inicio el 2026-11-03.
- El estado del servidor de producción se toma de `HALLAZGOS-REVISION-SERVIDOR-PRODUCCION.md`; no se revisó el servidor en esta evaluación.
