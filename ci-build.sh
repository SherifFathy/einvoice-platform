#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "========================================"
echo " E-Invoice Platform — CI Build"
echo "========================================"

echo ""
echo ">>> [1/5] Maven Build (all modules, checkstyle included)"
cd "$SCRIPT_DIR"
mvn clean install -DskipTests

echo ""
echo ">>> [2/5] Maven Tests"
mvn test

echo ""
echo ">>> [3/5] Frontend Build"
cd "$SCRIPT_DIR/frontend"
npm ci
npm run build

echo ""
echo ">>> [4/5] Frontend Lint (ESLint)"
npm run lint

echo ""
echo ">>> [5/5] Frontend Tests"
npm run test -- --no-watch --browsers=ChromeHeadless

cd "$SCRIPT_DIR"
echo ""
echo "========================================"
echo " CI Build PASSED"
echo "========================================"
