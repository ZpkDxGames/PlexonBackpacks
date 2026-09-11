#!/usr/bin/env bash
set -euo pipefail

CORE_VERSION="2.0.4"
CORE_SHA256="61d625a717da9f46ee9231e1970d84b4c317ae12cf4090cdf7c9d39b6a1a9baf"
CORE_URL="https://github.com/ZpkDxGames/PlexonCore/releases/download/v${CORE_VERSION}/PlexonCore-${CORE_VERSION}.jar"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOWNLOAD_DIR="${ROOT}/.deps/download"
REPO_DIR="${ROOT}/.deps/repository/com/zpkdxgames/PlexonCore/${CORE_VERSION}"
JAR="${DOWNLOAD_DIR}/PlexonCore-${CORE_VERSION}.jar"

mkdir -p "${DOWNLOAD_DIR}" "${REPO_DIR}"

if [[ ! -f "${JAR}" ]]; then
  curl --fail --location --retry 3 --output "${JAR}" "${CORE_URL}"
fi

echo "${CORE_SHA256}  ${JAR}" | sha256sum --check --status || {
  echo "PlexonCore checksum verification failed" >&2
  rm -f "${JAR}"
  exit 1
}

cp "${JAR}" "${REPO_DIR}/PlexonCore-${CORE_VERSION}.jar"
cat > "${REPO_DIR}/PlexonCore-${CORE_VERSION}.pom" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.zpkdxgames</groupId>
  <artifactId>PlexonCore</artifactId>
  <version>${CORE_VERSION}</version>
</project>
EOF

echo "Provisioned verified PlexonCore ${CORE_VERSION} into ${REPO_DIR}"
