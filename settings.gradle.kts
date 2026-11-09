// paper-junit-server is a submodule of NekotineCore: reach it through the
// nested path. NekotineCore includes the same directory, so Gradle sees one
// build; the pluginManagement include is still needed here, because a nested
// build's plugin management is not visible to the root.
pluginManagement {
    includeBuild("NekotineCore/paper-junit-server")
}

rootProject.name = "Vi6Clean"
includeBuild("NekotineCore")
includeBuild("NekotineCore/paper-junit-server")
