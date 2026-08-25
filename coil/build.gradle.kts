import coil3.addAllMultiplatformTargets
import coil3.androidLibrary

plugins {
    id("com.android.library")
    id("kotlin-multiplatform")
    id("org.jetbrains.kotlin.plugin.atomicfu")
}

addAllMultiplatformTargets(libs.versions.skiko)
androidLibrary(name = "coil3.singleton")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.coilCore)
                api(libs.atomicfu)
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
