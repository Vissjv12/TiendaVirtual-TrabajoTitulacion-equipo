'use client';

import React, { useEffect, useMemo, useState } from 'react';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { Dropdown } from 'primereact/dropdown';
import { Message } from 'primereact/message';
import usuariosService from '@/services/usuariosService';
import categoriasEmpresasService from '@/services/categoriasEmpresasService';
import { EmpresaBasica, esEmprendedor, mensajeResultadoVinculacion, nombreEmpresa, opcionesEmpresasVinculables } from '@/services/empresaVinculacion';

interface Props {
    usuarioId: number;
    /** Roles GUARDADOS del usuario (no los del formulario sin guardar): el backend exige ROLE_EMP ya asignado. */
    rolesGuardados: string[];
    empresaIdInicial?: number | null;
    onExito: (mensaje: string) => void;
    onError: (mensaje: string) => void;
}

/** T11: vincular o desvincular la empresa de un emprendedor (solo ADMIN). */
export default function EmpresaVinculadaCard({ usuarioId, rolesGuardados, empresaIdInicial, onExito, onError }: Props) {
    const [empresas, setEmpresas] = useState<EmpresaBasica[]>([]);
    const [empresaActual, setEmpresaActual] = useState<number | null>(empresaIdInicial ?? null);
    const [seleccion, setSeleccion] = useState<number | null>(empresaIdInicial ?? null);
    const [cargando, setCargando] = useState(true);
    const [procesando, setProcesando] = useState(false);
    const emprendedor = esEmprendedor(rolesGuardados);

    useEffect(() => {
        setEmpresaActual(empresaIdInicial ?? null);
        setSeleccion(empresaIdInicial ?? null);
    }, [empresaIdInicial]);

    useEffect(() => {
        if (!emprendedor) {
            setCargando(false);
            return;
        }
        let activo = true;
        (async () => {
            const respuesta = await categoriasEmpresasService.obtenerEmpresas();
            if (!activo) return;
            if (respuesta.success && respuesta.data) {
                setEmpresas(respuesta.data.map((e) => ({ id: e.id, nombre: e.nombre, activo: e.activo })));
            } else {
                onError('No se pudo cargar la lista de empresas: ' + respuesta.message);
            }
            setCargando(false);
        })();
        return () => {
            activo = false;
        };
        // onError es estable para esta tarjeta; recargar solo si cambia el rol
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [emprendedor]);

    const opciones = useMemo(() => opcionesEmpresasVinculables(empresas, empresaActual), [empresas, empresaActual]);

    const vincular = async () => {
        if (seleccion == null) return;
        setProcesando(true);
        const r = await usuariosService.vincularEmpresa(usuarioId, seleccion);
        setProcesando(false);
        if (r.success && r.data) {
            setEmpresaActual(r.data.empresaId);
            onExito(mensajeResultadoVinculacion(r.data.resultado, r.data.requiereNuevoLogin));
        } else {
            onError(r.message || 'No se pudo vincular la empresa');
        }
    };

    const desvincular = async () => {
        if (!window.confirm('¿Desvincular la empresa de este emprendedor? Perderá el acceso a su gestión al volver a iniciar sesión.')) {
            return;
        }
        setProcesando(true);
        const r = await usuariosService.desvincularEmpresa(usuarioId);
        setProcesando(false);
        if (r.success && r.data) {
            setEmpresaActual(null);
            setSeleccion(null);
            onExito(mensajeResultadoVinculacion(r.data.resultado, r.data.requiereNuevoLogin));
        } else {
            onError(r.message || 'No se pudo desvincular la empresa');
        }
    };

    return (
        <Card title="Empresa vinculada" className="mt-3">
            {!emprendedor ? (
                <Message severity="info" text="Solo los usuarios con rol Emprendedor (guardado) pueden vincularse a una empresa." />
            ) : (
                <div className="grid">
                    <div className="col-12">
                        <span className="text-600">Empresa actual: </span>
                        <span className="font-bold">{nombreEmpresa(empresas, empresaActual)}</span>
                    </div>
                    <div className="col-12 md:col-8">
                        <Dropdown
                            value={seleccion}
                            options={opciones}
                            optionLabel="label"
                            optionValue="value"
                            optionDisabled="disabled"
                            onChange={(e) => setSeleccion(e.value)}
                            placeholder={cargando ? 'Cargando empresas...' : 'Seleccione una empresa activa'}
                            filter
                            className="w-full"
                            disabled={cargando || procesando}
                            emptyMessage="No hay empresas activas"
                        />
                    </div>
                    <div className="col-12 md:col-4 flex gap-2">
                        <Button
                            label="Vincular"
                            icon="pi pi-link"
                            onClick={vincular}
                            loading={procesando}
                            disabled={cargando || procesando || seleccion == null || seleccion === empresaActual}
                        />
                        <Button
                            label="Desvincular"
                            icon="pi pi-times"
                            className="p-button-outlined p-button-danger"
                            onClick={desvincular}
                            disabled={procesando || empresaActual == null}
                        />
                    </div>
                    <div className="col-12">
                        <small className="text-600">
                            Una empresa solo puede pertenecer a un emprendedor. El cambio se aplica cuando el emprendedor vuelve a iniciar sesión.
                        </small>
                    </div>
                </div>
            )}
        </Card>
    );
}
