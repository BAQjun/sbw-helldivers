# SBW Helldivers

面向 Minecraft Java 1.20.1 的《绝地潜兵》战备附属模组，基于卓越前线（Superb Warfare）。当前版本：**0.3.76**。

## 内容

自动炮台、人工操控炮台、掷弹兵榴弹墙、地雷、护盾生成器、X 键目标标记，以及飞鹰空袭与机枪扫射。

## 安装

从 [Releases](https://github.com/BAQjun/sbw-helldivers/releases) 下载模组 JAR，放入 `mods` 文件夹，替换旧版本。联机时客户端与服务端一并更新。

依赖环境：

- Minecraft Java 1.20.1
- Forge 47.2.0
- 卓越前线 0.8.9.1
- Kotlin for Forge 4.11.0

卓越前线已内嵌的模型库无需重复安装。

可选兼容 TACZ 1.1.8 系列：枪弹、穿甲弹、霰弹与爆炸弹可与炮台、榴弹墙、地雷和护盾交互。护盾仅拦截外部射入的弹药，金属墙体会终止子弹穿透。未安装 TACZ 时可正常使用本模组。

## 管理员指令

- `/helldivers drop <玩家>`：让指定玩家乘空投仓降落到其所在位置的安全地面。支持 `@s`、`@a` 等玩家选择器。
- `/helldivers drop <玩家> <x> <y> <z>`：指定落点，坐标支持 `~`；多人同时空投会分开落点，附近已有空投仓时会避让。
- `/helldivers marking true`：开启 X 键标记功能。
- `/helldivers marking false`：关闭标记功能，并清除已有标记和隐藏索敌指令。
- `/helldivers marking`：查询当前开关状态。

以上指令需要 2 级管理员权限，可通过控制台、命令方块或聊天输入。标记开关保存在世界中。玩家空投仅由指令启动，不加入战备菜单；等待落点区块与地形就绪后再开始下落，下落时保持垂直俯视，用 WASD 调整落点；站姿出仓时先转为平视，再平滑推进至第一人称并恢复操作。默认在玩家自身维度落地，指定坐标时使用指令执行位置所在的维度。

## 构建

需要 JDK 17 和 Git LFS：

```powershell
git clone https://github.com/BAQjun/sbw-helldivers.git
cd sbw-helldivers
git lfs pull
.\gradlew.bat --no-daemon build
```

Linux 使用 `./gradlew --no-daemon build`。首次构建需要下载 Gradle 和依赖，产物位于 `build/libs/`。

`libs/geckolib-forge-1.20.1-4.4.6-sbw-api.jar` 与卓越前线内嵌版本一致，仅用于编译，不打入附属模组。

## 开发

- `src/`：模组源码、运行资源和 GameTest。
- `art/models/`：Blockbench 模型源文件与伴随材质。
- `tools/`：素材导入和核验工具。
- `docs/releases/`：版本说明。
- `ci/github-actions-build.yml`：构建工作流模板。

`main` 为开发分支，版本号由 `gradle.properties` 中的 `mod_version` 管理，发布标签采用 `v<版本号>`。大型模型和几何文件通过 Git LFS 管理。
