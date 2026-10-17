# SBW Helldivers

《绝地潜兵》战备模组，面向 Minecraft Java 1.20.1 / Forge 47.2.0 / 卓越前线 0.8.9.1 / Kotlin for Forge 4.11.0。

当前版本：**0.3.72**。包括自动炮台、人工操控炮台、掷弹兵榴弹墙、地雷、护盾生成器、X 键目标标记，以及飞鹰空袭与机枪扫射。灰烬前哨摄影地图作为独立存档发布。

## 获取和构建

先安装 Git LFS、JDK 17，然后：

```powershell
git clone https://github.com/BAQjun/sbw-helldivers.git
cd sbw-helldivers
git lfs pull
.\build.ps1 -Online
```

首次构建需要网络下载 Gradle 和依赖；完成后执行 `./build.ps1` 可使用 D 盘项目缓存离线构建。输出为 `build/libs/sbw_helldiver-0.3.72-mc1.20.1.jar`。Linux 使用 `./gradlew`。

`libs/geckolib-forge-1.20.1-4.4.6-sbw-api.jar` 是卓越前线内嵌版本的编译用接口依赖，已纳入版本管理，不打进最终附属模组；游戏内沿用卓越前线自带版本。

## 文件布局

- `src/`：Kotlin / Java 源码、运行资源和 GameTest。
- `art/models/`：当前接受的 Blockbench 模型源文件及伴随材质。
- `maps/ashen_outpost/`：灰烬前哨的原版方块地图生成脚本；使用带 NumPy 的 Python，或 Blender 自带 Python 运行 `build_map.py`。
- `tools/`：导入、素材核验与开发工具；历史脚本可能含开发机路径，运行前按说明核对。
- `docs/releases/`：保留的版本说明。
- GitHub Releases：可直接安装的 JAR、摄影地图和历史工作资料归档。

## 版本流程

开发分支以 `main` 为准，功能修复创建独立分支，提交后合并。版本号由 `gradle.properties` 的 `mod_version` 管理，发布标签使用 `v0.3.72` 格式；标签与对应构建产物共同保留，发布后的标签不移动。

本次是首次把已有项目纳入 Git：`v0.3.72` 记录当前真实源码基线，不为旧版本伪造源码历史。更早的版本说明和安装包保存在历史归档附件。

通常提交：`git add <本次修改的文件>`、`git commit -m "fix: 描述实际修复"`、`git push`。几何及模型文件由 Git LFS 上传。缓存、测试存档和构建包不提交。

JDK 17 构建工作流模板保存在 `ci/github-actions-build.yml`。当前 GitHub 登录令牌缺少 `workflow` 权限，因此尚未启用 Actions；将来授予该权限后，把模板放入 `.github/workflows/` 即可自动构建，并可手动选择当前 GameTest 集合。

## 本地存储

当前开发目录为 `D:/Project/sbw_helldiver`，模型工作区为 `D:/Codex/projects/2026-09-19/new-chat`。当前交付仍位于模型工作区的 `outputs/当前交付`。这些绝对路径不是普通构建的前置条件。旧脚本的历史参考路径不代表当前交付来源。

清理和归档说明见 `docs/STORAGE.md`。保留 `src/`、`art/`、地图脚本、Gradle Wrapper 和当前交付；可重新生成的缓存和测试产物按清单清理。
