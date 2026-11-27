#!/bin/sh
# Claude Code headersHelper: prints {"Authorization":"Bearer <token>"} with a fresh access token from the
# issuer realm, using the password grant. The defaults match docker/compose.yaml after docker/seed-master.sh.
#
#   MCP_ISSUER_URL   issuer realm URL (default http://localhost:8080/realms/master)
#   MCP_ISSUER_HOST  Host header for the token request, so the token's issuer matches the URL
#                    the server uses, e.g. host.docker.internal:8080 for the container image
#   MCP_USER         user of the issuer realm (default mcp-user)
#   MCP_PASS         that user's password (default: same as MCP_USER, as for the seeded users)
set -eu

issuer="${MCP_ISSUER_URL:-http://localhost:8080/realms/master}"
user="${MCP_USER:-mcp-user}"
pass="${MCP_PASS:-$user}"

set --
if [ -n "${MCP_ISSUER_HOST:-}" ]; then
    set -- -H "Host: $MCP_ISSUER_HOST"
fi

response=$(curl -sf "$@" \
    --data-urlencode grant_type=password \
    --data-urlencode client_id=keycloak-mcp \
    --data-urlencode "username=$user" \
    --data-urlencode "password=$pass" \
    "$issuer/protocol/openid-connect/token") || {
    echo "mcp-token.sh: token request to $issuer failed" >&2
    exit 1
}

token=$(printf '%s' "$response" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
if [ -z "$token" ]; then
    echo "mcp-token.sh: no access_token in the response from $issuer" >&2
    exit 1
fi
printf '{"Authorization":"Bearer %s"}\n' "$token"
