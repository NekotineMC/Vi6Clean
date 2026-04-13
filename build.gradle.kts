val JarOutputPath: String by project

plugins {
    java
    id("com.gradleup.shadow") version "+"
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
	compileOnly("io.papermc.paper:paper-api:1.21.11+")
	compileOnly("net.dmulloy2:ProtocolLib:+")
	compileOnly("dev.jorel:commandapi-paper-shade:11.1+")
	implementation( "fr.nekotine:NekotineCore:+")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:+")
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

tasks.register<Copy>("output") {
	group = "dev"
	description = "Sends jar to a custom output directory"
	from(tasks.shadowJar)
	into(JarOutputPath)
}

defaultTasks("shadowJar")
