import io.papermc.paperweight.userdev.PaperweightUserDependenciesExtension
import io.papermc.paperweight.userdev.PaperweightUserExtension
import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    java
}

applyCommonConfiguration()
apply(plugin = "java-library")
applyCommonJavaConfiguration(
        sourcesJar = true,
        banSlf4j = false,
)
apply(plugin = "io.papermc.paperweight.userdev")

dependencies {
    "implementation"(project(":worldedit-bukkit"))
}

tasks.named("javadoc") {
    enabled = false
}
extensions.getByType<PaperweightUserExtension>().reobfArtifactConfiguration = ReobfArtifactConfiguration.MOJANG_PRODUCTION


repositories {
    gradlePluginPortal()
}

dependencies {
    // https://repo.papermc.io/service/rest/repository/browse/maven-public/io/papermc/paper/dev-bundle/
    the<PaperweightUserDependenciesExtension>().paperDevBundle("1.21.8-R0.1-SNAPSHOT")
    compileOnly(libs.paperlib)
}
