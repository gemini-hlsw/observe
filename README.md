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

This module contains a web-based observe client. It contains a SPA (Single-page application) which communicates to the backend using Ajax-style calls and websockets.

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

Deployment is done via a Docker image.

## Building the Docker images

When a PR is merged into `main`, CI automatically builds a Docker image called [`noirlab/gpp-obs`](https://hub.docker.com/repository/docker/noirlab/gpp-obs/general). This image contains the configurations for all 6 deployments. The 6 deployments are the combination of each of the sites (GN and GS) and each of our 3 environments (Heroku, staging and production).

The image is automatically pushed to Heroku and to the `nlsoftware` account in Dockerhub. Both Heroku applications are also released after the push.

## Releasing in Staging and Production

The image is not release automatically in these environments.

There are a bunch of shell scripts in `deploy/scripts` that should be copied on the `~/observe` directory on each server. Then the correct site needs to be configured in `config.sh`. These scripts are:
- `update.sh` (will automatically stop and restart a running server).
- `start.sh`
- `stop.sh`

A Dockerhub access token has to be configued in `config.sh` too. Ideally, a read-only token should be used.

# Configuration

The base image contains a basic configuration called `base.conf`. These same file is the one used during development.

Each server has a `site.conf` providing overrides. The `site.conf` for each server is under `deploy/confs`. This is where they should be edited, making a new release when they change.

The only things not included in `site.conf` are the secrets. Namely: the SSO service token, and the passphrases needed for TLS in Staging and Production. These should be placed in `~/observe/conf/secrets.conf` at the staging and production servers. For Heroku, the SSO service token needs to be provided as an environment variable.

For Starging and Production, the SSO service token and TLS passphrases need to be provided in a local file `~/observe/conf/secrets.conf`. The whole `~/observe/conf` directory is [bind mounted](https://docs.docker.com/storage/bind-mounts/) into the container by `start.sh`.

A typical `secrets.conf` will look like this:

```
lucuma-sso {
  service-token = "<INSERT TOKEN HERE>"
}

web-server {
    tls {
        key-store-pwd = "<INSERT PASSPHRASE HERE>"
        cert-pwd = "<INSERT PASSPHRASE HERE>"
    }
}
```

Furthermore, observe will also read a `~/observe/conf/systems.conf`, which should be used to provide frequently changing overrides. The more permanent configurations should be in `site.conf`. In particular all the systems that are to be set to `full` should be done there. Otherwise, they will remain `simulated`.

NOTE:
- To generate a service token, see the [lucuma-sso documentation](https://github.com/gemini-hlsw/lucuma-sso?tab=readme-ov-file#obtaining-a-service-jwt).
