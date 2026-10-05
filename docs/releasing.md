# Releasing the app

The Android app reaches a phone in one of three ways:

- **A GitHub release.** A tag `v*` makes the workflow `.github/workflows/android-release.yml` build the release APK,
  sign it with the project's release key and attach it to the release. This is the APK for anyone who doesn't build.
- **A build of your own.** `companion/bin/lani-build-app` builds in Docker, without a JDK or an Android SDK; Gradle
  builds it with them (companion/README.md, "Android app"). It is signed with your key: your release key if you have
  one, else your debug key.
- **The bridge's self-update.** `companion/bin/release-app` puts an APK in `<data>/app/release/` and the app offers it
  as an update. It builds one (locally, or `--docker`), or takes a GitHub release's with `--github [<tag>]`.

A phone takes an update only when it is signed with the same key as the installed app. An app installed from the
GitHub releases updates from the next release (and from a bridge that publishes it with `release-app --github`); an
app built with another key has to be uninstalled before the other one goes on.

## The release key (once)

```bash
companion/bin/lani-keystore            # ~/.config/lani/release.keystore and signing.properties (mode 600)
companion/bin/lani-keystore --secrets  # the gh commands again
```

It makes a PKCS12 keystore with an RSA 4096 key, valid for 30 years, and a `signing.properties` with its alias and a
random password. Both stay outside the repository. Release builds on this machine (`release-app`,
`lani-build-app --release`, `./gradlew :app:assembleRelease`) are signed with it from then on. The script never
overwrites a key: **keep a backup of both files** (a password manager, an encrypted disk). With a lost key, no
installed app can update again.

It ends with four `gh secret set` commands that give the workflow the same key: `LANI_KEYSTORE_B64` (the keystore,
base64), `LANI_KEYSTORE_PASSWORD`, `LANI_KEY_ALIAS` and `LANI_KEY_PASSWORD`. Each command reads its value from the files,
so no password is shown. `gh secret list` shows the names afterwards.

A phone with a debug-signed app (every build before the key) needs the app installed again once: open it while it
reaches the bridge (its outbox empties), uninstall it, install the release-signed APK and pair it again
(`companion/bin/lani-pair`). The village, the reviews and the stats come from the bridge. What only the phone kept (the
chat archive, the app's settings) starts anew, as in [migrate-from-fluent.md](migrate-from-fluent.md) ("The app").

Without the secrets the workflow still builds, but signs with a throwaway debug key and keeps the APK as a workflow
artifact only, with a warning: an APK like that couldn't update an installed app, so it is never a release.

## A release

The version comes from the commit: versionName `0.2.<commit count>`, versionCode `1000 + <commit count>`
(`companion/android/app/build.gradle.kts`). The tag is `v` and the versionName of the commit it tags:

```bash
git switch main && git pull
version="0.2.$(git rev-list --count HEAD)"
git tag -a "v$version" -m "Lani $version"
git push origin "v$version"
```

The workflow then:

1. checks out the whole history (`fetch-depth: 0`; a shallow clone would count fewer commits);
2. builds the release APK in the build image (`companion/android/docker/Dockerfile`), with
   `companion/bin/lani-build-app --release`, as on any machine;
3. checks the signature with `apksigner` (and fails if the secrets are there but the APK has a debug key);
4. writes `lani-<version>.apk.sha256` and `lani-<version>.json` (versionCode, versionName, file, sha256, commit), and
   warns if the tag isn't `v<versionName>`;
5. keeps the three files as a workflow artifact, then creates the release "Lani <version>" (if it doesn't exist) and
   attaches them. A second run replaces the files.

Then:

```bash
gh release view "v$version"                       # the release and its files
companion/bin/release-app --github "v$version"    # the bridge offers it to the app ("--github" alone: the latest)
```

"Run workflow" (Actions, "Android release") from a tag does the same as the tag's push. From a branch it only builds
and keeps the artifact: a check that a release would build.

## The same bytes

The release APK is reproducible: the same commit and the same key give the same file, on GitHub, in Docker or with a
local Gradle (the APK has no dependency report in its signing block, which differs on every build). With the key:

```bash
git switch --detach "v$version"
companion/bin/lani-build-app --release --out /tmp/
sha256sum /tmp/lani-$version.apk                  # the SHA-256 of the release
```

Without the key, the entries of the APK are still the same; only the signature differs (`unzip -lv` lists each
entry's CRC).
