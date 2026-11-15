import { PageResponse } from './apiConfig';
import ventasService, { VentaResponse } from './ventasService';

/**
 * Escapa una celda para formato CSV RFC 4180 y previene inyección de fórmulas en Excel.
 */
export function sanitizeCsvCell(value: unknown): string {
    if (value === null || value === undefined) {
        return '';
    }

    let text = String(value);

    // Evitar interpretación de fórmulas al abrir en Excel/LibreOffice (=, +, -, @, tab)
    if (/^[=+\-@\t\r]/.test(text) && !/^-\d+(\.\d+)?$/.test(text)) {
        text = `'${text}`;
    }

    if (/[",\r\n;]/.test(text)) {
        return `"${text.replace(/"/g, '""')}"`;
    }

    return text;
}

/**
 * Construye el contenido CSV con BOM UTF-8 para compatibilidad con Excel en español.
 */
export function buildCsvContent(headers: string[], rows: unknown[][]): string {
    const headerLine = headers.map(sanitizeCsvCell).join(',');
    const dataLines = rows.map((row) => row.map(sanitizeCsvCell).join(','));
    return '\uFEFF' + [headerLine, ...dataLines].join('\r\n');
}

/**
 * Descarga un archivo CSV en el navegador.
 */
export function downloadCsvFile(filename: string, headers: string[], rows: unknown[][]): void {
    const csvContent = buildCsvContent(headers, rows);
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);

    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

/**
 * Recorre todas las páginas de un endpoint paginado para exportar el conjunto completo filtrado.
 */
export async function fetchAllPaginatedRecords<T>(
    fetchPage: (page: number, size: number) => Promise<{ success: boolean; message?: string; data?: PageResponse<T> }>,
    pageSize = 50
): Promise<{ success: boolean; items: T[]; message?: string }> {
    const collected: T[] = [];
    let currentPage = 0;
    let totalPages = 1;

    do {
        const response = await fetchPage(currentPage, pageSize);
        if (!response.success || !response.data) {
            return {
                success: false,
                items: collected,
                message: response.message || 'No se pudieron obtener los registros para exportar'
            };
        }

        collected.push(...(response.data.content || []));
        totalPages = Math.max(1, response.data.totalPages ?? 1);
        currentPage += 1;
    } while (currentPage < totalPages);

    return { success: true, items: collected };
}

/**
 * Exporta una lista de ventas a un archivo .csv listo para abrir en Excel.
 */
export function exportVentasToCsv(ventas: VentaResponse[], filenamePrefix = 'ventas'): string {
    const headers = [
        'N° Factura',
        'Fecha',
        'Cliente',
        'Email',
        'Teléfono',
        'Dirección',
        'Estado',
        'Subtotal (USD)',
        'Impuesto (USD)',
        'Total (USD)',
        'Productos'
    ];

    const rows = ventas.map((venta) => {
        const clienteNombre = [venta.cliente?.nombre, venta.cliente?.apellido].filter(Boolean).join(' ').trim();
        const productosResumen = (venta.detalles || [])
            .map((d) => `${d.nombreProducto} x${d.cantidad}`)
            .join('; ');

        return [
            venta.numeroFactura || '',
            venta.fechaVenta ? ventasService.formatearFecha(venta.fechaVenta) : '',
            clienteNombre || 'Sin cliente',
            venta.cliente?.email || '',
            venta.cliente?.telefono || '',
            venta.cliente?.direccion || '',
            ventasService.formatearEstado(venta.estado || ''),
            Number(venta.subtotal ?? 0).toFixed(2),
            Number(venta.impuesto ?? 0).toFixed(2),
            Number(venta.total ?? 0).toFixed(2),
            productosResumen
        ];
    });

    const today = new Date().toISOString().slice(0, 10);
    const filename = `${filenamePrefix}_${today}.csv`;
    downloadCsvFile(filename, headers, rows);
    return filename;
}
