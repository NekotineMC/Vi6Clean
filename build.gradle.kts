import java.util.zip.ZipFile

group = "fr.nekotine"
version = "0.0.1"
description = "vi6clean"

repositories {
    mavenLocal()
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://repo.codemc.org/repository/maven-public/") {
        name = "commandapi"
    }
    // ProtocolLib snapshots (the server runs 5.5.0-SNAPSHOT); only net.dmulloy2 resolves here.
    maven("https://repo.dmulloy2.net/repository/public/") {
        name = "protocollib"
        content { includeGroup("net.dmulloy2") }
    }
    maven("https://maven.maxhenkel.de/repository/public") {
        name = "simplevoicechat"
    }
    maven("https://repo.spongepowered.org/repository/maven-public/") {
        name = "sponge"
    }
}

dependencies {
    compileOnly(paperweight.paperDevBundle("26.2.build.+"))
    compileOnly(libs.protocollib)
    compileOnly(libs.commandapi)
    compileOnly(libs.configurate)
    // Core and bots are plain jars from the NekotineCore included build; this
    // jar shades both and relocates CommandAPI (below).
    implementation("fr.nekotine:core")
    implementation("fr.nekotine:bots")
    // The server runs CommandAPI 11.2.0; compile keeps the locked libs.commandapi.
    runtimeOnly("dev.jorel:commandapi-paper-shade") {
        version {
            strictly(
                libs.versions.commandapi.runtime
                    .get(),
            )
        }
    }
    compileOnly(libs.voicechat.api)
}

dependencyLocking {
    lockAllConfigurations()
}

plugins {
    java
    eclipse
    alias(libs.plugins.shadow)
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("com.diffplug.spotless") version "8.4.0"
    id("fr.xxgoldenbluexx.paper-junit-server")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.shadowJar {
    from("./datapacks") {
        into("datapacks")
    }
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    format("misc") {
        target("src/**/*.java", "*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
    java {
        eclipse("4.39")
        formatAnnotations()
        forbidWildcardImports()
    }
    kotlinGradle {
        ktlint()
    }
}

testing {
    suites {
        // Plain JUnit, no server: pure logic only (runs under build/check).
        named<JvmTestSuite>("test") {
            useJUnitJupiter("5.13.4")
        }
    }
}

// Integration suite (gradlew integrationTest, never part of build/check): an
// in-process Paper server staged from test-bundle/ (see test-bundle/README.md).
paperJunitServer {
    targetPlugin = "Vi6Clean"
    pluginJar = tasks.shadowJar.flatMap { it.archiveFile }
    bundle = layout.projectDirectory.dir("test-bundle")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<ProcessResources> {
    filteringCharset = "UTF-8"
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    // Core stays unrelocated so integration tests import its types; CommandAPI
    // goes under Core's package, where Core used to shade it.
    relocate("dev.jorel.commandapi", "fr.nekotine.core.commandapi")
}

tasks.shadowJar {
    archiveClassifier = ""
}

// Guards the shading layout: Core and bots unrelocated, CommandAPI under Core's package.
val verifyShadowJarLayout by tasks.registering {
    group = "verification"
    description = "Fails if the shadow jar misses Core or bots, has the old Core relocation or unrelocated CommandAPI."
    val jar = tasks.shadowJar.flatMap { it.archiveFile }
    inputs.file(jar)
    doLast {
        val entries =
            ZipFile(jar.get().asFile).use { zip ->
                zip
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .toList()
            }
        val forbidden =
            entries.filter {
                it.startsWith("fr/nekotine/vi6clean/nekotinecore/") || it.startsWith("dev/jorel/")
            }
        if (forbidden.isNotEmpty()) {
            throw GradleException("Shadow jar has forbidden entries, e.g. ${forbidden.take(5)}")
        }
        if (entries.none { it.startsWith("fr/nekotine/core/commandapi/") }) {
            throw GradleException("Shadow jar has no entries under fr/nekotine/core/commandapi/")
        }
        val required = listOf("fr/nekotine/core/NekotinePlugin.class", "fr/nekotine/core/bot/NekotineBots.class")
        val missing = required.filterNot { it in entries }
        if (missing.isNotEmpty()) {
            throw GradleException("Shadow jar is missing $missing")
        }
    }
}

tasks.check {
    dependsOn(verifyShadowJarLayout)
}

tasks.jar {
    archiveClassifier = "noshadow"
}

val outputDir = project.findProperty("Vi6CleanJarOutputPath") ?: layout.buildDirectory.dir("dist")

tasks.register<Copy>("out") {
    group = "dev"
    description = "Sends jar to a custom output directory"
    from(tasks.shadowJar)
    dependsOn(tasks.spotlessApply)
    into(outputDir)
}

defaultTasks("out")
