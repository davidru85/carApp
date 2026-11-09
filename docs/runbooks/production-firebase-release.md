# Production Firebase Release Runbook

This runbook creates the production Firebase project `davidruiz-carapp` and proves a release build of
each platform against it (`docs/BACKLOG.md` §`E4-10`, `D-258`). Steps marked **P** are run by the owner.
Steps marked **A** are repository changes made by the agent between them. The agent never runs a **P**
step.

## 1. Rules for every step

- Run every command from the root of the checkout that holds this file, in order. A step starts only
  after the previous one reads back as expected.
- Every Firebase CLI deploy to `davidruiz-carapp` names its targets with `--only`, and the list never
  contains `auth` (`D-272`) or `functions:dev-billing` (`D-247`).
- Production has no automatic billing cutoff (`D-66`): its budget only sends alerts.
- **Never paste** into the conversation or into any repository file: passwords, the keystore or its
  properties file, the Apple Team ID, API key strings, OAuth client secrets, access or debug tokens,
  user IDs or email addresses. **Paste** command results, app IDs, the project number and the read-backs
  each step names.
- If a step fails or reads back differently, stop and report it. Do not work around it.

Helpers used by later steps (zsh or bash):

```bash
FIREBASE=./node_modules/.bin/firebase
api() {
  curl -sS -H "Authorization: Bearer $(gcloud auth print-access-token)" \
    -H "x-goog-user-project: davidruiz-carapp" -H "Content-Type: application/json" "$@"
}
```

## 2. Steps

### P0 - Tools

```bash
gcloud auth login
npm ci
npm --prefix functions ci
```

`.npmrc` disables lifecycle scripts (`D-51`); do not override it.

### P1 - Create the project

```bash
$FIREBASE projects:create davidruiz-carapp --display-name "CarApp"
gcloud projects describe davidruiz-carapp --format='value(projectNumber,lifecycleState)'
```

Paste: the creation output and the project number. A successful creation proves the ID available
(`D-32`). If the ID is taken, stop: the project ID is an owner decision.

### A1 - Record the project identity

The agent records `D-237` (ADR-0238) and the production project rows of `docs/identifiers.md`.

### P2 - Billing and budget

`D-66` gives production its own billing account, separate from the development one, whose cutoff
identity holds `roles/billing.admin` (`D-69`). Create it in the Google Cloud console (Billing → Manage
billing accounts → Create account) with currency EUR, then:

```bash
export CARAPP_PROD_BILLING_ACCOUNT=<the new billing account ID>
gcloud billing projects link davidruiz-carapp --billing-account="$CARAPP_PROD_BILLING_ACCOUNT"
gcloud services enable billingbudgets.googleapis.com --project=davidruiz-carapp
gcloud billing budgets create --billing-account="$CARAPP_PROD_BILLING_ACCOUNT" \
  --billing-project=davidruiz-carapp --display-name="CarApp production" --budget-amount=10EUR \
  --filter-projects=projects/davidruiz-carapp \
  --threshold-rule=percent=0.5 --threshold-rule=percent=0.9 --threshold-rule=percent=1.0 \
  --threshold-rule=percent=1.0,basis=forecasted-spend
```

Read back:

```bash
gcloud billing projects describe davidruiz-carapp --format='value(billingEnabled,billingAccountName)'
gcloud billing budgets list --billing-account="$CARAPP_PROD_BILLING_ACCOUNT" \
  --billing-project=davidruiz-carapp --format=yaml
```

Expected: billing enabled; one budget of 10 EUR, filtered to `projects/davidruiz-carapp`, with the four
thresholds and **no** `notificationsRule.pubsubTopic`.

### P3 - Firestore

`europe-west1` is immutable after creation (`D-13`).

```bash
gcloud services enable firestore.googleapis.com --project=davidruiz-carapp
gcloud firestore databases create --project=davidruiz-carapp --location=europe-west1 --type=firestore-native
gcloud firestore databases describe --project=davidruiz-carapp --database='(default)' \
  --format='value(locationId,type)'
```

