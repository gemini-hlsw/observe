const { defineConfig } = require('vite');
const react = require('@vitejs/plugin-react');
const path = require('path');
const fs = require('fs');
const mkcert = require('vite-plugin-mkcert');

// https://vitejs.dev/config/
module.exports = ({ command, mode }) => {
  const scalaClassesDir = path.resolve(__dirname, 'target/scala-3.2.1-RC1');
  const isProduction = mode == 'production';
  const sjs = isProduction
    ? path.resolve(scalaClassesDir, 'new_web-opt')
    : path.resolve(scalaClassesDir, 'new_web-fastopt');
  const common = path.resolve(__dirname, 'common/');
  const webappCommon = path.resolve(common, 'src/main/webapp/');
  const imagesCommon = path.resolve(webappCommon, 'images');
  const publicDirProd = path.resolve(common, 'src/main/public');
  const publicDirDev = path.resolve(common, 'src/main/publicdev');

  const publicDir = mode == 'production' ? publicDirProd : publicDirDev;
  return {
    // TODO Remove this if we get EnvironmentPlugin to work.
    root: 'src/main/webapp',
    publicDir: publicDir,
    envPrefix: ['VITE_', 'CATS_EFFECT_'],
    resolve: {
      dedupe: ['react-is'],
      alias: [
        {
          find: 'process',
          replacement: 'process/browser',
        },
        {
          find: '@sjs',
          replacement: sjs,
        },
        {
          find: '/common',
          replacement: webappCommon,
        },
        {
          find: '/images',
          replacement: imagesCommon,
        }
      ],
    },
    css: {
      preprocessorOptions: {
        scss: {
          charset: false,
        },
      },
    },
    server: {
      strictPort: true,
      fsServe: {
        strict: true,
      },
      host: '0.0.0.0',
      port: 8080,
      https: true,
      watch: {
        ignored: [
          function ignoreThisPath(_path) {
            const sjsIgnored =
              _path.includes('/target/stream') ||
              _path.includes('/zinc/') ||
              _path.includes('/classes') ||
              _path.endsWith('.tmp');
            return sjsIgnored;
          }
        ]
      }
    },
    build: {
      emptyOutDir: true,
      chunkSizeWarningLimit: 20000,
      outDir: path.resolve(__dirname, 'deploy/static'),
    },
    plugins: [
      isProduction
        ? null
        : mkcert.default({ hosts: ['localhost', 'local.lucuma.xyz'] }),
      react(),
    ],
  };
};
