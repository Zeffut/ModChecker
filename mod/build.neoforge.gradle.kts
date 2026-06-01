import net.neoforged.moddevgradle.dsl.NeoForgeExtension

plugins {
    id("net.neoforged.moddev")
}

// Resolve moddev's extension explicitly: Gradle does not generate the `neoForge` accessor for
// Stonecutter's non-`build.gradle.kts` buildscripts.
val neoForge = extensions.getByType(NeoForgeExtension::class.java)
val mcVersion = property("deps.minecraft") as String

base.archivesName = "${property("mod.id")}-neoforge-$mcVersion"
version = "${property("mod.version")}+$mcVersion"
group = property("mod.group") as String

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
}

neoForge.apply {
    version = property("deps.neoforge") as String

    runs {
        register("client") { client() }
    }

    mods {
        register(property("mod.id") as String) {
            sourceSet(sourceSets["main"])
        }
    }
}

// Compile the Stonecutter-processed sources (where the inactive loader is commented out),
// not the raw shared `src/main`. generatedSourcesDir = versions/<node>/build/generated/stonecutter/.
sourceSets.named("main") {
    val generated = layout.buildDirectory.dir("generated/stonecutter/main")
    java.setSrcDirs(listOf(generated.get().dir("java")))
    resources.setSrcDirs(listOf(generated.get().dir("resources")))
}

val expandProps = mapOf(
    "version" to "${property("mod.version")}+$mcVersion",
    "minecraft" to mcVersion,
)

tasks.named<ProcessResources>("processResources") {
    // Only the NeoForge metadata is relevant for the NeoForge jar.
    exclude("fabric.mod.json")
    inputs.properties(expandProps)
    filesMatching("META-INF/neoforge.mods.toml") { expand(expandProps) }
}

// Every task that reads the generated sources must run after they are produced.
listOf("processResources", "compileJava", "sourcesJar").forEach { name ->
    tasks.matching { it.name == name }.configureEach { dependsOn("stonecutterGenerate") }
}

// moddev generates the MC artifacts from the (Stonecutter-processed) sources.
tasks.matching { it.name == "createMinecraftArtifacts" }.configureEach {
    dependsOn("stonecutterGenerate")
}

// 26.x requires JDK 25, 1.21.11 requires JDK 21. A Java toolchain resolves the correct
// compile JDK from org.gradle.java.installations.paths regardless of the launching JVM.
val javaTarget = if (stonecutter.eval(mcVersion, ">=26.1")) 25 else 21

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaTarget))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaTarget)
}
