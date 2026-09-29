#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
echo "Swagger: http://localhost:8080/swagger-ui.html | operador / operador-demo"
exec java -jar bin/sus-agenda.jar --spring.profiles.active=demo
