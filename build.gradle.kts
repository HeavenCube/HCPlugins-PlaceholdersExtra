plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "fr.noltox.hcplugins"
version = providers.gradleProperty("version").get()

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

dependencies {
    implementation(project(":placeholders-api"))
    compileOnly("fr.noltox.hcplugins:core-api")
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.24")
    compileOnly("de.tr7zw:item-nbt-api-plugin:2.16.1")
    compileOnly("com.nexomc:nexo:1.28.0") { isTransitive = false }

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation("de.maxhenkel.voicechat:voicechat-api:2.6.24")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("paper-plugin.yml") { expand("version" to pluginVersion) }
}

tasks.jar { enabled = false }
tasks.shadowJar {
    val buildDate = providers.gradleProperty("buildDate").orNull
    val buildVersion = project.version.toString()
    val fileVersion = if (buildDate == null) buildVersion else "$buildDate-b$buildVersion"
    archiveClassifier.set("")
    archiveFileName.set("HCPlaceholdersExtra-$fileVersion.jar")
}
tasks.build { dependsOn(tasks.shadowJar) }
tasks.test { useJUnitPlatform() }
