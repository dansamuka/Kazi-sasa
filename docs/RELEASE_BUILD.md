# Release build guide

Covers what recommendations doc §34 asks for: keystore creation, secure
storage, Gradle signing config, APK/AAB generation, and version bumps. Nothing
here is set up automatically - `isMinifyEnabled` is `false` and there is no
signing config wired into `app/build.gradle.kts` yet, on purpose (spec
`proguard-rules.pro` comment: "flip on... once the designer's build is close
to shipping"). This doc is what to do when that time comes.

## 1. Create a keystore

One-time, per app (not per release):

```bash
keytool -genkeypair -v \
  -keystore kazi-sasa-release.keystore \
  -alias kazi-sasa \
  -keyalg RSA -keysize 2048 -validity 10000
```

You'll be prompted for a keystore password, a key password, and identity
details (organisation, city, country). **Write the passwords down somewhere
that isn't this repo.** If this keystore is lost, you cannot publish updates
to an app already on the Play Store under the same package name - there is no
recovery path from Google.

## 2. Store credentials securely - never in source control

Do **not** commit the keystore file or its passwords. Two common patterns:

**Local development:** a `keystore.properties` file at the project root,
already covered by `.gitignore`'s existing `local.properties` pattern (add
`keystore.properties` there too if you use this approach):

```properties
storeFile=../kazi-sasa-release.keystore
storePassword=...
keyAlias=kazi-sasa
keyPassword=...
```

**CI (GitHub Actions):** store the keystore as a base64-encoded repository
secret and the passwords as separate secrets, decode at build time. Do not
put real values in the workflow YAML itself.

## 3. Wire signing into `app/build.gradle.kts`

```kotlin
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) load(FileInputStream(file))
}

android {
    signingConfigs {
        create("release") {
            storeFile = keystoreProperties["storeFile"]?.let { file(it) }
            storePassword = keystoreProperties["storePassword"] as String?
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true // see note below before flipping this on
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

Requires `import java.util.Properties` and `import java.io.FileInputStream`
at the top of `app/build.gradle.kts`.

**Before setting `isMinifyEnabled = true`:** run a release build and actually
exercise Room queries, Retrofit calls, and kotlinx.serialization
encode/decode on a real device first. `app/proguard-rules.pro` is currently
empty - R8 stripping something it shouldn't (a DAO implementation class, a
`@Serializable` class) fails silently as a runtime crash, not a build error.

## 4. Generate a release build

```bash
./gradlew bundleRelease   # .aab for Play Store upload - this is what Play Console wants
./gradlew assembleRelease # .apk - for direct/sideload distribution, internal testing outside Play Console
```

Output:
- AAB: `app/build/outputs/bundle/release/app-release.aab`
- APK: `app/build/outputs/apk/release/app-release.apk`

## 5. Version bumps

`app/build.gradle.kts`'s `defaultConfig`:

```kotlin
versionCode = 1      // must strictly increase on every Play Store upload, no exceptions
versionName = "2.0"  // user-visible - semantic versioning is a reasonable default (major.minor)
```

Bump both before every release build you intend to actually publish.
`versionCode` is what Google Play tracks internally; two uploads with the
same `versionCode` will be rejected regardless of `versionName`.
