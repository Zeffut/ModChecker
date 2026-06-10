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

    // MC 26.x ships UN-obfuscated: the Mojang `client.jar` already contains readable names
    // (net.minecraft.network.RegistryFriendlyByteBuf, net.minecraft.resources.Identifier, ...),
    // and Mojang no longer publishes a `client_mappings` proguard file for 26.x. Therefore:
    //  - loom.officialMojangMappings() fails ("Failed to find official mojang mappings").
    //  - intermediary 0.0.0 for 26.x is empty (official == intermediary, identity).
    // We feed Loom a prebuilt identity intermediary->named mapping jar (mappings/mappings.tiny
    // with only the `tiny 2 0 intermediary named` header => identity), so the mod compiles
    // directly against the Mojang names already present in the jar. It must be a real file that
    // exists at configuration time (Loom reads it during afterEvaluate), hence a committed jar
    // rather than a Gradle task output. For 1.21.11 the published Yarn mappings are used as-is.
    val yarn = (findProperty("deps.yarn") as String?)?.takeIf { it.isNotBlank() }
    if (yarn != null) {
        add("mappings", "net.fabricmc:yarn:$yarn:v2")
    } else {
        add("mappings", files(rootProject.file("mappings/mojang-26x-identity.jar")))
    }

    add("modImplementation", "net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
}

loom.runs.named("client") {
    client()
    // Harness e2e : `-PtestServer=host:port` fait auto-rejoindre ce serveur (quick-play) au lancement.
    (findProperty("testServer") as String?)?.takeIf { it.isNotBlank() }?.let { server ->
        programArgs("--quickPlayMultiplayer", server)
        vmArgs("-Dmodchecker.e2e.client=1")  // marqueur pour que le harness puisse tuer ce client
    }
    // Forward `-Pautoupdate.*` Gradle props to the dev client as system properties.
    project.properties.keys.filter { it.startsWith("autoupdate.") }.forEach { key ->
        vmArg("-D$key=${project.property(key)}")
    }
}

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

// 26.x requires JDK 25, 1.21.11 requires JDK 21. A Java toolchain resolves the correct
// compile JDK from org.gradle.java.installations.paths regardless of the launching JVM.
val javaTarget = if (stonecutter.eval(mcVersion, ">=26.1")) 25 else 21

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaTarget))
    }
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
    options.release.set(javaTarget)
}
