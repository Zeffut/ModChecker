plugins {
    id("dev.kikugie.stonecutter")
    id("fabric-loom") version "1.16.3" apply false
    id("net.neoforged.moddev") version "2.0.141" apply false
}

// Active node Stonecutter checks out into `src`. 1.21.11 is the build target for this phase.
stonecutter active "1.21.11-fabric"

stonecutter parameters {
    // Expose a `fabric` / `neoforge` constant derived from the node suffix, so source files
    // can be gated with `//? if fabric {` ... `//?}` and `//? if neoforge {` ... `//?}`.
    constants.match(node.metadata.project.substringAfterLast('-'), "fabric", "neoforge")
}
