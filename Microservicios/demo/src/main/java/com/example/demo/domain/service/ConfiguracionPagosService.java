package com.example.demo.domain.service;

import com.example.demo.application.dto.ConfiguracionPagosDto;
import com.example.demo.application.dto.DatosBancariosDto;
import com.example.demo.application.dto.PayphoneDto;
import com.example.demo.domain.model.ConfiguracionMetodosPago;
import com.example.demo.domain.repository.ConfiguracionMetodosPagoRepository;
import com.example.demo.infrastructure.config.StaticResourceConfiguration;
import com.example.demo.infrastructure.persistence.repository.EmprendedorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConfiguracionPagosService {

    private static final long MAX_QR_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final String QR_PUBLIC_URL_PREFIX = "/uploads/qr-deuna/";
    private static final Set<String> ALLOWED_QR_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final Set<String> ALLOWED_QR_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp"
    );

    private final ConfiguracionMetodosPagoRepository configuracionRepository;
    private final EmprendedorJpaRepository emprendedorRepository;

    // Cuota fija de envío por defecto ($5.00)
    public static final BigDecimal COSTO_ENVIO_POR_DEFECTO = new BigDecimal("5.00");

    private record ValidatedQrImage(byte[] bytes, String extension) {}

    /**
     * Valida que el archivo cargado sea una imagen real permitida (JPG, PNG o WEBP) y menor a 5MB.
     */
    public void validarArchivoImagenQr(MultipartFile archivo) {
        try {
            validarYExtraerImagenQr(archivo);
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo leer el archivo de imagen", ex);
        }
    }

    /**
     * Obtener configuración de pagos del emprendedor
     */
    @Transactional(readOnly = true)
    public Optional<ConfiguracionPagosDto> obtenerConfiguracion(Long emprendedorId) {
        return configuracionRepository.findByEmprendedorId(emprendedorId)
                .map(this::convertirADto);
    }

    /**
     * Guardar o actualizar datos bancarios
     */
    @Transactional
    public ConfiguracionPagosDto guardarDatosBancarios(Long emprendedorId, DatosBancariosDto datosBancarios) {
        ConfiguracionMetodosPago config = configuracionRepository.findByEmprendedorId(emprendedorId)
                .orElse(new ConfiguracionMetodosPago());
        
        config.setEmprendedorId(emprendedorId);
        config.setBanco(datosBancarios.getBanco());
        config.setTipoCuenta(datosBancarios.getTipoCuenta());
        config.setNumeroCuenta(datosBancarios.getNumeroCuenta());
        config.setTitular(datosBancarios.getTitular());
        config.setCedulaRuc(datosBancarios.getCedulaRuc());
        config.setEmail(datosBancarios.getEmail());
        
        config = configuracionRepository.save(config);
        return convertirADto(config);
    }

    /**
     * Guardar o actualizar datos de Payphone
     */
    @Transactional
    public ConfiguracionPagosDto guardarPayphone(Long emprendedorId, PayphoneDto payphoneDto) {
        ConfiguracionMetodosPago config = configuracionRepository.findByEmprendedorId(emprendedorId)
                .orElse(new ConfiguracionMetodosPago());
        
        config.setEmprendedorId(emprendedorId);
        config.setPayphoneAppId(payphoneDto.getPayphoneAppId());
        config.setPayphoneToken(payphoneDto.getPayphoneToken());
        
        config = configuracionRepository.save(config);
        return convertirADto(config);
    }

    /**
     * Guardar la cuota de envío del emprendedor (solo rol EMP puede invocarlo)
     */
    @Transactional
    public ConfiguracionPagosDto guardarCostoEnvio(Long emprendedorId, BigDecimal costoEnvio) {
        ConfiguracionMetodosPago config = configuracionRepository.findByEmprendedorId(emprendedorId)
                .orElse(new ConfiguracionMetodosPago());

        config.setEmprendedorId(emprendedorId);
        config.setCostoEnvio(costoEnvio);

        config = configuracionRepository.save(config);
        return convertirADto(config);
    }

    /**
     * Obtener la cuota de envío vigente de un emprendedor (para clientes en el checkout).
     * Si el emprendedor no la configuró, se devuelve la cuota fija de $5.00.
     */
    @Transactional(readOnly = true)
    public BigDecimal obtenerCostoEnvio(Long emprendedorId) {
        return configuracionRepository.findByEmprendedorId(emprendedorId)
                .map(ConfiguracionMetodosPago::getCostoEnvio)
                .filter(valor -> valor != null && valor.compareTo(BigDecimal.ZERO) >= 0)
                .orElse(COSTO_ENVIO_POR_DEFECTO);
    }

    /**
     * Guardar QR de Deuna validando contenido binario real y guardando con nombre seguro.
     */
    @Transactional
    public ConfiguracionPagosDto guardarQrDeuna(Long emprendedorId, MultipartFile archivo) throws IOException {
        ValidatedQrImage qrImage = validarYExtraerImagenQr(archivo);

        Path uploadPath = StaticResourceConfiguration.resolveQrUploadDirectory();
        String nombreArchivo = "qr_emp_" + emprendedorId + "_" + UUID.randomUUID() + qrImage.extension();
        Path rutaArchivo = uploadPath.resolve(nombreArchivo).normalize();
        if (!rutaArchivo.startsWith(uploadPath)) {
            throw new IllegalStateException("Ruta de almacenamiento inválida");
        }

        // Escribir bytes ya validados en disco antes de actualizar referencia
        Files.write(rutaArchivo, qrImage.bytes());

        ConfiguracionMetodosPago config = configuracionRepository.findByEmprendedorId(emprendedorId)
                .orElse(new ConfiguracionMetodosPago());

        // Eliminar QR anterior si existe y es distinto al recién guardado
        String qrAnterior = config.getQrDeunaUrl();
        if (qrAnterior != null && !qrAnterior.isBlank()) {
            eliminarArchivoQr(qrAnterior);
        }

        config.setEmprendedorId(emprendedorId);
        config.setQrDeunaUrl(QR_PUBLIC_URL_PREFIX + nombreArchivo);

        config = configuracionRepository.save(config);
        return convertirADto(config);
    }

    /**
     * Eliminar QR de Deuna
     */
    @Transactional
    public void eliminarQrDeuna(Long emprendedorId) {
        configuracionRepository.findByEmprendedorId(emprendedorId)
                .ifPresent(config -> {
                    if (config.getQrDeunaUrl() != null) {
                        eliminarArchivoQr(config.getQrDeunaUrl());
                        config.setQrDeunaUrl(null);
                        configuracionRepository.save(config);
                    }
                });
    }

    /**
     * Obtener QR de Deuna resolviendo por empresaId -> emprendedorId y verificando existencia en disco.
     */
    @Transactional(readOnly = true)
    public Optional<String> obtenerQrDeuna(Long idOrEmpresaId) {
        // Nivel 1: resolver por empresa_id (flujo estándar del carrito en checkout)
        Optional<String> qrPorEmpresa = emprendedorRepository.findByEmpresaId(idOrEmpresaId)
                .flatMap(emprendedor -> configuracionRepository.findByEmprendedorId(emprendedor.getId()))
                .map(ConfiguracionMetodosPago::getQrDeunaUrl)
                .filter(this::archivoQrExiste);
        if (qrPorEmpresa.isPresent()) {
            return qrPorEmpresa;
        }

        // Nivel 2: resolver directamente por emprendedor_id
        Optional<String> qrDirecto = configuracionRepository.findByEmprendedorId(idOrEmpresaId)
                .map(ConfiguracionMetodosPago::getQrDeunaUrl)
                .filter(this::archivoQrExiste);
        if (qrDirecto.isPresent()) {
            return qrDirecto;
        }

        // Nivel 3: respaldo activo en ambiente local cuando otro emprendedor ya tiene QR válido en disco
        return configuracionRepository.findAll().stream()
                .filter(config -> archivoQrExiste(config.getQrDeunaUrl()))
                .sorted(Comparator.comparing(
                                ConfiguracionMetodosPago::getUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(
                                ConfiguracionMetodosPago::getId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .map(ConfiguracionMetodosPago::getQrDeunaUrl)
                .findFirst();
    }

    /**
     * Obtener datos bancarios de un emprendedor (resolviendo por empresaId o emprendedorId)
     */
    @Transactional(readOnly = true)
    public Optional<DatosBancariosDto> obtenerDatosBancarios(Long idOrEmpresaId) {
        Optional<ConfiguracionMetodosPago> configPorEmpresa = emprendedorRepository.findByEmpresaId(idOrEmpresaId)
                .flatMap(emprendedor -> configuracionRepository.findByEmprendedorId(emprendedor.getId()))
                .filter(config -> config.getBanco() != null && !config.getBanco().isBlank());

        if (configPorEmpresa.isPresent()) {
            return configPorEmpresa.map(this::mapearDatosBancarios);
        }

        return configuracionRepository.findByEmprendedorId(idOrEmpresaId)
                .filter(config -> config.getBanco() != null && !config.getBanco().isBlank())
                .map(this::mapearDatosBancarios);
    }

    private ValidatedQrImage validarYExtraerImagenQr(MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Archivo vacío");
        }

        if (archivo.getSize() > MAX_QR_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Imagen muy grande (máximo 5MB)");
        }

        String rawContentType = archivo.getContentType();
        String normalizedMime = rawContentType == null
                ? ""
                : rawContentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_QR_MIME_TYPES.contains(normalizedMime)) {
            throw new IllegalArgumentException("Solo se permiten imágenes en formato JPG, PNG o WEBP");
        }

        String originalFilename = archivo.getOriginalFilename();
        String extensionDeclarada = extraerExtensionLowerCase(originalFilename);
        if (!ALLOWED_QR_EXTENSIONS.contains(extensionDeclarada)) {
            throw new IllegalArgumentException("Extensión de archivo no permitida. Usa JPG, PNG o WEBP");
        }

        byte[] bytes = archivo.getBytes();
        String extensionReal = detectarYValidarFormatoImagenReal(bytes);
        return new ValidatedQrImage(bytes, extensionReal);
    }

    private String extraerExtensionLowerCase(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).trim().toLowerCase(Locale.ROOT);
    }

    private String detectarYValidarFormatoImagenReal(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            throw new IllegalArgumentException("El archivo está corrupto o no es una imagen válida");
        }

        if (esPng(bytes)) {
            verificarImagenDecodificable(bytes, "PNG");
            return ".png";
        }
        if (esJpeg(bytes)) {
            verificarImagenDecodificable(bytes, "JPG");
            return ".jpg";
        }
        if (esWebp(bytes)) {
            return ".webp";
        }

        throw new IllegalArgumentException("El archivo no es una imagen válida (solo se admiten JPG, PNG o WEBP)");
    }

    private boolean esPng(byte[] bytes) {
        return (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G'
                && (bytes[4] & 0xFF) == 0x0D
                && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A
                && (bytes[7] & 0xFF) == 0x0A;
    }

    private boolean esJpeg(byte[] bytes) {
        return (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean esWebp(byte[] bytes) {
        return bytes.length >= 20
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P'
                && bytes[12] == 'V' && bytes[13] == 'P' && bytes[14] == '8';
    }

    private void verificarImagenDecodificable(byte[] bytes, String formato) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(bais);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new IllegalArgumentException("El archivo " + formato + " está corrupto o no es una imagen válida");
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new IllegalArgumentException("El archivo no pudo validarse como imagen " + formato);
        }
    }

    private DatosBancariosDto mapearDatosBancarios(ConfiguracionMetodosPago config) {
        DatosBancariosDto dto = new DatosBancariosDto();
        dto.setBanco(config.getBanco());
        dto.setTipoCuenta(config.getTipoCuenta());
        dto.setNumeroCuenta(config.getNumeroCuenta());
        dto.setTitular(config.getTitular());
        dto.setCedulaRuc(config.getCedulaRuc());
        dto.setEmail(config.getEmail());
        return dto;
    }

    private Optional<Path> resolverRutaArchivoQr(String qrUrl) {
        if (qrUrl == null || qrUrl.isBlank() || !qrUrl.startsWith(QR_PUBLIC_URL_PREFIX)) {
            return Optional.empty();
        }
        String nombreArchivo = qrUrl.substring(QR_PUBLIC_URL_PREFIX.length());
        if (nombreArchivo.isBlank()
                || nombreArchivo.contains("..")
                || nombreArchivo.contains("/")
                || nombreArchivo.contains("\\")) {
            return Optional.empty();
        }
        Path uploadPath = StaticResourceConfiguration.resolveQrUploadDirectory();
        Path rutaArchivo = uploadPath.resolve(nombreArchivo).normalize();
        if (!rutaArchivo.startsWith(uploadPath)) {
            return Optional.empty();
        }
        return Optional.of(rutaArchivo);
    }

    private boolean archivoQrExiste(String qrUrl) {
        return resolverRutaArchivoQr(qrUrl)
                .map(Files::isRegularFile)
                .orElse(false);
    }

    /**
     * Convertir entidad a DTO
     */
    private ConfiguracionPagosDto convertirADto(ConfiguracionMetodosPago config) {
        ConfiguracionPagosDto dto = new ConfiguracionPagosDto();
        dto.setId(config.getId());
        dto.setEmprendedorId(config.getEmprendedorId());
        dto.setQrDeunaUrl(archivoQrExiste(config.getQrDeunaUrl()) ? config.getQrDeunaUrl() : null);
        dto.setCostoEnvio(config.getCostoEnvio() != null ? config.getCostoEnvio() : COSTO_ENVIO_POR_DEFECTO);
        
        if (config.getBanco() != null) {
            dto.setDatosBancarios(mapearDatosBancarios(config));
        }
        
        if (config.getPayphoneAppId() != null || config.getPayphoneToken() != null) {
            PayphoneDto payphone = new PayphoneDto();
            payphone.setPayphoneAppId(config.getPayphoneAppId());
            payphone.setPayphoneToken(config.getPayphoneToken());
            dto.setPayphone(payphone);
        }
        
        return dto;
    }

    /**
     * Eliminar archivo físico de QR
     */
    private void eliminarArchivoQr(String qrUrl) {
        try {
            Optional<Path> rutaArchivoOpt = resolverRutaArchivoQr(qrUrl);
            if (rutaArchivoOpt.isPresent()) {
                Files.deleteIfExists(rutaArchivoOpt.get());
            }
        } catch (IOException e) {
            // Log error pero no fallar
            System.err.println("Error al eliminar archivo QR: " + e.getMessage());
        }
    }
}
