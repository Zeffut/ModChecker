pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.4"
}

stonecutter {
    create(rootProject) {
        // Flat "split buildscript" multiloader setup (Stonecutter's recommended approach):
        // every (MC version x loader) pair is a Stonecutter node sharing a single `src/`,
        // each pointing at the matching loader buildscript.
        fun mc(version: String, vararg loaders: String) = loaders.forEach { loader ->
            version("$version-$loader", version).buildscript = "build.$loader.gradle.kts"
        }

        mc("1.21.11", "fabric", "neoforge")
        mc("26.1", "fabric", "neoforge")
        mc("26.1.1", "fabric", "neoforge")
        mc("26.1.2", "fabric", "neoforge")

        // Active / default version (also the VCS / checked-out node).
        vcsVersion = "1.21.11-fabric"
    }
}

rootProject.name = "modchecker"
