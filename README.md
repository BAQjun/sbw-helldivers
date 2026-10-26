# SBW Helldivers

面向 Minecraft Java 1.20.1 的《绝地潜兵》战备附属模组，基于卓越前线（Superb Warfare）。

## 内容

自动炮台、人工操控炮台、掷弹兵榴弹墙、地雷、护盾生成器、X 键目标标记，以及飞鹰空袭、机枪扫射、集束炸弹、110mm 火箭巢、500kg 炸弹、凝固汽油弹、毒气与烟雾空袭，以及超级驱逐舰与轨道精准打击。

## 安装

从 [Releases](https://github.com/BAQjun/sbw-helldivers/releases) 下载模组 JAR，放入 `mods` 文件夹，替换旧版本。联机时客户端与服务端一并更新。

依赖环境：

- Minecraft Java 1.20.1
- Forge 47.2.0
- 卓越前线 0.8.9.1
- Kotlin for Forge 4.11.0

卓越前线已内嵌的模型库无需重复安装。

可选兼容 TACZ 1.1.8 系列：枪弹、穿甲弹、霰弹与爆炸弹可与炮台、榴弹墙、地雷和护盾交互。护盾仅拦截外部射入的弹药，金属墙体会终止子弹穿透。未安装 TACZ 时可正常使用本模组。

## 模型质量

在游戏内打开“模组 → SBW Helldivers → 配置”，可以选择“低质量模型”。默认使用保留外观细节的优化精细模型；开启后使用减面模型。保存后重新加载资源，已部署的战备也会更新。详见 [模型质量设置](docs/model-quality.md)。

进入世界后，该配置页面还可切换“战备自动收回”（默认开启）。关闭后，炮台不会因弹药耗尽或工作时限到达而自动缩回；设置保存至当前世界，单人主人或服务器管理员可以修改。

## 炮台配置

首次进入存档后，在该存档的 `serverconfig/sbw_helldiver-server.toml` 中修改炮台属性。13 种炮台各有独立分区，可配置生命值、爆炸抗性、直击伤害、射速、弹药／燃料、索敌距离、爆炸伤害与范围等适用属性；默认值沿用现有平衡。

修改前退出存档或关闭服务器，保存后重新进入。完整字段、单位和示例见 [炮台配置说明](docs/turret-configuration.md)。

## 飞鹰弹药

集束炸弹投放 8 发母弹，每发分裂为 8 发子弹；110mm 火箭巢优先攻击附近最大生命值最高的敌人，连续发射三组双联火箭；500kg 炸弹会插入地面或存活的生物，播放引信声后延时爆炸。集束分裂和 500kg 引信时间可由服务器配置，详见 [飞鹰战备与弹药](docs/eagle-stratagems.md)。

## 轨道战备

携带装有轨道战备的信标球时，顶部自动出现“呼叫超级驱逐舰”。输入呼叫指令后选择方位，三秒后跃迁抵达；再次呼叫可选择撤离或调整方位。舰船固定在世界中，默认支援半径 1024 格。轨道精准打击从舰船方向射向信标，直击 640、爆炸 240，内半径 4 格、外半径 12 格。详见 [超级驱逐舰与轨道战备](docs/orbital-stratagems.md)。

## 管理员指令

战备按玩家保存冷却与飞鹰次数，默认采用满舰船升级的数值。抵达和冷却在图标上以从上到下的擦除动画显示；飞鹰拥有独立次数、共享周转与重新武装。可在服务器配置中逐项调整，详见 [战备冷却与飞鹰补给](docs/stratagem-cooldowns.md)。

- `/helldivers drop <玩家>`：让指定玩家乘空投仓降落到其所在位置的安全地面。支持 `@s`、`@a` 等玩家选择器。
- `/helldivers drop <玩家> <x> <y> <z>`：指定落点，坐标支持 `~`；多人同时空投会分开落点，附近已有空投仓时会避让。
- `/helldivers marking true`：开启 X 键标记功能。
- `/helldivers marking false`：关闭标记功能，并清除已有标记和隐藏索敌指令。
- `/helldivers marking`：查询当前开关状态。
- `/helldivers cooldown [玩家名]`：切换自己或指定玩家的战备冷却，支持选择器。
- `/helldivers cooldown set true/false [玩家名]`：明确开启或关闭战备冷却；关闭时同时取消飞鹰次数与补给限制。

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
