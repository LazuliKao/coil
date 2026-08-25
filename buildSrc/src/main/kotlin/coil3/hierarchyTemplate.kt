@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

package coil3

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

private val hierarchyTemplate = KotlinHierarchyTemplate {
    withSourceSetTree(
        KotlinSourceSetTree.main,
        KotlinSourceSetTree.test,
    )

    common {
        withCompilations { true }

        groupNonAndroid()
        groupJsCommon()
        groupNonJsCommon()
        groupJvmCommon()
        groupNonJvmCommon()
        groupNative()
        groupNonNative()
        groupNonApple()
        groupOhos()
        groupNonOhos()
    }
}

private fun KotlinHierarchyBuilder.groupNonApple() {
    group("nonApple") {
        groupJvmCommon()
        groupJsCommon()
        groupLinux()
        groupOhos()
    }
}

private fun KotlinHierarchyBuilder.groupNonAndroid() {
    group("nonAndroid") {
        withJvm()
        groupJsCommon()
        groupNative()
        groupOhos()
    }
}

private fun KotlinHierarchyBuilder.groupJsCommon() {
    group("jsCommon") {
        withJs()
        withWasmJs()
    }
}

private fun KotlinHierarchyBuilder.groupNonJsCommon() {
    group("nonJsCommon") {
        groupJvmCommon()
        groupNative()
        groupOhos()
    }
}

private fun KotlinHierarchyBuilder.groupJvmCommon() {
    group("jvmCommon") {
        withAndroidTarget()
        withJvm()
    }
}

private fun KotlinHierarchyBuilder.groupNonJvmCommon() {
    group("nonJvmCommon") {
        groupJsCommon()
        groupNative()
        groupOhos()
    }
}

private fun KotlinHierarchyBuilder.groupNative() {
    group("native") {
        // Exclude ohos targets so they don't inherit Apple-specific code (e.g. NSURLMapper).
        withCompilations { it.platformType == KotlinPlatformType.native && !it.target.name.startsWith("ohos") }

        groupApple()
        groupLinux()
    }
}

private fun KotlinHierarchyBuilder.groupApple() {
    group("apple") {
        withApple()

        group("ios") {
            withIos()
        }

        group("macos") {
            withMacos()
        }
    }
}

private fun KotlinHierarchyBuilder.groupLinux() {
    group("linux") {
        withLinux()
    }
}

private fun KotlinHierarchyBuilder.groupNonNative() {
    group("nonNative") {
        groupJsCommon()
        groupJvmCommon()
    }
}

private fun KotlinHierarchyBuilder.groupOhos() {
    group("ohos") {
        withCompilations { it.platformType == KotlinPlatformType.native && it.target.name.startsWith("ohos") }
    }
}

private fun KotlinHierarchyBuilder.groupNonOhos() {
    group("nonOhos") {
        groupJvmCommon()
        groupNative()
    }
}

fun KotlinMultiplatformExtension.applyCoilHierarchyTemplate() {
    applyHierarchyTemplate(hierarchyTemplate)
}
