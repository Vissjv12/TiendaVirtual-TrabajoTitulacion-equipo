/** @type {import('next').NextConfig} */
const nextConfig = {
  webpack: (config, { dev, isServer }) => {
    // ✅ Sin obfuscación - el obfuscador causa bundles 3-5x más grandes
    // y 10x más lentos de parsear en el navegador. No va en producción.

    // ✅ Configuración para evitar errores de resolución de módulos
    config.resolve.fallback = {
      ...config.resolve.fallback,
      fs: false,
      net: false,
      tls: false
    };

    return config;
  },

  // ✅ Configuraciones adicionales de Next.js
  reactStrictMode: false, // Evitar doble renderizado
  swcMinify: true,

  // ✅ Configuración de transpilación para PrimeReact
  transpilePackages: ['primereact'],

  // ✅ Configuración experimental
  experimental: {
    esmExternals: 'loose',
    optimizeCss: false // Evitar conflictos con PrimeReact
  },

  // ✅ Configuración de imágenes
  images: {
    domains: ['via.placeholder.com', 'localhost', 'tienda.gadmsigchos.gob.ec'],
    dangerouslyAllowSVG: true,
    contentSecurityPolicy: "default-src 'self'; script-src 'none'; sandbox;",
    unoptimized: true
  },

  // ✅ Proxy inverso unificado hacia el API Gateway (Actividad A-13)
  // Ninguna ruta apunta directamente a los puertos internos 8081-8084.
  async rewrites() {
    const gatewayUrl = (process.env.GATEWAY_INTERNAL_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '');
    return [
      // Rutas de logos/imágenes
      { source: '/logo.png',       destination: '/layout/images/logo-dark.svg'  },
      { source: '/logo-dark.svg',  destination: '/layout/images/logo-dark.svg'  },
      { source: '/logo-white.svg', destination: '/layout/images/logo-white.svg' },

      // --- Enrutamiento hacia API Gateway (puerto 8080) ---
      {
        source: '/api/proxy/productos/:path*',
        destination: `${gatewayUrl}/api/:path*`
      },
      {
        source: '/api/proxy/inventarios/api/inventarios/:path*',
        destination: `${gatewayUrl}/api/inventarios/:path*`
      },
      {
        source: '/api/proxy/inventarios/api/movimientos/:path*',
        destination: `${gatewayUrl}/api/movimientos/:path*`
      },
      {
        source: '/api/proxy/inventarios/:path*',
        destination: `${gatewayUrl}/api/inventarios/:path*`
      },
      {
        source: '/api/proxy/ventas/api/ventas/:path*',
        destination: `${gatewayUrl}/api/ventas/:path*`
      },
      {
        source: '/api/proxy/ventas/:path*',
        destination: `${gatewayUrl}/:path*`
      },
      {
        source: '/api/proxy/auth/api/:path*',
        destination: `${gatewayUrl}/api/:path*`
      },
      {
        source: '/api/proxy/auth/:path*',
        destination: `${gatewayUrl}/:path*`
      }
    ];
  },

  // ✅ Headers de seguridad y cache
  async headers() {
    return [
      {
        // Archivos estáticos JS/CSS: cache agresivo (1 año, cambia con hash)
        source: '/_next/static/:path*',
        headers: [
          { key: 'Cache-Control', value: 'public, max-age=31536000, immutable' }
        ]
      },
      {
        // Imágenes: cache de 1 día
        source: '/_next/image',
        headers: [
          { key: 'Cache-Control', value: 'public, max-age=86400' }
        ]
      },
      {
        // Páginas: sin cache (siempre frescas)
        source: '/(.*)',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'X-Frame-Options',        value: 'SAMEORIGIN' }
        ]
      }
    ];
  },

  // ✅ Compilador: remover console.log en producción para menos overhead
  compiler: {
    removeConsole: process.env.NODE_ENV === 'production'
      ? { exclude: ['error', 'warn'] }
      : false
  }
};

module.exports = nextConfig;