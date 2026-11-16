# Hallazgos de revisión del servidor de producción

**Fecha de revisión:** 10 de noviembre de 2026  
**Alcance:** inspección inicial posterior al acceso SSH autorizado al servidor de la tienda.  
**Estado:** revisión informativa; no se realizaron cambios en producción.

## 1. Criterios de seguridad utilizados

- Se ejecutaron únicamente comandos de consulta y comprobación.
- No se reiniciaron servicios ni se modificaron archivos.
- No se registran contraseñas, claves privadas, tokens, contenidos de `.env`, datos de clientes ni facturas.
- Las acciones correctivas quedan pendientes de autorización de la responsable del proyecto.

## 2. Estado general observado

El servidor se encuentra operativo y utiliza una arquitectura basada en servicios nativos del sistema, no en Docker.

| Componente | Evidencia | Resultado |
|---|---|---|
| Sistema operativo | Servidor AlmaLinux documentado | Operativo |
| Uptime | Aproximadamente 11 días al momento de la revisión | Sin reinicio reciente |
| Unidades fallidas | `systemctl --failed` | Ninguna reportada |
| Nginx | Servicio activo; sintaxis válida | Operativo |
| MySQL | `mysqld.service` activo | Operativo |
| Eureka | `eureka-server.service` activo | Operativo |
| Auth | `auth-service.service` activo | Operativo |
| Productos | `msvc-producto.service` activo | Operativo |
| Inventario | `msvc-inventario.service` activo | Operativo |
| Ventas | `msvc-ventas.service` activo | Operativo |
| Docker | Comando no disponible | No utilizado en este servidor |

## 3. Pruebas funcionales iniciales

Las pruebas directas locales devolvieron los siguientes resultados:

| Endpoint | Resultado | Interpretación |
|---|---:|---|
| Productos `/api/productos/listado` | 200 | Servicio responde |
| Inventario `/api/inventarios/producto/1` | 200 | Servicio responde |
| Ventas `/api/ventas` | 200 | Servicio responde |
| Auth `/api/usuarios` sin token | 401 | Protección de autenticación activa |
| HTTPS en Nginx | 200 | Punto de entrada HTTPS responde |

Las primeras respuestas `500` obtenidas mediante `HEAD /` no se consideraron fallos concluyentes, porque esa operación no corresponde necesariamente a una ruta funcional implementada. Las verificaciones posteriores con `GET` sobre rutas reales respondieron correctamente.

No se realizó una prueba de escritura, migración, compra real ni modificación de datos en producción.

## 4. Hallazgo SSL/TLS

### 4.1 Evidencia

- Certbot está instalado.
- `certbot-renew.timer` está habilitado y activo.
- La última ejecución de `certbot-renew.service` terminó correctamente.
- Certbot reporta un certificado válido hasta el **28 de enero de 2027**.
- La configuración activa de Nginx declara las rutas administradas por Certbot:

```text
/etc/letsencrypt/live/tienda.gadmsigchos.gob.ec/fullchain.pem
/etc/letsencrypt/live/tienda.gadmsigchos.gob.ec/privkey.pem
```

- El navegador, accediendo mediante el punto de entrada documentado `https://tienda.gadmsigchos.gob.ec:444`, mostró un certificado con vencimiento el **25 de noviembre de 2026**.

### 4.2 Evaluación

Existe una discrepancia entre el certificado administrado por Certbot y el certificado que entrega actualmente el servicio HTTPS. Las causas posibles son:

1. Nginx no ha recargado el certificado renovado.
2. Existe otro bloque de servidor, proxy o punto de entrada atendiendo la conexión.
3. El certificado observado corresponde a una instancia diferente de la configuración revisada.

### 4.3 Acción pendiente

Con autorización y ventana de mantenimiento:

1. Respaldar la configuración vigente de Nginx.
2. Confirmar el bloque que escucha en el puerto `444`.
3. Ejecutar una validación de configuración.
4. Recargar Nginx sin reiniciar el servidor.
5. Verificar nuevamente el certificado desde navegador y desde una herramienta TLS.
6. Confirmar que la renovación automática y la recarga posterior queden documentadas.

No se debe eliminar el certificado anterior hasta comprobar que el nuevo se sirve correctamente.

## 5. Hallazgo de seguridad SSH

### 5.1 Configuración observada

La configuración efectiva de SSH reportó:

```text
passwordauthentication yes
permitrootlogin yes
maxauthtries 6
```

Fail2ban no está instalado o no existe como unidad disponible:

```text
Unit fail2ban.service could not be found
```

