pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/releases/")
        maven("https://repo.codemc.io/repository/maven-public/")
        maven("https://repo.nexomc.com/releases/")
        maven("https://maven.maxhenkel.de/repository/public")
    }
}

rootProject.name = "HCPlugins-PlaceholdersExtra"
include(":placeholders-api")

val coreBuild = file(".hcplugins/HCPlugins-Core").takeIf { it.isDirectory }
    ?: file("../HCPlugins-Core")
require(coreBuild.resolve("settings.gradle.kts").isFile) {
    "Clone HCPlugins-Core next to this repository before building."
}
includeBuild(coreBuild) {
    dependencySubstitution {
        substitute(module("fr.noltox.hcplugins:core-api")).using(project(":core-api"))
    }
}
