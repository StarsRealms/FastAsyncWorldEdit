import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.papermc.paperweight.userdev.attribute.Obfuscation

plugins {
    `java-library`
    alias(libs.plugins.mod.publish.plugin)
}

project.description = "Bukkit"

applyPlatformAndCoreConfiguration()
applyShadowConfiguration()

repositories {
    mavenLocal()
    maven {
        name = "AliYun-Snapshot"
        url = uri("https://packages.aliyun.com/maven/repository/2421751-snapshot-i7Aufp/")
        credentials {
            username = project.findProperty("aliyun.package.user") as String? ?: System.getenv("ALY_USER")
            password = project.findProperty("aliyun.package.password") as String? ?: System.getenv("ALY_PASSWORD")
        }
    }
    maven {
        name = "PaperMC"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "EngineHub"
        url = uri("https://maven.enginehub.org/repo/")
    }
    maven {
        name = "JitPack"
        url = uri("https://jitpack.io")
    }
    maven {
        name = "GriefDefender"
        url = uri("https://repo.glaremasters.me/repository/bloodshot/")
    }
    maven {
        name = "OSS Sonatype Snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
        name = "Glaremasters"
        url = uri("https://repo.glaremasters.me/repository/towny/")
    }
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") // TODO Remove when 4.17.0 is released
    flatDir { dir(File("src/main/resources")) }
}

val localImplementation = configurations.create("localImplementation") {
    description = "Dependencies used locally, but provided by the runtime Bukkit implementation"
    isCanBeConsumed = false
    isCanBeResolved = false
}

val adapters = configurations.create("adapters") {
    description = "Adapters to include in the JAR (Mojmap)"
    isCanBeConsumed = false
    isCanBeResolved = true
    shouldResolveConsistentlyWith(configurations["runtimeClasspath"])
    attributes {
        attribute(Obfuscation.OBFUSCATION_ATTRIBUTE, objects.named(Obfuscation.NONE))
    }
}

dependencies {
    // Modules
    api(projects.worldeditCore)
    api(projects.worldeditLibs.bukkit)

    project.project(":worldedit-bukkit:adapters").subprojects.forEach {
        "adapters"(project(it.path))
    }

    // Minecraft expectations
    implementation(libs.fastutil)

    // Platform expectations
    compileOnly(libs.paper) {
        exclude("junit", "junit")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    // Logging
    localImplementation(libs.log4jApi)
    localImplementation(libs.log4jBom) {
        because("Spigot provides Log4J (sort of, not in API, implicitly part of server)")
    }

    // Plugins
    compileOnly(libs.vault) { isTransitive = false }
    compileOnly(libs.dummypermscompat) {
        exclude("com.github.MilkBowl", "VaultAPI")
    }
    compileOnly(libs.worldguard) {
        exclude("com.sk89q.worldedit", "worldedit-bukkit")
        exclude("com.sk89q.worldedit", "worldedit-core")
        exclude("com.sk89q.worldedit.worldedit-libs", "bukkit")
        exclude("com.sk89q.worldedit.worldedit-libs", "core")
    }
    compileOnly(libs.mapmanager) { isTransitive = false }
    compileOnly(libs.griefprevention) { isTransitive = false }
    compileOnly(libs.griefdefender) { isTransitive = false }
    compileOnly(libs.residence) { isTransitive = false }
    compileOnly(libs.towny) { isTransitive = false }
    compileOnly(libs.plotSquaredBukkit) { isTransitive = false }
    compileOnly(libs.plotSquaredCore) { isTransitive = false }

    // Third party
    implementation(libs.paperlib)
    implementation(libs.bstatsBukkit) { isTransitive = false }
    implementation(libs.bstatsBase) { isTransitive = false }
    implementation(libs.serverlib)
    implementation(libs.paster) { isTransitive = false }
    api(libs.lz4Java) { isTransitive = false }
    api(libs.sparsebitset) { isTransitive = false }
    api(libs.parallelgzip) { isTransitive = false }
    compileOnly(libs.adventureApi)
    compileOnlyApi(libs.checkerqual)
    compileOnly("com.starsrealms.starock:api:1.7.1-20250715.033921-1")
    compileOnly("com.starsrealms.starock:plugin:1.7.1-20250715.033921-1")

    // Tests
    testImplementation(libs.mockito)
    testImplementation(libs.adventureApi)
    testImplementation(libs.checkerqual)
    testImplementation(libs.paper) { isTransitive = true }
}

tasks.named<Copy>("processResources") {
    val internalVersion = project.ext["internalVersion"]
    inputs.property("internalVersion", internalVersion)
    filesMatching("paper-plugin.yml") {
        expand("internalVersion" to internalVersion)
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes("Class-Path" to CLASSPATH,
                "WorldEdit-Version" to project.version)
    }
}

addJarManifest(WorldEditKind.Plugin, includeClasspath = true)

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("${rootProject.name}-Paper-${project.version}.${archiveExtension.getOrElse("jar")}")
    configurations.add(adapters)
    manifest {
        attributes(
            "paperweight-mappings-namespace" to "mojang",
            "FAWE-Plugin-Jar-Type" to "mojang"
        )
    }
}

