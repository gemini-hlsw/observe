#!/bin/bash

SCRIPTS_DIR=~/observe

SITE=$(cat "$SCRIPTS_DIR/site")

$SCRIPTS_DIR/stop.sh

docker pull noirlab/gpp-obs-$SITE:latest

$SCRIPTS_DIR/start.sh
