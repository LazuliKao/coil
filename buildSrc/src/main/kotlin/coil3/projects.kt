package coil3

import org.gradle.api.JavaVersion
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun Project.androidLibrary(
    name: String,
    config: Boolean = false,
    action: LibraryExtension.() -> Unit = {},
) = androidBase<LibraryExtension>(name) {
    buildFeatures {
        buildConfig = config
    }
    sourceSets["main"].resources {
        srcDirs("src/commonMain/resources", "src/jvmCommonMain/resources")
    }
    if (project.name in publicModules) {
        apply(plugin = "com.vanniktech.maven.publish.base")
        setupPublishing {
            val platform = if (project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                KotlinMultiplatform()
            } else {
                AndroidSingleVariantLibrary()
            }
            configure(platform)
        }
    }
    testOptions {
        unitTests.all { test ->
            test.testLogging {
                exceptionFormat = TestExceptionFormat.FULL
                showExceptions = true
                showStackTraces = true
                showCauses = false
            }
        }
    }
    action()
}

fun Project.setupPublishing(
    action: MavenPublishBaseExtension.() -> Unit = {},
) {
    extensions.configure<MavenPublishBaseExtension> {
        pomFromGradleProperties()
        publishToMavenCentral()
        action()
    }
}

fun Project.androidApplication(
    name: String,
    action: BaseAppModuleExtension.() -> Unit = {},
) = androidBase<BaseAppModuleExtension>(name) {
    defaultConfig {
        applicationId = name
        versionCode = project.versionCode
        versionName = project.versionName
        resourceConfigurations += "en"
        vectorDrawables.useSupportLibrary = true
    }
    action()
}

fun Project.androidTest(
    name: String,
    config: Boolean = false,
    action: TestExtension.() -> Unit = {},
) = androidBase<TestExtension>(name) {
    buildFeatures {
        buildConfig = config
    }
    defaultConfig {
        vectorDrawables.useSupportLibrary = true
    }
    action()
}

private fun <T : BaseExtension> Project.androidBase(
    name: String,
    action: T.() -> Unit,
) {
    android<T> {
        namespace = name
        compileSdkVersion(project.compileSdk)
        defaultConfig {
            minSdk = project.minSdk
            targetSdk = project.targetSdk
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        packagingOptions {
            resources.pickFirsts += listOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*kotlin_module",
            )
        }
        testOptions {
            unitTests.isIncludeAndroidResources = true
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        lint {
            warningsAsErrors = true
            disable += listOf(
                "ComposableNaming",
                "UnknownIssueId",
                "UnsafeOptInUsageWarning",
                "UnusedResources",
                "UseSdkSuppress",
                "VectorPath",
                "VectorRaster",
            )
        }
        action()
    }
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinAndroidTarget>().configureEach {
                publishLibraryVariants("release")
            }
            sourceSets.configureEach {
                languageSettings {
                    optIn("coil3.annotation.DelicateCoilApi")
                    optIn("coil3.annotation.ExperimentalCoilApi")
                    optIn("coil3.annotation.InternalCoilApi")
                }
            }
            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(System.getenv("CI").toBoolean())

            val arguments = mutableListOf<String>()
            arguments += "-Xno-call-assertions"
            arguments += "-Xno-param-assertions"
            arguments += "-Xno-receiver-assertions"

            freeCompilerArgs.addAll(arguments)
        }
    }
}

private fun <T : BaseExtension> Project.android(action: T.() -> Unit) {
    extensions.configure("android", action)
}

private fun BaseExtension.lint(action: Lint.() -> Unit) {
    (this as CommonExtension<*, *, *, *, *, *>).lint(action)
}
