import coil3.addAllMultiplatformTargets
import coil3.androidLibrary

plugins {
    id("com.android.library")
    id("kotlin-multiplatform")
    id("org.jetbrains.kotlin.plugin.atomicfu")
}

addAllMultiplatformTargets(libs.versions.skiko)
androidLibrary(name = "coil3.svg")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.coilCore)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.core)
                implementation(libs.svg)
            }
        }
        named("nonAndroidMain") {
            dependencies {
                implementation(libs.skiko)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.internal.testUtils)
                implementation(libs.bundles.test.common)
            }
        }
    }
}
