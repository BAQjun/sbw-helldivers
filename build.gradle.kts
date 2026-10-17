plugins {
    id("eclipse")
    id("idea")
    id("net.minecraftforge.gradle") version "[6.0.16,6.2)"
    id("org.jetbrains.kotlin.jvm") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

version = "${project.property("mod_version")}-mc${project.property("minecraft_version")}"
group = project.property("mod_group_id").toString()

base {
    archivesName.set(project.property("mod_id").toString())
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

minecraft {
    // 使用官方映射（Mojang），避免依赖连不上的 parchmentmc 仓库
    mappings("official", "1.20.1")
    copyIdeResources.set(true)

    // 这里**故意没有** `accessTransformer(...)`。
    //
    // 本来挂了一个只有注释、没有条目的 accesstransformer.cfg，结果 ForgeGradle 的
    // AT 解析器会把 `#` 开头的注释行当成非法行报错，进而导致 remap 后的 Forge 产物
    // 生成不出来，编译直接卡在依赖解析上（报「Could not find net.minecraftforge:forge:...
    // _mapped_official_..._at_<hash>」）。
    //
    // 本模组目前不需要开放任何私有成员：只继承卓越前线的公开 API，碰撞走它自己的 OBB
    // 系统，座位与炮塔参数都由数据 JSON 提供。将来真要用 AT 时再加回来，记得**至少要写
    // 一条有效条目**，不能只留注释。

    runs {
        create("gameTestServer") {
            workingDirectory(project.file("run-gametest-flame0313"))
            if(project.hasProperty("markGameTests"))workingDirectory(project.file("run-gametest-marks0370"))
            if (project.hasProperty("laserGameTests")) workingDirectory(project.file("run-gametest-laser0318"))
            if (project.hasProperty("teslaGameTests")) workingDirectory(project.file("run-gametest-tesla0320"))
            if (project.hasProperty("minefieldGameTests")) workingDirectory(project.file("run-gametest-minefield0325"))
            if(project.hasProperty("minefieldCleanTests")) workingDirectory(project.file("run-gametest-minefield0335"))
            if(project.hasProperty("elementalGameTests")) workingDirectory(project.file("run-gametest-elemental0335"))
            if(project.hasProperty("antiTankGameTests")) workingDirectory(project.file("run-gametest-antitank0339"))
            if(project.hasProperty("hmgGameTests"))workingDirectory(project.file("run-gametest-hmg0342"))
            if(project.hasProperty("eagleGameTests"))workingDirectory(project.file("run-gametest-eagle0371"))
            if(project.hasProperty("shieldGameTests"))workingDirectory(project.file("run-gametest-shield0369"))
            property("forge.enabledGameTestNamespaces", if(project.hasProperty("eagleGameTests")) "sbw_helldiver_eagle,sbw_helldiver_shield,sbw_helldiver_hmg,sbw_helldiver_grenadier" else if(project.hasProperty("markGameTests")) "sbw_helldiver_marks,sbw_helldiver_shield,sbw_helldiver_hmg,sbw_helldiver_grenadier" else if(project.hasProperty("shieldGameTests")) "sbw_helldiver_shield,sbw_helldiver_hmg,sbw_helldiver_grenadier" else if(project.hasProperty("hmgGameTests")) "sbw_helldiver_hmg,sbw_helldiver_grenadier" else if(project.hasProperty("antiTankGameTests")) "sbw_helldiver_antitank,sbw_helldiver_elemental,sbw_helldiver_minefield" else if(project.hasProperty("elementalGameTests")) "sbw_helldiver_elemental" else if(project.hasProperty("minefieldGameTests")) "sbw_helldiver_minefield" else if(project.hasProperty("teslaGameTests")) "sbw_helldiver_tesla" else if (project.hasProperty("laserGameTests")) "sbw_helldiver_laser" else "${project.property("mod_id")},sbw_helldiver_laser")
            property("forge.logging.console.level", "info")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")
            mods {
                create(project.property("mod_id").toString()) { source(sourceSets.main.get()) }
            }
        }
        create("client") {
            workingDirectory(project.file("run"))
            if (project.hasProperty("teslaSmokeTest")) {
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.teslaSmokeTest", "true")
                args("--quickPlaySingleplayer", "IntegrationTest", "--width", "1280", "--height", "720")
            }
            if (project.hasProperty("clientSmokeTest")) {
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.clientSmokeTest", "true")
                args("--quickPlaySingleplayer", "IntegrationTest", "--width", "1280", "--height", "720")
            }
            if (project.hasProperty("beaconSmokeTest")) {
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.beaconSmokeTest", "true")
                args("--quickPlaySingleplayer", "IntegrationTest", "--width", "1280", "--height", "720")
            }
            if (project.hasProperty("mortarSmokeTest")) {
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.mortarSmokeTest", "true")
                args("--quickPlaySingleplayer", "IntegrationTest", "--width", "1280", "--height", "720")
            }
            if (project.hasProperty("statusMortarSmokeTest")) {
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.statusMortarSmokeTest","true")
                args("--quickPlaySingleplayer","IntegrationTest","--width","1280","--height","720")
            }
            if (project.hasProperty("flameSmokeTest")) {
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.flameSmokeTest","true")
                args("--quickPlaySingleplayer","IntegrationTest","--width","1280","--height","720")
            }
            if (project.hasProperty("minefieldSmokeTest")) {
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.minefieldSmokeTest","true")
                property("sbw_helldiver.minefieldCapture","D:/Codex/projects/2026-09-19/new-chat/work/anti_personnel_mine/linkage_center_fix/client_capture")
                property("java.io.tmpdir","D:/Codex/projects/2026-09-19/new-chat/work/anti_personnel_mine/linkage_center_fix/client_capture/tmp")
                property("org.lwjgl.system.SharedLibraryExtractPath","D:/Codex/projects/2026-09-19/new-chat/work/anti_personnel_mine/linkage_center_fix/client_capture/natives")
                args("--quickPlaySingleplayer","IntegrationTest","--width","1280","--height","720")
            }
            if (project.hasProperty("elementalSmokeTest")) {
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.elementalSmokeTest","true")
                if(project.hasProperty("elementalEffectsOnly"))property("sbw_helldiver.elementalEffectsOnly","true")
                property("sbw_helldiver.elementalCapture","D:/Codex/projects/2026-09-19/new-chat/work/elemental_mines/integration/client_capture")
                property("java.io.tmpdir","D:/Codex/projects/2026-09-19/new-chat/work/elemental_mines/integration/client_capture/tmp")
                property("org.lwjgl.system.SharedLibraryExtractPath","D:/Codex/projects/2026-09-19/new-chat/work/elemental_mines/integration/client_capture/natives")
                args("--quickPlaySingleplayer","IntegrationTest","--width","1280","--height","720")
            }
            if(project.hasProperty("effects0336")) {
                property("sbw_helldiver.effects0336","true")
                property("sbw_helldiver.elementalCapture","D:/Codex/projects/2026-09-19/new-chat/work/effects_0336/client_capture")
            }
            if(project.hasProperty("eagleSmokeTest")) {
                jvmArgs("-Xms512m", "-Xmx3G", "-XX:ActiveProcessorCount=4")
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.eagleSmokeTest","true")
                if(project.hasProperty("eagleBeaconFixTest")) {
                    property("sbw_helldiver.eagleCapture","D:/Codex/projects/2026-09-19/new-chat/work/eagle/icon_color_fix/client_capture")
                    property("sbw_helldiver.eagleRecordMotion","false")
                }
                property("java.io.tmpdir","D:/Project/sbw_helldiver/.codex/tmp")
                args("--quickPlaySingleplayer","IntegrationTest","--width","1280","--height","720")
            }
            if(project.hasProperty("markSmokeTest")) {
                jvmArgs("-Xms512m", "-Xmx3G", "-XX:ActiveProcessorCount=4")
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.markSmokeTest","true")
                property("java.io.tmpdir","D:/Project/sbw_helldiver/.codex/tmp")
                args("--quickPlaySingleplayer","IntegrationTest","--width","1280","--height","720")
            }
            if(project.hasProperty("shieldSmokeTest")) {
                jvmArgs("-Xms512m", "-Xmx3G", "-XX:ActiveProcessorCount=4")
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.shieldSmokeTest","true")
                property("java.io.tmpdir","D:/Project/sbw_helldiver/.codex/tmp")
                args("--quickPlaySingleplayer","IntegrationTest","--width","1280","--height","720")
            }
            if(project.hasProperty("grenadierSmokeTest")) {
                jvmArgs("-Xms512m", "-Xmx3G", "-XX:ActiveProcessorCount=4")
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.grenadierSmokeTest","true")
                property("java.io.tmpdir","D:/Project/sbw_helldiver/.codex/tmp")
                args("--quickPlaySingleplayer","IntegrationTest","--width","1280","--height","720")
            }
            if(project.hasProperty("hmgSmokeTest")) {
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.hmgSmokeTest","true")
                property("java.io.tmpdir","D:/Project/sbw_helldiver/.codex/tmp")
                args("--quickPlaySingleplayer","IntegrationTest","--width","1280","--height","720")
            }
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            property("forge.enabledGameTestNamespaces", project.property("mod_id").toString())
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")

            mods {
                create(project.property("mod_id").toString()) {
                    source(sourceSets.main.get())
                }
            }
        }

        create("server") {
            workingDirectory(project.file("run"))
            if (project.hasProperty("elementalSmokeTest")) {
                workingDirectory(project.file("run-client-test"))
                property("sbw_helldiver.elementalSmokeTest","true")
                if(project.hasProperty("elementalEffectsOnly"))property("sbw_helldiver.elementalEffectsOnly","true")
                property("sbw_helldiver.elementalCapture","D:/Codex/projects/2026-09-19/new-chat/work/elemental_mines/integration/client_capture")
                property("java.io.tmpdir","D:/Codex/projects/2026-09-19/new-chat/work/elemental_mines/integration/client_capture/tmp")
                property("org.lwjgl.system.SharedLibraryExtractPath","D:/Codex/projects/2026-09-19/new-chat/work/elemental_mines/integration/client_capture/natives")
                args("--quickPlaySingleplayer","IntegrationTest","--width","1280","--height","720")
            }
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            property("forge.enabledGameTestNamespaces", project.property("mod_id").toString())
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")

            mods {
                create(project.property("mod_id").toString()) {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

repositories {
    // 国内镜像：替代被墙的 libraries.minecraft.net
    maven {
        name = "BMCLAPI"
        url = uri("https://bmclapi2.bangbang93.com/libraries")
        content {
            includeGroup("com.mojang")
            includeGroup("net.minecraft")
        }
    }
    mavenCentral()
    maven {
        name = "Kotlin for Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
    }
    maven {
        name = "CurseMaven"
        url = uri("https://cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
    // SimpleBedrockModel 只在 JitPack 上发布。限定 group，免得 JitPack 抢答其他依赖
    // （它对每个 artifact 都会现场构建，命中范围越小越好）。
    maven {
        name = "JitPack"
        url = uri("https://jitpack.io")
        content {
            includeGroup("com.github.mcmodderanchor")
        }
    }
}

dependencies {
    minecraft("net.minecraftforge:forge:1.20.1-47.2.0")
    implementation("thedarkcolour:kotlinforforge:4.11.0")

    // Superb Warfare 依赖（curse maven，经 fg.deobf 反混淆）。
    //
    // 本项目**只**依赖 SBW，不直接依赖 GeckoLib / SimpleBedrockModel：
    // SBW 的动画走它自研的 VehicleAnimationInstance 与 GeoVehicleRenderer，
    // GeckoLib 的动画控制器在 GeoVehicleEntity.registerControllers 里是空实现。
    // 只要代码只引用 SBW 自己的类型，就不需要这两个库进编译期 classpath。
    // 详见项目方案里的「风险二」。
    implementation(
        fg.deobf(
            "curse.maven:superb-warfare-${project.property("superbwarfare_curse_id")}:" +
                project.property("superbwarfare_file_id")
        )
    )

    // ---- 以下三个库都不打进我们的 jar，只为了让编译与开发运行能跑起来 ----
    //
    // 卓越前线的动画与渲染建立在这些库上：
    //   * SimpleBedrockModel —— 解析 Bedrock 几何与动画（`VehicleAnimationContext`
    //     里那个 `Map<String, BedrockAnimation>` 字段就是它）
    //   * MAE（Mayday Animation Engine）—— 动画求值与混合（`getPose()` 返回的 `Pose`）
    //   * GeckoLib —— 卓越前线部分载具模型仍继承 GeckoLib 的 `GeoModel`
    //
    // 它们**都没有进 Maven 依赖图**：SBW 只在 mods.toml 里把它们声明成前置模组，
    // 所以不会随 fg.deobf 自动传递过来。而代码里一旦引用
    // `VehicleAnimationInstance` / `VehicleAnimationContext`，Kotlin 就必须把这两个
    // 类的完整签名解析出来，缺了它们会直接编译失败。
    //
    // 用 `implementation` 而不是 `compileOnly`：开发运行（runClient / runServer）的
    // classpath 就是从这个配置来的，写成 compileOnly 的话游戏起不来。
    // 注意 `implementation` 并**不会**把它们打进我们的 jar —— 那需要 `jarJar`，
    // 而我们不需要：玩家侧由 SBW 的前置要求保证它们存在（我们也在 mods.toml 里
    // 把 SimpleBedrockModel 声明成了强制前置）。
    compileOnly(fg.deobf("com.github.mcmodderanchor:simplebedrockmodel:2.5.1-forge-mc1.20.1"))

    // MAE 由 SimpleBedrockModel 再往内嵌一层，同样只要编译期可见。
    compileOnly("com.maydaymemory:mae:1.1.4") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "it.unimi.dsi", module = "fastutil")
        exclude(group = "org.joml", module = "joml")
    }

    // Compile the native gun sight adapter against exactly SBW's embedded 4.4.6.
    // Compile-only: SBW supplies this jar at runtime; never install another GeckoLib version.
    compileOnly(files("libs/geckolib-forge-1.20.1-4.4.6-sbw-api.jar"))

    // 不声明额外的 GeckoLib 运行时依赖。
    //
    // 卓越前线把 GeckoLib 4.4.6 连同 SimpleBedrockModel、Flywheel、Ponder、Curios、
    // Cloth Config 一起用 jar-in-jar 内嵌在自己的 jar 里（见 SBW 的 META-INF/jarjar/），
    // 由 Forge 在运行时解开。自己再声明一份会变成**重复且版本不一致**：
    // 之前这里挂的是 GeckoLib 4.7.3，而 SBW 内嵌的是 4.4.6 —— 结果运行时
    // `EntityRenderDispatcher` 的渲染器注册被搞坏，进世界第一帧就崩在
    // `shouldRender ... because "entityrenderer" is null`。
    // SBW 的 mods.toml 里那句调侃式的 geckoanimfix 版本串也明说了它与新版本 GeckoLib 不兼容。
    //
    // 原生瞄具适配使用 GeoBone，因此仅在编译期引用上面的内嵌版本。
}

kotlin {
    jvmToolchain(17)
}

tasks.named<ProcessResources>("processResources") {
    /*
     * **必须显式指定 UTF-8。**
     *
     * `expand()` 默认按**平台编码**读文件，Windows 简体中文下是 GBK —— 而 `mods.toml`
     * 和 `pack.mcmeta` 都是 UTF-8 写的，里面只要有中文就会被读成乱码打进 jar。
     */
    filteringCharset = "UTF-8"

    val replaceProperties = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "forge_version" to project.property("forge_version"),
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
        "mod_license" to project.property("mod_license"),
        "mod_version" to project.property("mod_version"),
        "mod_authors" to project.property("mod_authors"),
        "mod_description" to project.property("mod_description"),
        "superbwarfare_version" to project.property("superbwarfare_version"),
    )
    inputs.properties(replaceProperties)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(replaceProperties)
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Specification-Title" to project.property("mod_id"),
            "Specification-Vendor" to project.property("mod_authors"),
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
        )
    }
    finalizedBy("reobfJar")
}
