'use client';

import React, { useState, useEffect } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { Card } from 'primereact/card';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { Password } from 'primereact/password';
import { MultiSelect } from 'primereact/multiselect';
import { Toast } from 'primereact/toast';
import { Divider } from 'primereact/divider';
import { Message } from 'primereact/message';
import { Skeleton } from 'primereact/skeleton';
import { Checkbox } from 'primereact/checkbox';
import usuariosService, { Usuario, ActualizarUsuarioRequest, ROLES_DISPONIBLES } from '@/services/usuariosService';
import EmpresaVinculadaCard from './EmpresaVinculadaCard';

interface FormData {
    username: string;
    email: string;
    nombre: string;
    apellido: string;
    roles: string[];
    cambiarPassword: boolean;
    password: string;
    confirmPassword: string;
}

interface FormErrors {
    username?: string;
    password?: string;
    confirmPassword?: string;
    email?: string;
    nombre?: string;
    apellido?: string;
    roles?: string;
    cambiarPassword?: string;
}

export default function EditarUsuarioPage() {
    const router = useRouter();
    const params = useParams();
    const usuarioId = Number(params.id);
    
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [usuario, setUsuario] = useState<Usuario | null>(null);
    const [formData, setFormData] = useState<FormData>({
        username: '',
        email: '',
        nombre: '',
        apellido: '',
        roles: [],
        cambiarPassword: false,
        password: '',
        confirmPassword: ''
    });
    const [errors, setErrors] = useState<FormErrors>({});
    const [validacionUsername, setValidacionUsername] = useState<string>('');
    const [validacionEmail, setValidacionEmail] = useState<string>('');
    const toast = React.useRef<Toast>(null);

    useEffect(() => {
        if (usuarioId) {
            cargarUsuario();
        }
    }, [usuarioId]);

    const cargarUsuario = async () => {
        setLoading(true);
        try {
            const response = await usuariosService.obtenerUsuarioPorId(usuarioId);
            if (response.success && response.data) {
                const usuarioData = response.data;
                setUsuario(usuarioData);
                setFormData({
                    username: usuarioData.username,
                    email: usuarioData.email,
                    nombre: usuarioData.nombre,
                    apellido: usuarioData.apellido,
                    roles: usuarioData.roles,
                    cambiarPassword: false,
                    password: '',
                    confirmPassword: ''
                });
            } else {
                mostrarError('Error al cargar usuario: ' + response.message);
                router.push('/administrador/usuarios');
            }
        } catch (error) {
            console.error('Error:', error);
            mostrarError('Error de conexión al cargar usuario');
            router.push('/administrador/usuarios');
        } finally {
            setLoading(false);
        }
    };

    const mostrarExito = (mensaje: string) => {
        toast.current?.show({ severity: 'success', summary: 'Éxito', detail: mensaje });
    };

    const mostrarError = (mensaje: string) => {
        toast.current?.show({ severity: 'error', summary: 'Error', detail: mensaje });
    };

    const validarFormulario = (): boolean => {
        const newErrors: FormErrors = {};

        // Validar username
        const validacionUsernameResult = usuariosService.validarFormatoUsername(formData.username);
        if (!validacionUsernameResult.valido) {
            newErrors.username = validacionUsernameResult.mensaje;
        }

        // Validar email
        if (!formData.email) {
            newErrors.email = 'El email es requerido';
        } else if (!usuariosService.validarFormatoEmail(formData.email)) {
            newErrors.email = 'Formato de email inválido';
        }

        // Validar password solo si se está cambiando
        if (formData.cambiarPassword) {
            const validacionPassword = usuariosService.validarPassword(formData.password);
            if (!validacionPassword.valida) {
                newErrors.password = validacionPassword.mensaje;
            }

            if (formData.password !== formData.confirmPassword) {
                newErrors.confirmPassword = 'Las contraseñas no coinciden';
            }
        }

        // Validar nombre
        if (!formData.nombre.trim()) {
            newErrors.nombre = 'El nombre es requerido';
        }

        // Validar apellido
        if (!formData.apellido.trim()) {
            newErrors.apellido = 'El apellido es requerido';
        }

        // Validar roles
        const validacionRoles = usuariosService.validarRoles(formData.roles);
        if (!validacionRoles.validos) {
            newErrors.roles = validacionRoles.mensaje;
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const verificarUsernameUnico = async (username: string) => {
        if (username.length < 4 || username === usuario?.username) return;
        
        try {
            const response = await usuariosService.validarUsername(username);
            if (response.success && response.data !== undefined) {
                setValidacionUsername(response.data ? 'Username disponible' : 'Username ya existe');
            }
        } catch (error) {
            setValidacionUsername('Error al validar username');
        }
    };

    const verificarEmailUnico = async (email: string) => {
        if (!usuariosService.validarFormatoEmail(email) || email === usuario?.email) return;
        
        try {
            const response = await usuariosService.validarEmail(email);
            if (response.success && response.data !== undefined) {
                setValidacionEmail(response.data ? 'Email disponible' : 'Email ya existe');
            }
        } catch (error) {
            setValidacionEmail('Error al validar email');
        }
    };

    const handleInputChange = (field: keyof FormData, value: any) => {
        setFormData(prev => ({
            ...prev,
            [field]: value
        }));

        // Limpiar error del campo
        if (errors[field]) {
            setErrors(prev => ({
                ...prev,
                [field]: undefined
            }));
        }

        // Validaciones en tiempo real
        if (field === 'username' && value.length >= 4) {
            verificarUsernameUnico(value);
        }
        if (field === 'email' && usuariosService.validarFormatoEmail(value)) {
            verificarEmailUnico(value);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        
        if (!validarFormulario()) {
            mostrarError('Por favor corrija los errores en el formulario');
            return;
        }

        // Verificar que username y email estén disponibles (si cambiaron)
        if (validacionUsername.includes('ya existe') || validacionEmail.includes('ya existe')) {
            mostrarError('Username o email ya están en uso');
            return;
        }

        setSaving(true);
        try {
            const updateData: ActualizarUsuarioRequest = {
                username: formData.username,
                email: formData.email,
                nombre: formData.nombre,
                apellido: formData.apellido,
                roles: formData.roles
            };

            // Solo incluir password si se está cambiando
            if (formData.cambiarPassword) {
                updateData.password = formData.password;
            }

            const response = await usuariosService.actualizarUsuario(usuarioId, updateData);
            
            if (response.success) {
                mostrarExito('Usuario actualizado exitosamente');
                setTimeout(() => {
                    router.push('/administrador/usuarios');
                }, 1500);
            } else {
                mostrarError('Error al actualizar usuario: ' + response.message);
            }
        } catch (error) {
            console.error('Error:', error);
            mostrarError('Error de conexión al actualizar usuario');
        } finally {
            setSaving(false);
        }
    };

    if (loading) {
        return (
            <div className="grid">
                <div className="col-12">
                    <Card>
                        <div className="flex justify-content-between align-items-center mb-4">
                            <Skeleton width="20rem" height="2rem" />
                            <Skeleton width="8rem" height="3rem" />
                        </div>
                        <div className="grid">
                            <div className="col-12 md:col-6">
                                <Skeleton width="100%" height="1rem" className="mb-2" />
                                <Skeleton width="100%" height="3rem" />
                            </div>
                            <div className="col-12 md:col-6">
                                <Skeleton width="100%" height="1rem" className="mb-2" />
                                <Skeleton width="100%" height="3rem" />
                            </div>
                            <div className="col-12 md:col-6">
                                <Skeleton width="100%" height="1rem" className="mb-2" />
                                <Skeleton width="100%" height="3rem" />
                            </div>
                            <div className="col-12 md:col-6">
                                <Skeleton width="100%" height="1rem" className="mb-2" />
                                <Skeleton width="100%" height="3rem" />
                            </div>
                        </div>
                    </Card>
                </div>
            </div>
        );
    }

    return (
        <div className="grid">
            <Toast ref={toast} />
            
            <div className="col-12">
                <Card>
                    <div className="flex justify-content-between align-items-center mb-4">
                        <div>
                            <h2 className="text-2xl font-bold text-900 m-0">Editar Usuario</h2>
                            <p className="text-600 m-0 mt-1">
                                Modificar información de <strong>{usuario?.username}</strong>
                            </p>
                        </div>
                        <Button 
                            label="Volver" 
                            icon="pi pi-arrow-left" 
                            className="p-button-outlined"
                            onClick={() => router.push('/administrador/usuarios')}
                        />
                    </div>

                    <form onSubmit={handleSubmit}>
                        <div className="grid">
                            {/* Información de cuenta */}
                            <div className="col-12">
                                <h3 className="text-lg font-semibold text-900 mb-3">
                                    <i className="pi pi-user mr-2"></i>
                                    Información de Cuenta
                                </h3>
                            </div>

                            <div className="col-12 md:col-6">
                                <label htmlFor="username" className="block text-900 font-medium mb-2">
                                    Username *
                                </label>
                                <InputText
                                    id="username"
                                    value={formData.username}
                                    onChange={(e) => handleInputChange('username', e.target.value)}
                                    className={`w-full ${errors.username ? 'p-invalid' : ''}`}
                                    placeholder="Ingrese el username"
                                />
                                {errors.username && (
                                    <small className="p-error block mt-1">{errors.username}</small>
                                )}
                                {validacionUsername && (
                                    <small className={`block mt-1 ${validacionUsername.includes('disponible') ? 'text-green-600' : 'text-red-600'}`}>
                                        {validacionUsername}
                                    </small>
                                )}
                            </div>

                            <div className="col-12 md:col-6">
                                <label htmlFor="email" className="block text-900 font-medium mb-2">
                                    Email *
                                </label>
                                <InputText
                                    id="email"
                                    type="email"
                                    value={formData.email}
                                    onChange={(e) => handleInputChange('email', e.target.value)}
                                    className={`w-full ${errors.email ? 'p-invalid' : ''}`}
                                    placeholder="Ingrese el email"
                                />
                                {errors.email && (
                                    <small className="p-error block mt-1">{errors.email}</small>
                                )}
                                {validacionEmail && (
                                    <small className={`block mt-1 ${validacionEmail.includes('disponible') ? 'text-green-600' : 'text-red-600'}`}>
                                        {validacionEmail}
                                    </small>
                                )}
                            </div>

                            {/* Cambiar contraseña */}
                            <div className="col-12">
                                <div className="flex align-items-center">
                                    <Checkbox
                                        id="cambiarPassword"
                                        checked={formData.cambiarPassword}
                                        onChange={(e) => handleInputChange('cambiarPassword', e.checked)}
                                    />
                                    <label htmlFor="cambiarPassword" className="ml-2 text-900 font-medium">
                                        Cambiar contraseña
                                    </label>
                                </div>
                            </div>

                            {formData.cambiarPassword && (
                                <>
                                    <div className="col-12 md:col-6">
                                        <label htmlFor="password" className="block text-900 font-medium mb-2">
                                            Nueva Contraseña *
                                        </label>
                                        <Password
                                            id="password"
                                            value={formData.password}
                                            onChange={(e) => handleInputChange('password', e.target.value)}
                                            className={`w-full ${errors.password ? 'p-invalid' : ''}`}
                                            placeholder="Ingrese la nueva contraseña"
                                            toggleMask
                                            feedback={false}
                                        />
                                        {errors.password && (
                                            <small className="p-error block mt-1">{errors.password}</small>
                                        )}
                                    </div>

                                    <div className="col-12 md:col-6">
                                        <label htmlFor="confirmPassword" className="block text-900 font-medium mb-2">
                                            Confirmar Nueva Contraseña *
                                        </label>
                                        <Password
                                            id="confirmPassword"
                                            value={formData.confirmPassword}
                                            onChange={(e) => handleInputChange('confirmPassword', e.target.value)}
                                            className={`w-full ${errors.confirmPassword ? 'p-invalid' : ''}`}
                                            placeholder="Confirme la nueva contraseña"
                                            toggleMask
                                            feedback={false}
                                        />
                                        {errors.confirmPassword && (
                                            <small className="p-error block mt-1">{errors.confirmPassword}</small>
                                        )}
                                    </div>
                                </>
                            )}

                            {/* Información personal */}
                            <div className="col-12">
                                <Divider />
                                <h3 className="text-lg font-semibold text-900 mb-3">
                                    <i className="pi pi-id-card mr-2"></i>
                                    Información Personal
                                </h3>
                            </div>

                            <div className="col-12 md:col-6">
                                <label htmlFor="nombre" className="block text-900 font-medium mb-2">
                                    Nombre *
                                </label>
                                <InputText
                                    id="nombre"
                                    value={formData.nombre}
                                    onChange={(e) => handleInputChange('nombre', e.target.value)}
                                    className={`w-full ${errors.nombre ? 'p-invalid' : ''}`}
                                    placeholder="Ingrese el nombre"
                                />
                                {errors.nombre && (
                                    <small className="p-error block mt-1">{errors.nombre}</small>
                                )}
                            </div>

                            <div className="col-12 md:col-6">
                                <label htmlFor="apellido" className="block text-900 font-medium mb-2">
                                    Apellido *
                                </label>
                                <InputText
                                    id="apellido"
                                    value={formData.apellido}
                                    onChange={(e) => handleInputChange('apellido', e.target.value)}
                                    className={`w-full ${errors.apellido ? 'p-invalid' : ''}`}
                                    placeholder="Ingrese el apellido"
                                />
                                {errors.apellido && (
                                    <small className="p-error block mt-1">{errors.apellido}</small>
                                )}
                            </div>

                            {/* Roles y permisos */}
                            <div className="col-12">
                                <Divider />
                                <h3 className="text-lg font-semibold text-900 mb-3">
                                    <i className="pi pi-shield mr-2"></i>
                                    Roles y Permisos
                                </h3>
                                <Message 
                                    severity="info" 
                                    text="Modifique los roles del usuario. Los cambios se aplicarán inmediatamente."
                                    className="mb-3"
                                />
                            </div>

                            <div className="col-12">
                                <label htmlFor="roles" className="block text-900 font-medium mb-2">
                                    Roles del Usuario *
                                </label>
                                <MultiSelect
                                    id="roles"
                                    value={formData.roles}
                                    options={ROLES_DISPONIBLES.map(rol => ({
                                        label: rol.label,
                                        value: rol.value,
                                        description: rol.description
                                    }))}
                                    onChange={(e) => handleInputChange('roles', e.value)}
                                    placeholder="Seleccione los roles"
                                    className={`w-full ${errors.roles ? 'p-invalid' : ''}`}
                                    itemTemplate={(option) => (
                                        <div className="flex flex-column">
                                            <span className="font-bold">{option.label}</span>
                                            <small className="text-600">{option.description}</small>
                                        </div>
                                    )}
                                    selectedItemTemplate={(option) => option.label}
                                />
                                {errors.roles && (
                                    <small className="p-error block mt-1">{errors.roles}</small>
                                )}
                            </div>

                            {/* Vista previa de roles seleccionados */}
                            {formData.roles.length > 0 && (
                                <div className="col-12">
                                    <h4 className="text-md font-medium text-900 mb-2">Roles Actuales:</h4>
                                    <div className="flex flex-wrap gap-2">
                                        {formData.roles.map((rol, index) => {
                                            const rolInfo = usuariosService.obtenerInfoRol(rol);
                                            return (
                                                <div key={index} className="border-1 border-300 border-round p-2 bg-gray-50">
                                                    <div className="flex align-items-center gap-2">
                                                        <i className="pi pi-shield text-600"></i>
                                                        <div>
                                                            <div className="font-bold text-sm">{rolInfo.label}</div>
                                                            <div className="text-xs text-600">{rolInfo.description}</div>
                                                        </div>
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            )}

                            {/* Botones de acción */}
                            <div className="col-12">
                                <Divider />
                                <div className="flex justify-content-end gap-2">
                                    <Button
                                        label="Cancelar"
                                        icon="pi pi-times"
                                        className="p-button-text"
                                        onClick={() => router.push('/administrador/usuarios')}
                                        disabled={saving}
                                    />
                                    <Button
                                        label="Guardar Cambios"
                                        icon="pi pi-check"
                                        type="submit"
                                        loading={saving}
                                        disabled={saving}
                                    />
                                </div>
                            </div>
                        </div>
                    </form>
                </Card>

                {/* T11: vinculación emprendedor-empresa */}
                {usuario && (
                    <EmpresaVinculadaCard
                        usuarioId={usuarioId}
                        rolesGuardados={usuario.roles}
                        empresaIdInicial={usuario.empresaId ?? null}
                        onExito={mostrarExito}
                        onError={mostrarError}
                    />
                )}
            </div>
        </div>
    );
}