# Development

```bash
# Tests and the stdio smoke test; Dev Services starts Keycloak in Docker
./mvnw verify

# One test class
./mvnw test -Dtest=UserToolsTest

# Dev mode: HTTP transport, Dev UI at http://localhost:8080/q/dev-ui, Keycloak from Dev Services
./mvnw quarkus:dev

# Formatting, checked during verify
./mvnw formatter:format

# Native build, then the stdio and HTTP integration tests against the binary
./mvnw verify -Dnative
```

The native integration tests run on Linux only, because the binary is a Linux executable. On macOS, `-Dnative`
builds the binary and reports those tests as skipped.

In dev and test mode, Dev Services starts its own Keycloak and imports both realm files, so the compose Keycloak is
not needed. Stop the compose Keycloak before `./mvnw quarkus:dev`, or start dev mode with
`-Dquarkus.http.port=8081`, because both use port 8080 by default.

## Local Keycloak

The [quick start](../README.md#quick-start) starts the compose Keycloak on <http://localhost:8080> with admin
`admin/admin` and the realm `acme`. The sections below connect Claude Code to it. For a real Keycloak, see
[Connect Claude Code](connect.md).

The HTTP setups sign users in to `master`. Realm import cannot add objects to `master`, so add them once with the seed
script, which needs `curl` and `jq`:

```bash
docker/seed-master.sh
```

It reads [`docker/master.json`](../docker/master.json) and adds the client scope `mcp:tools`, the realm role
`mcp-admin`, the public client `keycloak-mcp`, the token exchange client `keycloak-mcp-server` (secret
`keycloak-mcp-server-secret`, for local use only) and three users, each with the username as password:

| User | Roles | Through the server |
|---|---|---|
| `mcp-user` | `mcp-admin`, `admin` | Every tool in every realm |
| `mcp-viewer` | `mcp-admin`, `view-users` and `view-realm` of `acme-realm` | Reads users and settings of `acme`; other calls get `403` |
| `mcp-guest` | none | Signs in, but `/mcp` answers "needs additional permissions" |

Objects that already exist are left as they are, so running it again changes nothing. The tests load the same file
into Dev Services Keycloak.

The commands run from the repository root and use `$PWD` to build absolute paths. They add the server at the local
scope, which is Claude Code's default; add `--scope user` to make it available in every project. The HTTP setup also
needs `curl` for the token helper.

### stdio

```bash
claude mcp add keycloak \
  -e KEYCLOAK_URL=http://localhost:8080 \
  -e KEYCLOAK_USERNAME=admin \
  -e KEYCLOAK_PASSWORD=admin \
  -- java -Dquarkus.profile=stdio -jar "$PWD/target/quarkus-app/quarkus-run.jar"
```

### HTTP

Start the server on port 8081, because the compose Keycloak already uses 8080. `MCP_PUBLIC_URL` tells the server the
URL clients use, so its OAuth metadata matches it:

```bash
KEYCLOAK_URL=http://localhost:8080 MCP_OIDC_CLIENT_SECRET=keycloak-mcp-server-secret \
  MCP_PUBLIC_URL=http://localhost:8081/mcp \
  java -Dquarkus.profile=http -Dquarkus.http.port=8081 -jar target/quarkus-app/quarkus-run.jar
```

The server has no admin identity over HTTP: it exchanges each user's token through `keycloak-mcp-server` and calls
Keycloak as that user. Add it with the public client `keycloak-mcp` of `master` and a fixed callback port:

```bash
claude mcp add --transport http --client-id keycloak-mcp --callback-port 33418 \
  keycloak http://localhost:8081/mcp
```

Sign in with `/mcp` inside a session, or from the shell:

```bash
claude mcp login keycloak
```

The browser opens the Keycloak login page; sign in as `mcp-user` (password `mcp-user`). Claude Code stores the token
and refreshes it. `mcp-viewer` gets `Keycloak returned 403 Forbidden` for anything but reading `acme`, and a user
without the `mcp-admin` role, such as `mcp-guest`, signs in but gets "needs additional permissions". Keycloak
remembers the browser session, so log out at `http://localhost:8080/realms/master/account` or use a private window to
sign in as someone else. The admin events of a realm (turn them on with `updateEventSettings` or in the admin
console) show the signed-in user for each change.

### HTTP without a browser

`claude -p`, the Agent SDK and CI have no browser sign-in. For those, point Claude Code at `docker/mcp-token.sh` as a
`headersHelper`. Its defaults match the compose Keycloak, so it fetches a token for `mcp-user` from `master`; set
`MCP_USER=mcp-viewer` to act as the viewer:

```bash
claude mcp add-json keycloak \
  "{\"type\":\"http\",\"url\":\"http://localhost:8081/mcp\",\"headersHelper\":\"$PWD/docker/mcp-token.sh\"}"
```

### HTTP from the container image

Build the image and run it against the compose Keycloak:

```bash
./mvnw package -DskipTests -Dquarkus.container-image.build=true
docker run --rm -p 8081:8080 --add-host=host.docker.internal:host-gateway \
  -e KEYCLOAK_URL=http://host.docker.internal:8080 \
  -e MCP_OIDC_CLIENT_SECRET=keycloak-mcp-server-secret \
  ghcr.io/filippobuletto/keycloak-mcp:0.1.0-SNAPSHOT
```

Docker Desktop defines `host.docker.internal` on its own; Docker engines on Linux, including Lima on macOS, need the
`--add-host` option, which does no harm on Docker Desktop.

The container reaches Keycloak as `host.docker.internal:8080`, so it only accepts tokens issued for that address.
In dev mode Keycloak takes the issuer from the request's `Host` header, so the helper sends that header. Browser
sign-in doesn't work in this local setup: the browser can't open `host.docker.internal`, and the tokens it would get
carry the `localhost` issuer.

```bash
claude mcp add-json keycloak \
  "{\"type\":\"http\",\"url\":\"http://localhost:8081/mcp\",\"headersHelper\":\"MCP_ISSUER_HOST=host.docker.internal:8080 $PWD/docker/mcp-token.sh\"}"
```

### Native binary and image

The native build compiles the server ahead of time into a single Linux executable that starts in a few hundredths of
a second. It runs inside a builder container, so it needs Docker but no local GraalVM:

```bash
./mvnw package -Dnative -DskipTests -Dquarkus.container-image.build=true
```

This produces `target/keycloak-mcp-0.1.0-SNAPSHOT-runner` for the Docker host's CPU (Linux aarch64 on Apple silicon)
and the image `ghcr.io/filippobuletto/keycloak-mcp:0.1.0-SNAPSHOT-native`. Leave out
`-Dquarkus.container-image.build=true` to build only the binary. The JVM image keeps its own tag.

The image runs the HTTP transport by default, as the JVM image does, with the same environment variables. Arguments
after the image name go to the server, so `-Dquarkus.profile=stdio` switches it to stdio. To use it as Claude Code's
stdio server, on macOS too:

```bash
claude mcp add keycloak -- docker run -i --rm --add-host=host.docker.internal:host-gateway \
  -e KEYCLOAK_URL=http://host.docker.internal:8080 \
  -e KEYCLOAK_USERNAME=admin -e KEYCLOAK_PASSWORD=admin \
  ghcr.io/filippobuletto/keycloak-mcp:0.1.0-SNAPSHOT-native -Dquarkus.profile=stdio
```

For HTTP, run the image as in [HTTP from the container image](#http-from-the-container-image), with the `-native`
tag. On Linux, the binary also runs directly, with the same options as the jar:

```bash
claude mcp add keycloak \
  -e KEYCLOAK_URL=http://localhost:8080 \
  -e KEYCLOAK_USERNAME=admin \
  -e KEYCLOAK_PASSWORD=admin \
  -- "$PWD/target/keycloak-mcp-0.1.0-SNAPSHOT-runner" -Dquarkus.profile=stdio
```

Every native run logs `The profile 'prod' used to build the native image is different from the runtime profile ...`.
The `stdio` and `http` profiles only change run-time settings, so the warning is expected and harmless.

### Distroless images

The images in [Connect](connect.md#get-the-server) are distroless variants: no shell, no package manager, user
65532, and a filesystem they don't write to. The `distroless` profile builds them from
`src/main/docker/Dockerfile.jvm-distroless` (a JRE made with `jlink`) and `Dockerfile.native-distroless`:

```bash
# ghcr.io/filippobuletto/keycloak-mcp:0.1.0-SNAPSHOT-distroless
./mvnw package -DskipTests -Ddistroless -Dquarkus.container-image.build=true

# ghcr.io/filippobuletto/keycloak-mcp:0.1.0-SNAPSHOT-native-distroless
./mvnw package -DskipTests -Dnative -Ddistroless -Dquarkus.container-image.build=true
```

Without `-Ddistroless` the builds above give the local images `0.1.0-SNAPSHOT` (UBI9 Java runtime) and
`0.1.0-SNAPSHOT-native` (UBI9 micro), as before; releases don't publish those two.

Run them with `--read-only`. The Java image selects stdio with `-e QUARKUS_PROFILE=stdio`, because arguments after
its name reach the application, not Java:

```bash
claude mcp add keycloak -- docker run -i --rm --read-only --add-host=host.docker.internal:host-gateway \
  -e QUARKUS_PROFILE=stdio \
  -e KEYCLOAK_URL=http://host.docker.internal:8080 \
  -e KEYCLOAK_USERNAME=admin -e KEYCLOAK_PASSWORD=admin \
  ghcr.io/filippobuletto/keycloak-mcp:0.1.0-SNAPSHOT-distroless
```

The `jlink` module list in `Dockerfile.jvm-distroless` comes from
`jdeps --print-module-deps` on `target/quarkus-app`, plus `jdk.zipfs`: without it the server stops at startup with
`Unable to find provider supporting jar scheme`. A module reached only by reflection shows up the same way, as a
failure at startup or in a tool call that the jar doesn't have; add that module to the list.

### Service account

To test the `CLIENT_CREDENTIALS` [grant type](configuration.md#grant-types) of the stdio profile against the compose
Keycloak, create a confidential client in `master` and give its service account the `admin` role. The HTTP server
does not use it. The steps need `jq`:

```bash
kcadm() { docker compose -f docker/compose.yaml exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"; }
kcadm config credentials --server http://localhost:8080 --realm master --user admin --password admin
CID=$(kcadm create clients -r master -s clientId=keycloak-mcp-admin -s publicClient=false \
  -s serviceAccountsEnabled=true -s standardFlowEnabled=false -s directAccessGrantsEnabled=false -i)
kcadm add-roles -r master --uusername service-account-keycloak-mcp-admin --rolename admin
```

Add the `kc-admin-api` client scope as a default scope of the client (see
[Admin identity](connect.md#admin-identity) for why), then print its secret:

```bash
SCOPE=$(kcadm get client-scopes -r master | jq -r '.[] | select(.name=="kc-admin-api") | .id')
ADMIN_TOKEN=$(curl -s -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin \
  http://localhost:8080/realms/master/protocol/openid-connect/token | jq -r .access_token)
curl -s -o /dev/null -w '%{http_code}\n' -X PUT -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8080/admin/realms/master/clients/$CID/default-client-scopes/$SCOPE"
kcadm get "clients/$CID/client-secret" -r master
```

The `PUT` prints `204` on success. Then start the server with the printed secret:

```bash
KEYCLOAK_URL=http://localhost:8080 KEYCLOAK_GRANT_TYPE=CLIENT_CREDENTIALS \
  KEYCLOAK_CLIENT_ID=keycloak-mcp-admin KEYCLOAK_CLIENT_SECRET=<secret> \
  java -Dquarkus.profile=stdio -jar target/quarkus-app/quarkus-run.jar
```

Check the connection with `claude mcp list`, or with `/mcp` inside a session.

## Quality checks

### Coverage

`./mvnw verify` records the coverage of the unit and `@QuarkusTest` tests and fails when a class outside the `model`
package has less than 80% line coverage, naming each class:

```text
[WARNING] Rule violated for class io.github.filippobuletto.keycloakmcp.tools.JsonListEncoder: lines covered ratio is 0.60, but expected minimum is 0.80
```

The report is in `target/jacoco-report/` (`index.html`, `jacoco.xml`, `jacoco.csv`). `./mvnw test` and runs with
`-Dtest=` don't check coverage. The integration tests start the server in a separate process and add none.

### Vulnerability scan

[`docker/trivy-scan.sh`](../docker/trivy-scan.sh) runs Trivy from its container against the local Docker engine. It
scans the four images of a version (the two UBI9 images and the two distroless ones; build all four first) and the
repository:

```bash
docker/trivy-scan.sh                 # the pom version
docker/trivy-scan.sh 0.1.0-SNAPSHOT
```

Each target gets `target/trivy/<target>.json` with every finding and a table in `<target>.txt`. The script exits 1
when a target has a HIGH or CRITICAL vulnerability with a fixed version, or a HIGH or CRITICAL misconfiguration or
secret, and lists them in `<target>.verdict.txt`. [`.trivyignore`](../.trivyignore) holds the accepted findings,
each with its reason. The first run downloads the Trivy database into the Docker volume `keycloak-mcp-trivy-cache`.
`TRIVY_DOCKER_SOCKET` sets the socket path as the engine sees it (default `/var/run/docker.sock`).

`TRIVY_IMAGES` scans the given image references instead, with the same rules, and skips the version argument and the
repository scan. The release workflow uses it on the images it builds:

```bash
TRIVY_IMAGES="ghcr.io/filippobuletto/keycloak-mcp:0.1.0-SNAPSHOT-distroless other/image:1.2" docker/trivy-scan.sh
```

Each report is named after its reference, with `/`, `:` and `@` turned into `_`.

### SonarQube

The compose file has an optional SonarQube Community server under the profile `sonar`, on
<http://127.0.0.1:9000> (admin `admin/admin`; the port listens on 127.0.0.1 only):

```bash
docker compose -f docker/compose.yaml --profile sonar up -d sonarqube
```

SonarQube refuses anonymous analysis, so create a token with
[`docker/sonar-token.sh`](../docker/sonar-token.sh) and keep it in your shell, not in a file of the repository. Then
scan after `verify`, so the coverage report exists:

```bash
export SONAR_TOKEN="$(docker/sonar-token.sh)"
./mvnw verify
./mvnw org.sonarsource.scanner.maven:sonar-maven-plugin:5.9.0.7382:sonar \
  -Dsonar.host.url=http://127.0.0.1:9000 -Dsonar.projectKey=keycloak-mcp \
  -Dsonar.coverage.jacoco.xmlReportPaths=target/jacoco-report/jacoco.xml
```

The results are at <http://127.0.0.1:9000/dashboard?id=keycloak-mcp>. The server keeps its data in the volumes
`sonarqube_data`, `sonarqube_extensions` and `sonarqube_logs`; `docker compose -f docker/compose.yaml --profile sonar
down` keeps them, and adding `-v` deletes them.
