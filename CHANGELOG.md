# 版本记录

## 0.6.0-alpha.13

## 中文

- 修复近距离使用设备部署信标终端时玩家模型遮挡屏幕的问题。
- 简化设备部署信标碰撞，改善贴近时的移动体验。

## English

- Fixed the local player model obstructing the equipment deployment terminal when interacting at close range.
- Simplified equipment deployment beacon collision to improve movement alongside it.

## 0.6.0-alpha.12

## 中文

- 修复设备部署信标顶部叶片展开后错位的问题。

## English

- Fixed the equipment deployment beacon’s top blades becoming misaligned when deployed.

## 0.6.0-alpha.11

## 中文

- 修复设备部署信标的分段碰撞框在调试显示时偏离信标的问题。

## English

- Fixed equipment deployment beacon collision-part debug outlines drifting away from the beacon.

## 0.6.0-alpha.10

## 中文

- 恢复设备部署信标主体的原有大小，保留加宽的底座与仓口齐平的遮挡面。

## English

- Restored the equipment deployment beacon body to its original size while retaining the widened pedestal and flush pod-opening cover.

## 0.6.0-alpha.9

## 中文

- 放大设备部署信标，底座上沿与空投仓口齐平，并补齐封闭面以遮住仓内空隙。
- 同步调整屏幕交互、碰撞与顶部蓝光位置，保留原有展开、收回和音效时序。

## English

- Enlarged the equipment deployment beacon and aligned its pedestal with the pod opening, adding a sealing surface to cover the interior gap.
- Updated terminal interaction, collision and blue beam placement to match, preserving deployment, retraction and sound timing.

## 0.6.0-alpha.8

## 中文

- 加快信标本体展开动画，展开与收回机械音效恢复原速播放。
- 设备部署信标的下落与落地音效改用其他空投仓的共同流程，补齐落地停顿与开仓音效，移除额外叠加的反推音效。
- 降低信标升起高度，使下部宽底座留在空投仓内；同步调整屏幕交互和顶部蓝光位置。
- 为设备部署信标补齐空投仓与主体的分段碰撞，并随展开、收回动作同步变化，兼容 Lithium。
- 信标开始回收时立即关闭顶部蓝色光柱。

## English

- Speed up the beacon unfolding animation and play unfolding/retraction sounds at their original speed.
- Equipment deployment beacons now share the incoming and impact audio flow used by other hellpods, including the landing pause and opening sounds, without extra retro-thruster layers.
- Lowered the beacon lift to keep its wide lower pedestal inside the pod, with matching terminal interaction and blue beam positions.
- Added multipart collision for the pod and beacon body that follows deployment and retraction, including Lithium compatibility.
- The blue beam now switches off as soon as beacon retraction begins.

## 0.6.0-alpha.7

## 中文

- 调整机器集装箱和设备部署信标的机械音效，使其与开门、升起、展开及收回动作同步。
- 集装箱开门由 2.4 秒加快至 2 秒，保留原机械音效的正常播放时长。
- 手动放置机器集装箱时播放铁块放置音效。
- 设备部署终端的设备选择页和材料提交页输错指令时，所有指令箭头同步闪红一次。

## English

- Synchronized mechanical audio with the opening, lifting, unfolding and retraction of machine containers and equipment deployment beacons.
- Sped up container opening from 2.4 to 2 seconds while retaining the original mechanical sound duration.
- Added iron block placement sounds when placing machine containers by hand.
- All command arrows now flash red once after an incorrect input on both the device selection and material submission pages.

## 0.6.0-alpha.6

## 中文

- 修复潜行时使用卓越前线撬棍右键无法打包机器的问题，打包与重新部署保留物品、储能和加工进度。
- 修复重机枪部署支架、反坦克炮台与掷弹兵榴弹墙无法使用潜行＋撬棍回收的问题，保留剩余弹药，乘员操作中禁止回收。
- 打包物品以立体集装箱显示，机器使用对应的封闭部署外壳，载人炮台使用卓越前线原生载具集装箱。
- 新增可关闭的距离 LOD，支持在模组配置界面修改切换距离；炮台、地雷、飞鹰和机器可自动使用远景简化模型。
- 为生物处理器和精炼厂加入来自原游戏的运作循环音效，随加工状态播放和停止。
- 为生物处理器蓝色罐体玻璃与精炼厂蓝色部件补充光影发光效果。
- 优化两种机器及集装箱的模型提交、静态结构缓存与动画计算，保留原有外形和贴图细节。

