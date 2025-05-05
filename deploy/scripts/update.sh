#!/bin/bash

SCRIPTS_DIR=~/observe

. $SCRIPTS_DIR/config.sh

$SCRIPTS_DIR/stop.sh

IMAGE=noirlab/gpp-obs:$VERSION

echo "Logging into DockerHub..."
echo $DOCKERHUB_TOKEN | docker login -u $DOCKERHUB_USER --password-stdin

echo "Pulling image [$IMAGE]..."
docker pull $IMAGE

$SCRIPTS_DIR/start.sh
