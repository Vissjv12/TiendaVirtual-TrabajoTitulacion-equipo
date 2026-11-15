'use client';
import { Button } from 'primereact/button';
import React from 'react';

/**
 * Página 404 segura frente a NotFoundBoundary de Next.js.
 * Evita manipular <link id="theme-link"> con changeTheme y navega con recarga limpia.
 */
function NotFound() {
    const navigateToHome = () => {
        window.location.assign('/');
    };

    return (
        <React.Fragment>
            <div
                className="overflow-hidden m-0 relative h-screen bg-cover bg-center"
                style={{ backgroundImage: 'url(/layout/images/pages/exception/bg-404.png)' }}
            >
                <div className="text-center text-5xl pt-5 font-bold text-white">
                    <div className="inline-block py-1 px-2 text-white surface-900 mr-2">
                        <span>404</span>
                    </div>
                    <span className="text-color">Página no encontrada</span>
                </div>
                <div className="w-full absolute bottom-0 text-center surface-900 h-14rem">
                    <div className="w-full absolute text-center z-1" style={{ top: '-36px' }}>
                        <img src="/layout/images/pages/exception/icon-error.png" alt="Página no encontrada" />
                    </div>
                    <div className="mx-auto px-3 relative text-white" style={{ maxWidth: '29rem', top: '36px' }}>
                        <div className="p-3">
                            <h3 className="m-0 mb-2 text-white">Página no encontrada</h3>
                            <p className="m-0">La ruta solicitada no existe o ya no está disponible.</p>
                        </div>
                        <Button onClick={navigateToHome} label="Ir al Inicio" icon="pi pi-home" />
                    </div>
                </div>
            </div>
        </React.Fragment>
    );
}

export default NotFound;