## English

- Fixed sneaking right-click with the Superb Warfare crowbar failing to pack machines. Inventory, stored energy and processing progress survive packing and redeployment.
- Fixed crowbar recovery for the HMG Emplacement, Anti-Tank Emplacement and Grenadier Battlement. Remaining ammunition is preserved, and occupied emplacements cannot be packed.
- Packed items now appear as 3D containers: closed delivery shells for machines and native Superb Warfare vehicle containers for manned emplacements.
- Added optional distance-based LOD with an adjustable switching distance in the mod configuration screen for emplacements, mines, Eagles and machines.
- Added original-game operating loops for the Bioprocessor and Refinery, following their processing state.
- Added shader-compatible emissive rendering for the Bioprocessor's blue tank glass and the Refinery's blue components.
- Optimized mesh submission, static geometry caching and animation processing for both machines and containers while preserving their appearance and texture detail.

## 0.6.0-alpha.5

## 中文

- 设备部署信标展开后不再持续显示“已抵达”；移除集装箱落点的抬头提示和额外蓝色光柱，保留信标自身蓝光及左上角部署倒计时。
- 修正生物处理器与精炼厂的输入、输出接口对应关系，已部署设备自动更新并保留储能、物品和加工进度。
- 机器占据的每一格改为完整方块碰撞与选中范围，保留精炼塔的阶梯式占用布局。

## English

- Removed the persistent “Arrived” label from deployed equipment beacons, along with container landing labels and the extra blue beam. The beacon's own beam and the top-left deployment countdown remain.
- Corrected the Bioprocessor and Refinery input/output port assignments. Existing machines update automatically while preserving energy, inventory and processing progress.
- Machine cells now use full-block collision and selection shapes, preserving the Refinery's stepped footprint.

## 0.6.0-alpha.4

## 中文

- 补齐设备部署信标与机器集装箱落点旁的图标、名称和抵达倒计时；修正机器图标对应关系及生物处理器图标描边。
- 修复两种设备空投的喷口喷焰；部署完成后移除信标及空投仓，终端可输入六个左指令收回，创造模式无需提交材料。
- 机器接口支持各方向管道连接、手持原料右键投入，并自动从输入接口相邻容器抽取有效原料，向输出接口相邻容器输出样本。
- 按精炼厂模型修正多方块占用与碰撞范围，兼容已有设备的布局更新。

## English

- Added landing-point icons, names and arrival countdowns for equipment beacons and machine containers. Corrected machine icon assignments and preserved the Bioprocessor icon's native outlines.
- Fixed delivery thruster flames. Completed deliveries remove the beacon and its pod; six left inputs recall a deployed beacon, and Creative players can deploy machines without materials.
- Machine ports accept pipes from all directions and ingredients by right-click. Input ports pull valid ingredients from adjacent inventories; output ports transfer samples to adjacent inventories.
- Updated Refinery multiblock occupancy and collision to match its model, including migration for existing machines.

## 0.6.0-alpha.3

## 中文

- 接入生物处理器与精炼厂：提交部署材料后，集装箱从天而降，使用卓越前线的撬棍打开后生成多方块设备。
- 新增普通、稀有和超级样本，以及生物掉落物和矿物的概率加工配方。每批默认加工 5 秒，运行耗能 1,500 RF/t，储能容量与大型电池组一致。
- 设备无需 GUI，通过正面的能量、输入和输出接口连接管道；支持自动抽入原料、向相邻容器输出产物，无容器时以掉落物输出。
- 支持 Shift + 撬棍右键打包设备，保留储能、物品和加工进度；直接挖掘任意部分会拆除整台设备，不掉落机器。
- 新增可选 JEI 支持，显示加工需求、样本产量与概率，以及两种设备的部署材料；补齐集装箱喷焰、音效、开箱和精炼厂运作动画。

## English

