#!/usr/bin/env bash
# Builds the documentation site into ./site:
#   /              README.md rendered as HTML
#   /api/          Swagger UI for docs/openapi.yaml
#   /openapi.yaml  the OpenAPI v3 spec
#   /javadoc/      Javadoc API docs for the spring-boot-business-rules library
#   /docs/images/  images the README refers to (same relative path as on GitHub)
# Needs node/npm and a JDK 21+ with Maven. Used by .github/workflows/ci.yml; runnable locally.
#
# The npm tools (Redocly, marked, Swagger UI) are pinned in docs/tools/package.json and installed with `npm ci`, which
# checks every package against the integrity hashes in docs/tools/package-lock.json. `--ignore-scripts` stops any
# package from running code at install time. To update a tool, change docs/tools/package.json and run
# `npm install --package-lock-only --ignore-scripts` there (Dependabot does this too).
set -euo pipefail

cd "$(dirname "$0")/.."
rm -rf site && mkdir -p site/api

tools=docs/tools
(cd "$tools" && npm ci --ignore-scripts --no-audit --no-fund)

"$tools/node_modules/.bin/redocly" lint docs/openapi.yaml

# Javadoc is generated from the source comments (doclint is strict: any warning fails), not committed.
./mvnw --batch-mode --no-transfer-progress -pl spring-boot-business-rules javadoc:javadoc
mkdir -p site/javadoc
cp -R spring-boot-business-rules/target/reports/apidocs/. site/javadoc/

cp "$tools/node_modules/swagger-ui-dist/swagger-ui.css" "$tools/node_modules/swagger-ui-dist/swagger-ui-bundle.js" site/api/
cp docs/swagger-ui/index.html site/api/index.html
cp docs/openapi.yaml site/openapi.yaml
mkdir -p site/docs && cp -R docs/images site/docs/images

body=$("$tools/node_modules/.bin/marked" --gfm -i README.md)
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
