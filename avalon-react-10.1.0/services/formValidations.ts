/**
 * Utilidades centralizadas de sanitización y validación de formularios.
 */

export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Mantiene únicamente dígitos numéricos y aplica longitud máxima opcional.
 */
export function onlyDigits(value: string, maxLength?: number): string {
    const cleaned = value.replace(/\D/g, '');
    return maxLength ? cleaned.slice(0, maxLength) : cleaned;
}

/**
 * Sanitiza códigos de referencia bancaria (solo letras, números y guiones).
 */
export function sanitizeReferenceCode(value: string, maxLength = 50): string {
    return value.replace(/[^A-Za-z0-9-]/g, '').slice(0, maxLength);
}

/**
 * Sanitiza nombres y apellidos permitiendo letras, acentos, espacios, apóstrofes, puntos y guiones.
 */
export function sanitizePersonName(value: string, maxLength = 80): string {
    return value.replace(/[^A-Za-zÁÉÍÓÚáéíóúÑñÜü\s'.-]/g, '').slice(0, maxLength);
}

/**
 * Valida número de cuenta bancaria (10 a 20 dígitos numéricos, no todos iguales).
 */
export function isValidBankAccount(value: string): boolean {
    const cleaned = value.trim();
    return /^\d{10,20}$/.test(cleaned) && !/^(\d)\1+$/.test(cleaned);
}

/**
 * Valida código de referencia o comprobante (6-50 caracteres alfanuméricos/guion, debe incluir al menos un dígito).
 */
export function isValidTransferReference(value: string): boolean {
    const cleaned = value.trim();
    return /^[A-Za-z0-9-]{6,50}$/.test(cleaned) && /\d/.test(cleaned);
}

/**
 * Valida nombre o apellido de persona (mínimo 2 letras reales).
 */
export function isValidPersonName(value: string, minLength = 2): boolean {
    const cleaned = value.trim();
    if (cleaned.length < minLength) return false;
    return /^[A-Za-zÁÉÍÓÚáéíóúÑñÜü\s'.-]+$/.test(cleaned) && /[A-Za-zÁÉÍÓÚáéíóúÑñÜü]{2,}/.test(cleaned);
}

/**
 * Valida teléfono ecuatoriano de 10 dígitos (ej. 0999999999 o 0223456789).
 */
export function isValidEcuadorianPhone(value: string): boolean {
    const digits = value.replace(/\D/g, '');
    return /^0[2-9]\d{8}$/.test(digits) && !/^(\d)\1+$/.test(digits);
}

/**
 * Valida cédula ecuatoriana de 10 dígitos usando algoritmo módulo 10.
 */
export function isValidEcuadorianID(cedula: string): boolean {
    if (!/^\d{10}$/.test(cedula)) return false;

    const digits = cedula.split('').map(Number);
    const provinceCode = parseInt(cedula.substring(0, 2), 10);

    if (provinceCode < 1 || provinceCode > 24) return false;

    const checkDigit = digits[9];
    let sum = 0;

    for (let i = 0; i < 9; i++) {
        let value = digits[i];
        if (i % 2 === 0) {
            value *= 2;
            if (value > 9) value -= 9;
        }
        sum += value;
    }

    const expectedCheckDigit = sum % 10 === 0 ? 0 : 10 - (sum % 10);
    return checkDigit === expectedCheckDigit;
}

/**
 * Valida formato de Cédula (10 dígitos) o RUC (13 dígitos) numérico.
 */
export function isValidCedulaOrRucFormat(value: string): boolean {
    const cleaned = value.trim();
    return /^(\d{10}|\d{13})$/.test(cleaned) && !/^(\d)\1+$/.test(cleaned);
}
