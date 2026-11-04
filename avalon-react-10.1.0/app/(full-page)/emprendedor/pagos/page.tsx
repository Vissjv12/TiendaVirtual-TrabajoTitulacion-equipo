'use client';

import React, { useEffect, useState } from 'react';
import { Message } from 'primereact/message';
import PagosEmprendedor from './PagosEmprendedor';
import authService from '../../../../services/authService';

export default function PagosPage() {
    const [emprendedorId, setEmprendedorId] = useState<number | null>(null);
    const [checked, setChecked] = useState(false);

    useEffect(() => {
        const empresaId = authService.getEmpresaId();
        setEmprendedorId(empresaId);
        setChecked(true);
    }, []);

    if (!checked) {
        return <div>Cargando...</div>;
    }

    if (!emprendedorId) {
        return (
            <div className="card">
                <h1 className="text-3xl font-bold mb-4">Gestión de Pagos</h1>
                <Message
                    severity="warn"
                    text="Acceso restringido: Tu usuario no tiene una empresa asociada para consultar pagos."
                    className="w-full"
                />
            </div>
        );
    }

    return (
        <div className="card">
            <h1 className="text-3xl font-bold mb-4">Gestión de Pagos</h1>
            <p className="text-gray-600 mb-4">
                Aprueba o rechaza los pagos pendientes de tus clientes
            </p>
            <PagosEmprendedor emprendedorId={emprendedorId} />
        </div>
    );
}
