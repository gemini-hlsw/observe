import { defineConfig } from 'vite';
import path from 'path';
import fs from 'fs/promises';
import mkcert from 'vite-plugin-mkcert';
import Unfonts from 'unplugin-fonts/vite'

const fixCssRoot = (opts = {}) => {
  return {
    postcssPlugin: 'postcss-fix-nested-root',
    Once(root, { result }) {
      root.walkRules((rule) => {
        if (rule.selector.includes(' :root')) {
          rule.selector = rule.selector.replace(' :root', '');
        }
      });
    },
  };
};
/**
 * Refine type to 'true' instead of 'boolean'
 * @type {true}
 */
fixCssRoot.postcss = true;

const fontImport = Unfonts({
  fontsource: {
    families: [
      'Lato'
    ],
  },
});

/**
 * Check if a file or directory exists
 * @param {import('fs').PathLike} path
 * @returns
 */
const pathExists = async (path) => {
  try {
    await fs.access(path, fs.constants.F_OK);
    return true;
  } catch (err) {
    return false;
  }
};

// https://vitejs.dev/config/
export default defineConfig(async ({ mode }) => {
  const scalaClassesDir = path.resolve(__dirname, 'target/scala-3.3.1');
  const isProduction = mode == 'production';
  const sjs = isProduction
    ? path.resolve(scalaClassesDir, 'observe_web_client-opt')
    : path.resolve(scalaClassesDir, 'observe_web_client-fastopt');
  const common = __dirname;
  const webappCommon = path.resolve(common, 'src/main/webapp/');
  const imagesCommon = path.resolve(webappCommon, 'images');
  const resourceDir = path.resolve(scalaClassesDir, 'classes');
  const lucumaCss = path.resolve(__dirname, 'target/lucuma-css');

  return {
    // TODO Remove this if we get EnvironmentPlugin to work.
    root: 'src/main/webapp',
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
        },
        {
          find: '@resources',
          replacement: resourceDir
        },
        {
          find: '/lucuma-css',
          replacement: lucumaCss,
        },
      ],
    },
    css: {
      preprocessorOptions: {
        scss: {
          charset: false,
        },
      },
      postcss: {
        plugins: [fixCssRoot],
      }
    },
    server: {
      strictPort: true,
      fsServe: {
        strict: true,
      },
      host: '0.0.0.0',
      port: 8081,
      https: true,
      proxy: {
        '/api': {
          target: 'https://127.0.0.1:7070',
          changeOrigin: true,
          secure: false,
          ws: true
        }
      },
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
      outDir: path.resolve(__dirname, 'deploy'),
    },
    plugins: [
      mkcert({ hosts: ['localhost', 'local.lucuma.xyz'] }),
      fontImport
    ],
  };
});
