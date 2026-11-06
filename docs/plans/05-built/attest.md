# Only our app may use the voice service: hardware key attestation (2026-11-06)

**What.** The voice service (glas) makes new clips for the app's routes only for an install that proves, with the phone's
secure hardware, that it is our signed app on a phone whose boot is verified and whose bootloader is locked. Everyone else (an
emulator, a self-built copy, a phone with an unlocked bootloader, a script) hears the clips the service's library already has,
and the phone's own voice says the rest. Jan (2026-11-06): only our services may use glas; for the app, Android hardware key
attestation is "a good compromise for now". Bridges keep their node keys.

**Why attestation, not a secret.** The app is AGPL and an APK unpacks: any key shipped in it is public. A key made in the
phone's secure hardware comes with a certificate chain from the phone to Google's attestation roots, and its certificate
names the app (its package and the digests of its signing certificates), where the key lives (TEE or StrongBox) and the boot
state. No Play Store is needed: the app ships as an APK.

**What it does and doesn't do.** It limits making anything new to keys made by our signed app on hardware with a verified,
locked boot. It doesn't stop the genuine app being automated on a real phone, device farms or runtime exploits, and a
reinstall makes a new key: there is no one-device-one-trial. The name check, the budgets and the per-address limits stay the
real bound.

## How it works

1. Before the audio trial starts, the app asks glas for a challenge (`POST /v1/app/attest/challenge`: 32 random bytes, single
   use, 5 minutes).
