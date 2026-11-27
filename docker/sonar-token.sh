#!/bin/sh
# Creates a SonarQube user token for the local server of docker/compose.yaml (profile sonar) and prints it once on
# stdout. Export it in your shell for the scan; keep it out of the repository:
#
#   export SONAR_TOKEN="$(docker/sonar-token.sh)"
#
#   $1                  token name (default keycloak-mcp-<epoch seconds>; names must be unique per user)
#   SONAR_HOST_URL      SonarQube base URL (default http://127.0.0.1:9000)
#   SONAR_USERNAME      user the token belongs to (default admin)
#   SONAR_PASSWORD      that user's password (default admin)
set -eu

url="${SONAR_HOST_URL:-http://127.0.0.1:9000}"
name="${1:-keycloak-mcp-$(date +%s)}"
for tool in curl jq; do
    command -v "$tool" >/dev/null || { echo "sonar-token.sh: $tool is required" >&2; exit 1; }
done

token=$(curl -sf -u "${SONAR_USERNAME:-admin}:${SONAR_PASSWORD:-admin}" \
    --data-urlencode "name=$name" \
    --data-urlencode type=USER_TOKEN \
    "$url/api/user_tokens/generate" | jq -r '.token // empty') || true
if [ -z "$token" ]; then
    echo "sonar-token.sh: no token from $url (server down, wrong credentials, or name $name taken)" >&2
    exit 1
fi
echo "$token"
