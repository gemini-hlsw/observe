#!/bin/bash

mkdir ~/observe
cd ~/observe
https://raw.githubusercontent.com/gemini-hlsw/observe/refs/heads/main/deploy/scripts/config.sh >config.sh
https://raw.githubusercontent.com/gemini-hlsw/observe/refs/heads/main/deploy/scripts/start.sh >start.sh
https://raw.githubusercontent.com/gemini-hlsw/observe/refs/heads/main/deploy/scripts/stop.sh >stop.sh
https://raw.githubusercontent.com/gemini-hlsw/observe/refs/heads/main/deploy/scripts/update.sh >update.sh
chmod +x *.sh