### 5.2 Evidencia de intentos de acceso

Los registros de SSH reportaron múltiples intentos fallidos contra usuarios como `root`, `admin`, `ubuntu`, `kube` y otros usuarios inexistentes. El sistema indicó más de cien intentos fallidos desde el último acceso exitoso.

Las direcciones IP y cualquier dato de sesión deben conservarse únicamente en los registros del servidor; no se reproducen aquí.

### 5.3 Evaluación

No hay evidencia, en esta revisión, de que los intentos hayan sido exitosos. Sin embargo, permitir autenticación por contraseña y acceso directo de `root`, junto con la ausencia de Fail2ban, aumenta el riesgo de fuerza bruta y acceso no autorizado.

### 5.4 Acción pendiente

No modificar SSH hasta disponer de un acceso administrativo alternativo probado. El procedimiento seguro es:

1. Crear o confirmar un usuario administrativo nominal.
2. Registrar una clave pública SSH para ese usuario.
3. Probar una segunda sesión independiente.
4. Confirmar que el acceso alternativo funciona antes de cerrar la sesión root.
5. Deshabilitar el acceso SSH directo de `root`.
6. Deshabilitar la autenticación por contraseña.
7. Restringir el puerto SSH a una red, VPN o lista de IP autorizadas cuando sea viable.
8. Instalar y configurar Fail2ban o un mecanismo equivalente.
9. Revisar periódicamente los intentos de acceso y conservar evidencia de auditoría.

Estos cambios requieren autorización del responsable de infraestructura, porque un orden incorrecto puede dejar el servidor inaccesible.

## 6. Firewall y exposición de puertos

El firewall está activo. En la zona pública se observaron permitidos los puertos `22`, `80`, `444` y `8761`.

Los puertos de los microservicios `8081–8084` y MySQL `3306` no aparecieron como servicios permitidos por el firewall, aunque algunos procesos escuchan localmente o en todas las interfaces. Esto debe confirmarse desde una red externa antes de considerarlo cerrado.

### Observación pendiente sobre Eureka

El puerto `8761` está permitido públicamente. Debe confirmarse si Eureka necesita exposición externa. Si solo lo utilizan los microservicios internos, debería restringirse al tráfico interno o a una red administrativa.

### Acción pendiente

Antes de modificar reglas:

1. Confirmar dependencias de Eureka, Nginx y los microservicios.
2. Validar desde una red externa qué puertos realmente son accesibles.
3. Preparar reglas reversibles y una sesión SSH alternativa.
4. Aplicar cambios en una ventana autorizada.
5. Verificar HTTPS, frontend, API Gateway y comunicación interna después del cambio.

## 7. Diferencias con el entorno local

La producción revisada no utiliza Docker y administra los componentes mediante `systemd`. Esto difiere del entorno local, que utiliza Docker para MySQL y Gateway y ejecución local de microservicios.

Por tanto:

- Los cambios en `docker-compose.yml` no se aplican automáticamente a producción.
- Las variables del `.env` local no representan necesariamente la configuración productiva.
- Cualquier cambio productivo debe considerar unidades `systemd`, rutas de JAR, archivos de configuración, Nginx, certificados y firewall.
- No se deben copiar credenciales ni archivos `.env` del entorno local al servidor.

## 8. Estado de la revisión

### Confirmado

- Acceso SSH autorizado y operativo.
- Servicios principales activos.
- MySQL operativo.
- Nginx operativo y con sintaxis válida.
- Rutas funcionales principales responden.
- Certbot y su temporizador de renovación están activos.
- Existen controles de autenticación en Auth.

### Pendiente de autorización

- Recarga controlada de Nginx para validar el certificado servido.
- Endurecimiento de SSH.
- Instalación/configuración de Fail2ban.
- Restricción del acceso a Eureka.
- Confirmación externa de puertos publicados.

## 9. Mensaje para la responsable del proyecto

Durante una revisión únicamente informativa del servidor se confirmó que los servicios se encuentran operativos. Se identificaron dos riesgos que requieren coordinación antes de modificar producción:

1. Certbot dispone de un certificado válido hasta enero de 2027, pero el certificado observado por HTTPS en el puerto 444 vence el 25 de noviembre de 2026. Se requiere confirmar y recargar la configuración correcta de Nginx.
2. SSH permite autenticación por contraseña y acceso directo de root; además, se observaron numerosos intentos fallidos y no existe Fail2ban. Se recomienda migrar primero a un usuario administrativo con clave SSH probada y luego endurecer el acceso.

No se realizaron cambios, reinicios, migraciones ni modificaciones de datos.
