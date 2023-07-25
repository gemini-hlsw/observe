import "./styles/style.scss";
import '/lucuma-css/lucuma-ui-login.scss';
import '/lucuma-css/lucuma-ui-table.scss';
import "primereact/resources/primereact.min.css";     // core css
import "primeicons/primeicons.css";                   // icons

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
