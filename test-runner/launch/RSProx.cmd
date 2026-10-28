@echo off
rem RSProx for the private OSRSPS server, isolated from any other client data.
rem JAVA_TOOL_OPTIONS is inherited by every JVM RSProx starts (proxy and RuneLite), so
rem .rsprox, .runelite and jagexcache all live under C:\RSPS instead of %USERPROFILE%.
rem Uses the Temurin 21 on PATH (installer feature FeatureEnvironment).
rem
rem Goes in C:\RSPS, with RsproxAutoLaunch.java in C:\RSPS\bin (see test-runner\launch\README.md).
rem By default it opens RSProx and launches RuneLite on the OSRSPS target by itself.
rem RSProx.cmd --manual starts RSProx's own launcher instead, which also updates RSProx, and leaves
rem the Launch Session click to you. That is also what runs until the launcher has downloaded RSProx once.
set "JAVA_TOOL_OPTIONS=-Duser.home=C:\RSPS"
if /i "%~1"=="--manual" goto manual
if not exist "C:\RSPS\.rsprox\launcher\repository\proxy-tool-*.jar" goto manual
if not exist "C:\RSPS\bin\RsproxAutoLaunch.java" goto manual
java -cp "C:\RSPS\.rsprox\launcher\repository\*" "C:\RSPS\bin\RsproxAutoLaunch.java" %*
goto :eof

:manual
java -jar "C:\RSPS\bin\rsprox-launcher.jar"
