package coil3

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun Project.addAllMultiplatformTargets(
    skikoVersion: Provider<String>,
    enableWasm: Boolean = true,
    enableNativeLinux: Boolean = false,
) {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> {
            applyCoilHierarchyTemplate()

            androidTarget {
                publishLibraryVariants("release")
            }

            jvm()

            if (enableWasm) {
                @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
                wasmJs {
                    browser()
                    binaries.library()
                }
            }

            if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
                iosArm64()
                iosSimulatorArm64()
            }

            ohosArm64 {
                binaries {
                    all {
                        linkerOpts(
                            "-lz",
                            "-lhilog_ndk.z",
                            "-licu",
                            "${System.getenv("OHOS_NDK_HOME")}/native/llvm/lib/aarch64-linux-ohos/libunwind.a"
                        )
                    }
                }
            }
        }
    }

    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group.startsWith("org.jetbrains.compose") && requested.version?.contains("KBA") == true) {
                if (name.contains("desktop", ignoreCase = true) || name.contains("jvm", ignoreCase = true) || name.contains("wasm", ignoreCase = true)) {
                    useVersion("1.6.1")
                }
            }
            if (requested.group == "org.jetbrains.skiko" && requested.name == "skiko") {
                if (name.contains("desktop", ignoreCase = true) || name.contains("jvm", ignoreCase = true)) {
                    useTarget("org.jetbrains.skiko:skiko-awt:0.7.97")
                }
                if (name.contains("wasm", ignoreCase = true)) {
                    useVersion("0.7.97")
                }
            }
            if (requested.group == "io.ktor" && name.contains("wasm", ignoreCase = true)) {
                useVersion("3.0.3")
            }
        }
    }
}

