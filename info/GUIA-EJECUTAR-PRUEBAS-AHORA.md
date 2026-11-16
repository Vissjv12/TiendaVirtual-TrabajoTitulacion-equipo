# Guía Rápida - Ejecutar Pruebas AHORA

## ✅ 1. PRUEBAS DE INTEGRACIÓN (5 minutos)

### Paso 1: Ejecutar
```powershell
cd C:\Users\mycke\Desktop\TrabajoTitulacion-main\TrabajoTitulacion-main
.\ejecutar-pruebas-integracion.bat
```

### Paso 2: Ver Resultados
- Los tests se ejecutarán automáticamente
- Verás output en consola con ✅ o ❌
- Reporte HTML en: `Microservicios\msvc-ventas\target\surefire-reports\index.html`

### Qué prueba:
- ✅ Crear venta completa
- ✅ Consultar venta
- ✅ Consultar historial
- ✅ Generar factura PDF
- ✅ Validación de datos
- ✅ Manejo de errores

---

## ⚡ 2. PRUEBAS DE RENDIMIENTO CON JMETER (10-15 minutos)

### Paso 1: Descargar JMeter
1. Ve a: https://jmeter.apache.org/download_jmeter.cgi
2. Descarga: `apache-jmeter-5.6.3.zip`
3. Extrae en: `C:\jmeter`

### Paso 2: Iniciar Servicios
```powershell
# Terminal 1 - Eureka
cd Microservicios\eureka-server
.\mvnw.cmd spring-boot:run

# Terminal 2 - Productos
cd Microservicios\msvc-producto
.\mvnw.cmd spring-boot:run

# Terminal 3 - Ventas
cd Microservicios\msvc-ventas
.\mvnw.cmd spring-boot:run
```

### Paso 3: Ejecutar JMeter
```powershell
# Abrir JMeter GUI
C:\jmeter\bin\jmeter.bat

# O ejecutar directamente (modo no GUI - más rápido)
cd C:\Users\mycke\Desktop\TrabajoTitulacion-main\TrabajoTitulacion-main\pruebas-jmeter
C:\jmeter\bin\jmeter.bat -n -t ECommerce-Siachos-Test-Plan.jmx -l resultados/resultados.jtl -e -o resultados/reporte-html
```

### Paso 4: Ver Resultados
```powershell
# Abrir reporte HTML
start resultados/reporte-html/index.html
```

### Qué prueba:
- **Escenario 1**: 10 usuarios concurrentes (5 minutos)
- **Endpoints**:
  - GET /api/productos (listar)
  - GET /api/productos/1 (detalle)
  - POST /api/ventas (crear)
  - GET /api/ventas/cliente/1 (historial)
  - GET /api/ventas/{id}/factura (PDF)

### Métricas que obtendrás:
- ⏱️ Tiempo de respuesta promedio
- 📊 Throughput (requests/segundo)
- ❌ % de errores
- 📈 Gráficos de rendimiento

---

## 🎯 Opción Rápida con JMeter GUI

### Paso 1: Abrir JMeter
```powershell
C:\jmeter\bin\jmeter.bat
```

### Paso 2: Cargar Plan de Pruebas
1. File → Open
2. Seleccionar: `C:\Users\mycke\Desktop\TrabajoTitulacion-main\TrabajoTitulacion-main\pruebas-jmeter\ECommerce-Siachos-Test-Plan.jmx`

### Paso 3: Configurar (si necesario)
- Variables → HOST: `localhost`
- Variables → PORT_VENTAS: `8083`
- Variables → PORT_PRODUCTOS: `8081`

### Paso 4: Ejecutar
1. Click en botón verde "Start" ▶️
2. Ve a "Ver Resultados en Tabla" para ver en tiempo real
3. Ve a "Reporte Resumen" para métricas

### Paso 5: Detener
- Click en botón rojo "Stop" ⏹️

---

## 📊 Resultados Esperados

### Pruebas de Integración
```
Tests run: 5
Failures: 0
Errors: 0
Skipped: 0
Success rate: 100%
```

### Pruebas de Rendimiento (Escenario 1 - 10 usuarios)
```
Objetivo:
- Response Time < 500ms: ✅
- Throughput > 100 req/s: ✅
- Error Rate < 1%: ✅
```

---

## 🔧 Troubleshooting

### Si las pruebas de integración fallan:
```powershell
# Verificar que H2 está en el pom.xml
cd Microservicios\msvc-ventas
.\mvnw.cmd dependency:tree | findstr h2
```

### Si JMeter no conecta:
```powershell
# Verificar servicios corriendo
netstat -ano | findstr :8083
netstat -ano | findstr :8081
```

### Si hay errores de BD en tests:
- Los tests usan H2 en memoria (no afecta tu MySQL)
- Se crean/destruyen automáticamente

---

## ⏱️ Tiempo Estimado

- **Pruebas de Integración**: 5 minutos
- **Setup JMeter**: 10 minutos (primera vez)
- **Pruebas de Rendimiento**: 5-10 minutos
- **Análisis de Resultados**: 10 minutos

**TOTAL**: ~30-40 minutos para todas las pruebas

---

## 📝 Documentar Resultados

### Para tu tesis:
1. **Capturas de pantalla**:
   - JMeter: Reporte Resumen
   - JMeter: Gráfico de Resultados
   - Tests de integración: Terminal con ✅

2. **Reportes HTML**:
   - `resultados/reporte-html/index.html`
   - `Microservicios/msvc-ventas/target/surefire-reports/`

3. **Métricas clave a documentar**:
   - Número de tests ejecutados
   - % de éxito
   - Tiempo promedio de respuesta
   - Throughput alcanzado
   - Usuarios concurrentes soportados

---

## 🚀 EJECUTAR AHORA - Comando Rápido

```powershell
# 1. Pruebas de Integración (primero)
cd C:\Users\mycke\Desktop\TrabajoTitulacion-main\TrabajoTitulacion-main
.\ejecutar-pruebas-integracion.bat

# 2. Después, en otra terminal, ejecutar servicios para JMeter
# (ver Paso 2 de JMeter arriba)
```