Expected: `europe-west1  FIRESTORE_NATIVE`.

### P4 - Identity Platform and anonymous sign-in

```bash
gcloud services enable identitytoolkit.googleapis.com --project=davidruiz-carapp
api -X POST -d '{}' \
  https://identitytoolkit.googleapis.com/v2/projects/davidruiz-carapp/identityPlatform:initializeAuth
api -X PATCH -d '{"signIn":{"anonymous":{"enabled":true}},"autodeleteAnonymousUsers":true}' \
  "https://identitytoolkit.googleapis.com/admin/v2/projects/davidruiz-carapp/config?updateMask=signIn.anonymous.enabled,autodeleteAnonymousUsers"
api https://identitytoolkit.googleapis.com/admin/v2/projects/davidruiz-carapp/config \
  | jq '{subtype, anonymous: .signIn.anonymous.enabled, autodeleteAnonymousUsers}'
```

Expected: `IDENTITY_PLATFORM`, `true`, `true`, as in development.

### P5 - Register the apps

```bash
$FIREBASE apps:create ANDROID "CarApp" --package-name com.ruizurraca.carapp --project davidruiz-carapp
$FIREBASE apps:create IOS "CarApp" --bundle-id com.ruizurraca.carapp --project davidruiz-carapp
$FIREBASE apps:list --project davidruiz-carapp
```

Paste: the list. Expected: one Android and one iOS app, and **no** web app (`D-272`).

```bash
export ANDROID_APP_ID=<the Android app ID>
export IOS_APP_ID=<the iOS app ID>
```

### A2 - Production App Check sources

The agent writes `infra/google-cloud/app-check/production/` (`D-273`) with the project number and the
two app IDs.

### P6 - Apple Team ID on the iOS app

`D-270`: the personal account's Team ID, read from `iosApp/Local.xcconfig` and never printed.

```bash
TEAM_ID=$(sed -n 's/^[[:space:]]*DEVELOPMENT_TEAM[[:space:]]*=[[:space:]]*//p' iosApp/Local.xcconfig | tr -d '[:space:]')
[ -n "$TEAM_ID" ] && echo "team found" || echo "DEVELOPMENT_TEAM missing"
api -X PATCH -d "{\"teamId\":\"$TEAM_ID\"}" -o /dev/null -w '%{http_code}\n' \
  "https://firebase.googleapis.com/v1beta1/projects/davidruiz-carapp/iosApps/$IOS_APP_ID?updateMask=teamId"
api "https://firebase.googleapis.com/v1beta1/projects/davidruiz-carapp/iosApps/$IOS_APP_ID" \
  | jq --arg t "$TEAM_ID" '.teamId == $t'
```

Expected: `team found`, `200`, `true`.

### P7 - Interim Android signing key

`D-257`: a carApp-only key outside the repository. `keytool` asks for the password; use the same one for
the store and the key.

```bash
mkdir -p ~/carapp-secrets && chmod 700 ~/carapp-secrets
keytool -genkeypair -v -storetype PKCS12 -keystore ~/carapp-secrets/carapp-interim.p12 \
  -alias carapp-interim -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=CarApp"
```

Create `~/carapp-secrets/keystore.properties` (mode `600`) with `storeFile` (the absolute keystore
path), `storePassword`, `keyAlias=carapp-interim` and `keyPassword`. Add this line to
`~/.gradle/gradle.properties`, with your home directory:

```properties
carapp.releaseSigningProperties=/Users/<you>/carapp-secrets/keystore.properties
```

Register both fingerprints on the Android app. Google sign-in needs them there, not only on the API key:

