#!/bin/bash

echo "Stoping Observe Server..."
docker kill observe-server
docker rm observe-server
