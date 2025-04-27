#!/bin/bash

echo "Starting Observe Server..."
docker run --name observe-server -d --network host  -e SSO_SERVICE_JWT=$SSO_SERVICE_JWT --mount type=bind,src=/home/software/observe/conf,dst=/opt/docker/conf/local noirlab/gpp-obs:latest