```bash
keytool -list -v -keystore ~/carapp-secrets/carapp-interim.p12 -alias carapp-interim | grep -E 'SHA1:|SHA256:'
$FIREBASE apps:android:sha:create "$ANDROID_APP_ID" <SHA1 without colons> --project davidruiz-carapp
$FIREBASE apps:android:sha:create "$ANDROID_APP_ID" <SHA256 without colons> --project davidruiz-carapp
$FIREBASE apps:android:sha:list "$ANDROID_APP_ID" --project davidruiz-carapp
```

Paste: only that the list shows one `SHA_1` and one `SHA_256`. The fingerprints stay in Firebase, not in
the repository.

### P8 - Sign-in providers and brand

`D-272`: in the Firebase console, never through `firebase deploy`. Authentication → Sign-in method:

1. Google → Enable. Public-facing name: `CarApp`. Support email: yours. Save.
2. Apple → Enable. Leave the Services ID and OAuth code-flow fields empty; the revocation key is
   `E4-09`. Save.
3. Anonymous already reads Enabled (P4).

Read back without printing the Google client secret:

```bash
api https://identitytoolkit.googleapis.com/admin/v2/projects/davidruiz-carapp/defaultSupportedIdpConfigs \
  | jq '.defaultSupportedIdpConfigs[] | {name, enabled}'
```

Expected: `google.com` and `apple.com`, both `true`. Also confirm that Google Cloud console → APIs &
Services → OAuth consent screen shows the app name `CarApp`.

### P9 - Restrict the client API keys

Before any configuration file is downloaded. In Google Cloud console → APIs & Services → Credentials,
keep each key's API restrictions as Firebase created them and add only the application restriction, as
in development:

- "Android key (auto created by Firebase)": Android apps → `com.ruizurraca.carapp` with the interim
  SHA-1.
- "iOS key (auto created by Firebase)": iOS apps → `com.ruizurraca.carapp`.

```bash
gcloud services api-keys list --project=davidruiz-carapp --format='table(displayName,
  restrictions.androidKeyRestrictions.allowedApplications[].packageName.list(),
  restrictions.iosKeyRestrictions.allowedBundleIds.list(),restrictions.apiTargets.len())'
```

Expected: the two keys, each with its identifier and a non-zero API target count. Any other key, such as
a browser key, is reported before going on.

### P10 - Google Analytics

`D-271`. Firebase console → Project settings → Integrations → Google Analytics → Enable: create a new
Analytics account and property for CarApp, and clear "Use the default settings for sharing Google
Analytics data", leaving every sharing option off. Then, in Google Analytics → Admin:

- Data collection: Google signals off; ads personalization off for every region.
- Data retention: event data 2 months.
- Account settings → Data sharing settings: every option off.
- No Google Ads or other product links.

Paste: a one-line confirmation of each setting.

### P11 - App Check

`D-249`, `D-67` and `D-273`, once A2 is committed on this branch:

```bash
gcloud services enable firebaseappcheck.googleapis.com playintegrity.googleapis.com --project=davidruiz-carapp
D=infra/google-cloud/app-check/production
url() { echo "https://firebaseappcheck.googleapis.com/v1/$(jq -r .name "$1")"; }
api -X PATCH --data @$D/android-play-integrity.json "$(url $D/android-play-integrity.json)?updateMask=tokenTtl,appIntegrity,deviceIntegrity"
api -X PATCH --data @$D/ios-app-attest.json "$(url $D/ios-app-attest.json)?updateMask=tokenTtl"
api -X PATCH --data @$D/auth-enforcement.json "$(url $D/auth-enforcement.json)?updateMask=enforcementMode"
api -X PATCH --data @$D/firestore-enforcement.json "$(url $D/firestore-enforcement.json)?updateMask=enforcementMode"
for f in $D/*.json; do echo "== $f"; api "$(url $f)" | jq -S .; done
```

Paste: the read-backs. Each must match its file. If the API rejects `appIntegrity` or
`deviceIntegrity`, stop and report; do not set them in the console without telling the agent.
Enforcement can take 15 minutes to propagate.

### P12 - Firestore rules and indexes

```bash
$FIREBASE deploy --only firestore:rules,firestore:indexes --project davidruiz-carapp
```

