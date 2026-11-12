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
    maven("https://maven.theillusivec4.top/") { content { includeGroup("top.theillusivec4.curios") } }
    maven("https://maven.createmod.net") { content { includeGroup("net.createmod.ponder") } }
    mavenCentral()
    maven("https://thedarkcolour.github.io/KotlinForForge/") { content { includeGroup("thedarkcolour") } }
    maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
}
val portTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
val taczCompatTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[taczCompatTest.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[taczCompatTest.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())
configurations[taczCompatTest.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())
configurations[portTest.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[portTest.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())
configurations[portTest.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())
val clientRunDirectory = file(providers.gradleProperty("clientRunDirectory").orElse(".codex/neoforge1211/runtime-client").get())
neoForge {
    version = property("neo_version") as String
    addModdingDependenciesTo(portTest)
    addModdingDependenciesTo(taczCompatTest)
    runs {
        create("client") { client(); gameDirectory = clientRunDirectory
            if (providers.gradleProperty("portTests").isPresent) systemProperty("sbw_helldiver.portClientTests", "true")
            if (providers.gradleProperty("guardDogAttachmentTests").isPresent) systemProperty("sbw_helldiver.guardDogAttachmentTests", "true")
            if (providers.gradleProperty("attachedLightTests").isPresent) systemProperty("sbw_helldiver.attachedLightTests", "true")
            if (providers.gradleProperty("stunGuardDogTests").isPresent) systemProperty("sbw_helldiver.stunGuardDogTests", "true")
            if (providers.gradleProperty("gasGuardDogTests").isPresent) systemProperty("sbw_helldiver.gasGuardDogTests", "true")
            if (providers.gradleProperty("flameGuardDogTests").isPresent) systemProperty("sbw_helldiver.flameGuardDogTests", "true")
            if (providers.gradleProperty("baselineNearMeshes").isPresent && providers.gradleProperty("portTests").isPresent) systemProperty("sbw_helldiver.qaDisableNearMeshes", "true")
            if (providers.gradleProperty("turretRocketTests").isPresent) { systemProperty("sbw_helldiver.turretPerformanceTests", "true"); systemProperty("sbw_helldiver.perfRocketOnly", "true") }
            if (providers.gradleProperty("turretPerformanceTests").isPresent) systemProperty("sbw_helldiver.turretPerformanceTests", "true")
            if (providers.gradleProperty("laserMeshTests").isPresent) { systemProperty("sbw_helldiver.turretPerformanceTests", "true"); systemProperty("sbw_helldiver.perfLaserOnly", "true") }
            if (providers.gradleProperty("laserGuardDogTests").isPresent) systemProperty("sbw_helldiver.laserGuardDogTests", "true")
            if (providers.gradleProperty("guardDogTests").isPresent) systemProperty("sbw_helldiver.guardDogTests", "true")
            if (providers.gradleProperty("effectDiagnostics").isPresent) { systemProperty("sbw_helldiver.visualOnly", "true"); systemProperty("sbw_helldiver.laserShaderSmokeTest", "true") }
            if (providers.gradleProperty("resupplyTests").isPresent) systemProperty("sbw_helldiver.resupplyTests", "true")
            if (providers.gradleProperty("itemDeliveryTests").isPresent) systemProperty("sbw_helldiver.itemDeliveryTests", "true")
            if (providers.gradleProperty("taczCompatTests").isPresent) systemProperty("sbw_helldiver.taczCompatTests", "true")
            if (providers.gradleProperty("rocketTests").isPresent) systemProperty("sbw_helldiver.rocketRegression", "true")
            if (providers.gradleProperty("explosionTests").isPresent) systemProperty("sbw_helldiver.explosionRegression", "true")
            if (providers.gradleProperty("destroyerTests").isPresent) systemProperty("sbw_helldiver.destroyerRegression", "true")
            if (providers.gradleProperty("mineTests").isPresent) systemProperty("sbw_helldiver.mineRegression", "true")
            if (providers.gradleProperty("equipmentTests").isPresent) systemProperty("sbw_helldiver.equipmentRegression", "true")
            if (providers.gradleProperty("equipmentCollisionOnly").isPresent) systemProperty("sbw_helldiver.equipmentCollisionOnly", "true")
            if (providers.gradleProperty("sampleTests").isPresent) systemProperty("sbw_helldiver.sampleRegression", "true")
            if (providers.gradleProperty("samplePonderTests").isPresent) systemProperty("sbw_helldiver.samplePonderTests", "true")
            if (providers.gradleProperty("tacticalTests").isPresent) systemProperty("sbw_helldiver.tacticalRegression", "true")
            if (providers.gradleProperty("machineTests").isPresent) systemProperty("sbw_helldiver.machineRegression", "true")
            if (providers.gradleProperty("hudTests").isPresent) {
                systemProperty("sbw_helldiver.hudRegression", "true")
            } }
        create("server") { server(); programArgument("--nogui"); gameDirectory = file(".codex/neoforge1211/runtime-server") }
        create("gameTestServer") { type = "gameTestServer"; gameDirectory = file(".codex/neoforge1211/runtime-gametest"); systemProperty("neoforge.enabledGameTestNamespaces", if (providers.gradleProperty("resupplyTests").isPresent) "sbw_helldiver_samples" else if (providers.gradleProperty("taczCompatTests").isPresent) "sbw_helldiver_tacz" else if (providers.gradleProperty("sampleTests").isPresent) "sbw_helldiver_samples" else if (providers.gradleProperty("destroyerPersistenceTests").isPresent) "sbw_helldiver_destroyer" else "sbw_helldiver") }
    }
    mods { create("sbw_helldiver") { sourceSet(sourceSets.main.get()); if (providers.gradleProperty("portTests").isPresent) sourceSet(portTest); if (providers.gradleProperty("taczCompatTests").isPresent) sourceSet(taczCompatTest) } }
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
    compileOnly("top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1:api") { isTransitive = false }
    // Official TaCZ 1.2.0 NeoForge artifact, pinned independently of the Forge upload.
    compileOnly("maven.modrinth:timeless-and-classics-zero:O2Pfsh6w")
    if (providers.gradleProperty("taczCompatTests").isPresent) runtimeOnly("maven.modrinth:timeless-and-classics-zero:O2Pfsh6w")
    compileOnly("net.createmod.ponder:ponder-neoforge:1.0.87+mc1.21.1") { isTransitive = false }
    compileOnly("maven.modrinth:jei:UJRXzDfp") { isTransitive = false }
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
    // Optional QA geometry overrides, isolated from the checked-in runtime assets.
    val nearOverride = providers.gradleProperty("nearModelOverride").orElse("").get()
    inputs.property("nearModelOverride", nearOverride)
    doLast {
        if (nearOverride.isNotEmpty()) copy {
            from(file(nearOverride).resolve("assets"))
            into(destinationDir.resolve("assets"))
        }
    }
}

// These directories belong exclusively to explicitly requested isolated checks.
if (providers.gradleProperty("portTests").isPresent) {
    for ((runName, testSave) in mapOf(
        "runClient" to clientRunDirectory.resolve("saves/port-client-test"),
        "runGameTestServer" to file(".codex/neoforge1211/runtime-gametest/world")
    )) {
        val reset = tasks.register<Delete>("reset${runName}World") { delete(testSave) }
        val cleanup = tasks.register<Delete>("clean${runName}World") { delete(testSave) }
        tasks.named(runName) { dependsOn(reset); finalizedBy(cleanup) }
    }
}
