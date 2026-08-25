import coil3.addAllMultiplatformTargets
import coil3.androidLibrary
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

val skikoJvmNativeTarget = buildString {
    append(
        when {
            System.getProperty("os.name").startsWith("Windows") -> "windows"
            System.getProperty("os.name").startsWith("Mac") -> "macos"
            System.getProperty("os.name").startsWith("Linux") -> "linux"
            else -> error("Unsupported OS: ${System.getProperty("os.name")}")
        },
    )
    append('-')
    append(
        when (System.getProperty("os.arch")) {
            "x86_64", "amd64" -> "x64"
            "aarch64" -> "arm64"
            else -> error("Unsupported architecture: ${System.getProperty("os.arch")}")
        },
    )
}

plugins {
    id("com.android.library")
    id("kotlin-multiplatform")
    id("org.jetbrains.kotlin.plugin.atomicfu")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

addAllMultiplatformTargets(libs.versions.skiko)
androidLibrary(name = "coil3.gif")

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        if (name == "ohosArm64") {
            compilations.getByName("main").cinterops.create("coilGifNative") {
                definitionFile.set(file("src/nativeInterop/cinterop/coil_gif_ohosArm64.def"))
                includeDirs("src/nativeInterop/cinterop/cpp")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.coilCore)
            api(projects.coil)
            api(projects.coilCompose)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(projects.coilComposeCore)
            implementation(libs.coroutines.core)
        }
        androidMain.dependencies {
            implementation(projects.coil)
            implementation(libs.androidx.core)
            implementation(libs.androidx.vectordrawable.animated)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        val ohosArm64Main by getting {
            dependencies {
                implementation(libs.skiko)
            }
        }
        jvmTest.dependencies {
            implementation(libs.bundles.test.jvm)
            implementation("org.jetbrains.skiko:skiko-awt-runtime-$skikoJvmNativeTarget:0.7.97")
        }
    }
}
