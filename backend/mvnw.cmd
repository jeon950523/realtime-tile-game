@echo off
setlocal EnableDelayedExpansion
set "BASE_DIR=%~dp0"
set "PROPERTIES_FILE=%BASE_DIR%.mvn\wrapper\maven-wrapper.properties"

for /f "tokens=1,* delims==" %%A in ('findstr /b "distributionUrl=" "%PROPERTIES_FILE%"') do set "DISTRIBUTION_URL=%%B"
for /f "tokens=1,* delims==" %%A in ('findstr /b "distributionSha512Sum=" "%PROPERTIES_FILE%"') do set "EXPECTED_SHA512=%%B"
for /f "tokens=1 delims=/" %%A in ("!DISTRIBUTION_URL:*apache-maven/=!") do set "MAVEN_VERSION=%%A"

if not defined MAVEN_USER_HOME set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "MAVEN_HOME=!MAVEN_USER_HOME!\wrapper\dists\apache-maven-!MAVEN_VERSION!"
set "MAVEN_BIN=!MAVEN_HOME!\bin\mvn.cmd"

if not exist "!MAVEN_BIN!" (
  set "TMP_DIR=%TEMP%\mvnw-!RANDOM!-!RANDOM!"
  mkdir "!TMP_DIR!" >nul 2>&1
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop'; $archive='!TMP_DIR!\maven.zip'; Invoke-WebRequest -UseBasicParsing -Uri '!DISTRIBUTION_URL!' -OutFile $archive; $actual=(Get-FileHash -Algorithm SHA512 $archive).Hash.ToLowerInvariant(); if ($actual -ne '!EXPECTED_SHA512!') { throw 'Maven distribution SHA-512 verification failed.' }; Expand-Archive -Path $archive -DestinationPath '!TMP_DIR!\extracted' -Force; New-Item -ItemType Directory -Path '!MAVEN_HOME!' -Force | Out-Null; Copy-Item -Path '!TMP_DIR!\extracted\apache-maven-!MAVEN_VERSION!\*' -Destination '!MAVEN_HOME!' -Recurse -Force"
  if errorlevel 1 exit /b 1
  rmdir /s /q "!TMP_DIR!" >nul 2>&1
)

call "!MAVEN_BIN!" %*
endlocal
