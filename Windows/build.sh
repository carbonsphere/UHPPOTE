#!/bin/sh
# Cross-compiles UHPPOTE-Manager.exe (Windows) into this folder from
# Linux, WSL or macOS. Requires Go 1.24+: https://go.dev/dl/
set -e
cd "$(dirname "$0")"
echo "Running tests..."
go test ./uhppote
echo "Building UHPPOTE-Manager.exe..."
GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-H windowsgui -s -w" -o UHPPOTE-Manager.exe ./gui
echo "Built $(pwd)/UHPPOTE-Manager.exe"
