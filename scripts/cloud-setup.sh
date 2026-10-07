#!/usr/bin/env bash
# Reproducible cloud tooling. Application source and existing databases are preserved.
set -euo pipefail
mkdir -p /workspace/tools /workspace/setup /workspace/tools/dotnet
if ! /workspace/tools/dotnet/dotnet --list-sdks 2>/dev/null | rg -q '^10.0.401 '; then
  curl -fsSL https://builds.dotnet.microsoft.com/dotnet/Sdk/10.0.401/dotnet-sdk-10.0.401-linux-x64.tar.gz -o /workspace/setup/dotnet-sdk.tar.gz
  echo '51c8b999af9e8dd9998c9edc5944e19a90788862068acd38694e098889054ce8c23d4f0c5cccfa16bf187d044562359e5ee69a9f8ad0bbe913ba90311fbce25b  /workspace/setup/dotnet-sdk.tar.gz' | sha512sum -c -
  tar -xzf /workspace/setup/dotnet-sdk.tar.gz -C /workspace/tools/dotnet
fi
if ! test -x /workspace/tools/jdk17/bin/jlink; then
  curl -fsSL 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.16%2B8/OpenJDK17U-jdk_x64_linux_hotspot_17.0.16_8.tar.gz' -o /workspace/setup/jdk17.tar.gz
  echo '166774efcf0f722f2ee18eba0039de2d685b350ee14d7b69e6f83437dafd2af1  /workspace/setup/jdk17.tar.gz' | sha256sum -c -
  mkdir -p /workspace/tools/jdk17
  tar -xzf /workspace/setup/jdk17.tar.gz --strip-components=1 -C /workspace/tools/jdk17
fi
if ! test -x /workspace/tools/android-sdk/cmdline-tools/latest/bin/sdkmanager; then
  curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o /workspace/setup/android-commandlinetools.zip
  # Official Android repository2-3.xml checksum for command-line tools 19.0.
  echo '5fdcc763663eefb86a5b8879697aa6088b041e70  /workspace/setup/android-commandlinetools.zip' | sha1sum -c -
  mkdir -p /workspace/tools/android-sdk/cmdline-tools
  unzip -q /workspace/setup/android-commandlinetools.zip -d /workspace/setup/android-cli
  mv /workspace/setup/android-cli/cmdline-tools /workspace/tools/android-sdk/cmdline-tools/latest
fi
export JAVA_HOME=/workspace/tools/jdk17
export DOTNET_ROOT=/workspace/tools/dotnet
export ANDROID_HOME=/workspace/tools/android-sdk
export ANDROID_USER_HOME=/workspace/tools/android-user
export GRADLE_USER_HOME=/workspace/tools/gradle-home
export DOTNET_CLI_HOME=/workspace/tools/dotnet-home NUGET_PACKAGES=/workspace/tools/nuget
export JAVA_TOOL_OPTIONS='-Duser.home=/workspace/tools/java-home'
if test -f /etc/ssl/certs/java/cacerts; then
  export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
export PATH="$JAVA_HOME/bin:$DOTNET_ROOT:$PATH"
mkdir -p "$ANDROID_USER_HOME/cache" /workspace/tools/java-home "$GRADLE_USER_HOME"
# Java does not consume the shell proxy variables. No proxy credentials are copied.
python3 - <<'PY'
import os, pathlib, subprocess, urllib.parse
proxy=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',''))
flags=[]
if proxy.hostname:
    port=proxy.port or 80
    pathlib.Path(os.environ['GRADLE_USER_HOME'],'gradle.properties').write_text('\n'.join(f'systemProp.{scheme}.proxy{kind}={value}' for scheme in ('http','https') for kind,value in (('Host',proxy.hostname),('Port',port)))+'\n')
    flags=['--proxy=http','--proxy_host='+proxy.hostname,'--proxy_port='+str(port)]
manager=os.environ['ANDROID_HOME']+'/cmdline-tools/latest/bin/sdkmanager'
base=[manager,'--sdk_root='+os.environ['ANDROID_HOME'],*flags]
subprocess.run(base+['--licenses'],input='y\n'*100,text=True,check=True)
subprocess.run(base+['platform-tools','platforms;android-35','build-tools;34.0.0','build-tools;35.0.0'],input='y\n'*100,text=True,check=True)
PY
cd /workspace/Be-Nobat-App
dotnet restore BeNobat.slnx --artifacts-path /workspace/setup/artifacts
dotnet test BeNobat.slnx --no-restore --artifacts-path /workspace/setup/artifacts
node --test tests/localization/*.test.cjs
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon
