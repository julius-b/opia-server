#!/usr/bin/env bash
set -e
rsync -a --exclude build --exclude uploads . root@opia.app:~/opia-server/
