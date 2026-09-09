import org.gradle.buildconfiguration.tasks.UpdateDaemonJvm
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
}

// Keep the Gradle daemon JVM criteria aligned with the app's Java 17 source/target
// compatibility and resolve a JetBrains Runtime (vendor "JetBrains") JDK on demand:
// updateDaemonJvm generates gradle/gradle-daemon-jvm.properties pointing at JetBrains
// Runtime downloads so an incompatible/missing JDK is fetched automatically.
tasks.withType(UpdateDaemonJvm::class.java).configureEach {
    languageVersion.set(JavaLanguageVersion.of(17))
    vendor.set(JvmVendorSpec.JETBRAINS)
}
