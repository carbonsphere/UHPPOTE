@echo off
rem Builds UHPPOTE-Manager.exe in this folder. Requires Go 1.24+: https://go.dev/dl/
cd /d "%~dp0"
echo Running tests...
go test ./uhppote
if errorlevel 1 goto failed
echo Building UHPPOTE-Manager.exe...
go build -trimpath -ldflags "-H windowsgui -s -w" -o UHPPOTE-Manager.exe ./gui
if errorlevel 1 goto failed
echo Built %~dp0UHPPOTE-Manager.exe
exit /b 0

:failed
echo Build failed.
exit /b 1
