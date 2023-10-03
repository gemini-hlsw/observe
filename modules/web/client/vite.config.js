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
  // const common = path.resolve(__dirname, 'common/');
  const common = __dirname;
  const webappCommon = path.resolve(common, 'src/main/webapp/');
  const imagesCommon = path.resolve(webappCommon, 'images');
  const publicDirProd = path.resolve(common, 'src/main/public');
  const publicDirDev = path.resolve(common, 'src/main/publicdev');
  const resourceDir = path.resolve(scalaClassesDir, 'classes');
  const lucumaCss = path.resolve(__dirname, 'target/lucuma-css');

  if (!(await pathExists(publicDirDev))) {
    await fs.mkdir(publicDirDev);
  }
  const localConf = path.resolve(publicDirProd, 'local.conf.json');
  const devConf = path.resolve(publicDirProd, 'environments.conf.json');

  const publicDirProdFiles = (await fs.readdir(publicDirProd)).filter(
    (file) =>
      !file.endsWith('local.conf.json') &&
      !file.endsWith('environments.conf.json') &&
      !file.endsWith('README.txt'),
  );

  await Promise.all([
    fs.copyFile(
      (await pathExists(localConf)) ? localConf : devConf,
      path.resolve(publicDirDev, 'environments.conf.json'),
    ),
    ...publicDirProdFiles.map((file) =>
      fs.copyFile(path.resolve(publicDirProd, file), path.resolve(publicDirDev, file)),
    ),
  ]);

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
      mkcert({ hosts: ['localhost', 'local.lucuma.xyz'] }),
      fontImport
    ],
  };
});
