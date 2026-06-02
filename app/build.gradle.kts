plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val xtunnelLib = rootProject.layout.projectDirectory.file("out/apk-inspect/xtunnel/extract/lib/arm64-v8a/libgo.so")
val xtunnelEmbed = rootProject.layout.projectDirectory.file("out/apk-inspect/xtunnel/extract/assets/flutter_assets/assets/embed.data")
val generatedXtunnelDir = layout.buildDirectory.dir("generated/xtunnel")

val prepareXtunnelJniLibs by tasks.registering(Copy::class) {
    from(xtunnelLib)
    into(generatedXtunnelDir.map { it.dir("jniLibs/arm64-v8a") })
    doFirst {
        if (!xtunnelLib.asFile.isFile) {
            throw GradleException("Missing XTunnel native library: ${xtunnelLib.asFile}")
        }
    }
}

val prepareXtunnelAssets by tasks.registering(Copy::class) {
    from(xtunnelEmbed)
    into(generatedXtunnelDir.map { it.dir("assets") })
    doFirst {
        if (!xtunnelEmbed.asFile.isFile) {
            throw GradleException("Missing XTunnel embed asset: ${xtunnelEmbed.asFile}")
        }
    }
}

android {
    namespace = "com.example.magnetcatcher"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "com.example.magnetcatcher.modern"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0-modern"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.named("main") {
        jniLibs.directories.add(generatedXtunnelDir.get().dir("jniLibs").asFile.absolutePath)
        assets.directories.add(file("src/main/assets").absolutePath)
        assets.directories.add(generatedXtunnelDir.get().dir("assets").asFile.absolutePath)
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }
}

tasks.matching { it.name.matches(Regex("merge.*JniLibFolders")) }.configureEach {
    dependsOn(prepareXtunnelJniLibs)
}

tasks.matching { it.name.matches(Regex("merge.*Assets")) }.configureEach {
    dependsOn(prepareXtunnelAssets)
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.webkit)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
