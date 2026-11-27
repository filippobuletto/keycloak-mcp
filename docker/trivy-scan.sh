#!/bin/sh
# Scans the four keycloak-mcp images of one version and the repository with Trivy, run from its pinned container
# against the local Docker engine, so the host needs no Trivy install.
#
#   docker/trivy-scan.sh [version]     version defaults to the pom version
#
# Each target gets a JSON report with every finding and a table made from it, in target/trivy/<target>.json and .txt.
# The script exits 1 when any target has a HIGH or CRITICAL vulnerability with a fixed version, or a HIGH or CRITICAL
# misconfiguration or secret, that .trivyignore doesn't accept; target/trivy/<target>.verdict.txt lists them.
#
#   TRIVY_DOCKER_SOCKET  Docker socket as the engine sees it (default /var/run/docker.sock)
set -eu

trivy_image="aquasec/trivy:0.76.2@sha256:8f6bb947dbc528902e11f2f5be52f7b519f6a39d5d148f41166f2040499768fb"
repo="ghcr.io/filippobuletto/keycloak-mcp"
root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/target/trivy"

version="${1:-}"
if [ -z "$version" ]; then
    version=$("$root/mvnw" -q -f "$root/pom.xml" help:evaluate -Dexpression=project.version -DforceStdout)
fi

images="$version $version-native $version-distroless $version-native-distroless"
for tag in $images; do
    docker image inspect "$repo:$tag" >/dev/null 2>&1 || { echo "trivy-scan.sh: no local image $repo:$tag" >&2; exit 1; }
done
mkdir -p "$out"

trivy() {
    docker run --rm -i \
        -v "${TRIVY_DOCKER_SOCKET:-/var/run/docker.sock}:/var/run/docker.sock" \
        -v keycloak-mcp-trivy-cache:/root/.cache/trivy \
        -v "$root:/repo:ro" -w /repo \
        "$trivy_image" "$@"
}

# scan <name> <trivy args...>: writes the reports and records the verdict
failed=""
scan() {
    name="$1"
    shift
    echo "scanning $name"
    trivy "$@" --quiet --format json --ignorefile "" > "$out/$name.json"
    # stdin, not the mount: the engine may not see a file the host has just written
    trivy convert --quiet --format table --ignorefile "" /dev/stdin < "$out/$name.json" > "$out/$name.txt"
    if ! trivy "$@" --quiet --skip-db-update --format table --exit-code 1 --severity HIGH,CRITICAL \
        --ignore-unfixed --ignorefile .trivyignore > "$out/$name.verdict.txt"; then
        failed="$failed $name"
    fi
}

for tag in $images; do
    scan "image-$tag" image "$repo:$tag"
done
# --offline-scan: no pom lookups on Maven Central; the image scans cover every jar the images ship
scan repository fs --scanners vuln,secret,misconfig --offline-scan --skip-dirs target,.scratch,.claude-tmp .

if [ -n "$failed" ]; then
    echo "trivy-scan.sh: HIGH or CRITICAL findings with a fix in:$failed (see target/trivy/<name>.verdict.txt)" >&2
    exit 1
fi
echo "trivy-scan.sh: no HIGH or CRITICAL finding with a fix; reports in target/trivy/"
