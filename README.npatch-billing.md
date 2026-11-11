# NPatch Billing library

The `npatch-billing` branch maintains the embedded Billing provider as ordinary
source, based on MorpheApp/MicroG-RE revision
`a5be29c8ea3ca993839abef051e37bf8d25a7456`. No source patching is performed during
the build. The original microG licenses and file notices remain applicable.

The build includes the 14 projects needed by `vending-app`. It preserves the
embedded provider's Google checkin registration, account authentication, purchase
and consumption behavior, HTTP form encoding fix, and Compose compatibility.
Only a host that installs a caller verifier can enable Billing. The host supplies
the original app version and certificate through `NPatchBillingBridge`; the
library does not parse NPatch metadata.

## Build and publish

Use JDK 21, Android SDK platform 36, and build-tools 35.0.0:

```sh
./gradlew --no-configuration-cache publishToMavenLocal
```

Each module publishes its AAR or JAR, POM, Gradle module metadata, and sources
through Gradle's `maven-publish` plugin. The default coordinates are
`com.github.Nich87.MicroG-RE:<module>:7.2.2-npatch2`.

JitPack uses the committed wrapper and `jitpack.yml`. Consumers can depend on
`com.github.Nich87.MicroG-RE:vending-app:7.2.2-npatch2` from `https://jitpack.io`.
Tags used by consumers must not be moved. The complete matching source is kept
in this repository; do not distribute only a binary without the applicable
licenses, attribution, and corresponding source.