tasks.withType<ShadowJar>().configureEach {
    dependencies {
        // In tandem with not bundling log4j, we shouldn't relocate base package here.
        // relocate("org.apache.logging", "com.sk89q.worldedit.log4j")
        relocate("org.antlr.v4", "com.sk89q.worldedit.antlr4")

        exclude(dependency("$group:$name"))

        include(dependency(":worldedit-core"))
        include(dependency(":worldedit-libs:bukkit"))
        // Purposefully not included, we assume (even though no API exposes it) that Log4J will be present at runtime
        // If it turns out not to be true for Spigot/Paper, our only two official platforms, this can be uncommented.
        // include(dependency("org.apache.logging.log4j:log4j-api"))
        include(dependency("org.antlr:antlr4-runtime"))
        // ZSTD does not work if relocated. https://github.com/luben/zstd-jni/issues/189 Use not latest as it can be difficult
        // to obtain latest ZSTD lib
        include(dependency("com.github.luben:zstd-jni:1.4.8-1"))
        relocate("org.bstats", "com.sk89q.worldedit.bstats") {
            include(dependency("org.bstats:"))
        }
        relocate("io.papermc.lib", "com.sk89q.worldedit.bukkit.paperlib") {
            include(dependency("io.papermc:paperlib"))
        }
        relocate("it.unimi.dsi.fastutil", "com.sk89q.worldedit.bukkit.fastutil") {
            include(dependency("it.unimi.dsi:fastutil"))
        }
        relocate("net.royawesome.jlibnoise", "com.sk89q.worldedit.jlibnoise")
        relocate("org.incendo.serverlib", "com.fastasyncworldedit.serverlib") {
            include(dependency("dev.notmyfault.serverlib:ServerLib:2.3.7"))
        }
        relocate("com.intellectualsites.paster", "com.fastasyncworldedit.paster") {
            include(dependency("com.intellectualsites.paster:Paster"))
        }
        relocate("org.lz4", "com.fastasyncworldedit.core.lz4") {
            include(dependency("org.lz4:lz4-java:1.8.0"))
        }
        relocate("com.zaxxer", "com.fastasyncworldedit.core.math") {
            include(dependency("com.zaxxer:SparseBitSet:1.3"))
        }
        relocate("org.anarres", "com.fastasyncworldedit.core.internal.io") {
            include(dependency("org.anarres:parallelgzip:1.0.5"))
        }
    }

    project.project(":worldedit-bukkit:adapters").subprojects.forEach {
        dependencies {
            include(dependency("${it.group}:${it.name}"))
        }
        minimize {
            exclude(dependency("${it.group}:${it.name}"))
        }
    }
}

tasks.named("assemble").configure {
    dependsOn("shadowJar")
}
