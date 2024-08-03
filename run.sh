#!/usr/bin/env bash
set -e
./gradlew publishImageToLocalRegistry
docker compose down
docker compose up -d
