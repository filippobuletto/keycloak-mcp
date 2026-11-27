#!/bin/sh
# Adds the keycloak-mcp HTTP objects of docker/master.json to the master realm of compose Keycloak: the client scope
# mcp:tools, the realm role mcp-admin, the clients keycloak-mcp and keycloak-mcp-server, and the users mcp-user,
# mcp-viewer and mcp-guest (password = username). Realm import cannot merge into master, so this uses the Admin API.
# Objects that already exist are left as they are, so running it again changes nothing.
#
#   KEYCLOAK_URL       Keycloak base URL (default http://localhost:8080)
#   KEYCLOAK_USERNAME  master admin (default admin)
#   KEYCLOAK_PASSWORD  that admin's password (default admin)
set -eu

url="${KEYCLOAK_URL:-http://localhost:8080}"
fixture="$(dirname "$0")/master.json"
for tool in curl jq; do
    command -v "$tool" >/dev/null || { echo "seed-master.sh: $tool is required" >&2; exit 1; }
done

token=$(curl -sf \
    --data-urlencode grant_type=password \
    --data-urlencode client_id=admin-cli \
    --data-urlencode "username=${KEYCLOAK_USERNAME:-admin}" \
    --data-urlencode "password=${KEYCLOAK_PASSWORD:-admin}" \
    "$url/realms/master/protocol/openid-connect/token" | jq -r '.access_token // empty')
if [ -z "$token" ]; then
    echo "seed-master.sh: admin sign-in to $url failed" >&2
    exit 1
fi

# Partial import skips client scopes, and clients can only reference scopes that exist, so scopes go first
jq -c '.clientScopes[]' "$fixture" | while read -r scope; do
    status=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $token" \
        -H 'Content-Type: application/json' -d "$scope" "$url/admin/realms/master/client-scopes")
    case "$status" in
    201) echo "client scope $(printf '%s' "$scope" | jq -r .name): added" ;;
    409) echo "client scope $(printf '%s' "$scope" | jq -r .name): exists" ;;
    *) echo "seed-master.sh: client scope request answered $status" >&2; exit 1 ;;
    esac
done

jq '. + {ifResourceExists: "SKIP"}' "$fixture" | curl -sf -X POST -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' -d @- "$url/admin/realms/master/partialImport" |
    jq -r '"roles, clients, users: \(.added) added, \(.skipped) skipped"'