### P13 - Cloud Functions

`D-247`: the `default` codebase only. If the deploy offers to set an artifact cleanup policy, accept one
day.

```bash
$FIREBASE deploy --only functions:default --project davidruiz-carapp
$FIREBASE functions:artifacts:setpolicy --location europe-west1 --days 1 --force --project davidruiz-carapp
gcloud functions list --project=davidruiz-carapp --format='table(name,state,environment)'
gcloud artifacts repositories describe gcf-artifacts --location=europe-west1 --project=davidruiz-carapp \
  --format='yaml(cleanupPolicies)'
```

Expected: `deleteAccount`, `issueOrphanCleanupTicket`, `deleteOrphanedAnonymousAccount` and
`onAnonymousUserDeleted` active, no `stopBilling`, and a one-day `DELETE` policy (`D-73`).

### P14 - Client configuration files

Only after P8, P9 and P10:

```bash
$FIREBASE apps:sdkconfig ANDROID "$ANDROID_APP_ID" --project davidruiz-carapp -o androidApp/src/release/google-services.json
$FIREBASE apps:sdkconfig IOS "$IOS_APP_ID" --project davidruiz-carapp -o iosApp/Config/Release/GoogleService-Info.plist
```

Tell the agent; do not commit them. `.gitignore` still ignores both paths until A4.

### A3 and A4 - Guards and configuration

A3 (RED) changes the `E4-04` guards from "absent is correct" to "present and production-only". A4
(GREEN) commits the two files with their `.gitignore` negations, sets `GOOGLE_REVERSED_CLIENT_ID` in
`iosApp/Config/Release/Firebase.xcconfig` and adds the plist as a Release resource (`D-238`, `D-261`).

### P15 - Release builds on devices

Use the OnePlus 3 and the iPhone 13 mini. The release identity `com.ruizurraca.carapp` installs beside
the `.debug` one.

Android:

```bash
./gradlew :androidApp:assembleRelease
SDK=$(sed -n 's/^sdk.dir=//p' local.properties)
APKSIGNER="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)/apksigner"
"$APKSIGNER" verify --print-certs androidApp/build/outputs/apk/release/androidApp-release.apk | grep SHA-256
adb -s <OnePlus serial> install -r androidApp/build/outputs/apk/release/androidApp-release.apk
```

The printed SHA-256 must equal the keystore's (P7).

iOS (`-allowProvisioningUpdates` lets Xcode register `com.ruizurraca.carapp` with Sign in with Apple and
App Attest on the personal team):

```bash
cd iosApp
xcodebuild -project carApp.xcodeproj -scheme carApp -configuration Release -sdk iphoneos \
  -destination 'generic/platform=iOS' -derivedDataPath build/ios-release -allowProvisioningUpdates \
  build 2>&1 | tee build/ios-release.log | tail -3
grep -c 'Upload Crashlytics symbols' build/ios-release.log
xcrun devicectl device install app --device <iPhone identifier> build/ios-release/Build/Products/Release-iphoneos/carApp.app
cd ..
```

Expected: `** BUILD SUCCEEDED **` and a non-zero count, which is the `D-240` evidence that the symbol
upload phase ran.

On each device, online:

1. Open the app → Continue without an account. The app signs in anonymously.
2. Add a vehicle. Settings shows the backup as complete.
3. Firebase console → Firestore: the vehicle document exists for that user.
4. Settings → create an account with Google (Android) or Apple (iOS). The backup completes again.
5. Uninstall, reinstall, and sign in with the same account. The vehicle is restored.

Paste: the result of each numbered item per device, and any error code shown.

### P16 - Clean up

On each device, Settings → delete the account (`D-23`), so no test data stays in production.

### A5 - Close the story

The agent updates `docs/SECURITY.md`, `docs/identifiers.md` (app registrations), `AGENTS.md`, the
handoff and the project log, and opens the pull request. The owner merges.
