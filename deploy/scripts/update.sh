#!/bin/bash

SCRIPTS_DIR=~/observe

. $SCRIPTS_DIR/config.sh

$SCRIPTS_DIR/stop.sh

IMAGE=noirlab/gpp-obs-$SITE:$VERSION

echo "Pulling image [$IMAGE]..."
docker pull $IMAGE

$SCRIPTS_DIR/start.sh
