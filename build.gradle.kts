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
    /*
    maven("https://repo.dmulloy2.net/repository/public/") {
        name = "protocolib"
    }
     */
    maven("https://maven.maxhenkel.de/repository/public") {
        name = "simplevoicechat"
    }
}

dependencies {
    compileOnly(paperweight.paperDevBundle("26.1.2.build.+"))
    compileOnly(libs.protocollib)
    compileOnly(libs.commandapi)
    implementation("fr.nekotine:NekotineCore:+")
    compileOnly(libs.voicechat.api)
}

dependencyLocking {
    lockAllConfigurations()
}

plugins {
    java
    alias(libs.plugins.shadow)
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("com.diffplug.spotless") version "8.4.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
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

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<ProcessResources> {
    filteringCharset = "UTF-8"
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    relocate("fr.nekotine.core", "fr.nekotine.vi6clean.nekotinecore")
    relocate("dev.jorel.commandapi", "fr.nekotine.vi6clean.nekotinecore.commandapi")
}

tasks.shadowJar {
    archiveClassifier = ""
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
