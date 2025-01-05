rootProject.name = "FastAsyncWorldEdit"

include("worldedit-libs")

listOf("1_21_4").forEach {
    include("worldedit-bukkit:adapters:adapter-$it")
}

listOf("bukkit", "core").forEach {
    include("worldedit-libs:$it")
    include("worldedit-$it")
}
include("worldedit-libs:core:ap")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