- Added the Bioprocessor and Refinery. After submitting deployment materials, a container descends from the sky; open it with a Superb Warfare crowbar to assemble the multiblock machine.
- Added Common, Rare and Super Samples with weighted processing recipes for mob drops and minerals. Batches take 5 seconds by default at 1,500 RF/t, with energy storage matching a large battery pack.
- Machines use front-facing energy, input and output ports without a GUI. They accept standard energy and item pipes, automatically pull valid ingredients and output to adjacent inventories, or drop products when no container is present.
- Shift-right-click with a crowbar to pack a machine while preserving energy, contents and processing progress. Mining any part removes the entire machine without dropping it.
- Added optional JEI integration for ingredients, sample yields and probabilities, and equipment deployment materials, plus container thruster effects, sounds, opening animations and the Refinery working animation.

## 0.6.0-alpha.2

## 中文

- 设备部署信标终端改用信标球界面的粗箭头，包括设备指令、提交材料及返回按钮。
- 移除终端底部的按键提示和信标球提示中的单次投掷耗能文字。

## English

- Updated the equipment deployment terminal to use the beacon ball HUD's bold arrows for equipment codes, material submission and the back button.
- Removed the terminal's bottom key hint and the per-throw energy cost text from the beacon ball tooltip.

## 0.6.0-alpha.1

## 中文

- 新增设备部署信标，持有信标球即可呼叫；支持屏幕交互、设备指令、材料提交与部署空间检查。
- 信标球新增合成配方与储能，每次投掷消耗 1,000 RF，合成时继承小型电池组的电量。
- 设备提交后立即在左上角以所选设备的名称和图标显示 10 秒抵达倒计时，剩余 3 秒时回收信标。新增两种设备的材料清单；当前阶段尚未接入集装箱部署。
- 屏幕根据投掷点与落点的连线朝向最接近的四个正方向之一，方便接近和操作。
- 使用原版终端输入音效，补齐展开和回收机械音效；加粗终端文字并修正屏幕交互视角，空投仓盖板改为独立弹出实体。

## English

- Added an Equipment Deployment Beacon, callable with any beacon ball, with an interactive terminal, equipment codes, material submission and deployment clearance checks.
- Added a beacon ball crafting recipe and energy storage. Each throw costs 1,000 RF; crafting preserves the small battery pack's charge.
- Submission immediately starts a 10-second arrival countdown with the selected machine's name and icon in the upper-left HUD. The beacon retracts during the final three seconds. Added separate material bills for both machines; container deployment is not implemented yet.
- The screen faces the nearest cardinal direction towards the beacon's original throwing position for easier access.
- Added native terminal input sounds and mechanical deployment/retraction sounds, bolder terminal text, a corrected interaction camera, and a separately ejected hellpod cover entity.

## 0.5.27

## 中文

- 修复与 Doggie’s New Vehicle 等同样修改爆炸震屏的模组共同安装时，战备炮弹爆炸可能导致游戏崩溃的问题。
- 爆炸震屏改为可组合的处理方式，保留轨道加特林不震屏、自动机炮与火箭哨戒炮减弱震屏的设定，并保留其他模组的震屏处理。

## English

- Fixed a crash on stratagem projectile explosions when installed alongside Doggie’s New Vehicle or other mods modifying the same explosion camera-shake call.
- Made explosion camera-shake handling composable while preserving disabled shake for Orbital Gatling Barrage, reduced shake for Autocannon and Rocket Sentries, and other mods’ shake handling.

## 0.5.23

## 中文

- 重做轨道120mm、380mm、游走及凝固汽油弹火力网的散布，按面积均匀覆盖中心、中间环带与外围，减少重复轰炸同一区域。
- 修正落点围成外圈、中心与外围之间缺少炮击的问题。优先保留同轮间距，必要时允许爆炸范围重叠；轰炸范围、伤害、弹量及射速保持不变。

## English

- Reworked scatter for Orbital 120mm HE, 380mm HE, Walking and Napalm Barrages to distribute impacts evenly by area across the inner, middle and outer strike zones, with fewer repeated hits in the same area.
- Fixed impacts forming a perimeter ring with an under-covered middle annulus. Same-salvo spacing remains a preference, with blast overlap allowed when needed for even coverage. Strike radii, damage, shell counts and firing cadence are unchanged.


