# syntax=docker/dockerfile:1
#
# Where the Android app builds without a JDK or an Android SDK on the machine: companion/bin/lani-build-app runs the
# Gradle build of companion/android in it, and so do the workflows in .github/workflows (CI and the release APKs).
# The image holds only the tools; the sources, the Gradle cache and the debug key are mounted when it runs, as the
# user who runs it (the files it writes are theirs, not root's).
#
# Keep it in step with the build: the JDK is the one the build uses on Jan's machine (Temurin 21, `mise where java`),
# the platform is compileSdk/targetSdk and the build tools are what AGP (gradle/libs.versions.toml) asks for. A build
# that needs another SDK package fails here (the SDK is read-only), so add the package below instead of letting
# Gradle download it.
FROM eclipse-temurin:21.0.11_10-jdk-noble@sha256:8a79c84cdf6967ae437eba13c8859d74d41aeccb1e65a42191ca57b1992ad0b8

# The Android SDK command-line tools 20.0 (sdkmanager), pinned by their SHA-256.
ARG CMDLINE_TOOLS_BUILD=14742923
ARG CMDLINE_TOOLS_SHA256=04453066b540409d975c676d781da1477479dde3761310f1a7eb92a1dfb15af7
# compileSdk and targetSdk (app/build.gradle.kts), and AGP 9.2's build tools; AGP also looks for the platform tools
# (adb), which sdkmanager installs at their latest version
ARG ANDROID_PLATFORM=android-36
ARG ANDROID_BUILD_TOOLS=36.0.0

LABEL org.opencontainers.image.title="lani-android-build" \
      org.opencontainers.image.description="JDK 21 and the Android SDK to build the Lani app (companion/android)" \
      org.opencontainers.image.source="https://github.com/fentas/lani" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later" \
      si.lanisce.lani.build="android"

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/build-tools/${ANDROID_BUILD_TOOLS}

# git: the version is the commit count (app/build.gradle.kts); unzip: the command-line tools
RUN apt-get update \
 && apt-get install -y --no-install-recommends git unzip curl ca-certificates \
 && rm -rf /var/lib/apt/lists/*

RUN curl -fsSLo /tmp/cmdline-tools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip" \
 && echo "${CMDLINE_TOOLS_SHA256}  /tmp/cmdline-tools.zip" | sha256sum -c - \
 && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
 && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools \
 && mv /tmp/cmdline-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
 && rm -rf /tmp/cmdline-tools /tmp/cmdline-tools.zip \
 && (yes | sdkmanager --licenses > /dev/null) \
 && sdkmanager --install "platforms;${ANDROID_PLATFORM}" "build-tools;${ANDROID_BUILD_TOOLS}" platform-tools > /dev/null \
 && sdkmanager --list_installed \
 && rm -rf /root/.android /root/.cache /tmp/*

# The home of whoever runs the build (lani-build-app mounts its cache here: ~/.gradle, Robolectric's ~/.m2): any user
# may write it, so a run with --user and no mount still works. The JDK takes user.home from HOME when the user has
# no entry in /etc/passwd, as with docker run --user; the base image's own user (ubuntu, uid 1000) would give uid
# 1000 its /home/ubuntu instead, so it goes.
RUN userdel --remove ubuntu \
 && mkdir -p /home/lani && chmod 1777 /home/lani
ENV HOME=/home/lani \
    GRADLE_USER_HOME=/home/lani/.gradle \
    ANDROID_USER_HOME=/home/lani/.android

WORKDIR /home/lani
