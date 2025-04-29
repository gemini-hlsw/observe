#!/bin/bash

# Set to one of staging-gn, staging-gs, production-gs, production-gn
SITE=
# We usually want the latest version, but this allows you to roll back to a specific version
VERSION=latest
# Dockerhub username - We probably don't want to change this
DOCKERHUB_USER=nlsoftware
# Dockerhub Personal Access Token - Use a read-only token
DOCKERHUB_TOKEN=