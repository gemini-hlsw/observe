- Place these files on `~/observe` in each server.
- Set the `SITE`, `DOCKERHUB_TOKEN` and `LOCAL_LOG_DIR` variables in `config.sh`.

You can use the commands in `install.sh` to copy the latest version of the scripts:
```
curl https://raw.githubusercontent.com/gemini-hlsw/observe/refs/heads/main/deploy/scripts/install.sh >install.sh
chmod +x install.sh
./install.sh
```