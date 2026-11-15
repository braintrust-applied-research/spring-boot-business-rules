#!/usr/bin/env bash
# Builds the documentation site into ./site:
#   /              README.md rendered as HTML
#   /api/          Swagger UI for docs/openapi.yaml
#   /openapi.yaml  the OpenAPI v3 spec
#   /javadoc/      Javadoc API docs for the spring-boot-business-rules library
#   /docs/images/  images the README refers to (same relative path as on GitHub)
# Needs node/npm and a JDK 21+ with Maven. Used by .github/workflows/ci.yml; runnable locally.
set -euo pipefail

SWAGGER_UI_VERSION=5.35.0
MARKED_VERSION=18.2.0
REDOCLY_VERSION=2.78.0

cd "$(dirname "$0")/.."
rm -rf site && mkdir -p site/api

npx --yes "@redocly/cli@${REDOCLY_VERSION}" lint docs/openapi.yaml

# Javadoc is generated from the source comments (doclint is strict: any warning fails), not committed.
mvn --batch-mode --no-transfer-progress -pl spring-boot-business-rules javadoc:javadoc
mkdir -p site/javadoc
cp -R spring-boot-business-rules/target/reports/apidocs/. site/javadoc/

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
(cd "$work" && npm pack "swagger-ui-dist@${SWAGGER_UI_VERSION}" --silent && tar xzf swagger-ui-dist-*.tgz)
cp "$work"/package/swagger-ui.css "$work"/package/swagger-ui-bundle.js site/api/
cp docs/swagger-ui/index.html site/api/index.html
cp docs/openapi.yaml site/openapi.yaml
mkdir -p site/docs && cp -R docs/images site/docs/images

body=$(npx --yes "marked@${MARKED_VERSION}" --gfm -i README.md)
cat > site/index.html <<HTML
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>spring-boot-business-rules</title>
<style>body{max-width:60rem;margin:2rem auto;padding:0 1rem;font:16px/1.5 system-ui,sans-serif}
table{border-collapse:collapse}td,th{border:1px solid #ccc;padding:.3rem .6rem}pre{background:#f5f5f5;padding:1rem;overflow:auto}
img{max-width:100%;height:auto}</style>
</head><body>
<p><a href="api/">API reference (Swagger UI)</a> · <a href="openapi.yaml">openapi.yaml</a> · <a href="javadoc/">Javadoc</a></p>
${body}
</body></html>
HTML
touch site/.nojekyll
echo "Site built in $(pwd)/site"
