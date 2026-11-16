# Verificación y restauración local de respaldos

## Propósito y límites

Este plan deja comprobables las copias locales de producción y el procedimiento de recuperación. La copia local que se conservará está bajo preparacion-produccion-redis/backups servidor tienda sigchos/respaldo-produccion-privado. No incluirla en GitHub. El repositorio sí conserva backups/backup_pre_migracion.sql como fixture pequeño para reconstruir un esquema de pruebas; no es un dump de producción. No crear partición ni automatizar respaldos en el servidor. No restaurar directamente sobre producción durante un ensayo. La última reconsulta SSH no resolvió el nombre del servidor, por lo que no se confirmó si aún quedan copias temporales allí ni se borraron.

No se dispone de panel VMware ni de un segundo servidor. La recuperación de la VM completa y una prueba idéntica de systemd en Linux siguen fuera del alcance local; se debe obtener apoyo del administrador si la VM o su disco fallan.

## Inventario y evidencia local

La carpeta privada contiene las copias de cinco JAR, frontend, unidades systemd, NGINX, certificados, tabla de particiones, dumps MySQL y ZIP consolidado. En la verificación del 15 de noviembre se contaron 27 archivos, 1,328,027,567 bytes, y se creó SHA256SUMS-local-2026-11-15.txt en la carpeta privada. El script validó 12 archivos comprimidos/ejecutables. El dump fechado de las cuatro bases coincide con el manifiesto copiado desde el servidor: SHA-256 c5c937b6529d3176a04a077f71f7fdc025ebb16bf50956698f88cd1032613de1. No se imprime contenido de archivos de entorno.

El ensayo aislado previo importó 64 tablas en MySQL 8.4.9. Los conteos fueron auth_service=34, inventario=93, productos=49 y ventas=144966. Esos conteos detectan una carga incompleta, pero no comprueban igualdad fila por fila ni restauran usuarios/permisos globales de MySQL.

## Paso 1: recalcular y verificar integridad

Desde PowerShell, ejecutar el verificador guardado junto a los demás scripts:

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Alfonsooo\ESPE\VINCULACION\preparacion-produccion-redis\programa tienda sigchos\verificar_respaldos_locales.ps1"

El script recalcula SHA-256 de cada archivo, compara el dump reciente con el manifiesto recibido por SSH, comprueba GZIP, TAR.GZ, ZIP y JAR, y renueva la lista privada de huellas. Guardar la salida bajo programa tienda sigchos/resultados. Si una huella difiere, detener el ensayo, preservar ambas copias y volver a transferir desde la fuente autorizada.

## Paso 2: ensayar la base de datos en aislamiento

Con Docker local disponible, ejecutar desde PowerShell:

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Alfonsooo\ESPE\VINCULACION\preparacion-produccion-redis\programa tienda sigchos\restaurar_ensayo_manual_2026_11_15.ps1"

El script verifica dump y manifiesto, monta el SQL comprimido en modo solo lectura y crea MySQL sin red externa ni puertos publicados. Confirmar las 64 tablas, los conteos por esquema, la consulta de versión, y que el script retire contenedor y volumen al terminar. No copiar usuarios o grants de producción sin revisar su necesidad y secretos.

## Paso 3: ensayar los artefactos de aplicación

1. En una carpeta temporal dentro de la carpeta privada, validar SHA antes de tocar los archivos.
2. Restaurar cada JAR con su nombre de unidad: eureka-server, auth-service, msvc-producto, msvc-inventario y msvc-ventas. Comparar su SHA con el manifiesto y listar sus entradas con jar tf; no ejecutar los JAR de producción en el host.
3. Extraer frontend-antes.tar.gz en un directorio de prueba separado. Comparar estructura y tamaño, revisar package.json y verificar que no se sobrescribieron archivos originales.
4. Extraer systemd-antes.tar.gz, nginx-antes.tar.gz y certificado-tienda-antes.tar.gz solo en directorios separados con ACL privada. Ejecutar validación de sintaxis de NGINX en un contenedor aislado, sin publicar puertos ni montar las rutas activas del servidor.
5. Comprobar que el certificado y la clave privada forman pareja con herramientas locales. No mostrar el contenido de la clave ni incorporarla a Git.
6. Conservar sda-tabla-antes.sfdisk como referencia. No ejecutarlo ni escribir la tabla de particiones; el disco reportó advertencia GPT/PMBR y no hay consola VMware disponible.

## Paso 4: completar el ensayo de recuperación

Registrar fecha, commit, ubicación, SHA, versión de MySQL, tablas/conteos y estado de cada artefacto. Añadir evidencia de extracción y validación de NGINX. Limpiar solo el directorio de ensayo después de verificar la copia; mantener intacta la carpeta privada de respaldos. Repetir todo después de cada respaldo nuevo o cambio de artefactos.

Para validar arranque de systemd, frontend y NGINX como conjunto hace falta una VM Linux desechable con versión compatible. No sustituir esa prueba con la restauración del SQL o con listar los archivos.

## Preparación para una futura ventana

Antes de una publicación se debe generar un respaldo manual nuevo, copiarlo de inmediato a la laptop, recalcular SHA y repetir la restauración aislada. Las copias en el servidor solo serían temporales durante transferencia y se retirarían después de confirmar la copia local y la aprobación operativa. El archivo de particiones no sirve como respaldo de VM. La ausencia de panel VMware impide recuperar por sí sola el sistema operativo o el disco.

No guardar secretos, dump, JAR ni archivos comprimidos reales en el repositorio. Documentar solo comandos, huellas necesarias, resultados y límites.