2. It makes an EC P-256 key in AndroidKeyStore with `setAttestationChallenge(<the bytes>)`: a dedicated key, never exportable,
   for signing with SHA-256, in the TEE. StrongBox isn't asked for: it is slower and its resources are few, and glas accepts
   either. (`sync/P256.kt` and `phone/HostCrypto.kt` make exportable keys for other things and aren't used for this.)
3. It enrolls the key (`POST /v1/app/attest`): the certificate chain, the request signed by the new key.
4. glas checks the chain against Google's roots (pinned by public key), the CA flags, the key usage and the critical
   extensions, the key description of the first certificate that has one counted from the root (nothing may follow it),
   expiry (expired factory chains under the legacy root are accepted, remotely provisioned ones must be valid), every serial
   against Google's status list (revoked and suspended both refuse), the challenge, TEE or StrongBox, a generated EC P-256 key
   for signing with SHA-256, a verified boot and a locked bootloader, exactly our package and an approved set of signer
   digests. It answers with the key's id (a hash of the key) and the enrollment's end, or a refusal with a reason code.
5. From then on every request to glas's voice routes and its trial is signed by the key (`data/GlasAttest.kt`, `GlasAuth`, in
   the app's shared HTTP client, so the app, the day's prefetch and the car's library all sign): a protocol version, the
   service's host, the key id, the method, the path and query, the time, a random nonce and the body's hash, in DER ECDSA as
   `P256.kt` makes it. Each attempt is signed anew (a retried connection is never a replay); a POST has one Idempotency-Key per
   call, so a retry is charged nothing more. Every answer carries the service's time, and the app signs with it, so a phone
   with a wrong clock still works (a "stale" answer is tried once more with the corrected time).
6. An enrollment lasts 30 days. In its last week the service asks for a new key (`X-Glas-Renew`); the app makes one for a new
   challenge (Android attaches a challenge only when a key is made) and enrolls it naming the old key, which signs the same
   request: the install, and with it the trial's counters, carries over, and the old key is deleted. A key lost to a backup
   restored onto a new phone isn't there: the app enrolls again as a new install.

## Where the phone can't attest

An emulator, an old phone, an unlocked bootloader, or glas refusing: the trial is the library's only. The app starts it all
the same (it takes none of the day's places on the service), hears the clips the library has, and the phone's voice says the
rest, silently, as when the trial's budget is used up. No error reaches the learner.

- **Transient failures** (no network, `ERROR_ATTESTATION_KEYS_UNAVAILABLE` while remote keys are being provisioned, the
  service's status list too old, a challenge that expired) are tried again later with a backoff (a minute, doubling, at most
  12 hours), are never taken as a refusal, and start no trial, so none is used up.
- **A refusal** (403 `attestation_refused`) is final for 30 days: an app update or a change on the service may change it then.
- **The trial card.** Per rules-ux.md, never sell what this device can't get: a trial of the library only has no card on Home,
  neither at its start ("every voice in the village, free") nor at its end. The settings' quiet line under "🔊 Zvok in glas"
  says once what this phone has and why: "On this device you hear the voices already in the library; new lines are spoken by
  the device itself. The voice service makes new ones only for a phone it can check." (`audioTrial.library`, in sl, en, de and
  it: machine-written, for a native check.) Nothing nags.
- **Tap counts:** none before, none after. Nothing here asks the learner anything.

## Off until hosted

With no voice service in the build (`lani.glasUrl` empty), nothing new runs: no challenge, no key, no signature. An older app
(Teo's tablet runs 0.2.698) never calls the service and keeps working as it does. A trial kept on a device before this counts
as every voice's, as it was.

## The settings on the service

glas's README, "Attestation", has them all. The one Jan sets: the release key's certificate digest in the service's deployment
settings (`GLAS_APP_SIGNERS`), which is public: `apksigner verify --print-certs <the release APK>` prints it as "Signer #1 certificate
SHA-256 digest". Until it is set no Android install is attested and trials hear the library only. Enforcement is on from the
start, with a switch to relax it if a whole group of real phones turns out to fail; the service's metrics count both tiers
and why an attestation failed (a reason code, nothing about the device), so Jan sees real numbers.

**Signing-key rotation.** The signer digests are matched as sets, and the service takes a list of sets. A rotated signing key
(APK Signature Scheme v3) may be reported with the first signer on some Android versions and the current one on others: the
new certificate's digest goes in as another set beside the old one until no supported version reports the old.

**iOS.** The app is Android only. The service also takes Apple's App Attest (`apple-app-attest`), the same signed requests
with an assertion and a counter, for an iOS app one day; nothing on this side.

## QA

- `--steps audio-trial` (alone, against the service's dev server): the dev server accepts the emulator's software attestation
  and the debug build's signer (`GLAS_ATTEST_ALLOW_SOFTWARE`, refused in production), so the step shows the key enrolled
  ("verdict=enrolled level=software"), the trial every voice's, and the voice routes signed by the key (the service's metrics:
  signed requests, no replay). This is the evidence that enrollment and signing work end to end on an emulator.
- `--steps attest-refused`: the dev server as production has it refuses the emulator's chain (`root_unknown`): a trial of the
  library only, no card, a line the library has from the service and a new one from the phone, no error, the settings' line.
- `--steps attest-qakey`: Jan's QA key (a key with `app`, which the service counts as attested), written into the app's folder
  through run-as from the script's stdin (never a command line, never printed) and taken by the hook "audio:trial:qakey" of a
  debug build; a release build ignores the hook (`QaHooksTest`). Against a real service, `LANI_QA_GLAS_URL` with `GLAS_QA_KEY`
  from `~/.config/lani/keys.env`.
- **On Jan's phone:** a debug build with the hook "audio:trial:url:<a dev server the phone reaches>" and "audio:trial:attest"
  logs the service's verdict (tag GlasAttest: `verdict=enrolled level=tee`, or `verdict=refused reason=<code>`): never the
  chain. The dev server needs the debug build's signer in `GLAS_APP_SIGNERS`.

## What no emulator can show

A hardware chain (TEE or StrongBox), a verified boot and a locked bootloader, remote key provisioning and its
`ERROR_ATTESTATION_KEYS_UNAVAILABLE`, a revoked factory key, a rotated signing key: these need real phones. The service's tests
hold the checks to real chains from Google's verifier's test data (Pixel, Sony, the 2026 root, StrongBox) and to validly signed
chains under test roots, one per check. Jan's phone with the QA hook is the first real try.

## For the privacy page

docs/privacy.md is another round's this time; the line it needs: "The trial checks that the app is ours with the phone's
hardware; the service keeps a hash of a key made for this, nothing about the phone." (To be exact: it keeps the key's public
half too, to check its signatures, and the serial numbers of the chain's certificates, so that a later revocation ends the
enrollment: a factory batch's is shared by many phones, a remotely provisioned one is one phone's for a few days. All of it
goes with the enrollment.)
