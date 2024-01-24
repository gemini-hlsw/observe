# Observe

This set of modules contains a web server and the client for the Observe tool

## Web backend

The backend is written with [http4s](http://http4s.org), it exposes a REST API and can provide static files for the UI. It is intended to run in the same process as the observe-server

## sbt-revolver

This project uses an extra plugin

- [sbt-revolver](https://github.com/spray/sbt-revolver): This plugin allows to restart the web server and trigger a recompilation when the source code changes

## How to compile and start the server (Good for backend development)

Go to the JVM project

```
    project observe_web_server
    ~reStart
```

Now every time a file is changed in the server code, the server files will be compiled, then the server will restart

By default the REST backend will run on port 7070

It can be stopped by executing

```
   reStop
```

from within the project.

# Observe Web Client

This module contains a web-based observe client. It contains a SPA (Single-page application) which communicates to the backend using Ajax-style calls and websockets

# How to run/develop the client

<!-- For the common case we want to develop the client but we also need to run the backend.

an sbt task

```
startObserveAll
```

Will do the following:

- Launch the backend on the background
- Pack the client going through scala.js and webpack
- Launch webpack-dev-server with a proxy to the backend

Now you can open the client at

http://localhost:8081

if you want to update the client and get automatic reload do in sbt:

```
    project observe_web_client
    ~fastOptJS
```

and to stop all the processes you can do

```
stopObserveAll
``` -->

# Deployment

Deployment is done via Docker images.

## Staging

Make sure you have both `docker` and `heroku` CLIs installed and working.

If you haven't already, run

```
heroku login
heroku container:login
```

This will give your system access to Heroku's Docker registry.

To deploy to Heroku, run in `sbt`:

```
deploy_observe_server_staging/docker:publish
```

This will build and push the image to Heroku's Docker registry, but it won't publish it yet. To publish it, run from the shell:

```
heroku container:release web -a observe-staging
```

The new version should be accessible now at [https://observe-staging.lucuma.xyz](https://observe-staging.lucuma.xyz).

## Test and Production

To build test and production images, run from `sbt`:

```
deploy_observe_server_gn_test/docker:publishLocal
deploy_observe_server_gs_test/docker:publishLocal
deploy_observe_server_gn/docker:publishLocal
deploy_observe_server_gs/docker:publishLocal
```

These images must then be pushed to a registry reachable by the testing/production servers.

In order for these images to run, their container must have a [bind mount](https://docs.docker.com/storage/bind-mounts/) providing the TLS configuration, consisting of a file called `tls.conf` containing:

```
tls {
    key-store = "/tls/cacerts.jks"
    key-store-pwd = "passphrase"
    cert-pwd = "passphrase"
}
```

as well as the file with the certificates (`cacerts.jks` in the example). The directory with these files must be mounted at `/tls` in the container.

Furthermore, an environment variable `SSO_SERVICE_JWT` must be provided with the production SSO Service token for the server to access the ODB. To generate a service token, see the [lucuma-sso documentation](https://github.com/gemini-hlsw/lucuma-sso?tab=readme-ov-file#obtaining-a-service-jwt).

The SSL port is by default 9090 but can be overriden by specifying the `PORT` environment variable. This port must be exposed in the container.

For example:

```
docker run -p 443:9090 --mount type=bind,src=</localdir>,dst=/tls -e SSO_SERVICE_JWT=<service-token> observe-gn-test:<version>
```

where `/localdir` contains `tls.conf` and `cacerts.jks`.
