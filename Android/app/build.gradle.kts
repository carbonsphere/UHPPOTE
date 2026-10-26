import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// -PappVersion=1.2.3 (or v1.2.3) sets versionName 1.2.3 and versionCode 10203.
val appVersion = (findProperty("appVersion") as String?)?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() }
val appVersionCode = appVersion?.let { v ->
    val m = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(v)
        ?: error("appVersion must look like 1.2.3, got '$v'")
    val (major, minor, patch) = m.destructured.toList().map(String::toInt)
    require(minor < 100 && patch < 100) { "appVersion minor and patch must be below 100, got '$v'" }
    major * 10000 + minor * 100 + patch
}

/** The release key: from CI environment variables, else the private repo, else none (unsigned). */
class ReleaseKey(val store: File, val storePassword: String, val alias: String, val keyPassword: String)

fun releaseKey(): ReleaseKey? {
    val env = System.getenv()
    env["ANDROID_KEYSTORE_PATH"]?.takeIf { it.isNotBlank() }?.let { path ->
        fun need(name: String) = env[name]?.takeIf { it.isNotEmpty() } ?: error("$name is not set")
        return ReleaseKey(rootProject.file(path), need("ANDROID_KEYSTORE_PASSWORD"), need("ANDROID_KEY_ALIAS"), need("ANDROID_KEY_PASSWORD"))
    }
    val props = rootProject.file("../private/signing/keystore.properties")
    if (!props.isFile) return null
    val p = Properties().apply { props.inputStream().use { load(it) } }
    return ReleaseKey(
        File(props.parentFile, p.getProperty("storeFile")),
        p.getProperty("storePassword"),
        p.getProperty("keyAlias"),
        p.getProperty("keyPassword"),
    )
}

val key = releaseKey()

android {
    namespace = "com.carbonsphere.uhppote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.carbonsphere.uhppote"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode ?: 1
        versionName = appVersion ?: "0.0.0-dev"
    }

    signingConfigs {
        if (key != null) {
            create("release") {
                storeFile = key.store
                storePassword = key.storePassword
                keyAlias = key.alias
                keyPassword = key.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (key != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":protocol"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
