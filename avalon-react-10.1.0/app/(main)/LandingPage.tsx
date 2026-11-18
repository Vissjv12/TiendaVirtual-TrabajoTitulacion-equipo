'use client';

import React, { useEffect } from 'react';
import HeroSection from './components/HeroSection';
import FeaturedProducts from './components/FeaturedProducts';
import AboutSection from './components/AboutSection';
import LocationSection from './components/LocationSection';
import StatsSection from './components/StatsSection';
import visitasService from '../../services/visitasService';

const LandingPage: React.FC = () => {
    useEffect(() => {
        void visitasService.registrarVisitaUnica();
    }, []);

    return (
        <div className="landing-page">
            {/* Sección Hero */}
            <section id="hero-section">
                <HeroSection />
            </section>

            {/* Estadísticas */}
            <section id="stats-section">
                <StatsSection />
            </section>

            {/* Productos Destacados */}
            <section id="productos-destacados">
                <FeaturedProducts />
            </section>

            {/* Sobre Nosotros */}
            <section id="about-section">
                <AboutSection />
            </section>

            {/* Ubicación (esto puede ser tu sección de contacto) */}
            <section id="contact-section">
                <LocationSection />
            </section>
        </div>
    );
};

export default LandingPage;