## 0.5.22

## 中文

- 地火现在由外向内分批熄灭，适用于轨道凝固汽油弹、飞鹰凝固汽油弹、燃烧地雷及火焰哨戒炮被摧毁后的火区；修正重叠火区与存档续燃行为。
- 核对原生计时逻辑，将凝固汽油地火默认最长时间修正为 13.2 秒，普通地火为约 10.6 秒。燃烧伤害与状态持续时间不变；已有自定义配置保留。

## English

- Ground fire now extinguishes in stages from the outside inward for Orbital/Eagle Napalm, incendiary mines, and destroyed Flame Sentries. Fixed overlapping-fire and save/reload lifetime handling.
- Checked native fire timing and corrected default maximum lifetimes to 13.2 seconds for napalm and approximately 10.6 seconds for ordinary ground fire. Burn damage and status durations are unchanged; existing custom configuration is preserved.

## 0.5.20

## 中文

- 修正轨道120mm、380mm及徐进弹幕的落点过度偏向外围的问题。默认每轮至少一发落在中心附近（距该轮中心不超过轰炸半径的一半），其余炮弹分散覆盖其他位置，中心弹的发射顺序随机。
- 保留默认配置下同轮炮弹的爆炸范围互不重叠；徐进弹幕的中心覆盖随轰炸方向向前推进。

## English

- Fixed Orbital 120mm HE, 380mm HE, and Walking Barrages concentrating impacts near the perimeter. With default settings, each salvo now includes at least one randomized hit within half the spread radius, with the other shells distributed elsewhere and the inner hit's firing order randomized.
- Preserved non-overlapping blast footprints within each default salvo. Walking Barrage inner coverage advances with its moving strike center.

## 0.5.19

### 中文

- 调整轨道 120mm、380mm 和徐进弹幕的同轮散布，使默认配置下同轮炮弹的爆炸范围不再重叠；保留随机落点、轰炸范围及徐进方向。
- 普通燃烧改为每 0.5 秒造成 10 点伤害，重型燃烧改为每 0.5 秒造成 20 点伤害，两者仍可叠加。
- 火焰哨戒炮默认射流直击伤害提升至原来的 2.5 倍：每次 6.25 点，默认直击 DPS 为 25，燃料消耗不变。已有配置保留原值；采用新版默认平衡时，将 `turrets.flame_sentry.damage` 改为 `6.25`。

### English

- Adjusted same-salvo scatter for Orbital 120mm HE, 380mm HE, and Walking Barrages to prevent overlapping blast footprints with default settings, while retaining randomized impacts, spread radii, and walking direction.
- Standard burning now deals 10 damage every 0.5 seconds; heavy burning deals 20 damage every 0.5 seconds. Both effects still stack.
- Increased the Flame Sentry's default direct jet damage by 2.5× to 6.25 per pulse (25 direct DPS at the default fire rate), with unchanged fuel consumption. Existing configuration values are preserved; set `turrets.flame_sentry.damage` to `6.25` to adopt the new default balance.

## 0.5.18

## 中文

- 修复轨道 380mm 弹幕等战备命中时，爆炸兼容代码可能导致炮弹异常、被防崩溃模组挂起或使游戏崩溃的问题。
- 修正正式游戏环境中的爆炸方法匹配，并兼容卓越前线 0.8.9.2 的爆炸处理结构；保留独立的战备破坏方块开关。

## English

- Fixed an explosion compatibility issue that could crash the game or suspend projectiles such as Orbital 380mm HE Barrage shells when a crash-prevention mod is installed.
- Corrected explosion method targeting in production and added compatibility with Superb Warfare 0.8.9.2's explosion layout, while preserving the independent stratagem block-damage setting.


## 0.5.17

## 中文

- 重做轨道激光的白热命中喷发，加入快速、尖锐且方向各异的放射状焰束。
- 改善光柱与白焰的衔接，消除命中点附近生硬的暖色截面。

## English

- Reworked the Orbital Laser impact with fast, sharp, irregular white-hot jets.
- Smoothed the beam-to-impact transition and removed the abrupt warm-colored cutoff near the contact point.


