import coil3.addAllMultiplatformTargets
import coil3.androidLibrary

plugins {
    id("com.android.library")
    id("kotlin-multiplatform")
    id("org.jetbrains.kotlin.plugin.atomicfu")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

addAllMultiplatformTargets(libs.versions.skiko)
androidLibrary(name = "coil3.compose.core")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.coilCore)
                api(compose.foundation)
                api(libs.atomicfu)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.internal.testUtils)
                implementation(libs.kotlin.test)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.google.drawablepainter)
            }
        }
    }
}
