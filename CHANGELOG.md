# 版本记录

## 0.4.2

### 中文

- 新增飞鹰集束炸弹：投放 8 发母弹，每发在约 0.35 秒后分裂为 8 发子弹，覆盖宽幅轰炸区域。母弹直击 240、爆炸 60（内半径 1m／外半径 2m）；子弹直击 3、爆炸 60（3m／6m）。
- 新增飞鹰 110mm 火箭巢：优先锁定信标附近最大生命值最高的敌人，发射三组双联火箭。每发直击 100、爆炸 50，内半径 1.6m、外半径 5m。
- 新增飞鹰 500kg 炸弹：直击 320、爆炸 240，内半径 10m、外半径 25m。炸弹会插在地面或直击后仍存活的生物身上，随生物移动，播放起爆提示音并在约 0.8 秒后爆炸。
- 新飞鹰战备接入独立使用次数、共享周转与重新武装，默认满升级次数分别为 5／4／2 次。
- 服务器可配置集束分裂延时与 500kg 落地引信延时；信标会保留到最后一发子弹命中或延时炸弹起爆。

### English

- Added Eagle Cluster Bomb: drops 8 carrier bombs, each splitting into 8 bomblets after approximately 0.35 seconds. Carriers deal 240 direct and 60 blast damage (1m inner / 2m outer radius); bomblets deal 3 direct and 60 blast damage (3m / 6m).
- Added Eagle 110mm Rocket Pods: targets the nearby enemy with the highest maximum health and fires three pairs of rockets. Each rocket deals 100 direct and 50 blast damage, with a 1.6m inner and 5m outer radius.
- Added Eagle 500kg Bomb: deals 320 direct and 240 blast damage, with a 10m inner and 25m outer radius. The bomb embeds in the ground or a surviving creature, follows its host, plays its fuse cue, and detonates approximately 0.8 seconds after impact.
- Integrated the new Eagles with separate use counts, shared turnaround, and rearm. Fully upgraded defaults are 5 / 4 / 2 uses respectively.
- Added server settings for cluster separation and the 500kg impact fuse. Beacons remain until the final bomblet impact or delayed detonation.

## 0.4.1 — 飞鹰机炮弹显示 / Eagle cannon round visuals

### 中文

- 飞鹰机枪扫射改用 SBW A-10A 的 GAU-8/A 原生机炮弹渲染，保持原有弹射物伤害、射速、高爆弹比例及爆炸范围。
- 飞鹰机炮弹不再套用玩家手持武器的枪口显示偏移，弹道始终从飞机实际发射位置显示。

### English

- Use the native SBW A-10A GAU-8/A cannon round renderer for the Eagle Strafing Run, preserving its damage, rate of fire, explosive round ratio and blast radii.
- Exclude Eagle cannon rounds from handheld weapon muzzle offsets so their tracers remain on the aircraft's actual firing path.

## 0.4 — 模型优化与质量设置 / Model optimization and quality settings

### 中文

- 优化默认精细模型，清理完全遮挡的立方体面及重复属性数据，保留原有外表网格、轮廓、法线与贴图坐标。
- 在“模组 → SBW Helldivers → 配置”中新增“低质量模型”客户端开关，默认关闭；保存后重新加载资源，已部署的战备也会随之切换。
- 可选减面模型覆盖炮台、空投仓、地雷、迫击炮弹与飞鹰，保留原有贴图、动画及战备属性。
- 修复空投仓下沿锁扣悬空与喷口上沿的缝隙。
- 将光影兼容的网格三角形处理统一应用于全部空投仓战备，避免模型拉出条状面。

### English

- Optimize the default full-detail models by removing fully hidden cube faces and duplicate attribute records, preserving the original mesh surfaces, silhouettes, normals and UVs.
- Add a client-side **Low Quality Models** toggle under **Mods → SBW Helldivers → Config**, off by default. Saving reloads resources and updates already deployed stratagems.
- Optional reduced models cover turrets, Hellpods, mines, mortar rounds and the Eagle while preserving textures, animations and gameplay properties.
- Fix floating lower Hellpod lock tabs and the gap above the thruster housings.
- Apply shader-compatible mesh triangle handling to all Hellpod stratagems to prevent stretched strips of geometry.

## 0.3.92-perftest2 — 可选低质量模型 / Optional low quality models

- 默认精细模型仅清理完全遮挡的面及重复的顶点属性数据，保留全部网格表面、轮廓、法线与贴图坐标。
- 在游戏内模组配置界面新增“低质量模型”客户端开关，默认使用原精度模型。
- 保存后重新加载模型，已部署的炮台、地雷及飞鹰随设置切换，保留贴图、动画与战备属性。
- 此版本仍仅供性能与画面测试。

