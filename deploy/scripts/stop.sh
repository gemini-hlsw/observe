#!/bin/bash

echo "Stopping Observe Server..."
docker kill observe-server
docker rm observe-server
