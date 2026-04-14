val JarOutputPath: String? by project
val Vi6CleanServerPluginDirectory: String? by project

plugins {
    java
    alias(libs.plugins.shadow)
}

group = "fr.nekotine"
version = "0.0.1"
description = "vi6clean"

repositories {
    mavenLocal()
    mavenCentral()

    // PaperMC
    maven("https://repo.papermc.io/repository/maven-public/"){
        name = "papermc"
    }
    // CommandAPI
    maven ("https://repo.codemc.org/repository/maven-public/"){
        name = "commandapi"
    }
    /*
    // ProtocolLib
    maven("https://repo.dmulloy2.net/repository/public/") {
        name = "protocolib"
    }
    */
    // Simple Voice Chat
    maven("https://maven.maxhenkel.de/repository/public") {
        name = "simplevoicechat"
    }
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.protocollib)
    compileOnly(libs.commandapi)
    implementation("fr.nekotine:NekotineCore:+")
    compileOnly(libs.voicechat.api)
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<ProcessResources> {
    filteringCharset = "UTF-8"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
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

// Configuration


var outputDir = JarOutputPath
if (!Vi6CleanServerPluginDirectory.isNullOrEmpty()) {
    outputDir = Vi6CleanServerPluginDirectory
}

tasks.register<Copy>("output") {
	group = "dev"
	description = "Sends jar to a custom output directory"
	from(tasks.shadowJar)
	into(outputDir!!)
}

defaultTasks("shadowJar")
