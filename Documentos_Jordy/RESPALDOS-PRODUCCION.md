# Respaldos de producción

El repositorio del equipo es público. Los respaldos reales del servidor —dump de MySQL, JAR, frontend, archivos `.env`, certificados, claves y configuraciones de `systemd`/Nginx— se conservan fuera de GitHub, en almacenamiento restringido bajo control del equipo.

Antes de una ventana de despliegue:

1. Crear el respaldo manual en el directorio privado designado y comprobar que el proceso terminó correctamente.
2. Verificar SHA-256 y compresión de los archivos copiados; proteger el directorio con permisos restrictivos.
3. Probar una restauración del dump en una instancia MySQL aislada. Un dump legible y con hash válido todavía no demuestra que pueda restaurarse.
4. Mantener disponibles los binarios y configuraciones anteriores para el rollback, junto con el procedimiento de recuperación.

En GitHub se versionan únicamente este procedimiento y scripts sin secretos. No agregar datos reales, binarios de producción, `.env`, certificados ni respaldos cifrados con una clave que también se guarde en el repositorio.