## 0.5.16

## 中文

- 修正轨道激光末端的圆头与圆形光晕，让光束在白焰命中处截断。
- 轨道激光扫过地面后留下持续燃烧的地火。

## English

- Removed the rounded tip and circular impact halo from the Orbital Laser. The beam now ends at the white-hot impact point.
- Orbital Lasers now leave lingering ground fire along their sweep paths.


## 0.4.8

## 中文

- 飞鹰空袭改为左右机翼交替投弹，落点沿投弹带左右错开，不再全部落在同一条直线上。
- 核对全部投弹类飞鹰，补上集束炸弹母弹的左右交错落点；空袭、集束、凝固汽油弹、毒气和烟雾的多弹投放采用交替机翼挂点，500kg 保持单枚中心投放。
- 修正凝固汽油弹第五枚炸弹折返中心的问题；五枚落点依次前进，整组着弹点及覆盖范围以信标为中心。
- 修正 110mm 火箭巢发射后的通场高度过低，改为发射期间逐渐改平，随后连续拉升离场。

## English

- Eagle Airstrike now releases bombs from alternating wing racks, with staggered left/right impacts instead of a single centerline.
- Checked every bomb-dropping Eagle and added staggered carrier-bomb impacts for Eagle Cluster Bomb. Multi-bomb airstrike, cluster, napalm, gas, and smoke payloads use alternating wing racks; the single 500kg bomb retains its central release.
- Fixed the fifth Eagle Napalm Airstrike bomb returning to the center. All five impacts now advance in sequence, with both the complete impact pattern and its bounds centered on the beacon.
- Fixed Eagle 110mm Rocket Pods flying excessively low after firing. The aircraft now levels out during its firing pass and climbs smoothly on departure.

## 0.4.7

## 中文

- 调整飞鹰投弹阵列，使完整覆盖带以信标点为中心；毒气和烟雾的升级追加弹纳入对称排列，保持各枚落点分离。
- 所有毒雾统一采用白烟同款的起伏体积云团和 10m 高度上限，保留绿色，并降低至比白烟更稀薄的密度。
- 优化烟雾和毒雾的体积渲染，加入深度感知的半分辨率重建与视野裁剪，降低多片区域重叠时的渲染开销。
- 优化飞鹰模型的顶点提交与投弹区块加载，避免为远处的进场、撤离航线同步生成地形，减少重复的区块请求。

## English

- Centered complete Eagle bombing patterns on the beacon, including the upgraded gas and smoke canisters, with distinct, symmetric impact positions.
- Gave all gas clouds the same billowing volume shape and 10m height limit as white smoke, retaining their green color with lower density.
- Optimized smoke and gas rendering with depth-aware half-resolution reconstruction and view-frustum culling to reduce the cost of overlapping clouds.
- Optimized Eagle vertex submission and ordnance chunk loading, avoiding synchronous terrain generation along distant approach and departure paths and redundant chunk requests.


## 0.4.6

## 中文

- 修正“飞鹰”毒气空袭第四枚炸弹与第二枚落点重叠的问题，追加弹沿投弹带继续向前投放。
- 修正“飞鹰”烟雾攻击最后一枚回落到中心的问题，并调整释放航段，避免后半段烟雾弹逆向飞行。
- 白色烟雾的高度上限提高至 10m，改用起伏的云团轮廓与柔和的不规则边缘，消除平顶和明显切边；保留光影兼容及原版 AI 感知机制。

## English

- Fixed the fourth Eagle Gas Airstrike bomb overlapping the second impact. The extra bomb now continues along the strike pattern.
- Fixed the final Eagle Smoke Strike bomb returning to the center and adjusted the release corridor so later canisters no longer launch backwards.
- Raised white smoke’s height limit to 10m and gave it a billowing silhouette and soft, irregular edges to remove flat tops and hard boundaries, while preserving shader compatibility and vanilla AI perception behavior.

## 0.4.5

## 中文

