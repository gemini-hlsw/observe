#!/bin/bash

mkdir ~/observe
cd ~/observe
curl https://raw.githubusercontent.com/gemini-hlsw/observe/refs/heads/main/deploy/scripts/config.sh >config.sh
curl https://raw.githubusercontent.com/gemini-hlsw/observe/refs/heads/main/deploy/scripts/start.sh >start.sh
curl https://raw.githubusercontent.com/gemini-hlsw/observe/refs/heads/main/deploy/scripts/stop.sh >stop.sh
curl https://raw.githubusercontent.com/gemini-hlsw/observe/refs/heads/main/deploy/scripts/update.sh >update.sh
chmod +x *.sh
mkdir ~/observe/conf
mkdir ~/observe/log