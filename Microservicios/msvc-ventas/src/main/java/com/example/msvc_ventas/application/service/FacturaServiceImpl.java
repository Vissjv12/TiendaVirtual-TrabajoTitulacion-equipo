package com.example.msvc_ventas.application.service;

import com.example.msvc_ventas.domain.model.DetalleVenta;
import com.example.msvc_ventas.domain.model.Venta;
import com.example.msvc_ventas.domain.repository.DetalleVentaRepository;
import com.example.msvc_ventas.domain.repository.VentaRepository;
import com.example.msvc_ventas.domain.service.FacturaService;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class FacturaServiceImpl implements FacturaService {

    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;

    @Value("${factura.directorio:facturas}")
    private String directorioFacturas;

    @Override
    public String generarFacturaPDF(Long ventaId) {
        log.info("Generando factura PDF para venta ID: {}", ventaId);

        // Obtener datos de la venta
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada con ID: " + ventaId));

        // Cargar los detalles de la venta manualmente
        List<DetalleVenta> detalles = detalleVentaRepository.findByVentaId(ventaId);
        venta.setDetalles(detalles);
        log.info("Cargados {} detalles para la venta {}", detalles.size(), ventaId);

        // Crear directorio si no existe
        File directorio = new File(directorioFacturas);
        if (!directorio.exists()) {
            directorio.mkdirs();
            log.info("Directorio de facturas creado: {}", directorio.getAbsolutePath());
        }

        // Nombre del archivo
        String nombreArchivo = String.format("%s/factura_%s.pdf", 
                directorioFacturas, venta.getNumeroFactura());

        try {
            // Crear el PDF
            PdfWriter writer = new PdfWriter(nombreArchivo);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // Color corporativo
            DeviceRgb colorPrimario = new DeviceRgb(52, 152, 219); // Azul

            // ===== ENCABEZADO =====
            document.add(new Paragraph("FACTURA")
                    .setFontSize(24)
                    .setBold()
                    .setFontColor(colorPrimario)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10));

            document.add(new Paragraph("ECommerce Sigchos")
                    .setFontSize(16)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(5));

            document.add(new Paragraph("Emprendimientos & Turismo Rural")
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20));

            // ===== INFORMACIÓN DE LA FACTURA =====
            Table infoTable = new Table(new float[]{1, 1});
            infoTable.setWidth(UnitValue.createPercentValue(100));
            infoTable.setMarginBottom(20);

            // Columna izquierda - Datos del cliente
            infoTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("INFORMACIÓN DEL CLIENTE").setBold().setFontSize(11)));
            infoTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("INFORMACIÓN DE LA FACTURA").setBold().setFontSize(11)));

            String clienteInfo = venta.getCliente() != null 
                    ? venta.getCliente().getNombre() + " " + (venta.getCliente().getApellido() != null ? venta.getCliente().getApellido() : "")
                    : "Cliente";
            
            infoTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("Cliente: " + clienteInfo).setFontSize(9)));
            infoTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("N° Factura: " + venta.getNumeroFactura()).setFontSize(9)));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            infoTable.addCell(new Cell().setBorder(null).add(new Paragraph("")));
            infoTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("Fecha: " + venta.getFechaVenta().format(formatter)).setFontSize(9)));

            infoTable.addCell(new Cell().setBorder(null).add(new Paragraph("")));
            infoTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("Estado: " + venta.getEstado()).setFontSize(9)));

            document.add(infoTable);

            // ===== TABLA DE PRODUCTOS =====
            document.add(new Paragraph("DETALLE DE PRODUCTOS")
                    .setBold()
                    .setFontSize(12)
                    .setMarginBottom(10));

            Table productosTable = new Table(new float[]{3, 1, 2, 2});
            productosTable.setWidth(UnitValue.createPercentValue(100));

            // Encabezados
            productosTable.addHeaderCell(new Cell()
                    .add(new Paragraph("Producto").setBold().setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(colorPrimario)
                    .setTextAlignment(TextAlignment.CENTER));
            productosTable.addHeaderCell(new Cell()
                    .add(new Paragraph("Cant.").setBold().setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(colorPrimario)
                    .setTextAlignment(TextAlignment.CENTER));
            productosTable.addHeaderCell(new Cell()
                    .add(new Paragraph("Precio Unit.").setBold().setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(colorPrimario)
                    .setTextAlignment(TextAlignment.CENTER));
            productosTable.addHeaderCell(new Cell()
                    .add(new Paragraph("Subtotal").setBold().setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(colorPrimario)
                    .setTextAlignment(TextAlignment.CENTER));

            // Detalles de productos
            for (DetalleVenta detalle : venta.getDetalles()) {
                productosTable.addCell(new Cell()
                        .add(new Paragraph(detalle.getNombreProducto()).setFontSize(9))
                        .setTextAlignment(TextAlignment.LEFT));
                productosTable.addCell(new Cell()
                        .add(new Paragraph(String.valueOf(detalle.getCantidad())).setFontSize(9))
                        .setTextAlignment(TextAlignment.CENTER));
                productosTable.addCell(new Cell()
                        .add(new Paragraph("$" + detalle.getPrecioUnitario().toString()).setFontSize(9))
                        .setTextAlignment(TextAlignment.RIGHT));
                productosTable.addCell(new Cell()
                        .add(new Paragraph("$" + detalle.getSubtotal().toString()).setFontSize(9))
                        .setTextAlignment(TextAlignment.RIGHT));
            }

            document.add(productosTable);

            // ===== TOTALES =====
            Table totalesTable = new Table(new float[]{3, 1});
            totalesTable.setWidth(UnitValue.createPercentValue(100));
            totalesTable.setMarginTop(20);

            // Usar los valores almacenados en la venta; el envío es la diferencia
            BigDecimal subtotal = venta.getSubtotal() != null
                    ? venta.getSubtotal()
                    : venta.getTotal().divide(BigDecimal.valueOf(1.15), 2, BigDecimal.ROUND_HALF_UP);
            BigDecimal iva = venta.getImpuesto() != null
                    ? venta.getImpuesto()
                    : venta.getTotal().subtract(subtotal);
            BigDecimal envio = venta.getTotal().subtract(subtotal).subtract(iva);
            if (envio.compareTo(BigDecimal.ZERO) < 0) {
                envio = BigDecimal.ZERO;
            }

            totalesTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("Subtotal:").setBold().setTextAlignment(TextAlignment.RIGHT)));
            totalesTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("$" + subtotal.toString()).setTextAlignment(TextAlignment.RIGHT)));

            totalesTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("IVA (15%):").setBold().setTextAlignment(TextAlignment.RIGHT)));
            totalesTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("$" + iva.toString()).setTextAlignment(TextAlignment.RIGHT)));

            totalesTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("Envío:").setBold().setTextAlignment(TextAlignment.RIGHT)));
            totalesTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("$" + envio.setScale(2, BigDecimal.ROUND_HALF_UP).toString()).setTextAlignment(TextAlignment.RIGHT)));

            totalesTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("TOTAL:").setBold().setFontSize(14)
                            .setFontColor(colorPrimario).setTextAlignment(TextAlignment.RIGHT)));
            totalesTable.addCell(new Cell().setBorder(null)
                    .add(new Paragraph("$" + venta.getTotal().toString()).setBold().setFontSize(14)
                            .setFontColor(colorPrimario).setTextAlignment(TextAlignment.RIGHT)));

            document.add(totalesTable);

            // ===== PIE DE PÁGINA =====
            document.add(new Paragraph("\n\nGracias por su compra")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(10)
                    .setItalic()
                    .setMarginTop(30));

            document.add(new Paragraph("www.ecommerce-sigchos.com | contacto@ecommerce-sigchos.com")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(8)
                    .setFontColor(ColorConstants.GRAY));

            document.close();

            log.info("Factura PDF generada exitosamente: {}", nombreArchivo);
            return nombreArchivo;

        } catch (FileNotFoundException e) {
            log.error("Error al crear archivo PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar factura PDF", e);
        }
    }

    @Override
    public File obtenerFacturaPDF(Long ventaId) {
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada con ID: " + ventaId));

        String rutaArchivo = String.format("%s/factura_%s.pdf", 
                directorioFacturas, venta.getNumeroFactura());
        
        File archivo = new File(rutaArchivo);
        
        if (!archivo.exists()) {
            log.warn("Factura no existe, generando nueva: {}", rutaArchivo);
            generarFacturaPDF(ventaId);
            archivo = new File(rutaArchivo);
        }

        return archivo;
    }

    @Override
    public boolean existeFactura(Long ventaId) {
        Venta venta = ventaRepository.findById(ventaId).orElse(null);
        if (venta == null) {
            return false;
        }

        String rutaArchivo = String.format("%s/factura_%s.pdf", 
                directorioFacturas, venta.getNumeroFactura());
        
        return new File(rutaArchivo).exists();
    }
}