- Optimize the default full-detail models by removing fully hidden cube faces and deduplicating identical vertex attributes, preserving every mesh surface triangle, silhouette, normal and UV.
- Add a client-side “Low Quality Models” toggle to the in-game mod configuration screen; full-detail models remain the default.
- Saving reloads models for existing turrets, mines and the Eagle while preserving textures, animations and gameplay properties.
- This build remains a performance and visual preview.

## 0.3.92-perftest1 — 性能测试版 / Performance preview

- 适度精简炮台、地雷、迫击炮弹和飞鹰的游戏模型网格，剔除同一动画部件内部被完全遮挡的面。
- 保留骨骼、动画挂点、原有贴图及地雷发光标记，修正空投仓下沿锁扣悬空与喷口上沿透空缝隙。
- 将光影下的网格三角形兼容处理扩展至所有空投仓战备。
- 仅供性能与画面测试。

- Moderately reduce runtime meshes for turrets, mines, mortar rounds and the Eagle; remove fully enclosed faces within individual animated parts.
- Preserve animation bones, attachment points, original textures and emissive mine markings. Fix floating lower Hellpod lock tabs and the gap above the thruster housings.
- Apply shader-compatible triangle grouping to all Hellpod stratagem meshes.
- Intended for performance and visual testing only.

## 0.3.85

- 为 13 种自动与手动炮台增加独立服务端配置，支持生命值、爆炸抗性、伤害、射速、弹药／燃料、索敌距离、工作时限及适用的爆炸范围、迫击炮散布等属性。
- 激光炮台支持光束距离、过热阈值与升温速度配置；特斯拉塔支持电弧跳跃距离和目标数量配置。
- 修改容量或生命上限时保留已有炮台的剩余弹药与受损状态；榴弹墙默认仍在打空弹药后保持展开。

## 0.3.84

- 修复地雷引爆时爆炸和烟尘被原版粒子距离限制隐藏的问题，支持高空俯视等远距离观察视角。
- 保留原有粒子数量、地雷伤害与火焰／毒气区域效果。

## 0.3.83

- 修复开启 Iris／Oculus 光影时地雷布撒出现拉长尖片、模型撕裂的问题，覆盖投弹器挂载地雷和飞出、落地后的地雷。
- 为远处地雷增加分级细节，近距离特写保留完整模型，减轻大面积布雷时的渲染负担。
- 优化 Embeddium 环境下地雷与战备球的批量渲染，减少光影兼容路径的顶点提交开销。
- 减少炮台索敌时重复的视线检测，以及无护盾时的全世界实体扫描；护盾仍按最近入射面拦截射弹。
- 减少炮台动画的重复分配，并在实体离开后释放转向缓存。

## 0.3.82

- 火焰炮台直击伤害调整为每秒 10 点，燃料消耗保持不变。
- 修复激光炮台射击、转向或过热时出现悬空细条的问题，避免模型结构件被误识别为卓越前线的激光发射点。

## 0.3.81

- 激光炮台直射伤害调整为每 0.25 秒结算 7.5 点（每秒 30 点）；光束持续显示，附带燃烧伤害保持不变。

## 0.3.80

- 所有空投仓落地造成 400 点直接砸击伤害；重叠落点会摧毁并清除下方空投仓，避免仓体相互挤压。
- 激光与火焰喷射的持续命中、附带燃烧不再产生击退，保留伤害和点燃效果。
- 修复激光炮台空白热量屏透空、进度条局部遮挡的问题。
- 激光屏幕进度、光束和过热火花使用独立发光绘制，改善 Iris／Oculus 光影兼容。

## 0.3.79

- 火焰射流改为三维体积效果，并修复 Iris／Oculus 光影下的显示异常。
- 加密射流后段的火焰，并为光影下的火焰加入自发光与辉光。
- 增加末端升腾的体积烟雾，以及随射流移动、照亮附近表面的动态光照。
- 所有地火继承原版火焰亮度，光影下同步使用原版火焰材质。
- 补全地火实体和方块的“HD2火焰”名称。

## 0.3.78

- 火焰喷射哨戒炮被摧毁时产生 60 点伤害的爆炸（内半径 0.4 米、外半径 4 米），并留下半径 3～4 米、数值与喷射地火一致的燃烧区域。

## 0.3.72

- 飞鹰战备图标采用分离通道配色：飞机与边框红橙色，弹药符号米白色。
- 倒计时归零后显示“已抵达”，信标保留至实际弹药打击完成。

此前版本说明见 `docs/releases/`。
