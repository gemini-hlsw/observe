import "./styles/style.scss";

import { Main } from "@sjs/main.js";

// Setting this here shouldn't be necessary if we get `vite-plugin-environment` to work.
// but for now we can survive setting this only on dev
if (!process) {
  process = {
    env: {},
  };
}
if (import.meta.env.DEV) {
  process.env = { CATS_EFFECT_TRACING_MODE: "none" };
}

Main.runIOApp();

if (import.meta.hot) {
  import.meta.hot.accept();
}
