plugins { `java-library` }

group = "fr.noltox.hcplugins"
version = rootProject.version

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}
