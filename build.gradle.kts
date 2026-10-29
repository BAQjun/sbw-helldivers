plugins {
    `java-library`
    idea
    id("net.neoforged.moddev") version "2.0.148"
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
}
version = "${property("mod_version")}-mc${property("minecraft_version")}-neoforge"
group = property("mod_group_id") as String
base { archivesName.set(property("mod_id") as String) }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
kotlin { jvmToolchain(21) }
repositories {
    mavenCentral()
    maven("https://thedarkcolour.github.io/KotlinForForge/") { content { includeGroup("thedarkcolour") } }
    maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
}
val portTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[portTest.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[portTest.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())
configurations[portTest.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())
neoForge {
    version = property("neo_version") as String
    addModdingDependenciesTo(portTest)
    runs {
        create("client") { client(); gameDirectory = file(".codex/neoforge1211/runtime-client")
            if (providers.gradleProperty("portTests").isPresent) systemProperty("sbw_helldiver.portClientTests", "true")
            if (providers.gradleProperty("rocketTests").isPresent) systemProperty("sbw_helldiver.rocketRegression", "true")
            if (providers.gradleProperty("explosionTests").isPresent) systemProperty("sbw_helldiver.explosionRegression", "true")
            if (providers.gradleProperty("destroyerTests").isPresent) systemProperty("sbw_helldiver.destroyerRegression", "true")
            if (providers.gradleProperty("mineTests").isPresent) systemProperty("sbw_helldiver.mineRegression", "true")
            if (providers.gradleProperty("hudTests").isPresent) {
                systemProperty("sbw_helldiver.hudRegression", "true")
            } }
        create("server") { server(); programArgument("--nogui"); gameDirectory = file(".codex/neoforge1211/runtime-server") }
        create("gameTestServer") { type = "gameTestServer"; gameDirectory = file(".codex/neoforge1211/runtime-gametest"); systemProperty("neoforge.enabledGameTestNamespaces", "sbw_helldiver") }
    }
    mods { create("sbw_helldiver") { sourceSet(sourceSets.main.get()); if (providers.gradleProperty("portTests").isPresent) sourceSet(portTest) } }
}
val sbwApiSource = configurations.create("sbwApiSource") { isTransitive = false }
val extractSbwApis = tasks.register<Copy>("extractSbwApis") {
    from(provider { zipTree(sbwApiSource.singleFile) })
    include("META-INF/jarjar/*geckolib*.jar", "META-INF/jarjar/*SimpleBedrockModel*.jar")
    eachFile { path = name }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("generated/sbw-api"))
}
val embeddedSbwApis = files(provider { fileTree(layout.buildDirectory.dir("generated/sbw-api")) { include("*.jar") } }).builtBy(extractSbwApis)

dependencies {
    add("sbwApiSource", "maven.modrinth:superb-warfare:bl7W8fjU")
    implementation("thedarkcolour:kotlinforforge-neoforge:5.10.0")
    implementation("maven.modrinth:superb-warfare:bl7W8fjU")
    compileOnly(embeddedSbwApis)
    compileOnly("com.maydaymemory:mae:1.1.4") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "it.unimi.dsi", module = "fastutil")
        exclude(group = "org.joml", module = "joml")
    }
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.processResources {
    includeEmptyDirs = false
    filteringCharset = "UTF-8"
    val props = listOf("minecraft_version", "neo_version", "mod_id", "mod_name", "mod_license", "mod_version", "mod_authors", "mod_description", "superbwarfare_version").associateWith { project.property(it) }
    inputs.properties(props)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta")) { expand(props) }
}

// These directories belong exclusively to explicitly requested isolated checks.
if (providers.gradleProperty("portTests").isPresent) {
    for ((runName, relativeSave) in mapOf(
        "runClient" to ".codex/neoforge1211/runtime-client/saves/port-client-test",
        "runGameTestServer" to ".codex/neoforge1211/runtime-gametest/world"
    )) {
        val reset = tasks.register<Delete>("reset${runName}World") { delete(layout.projectDirectory.dir(relativeSave)) }
        val cleanup = tasks.register<Delete>("clean${runName}World") { delete(layout.projectDirectory.dir(relativeSave)) }
        tasks.named(runName) { dependsOn(reset); finalizedBy(cleanup) }
    }
}
