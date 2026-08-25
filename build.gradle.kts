import coil3.enableComposeMetrics
import coil3.groupId
import coil3.publicModules
import coil3.versionName
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.gradlePlugin.android)
        classpath(libs.gradlePlugin.atomicFu)
        classpath(libs.gradlePlugin.jetbrainsCompose)
        classpath(libs.gradlePlugin.composeCompiler)
        classpath(libs.gradlePlugin.kotlin)
        classpath(libs.gradlePlugin.mavenPublish)
    }
}


allprojects {
    repositories {
        mavenLocal()
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public")
        google()
        mavenCentral()
    }

    // Necessary to publish to Maven.
    group = groupId
    version = versionName

    // Target JVM 11.
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_11.toString()
        targetCompatibility = JavaVersion.VERSION_11.toString()
        options.compilerArgs = options.compilerArgs + "-Xlint:-options"
    }
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions.jvmTarget = JvmTarget.JVM_11
    }

    // Target Kotlin 2.0.
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.languageVersion = KotlinVersion.KOTLIN_2_0
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name == "atomicfu") {
                useVersion(libs.versions.atomicfu.get())
            }
        }
    }
}