- 全面核对所有战备及重新武装指令的中英文名称，与原版正式显示名称保持一致。
- 将“掷弹兵榴弹墙”修正为“掷弹兵防卫墙”，“防护罩生成器”修正为“防护罩生成中继器”，“反步兵地雷”修正为“反步兵雷区”。
- 英文反坦克地雷名称修正为原版的 `Anti-tank Mines`。
- 同步统一物品、部署实体、载具和 HUD 中的对应名称。

## English

- Checked all stratagem and Eagle Rearm names against the original Simplified Chinese and US English localization.
- Corrected the Chinese names of Grenadier Battlement, Shield Generator Relay, and Anti-Personnel Minefield.
- Matched the original capitalization of `Anti-tank Mines`.
- Updated the corresponding item, deployed entity, vehicle, and HUD labels for consistency.

## 0.4.4

## 中文

- 新增“飞鹰”凝固汽油弹空袭、“飞鹰”毒气空袭和“飞鹰”烟雾攻击，支持独立使用次数、短冷却及共享补给。
- 新增三种飞鹰的单次投弹量按满升级设置：凝固汽油弹 5 枚、毒气弹 4 枚、烟雾弹 9 枚，采用对应原版落点阵列。
- 所有飞鹰战备的中英文显示名称与原版语言文件保持一致。
- 凝固汽油弹造成 240 直击与 40 爆炸伤害（内半径 2m、外半径 10m），留下持续 21 秒的地火；地火同时施加 3 秒燃烧与 1 秒重型燃烧，两种伤害可以叠加，重型燃烧伤害为普通燃烧的两倍。
- “飞鹰”毒气空袭的每枚炸弹造成 240 直击，不造成爆炸伤害；半径 12m 的毒雾持续 15 秒，持续施加 6 秒毒气效果。
- “飞鹰”烟雾攻击的每枚炸弹造成 240 直击，不造成爆炸伤害；生成半径 15m、持续 30 秒的白色体积烟雾，兼容光影。
- 烟雾接入原版视线、目标判定与寻路：阻挡隔烟获取新目标，烟雾内的感知距离上限为 4m；已有仇恨及其遗忘时间仍由原版 AI 决定。敌人向最后目击位置寻路，穿烟受击后则寻找攻击发出的位置。
- 体积雾只在屏幕内对应范围进行绘制，降低多片区域同时显示时的渲染开销。

## English

- Added Eagle Napalm Airstrike, Eagle Gas Airstrike, and Eagle Smoke Strike with independent charges, short cooldowns, and shared Eagle rearming.
- The three new Eagles use fully upgraded payload counts: 5 napalm bombs, 4 gas bombs, and 9 smoke bombs, with their native landing patterns.
- All Eagle stratagem display names now match the original Chinese and English localization.
- Napalm bombs deal 240 direct damage plus 40 explosive damage (2m inner / 10m outer radius), leaving ground fire for 21 seconds. Ground fire applies 3 seconds of Burning and 1 second of Heavy Burning; both stack, and Heavy Burning deals twice the normal burn damage.
- Gas bombs deal 240 direct damage without an explosion. Their 12m-radius clouds last 15 seconds and continuously apply a 6-second gas effect.
- Smoke bombs deal 240 direct damage without an explosion, creating white, shader-compatible volumetric smoke with a 15m radius and a 30-second lifetime.
- Smoke integrates with vanilla sight checks, targeting conditions, and navigation. It blocks new targets across smoke and caps perception inside smoke at 4m, while each mob retains its original AI and aggro-memory rules. Obscured enemies path toward the last seen location; attacks through smoke reveal the attack's origin instead of the player's subsequent position.
- Volume rendering is restricted to each field's projected screen bounds to reduce the cost of overlapping effects.

## 0.4.3

### 中文

- 扩大飞鹰 500kg 炸弹插入与引信提示音的传播范围，最远可在 128 格内听见。
- 修复村民等缺少攻击伤害属性的生物受到混乱或恐慌效果时可能导致崩溃的问题；保留混乱移动，并维持正常近战及远程生物的攻击行为。

### English

- Extended the audible range of Eagle 500kg Bomb embedding and fuse cues to 128 blocks.
- Fixed a crash when confusion or panic affected villagers and other mobs without an attack damage attribute. Confused movement is preserved, along with attacks from supported melee and ranged mobs.

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
