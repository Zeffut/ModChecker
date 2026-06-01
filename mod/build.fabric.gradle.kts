import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.configuration.ide.RunConfigSettings

plugins {
    // Classic full Loom (LoomGradlePlugin) registers `minecraft`, `mappings`,
    // `modImplementation` configurations and jar remapping.
    id("fabric-loom")
}

// Resolve Loom's extension explicitly: Gradle does not generate the `loom` accessor for
// Stonecutter's non-`build.gradle.kts` buildscripts.
val loom = extensions.getByType(LoomGradleExtensionAPI::class.java)
val mcVersion = property("deps.minecraft") as String

base.archivesName = "${property("mod.id")}-fabric-$mcVersion"
version = "${property("mod.version")}+$mcVersion"
group = property("mod.group") as String

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
}

dependencies {
    // Generic add(configuration, notation) avoids relying on Loom's Kotlin DSL accessors,
    // which Gradle does not generate for Stonecutter's non-`build.gradle.kts` buildscripts.
    add("minecraft", "com.mojang:minecraft:$mcVersion")

    // MC 26.x has no Yarn mappings published -> fall back to Mojang mappings.
    val yarn = (findProperty("deps.yarn") as String?)?.takeIf { it.isNotBlank() }
    add(
        "mappings",
        if (yarn != null) "net.fabricmc:yarn:$yarn:v2"
        else loom.officialMojangMappings()
    )

    add("modImplementation", "net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
}

loom.runs.named("client") { client() }

val expandProps = mapOf(
    "version" to "${property("mod.version")}+$mcVersion",
    "minecraft" to mcVersion,
)

tasks.named<ProcessResources>("processResources") {
    // Only the Fabric metadata is relevant for the Fabric jar.
    exclude("META-INF/neoforge.mods.toml")
    inputs.properties(expandProps)
    filesMatching("fabric.mod.json") { expand(expandProps) }
}

// Every task that reads the generated sources must run after they are produced.
listOf("processResources", "compileJava", "sourcesJar").forEach { name ->
    tasks.matching { it.name == name }.configureEach { dependsOn("stonecutterGenerate") }
}

java {
    val javaVersion = if (stonecutter.eval(mcVersion, ">=26.1")) JavaVersion.VERSION_25 else JavaVersion.VERSION_21
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    withSourcesJar()
}

// Compile the Stonecutter-processed sources (where the inactive loader is commented out),
// not the raw shared `src/main`. generatedSourcesDir = versions/<node>/build/generated/stonecutter/.
sourceSets.named("main") {
    val generated = layout.buildDirectory.dir("generated/stonecutter/main")
    java.setSrcDirs(listOf(generated.get().dir("java")))
    resources.setSrcDirs(listOf(generated.get().dir("resources")))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(if (stonecutter.eval(mcVersion, ">=26.1")) 25 else 21)
}
