@echo off
rem Runs restore.ps1 as administrator (Windows will ask for permission).
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -NoExit -File \"%~dp0restore.ps1\"'"
