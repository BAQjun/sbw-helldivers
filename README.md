# SBW Helldivers

面向 Minecraft Java 1.20.1 的《绝地潜兵》战备附属模组，基于卓越前线（Superb Warfare）。当前版本：**0.3.74**。

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
