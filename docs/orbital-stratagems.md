# 超级驱逐舰与轨道战备

将任一轨道战备装入战备信标球后，战备列表顶部自动出现“呼叫超级驱逐舰”。按住战备键输入 `↑ ↑ ↓ ↓ ← → ← →`，再输入一个方向：`↑ 北`、`← 西`、`→ 东`、`↓ 南`。舰船在最后 0.15 秒沿舰艏方向高速飞入，在三秒等待结束时骤停至所选方位。

下界禁用所有战备，包括舰船呼叫、轨道打击、飞鹰与地面部署，关闭冷却也不能绕过限制。All stratagems are unavailable in the Nether, including ship calls, orbital strikes, Eagle attacks and ground deployments, regardless of the cooldown setting.

舰船相对呼叫地点的默认高度为 900 格，水平方位偏移 700 格。支援区域以呼叫时的地面位置为圆心，默认水平半径 1024 格。舰船不随玩家移动，也不需要加载远方的舰船区块。每位玩家呼叫的舰船单独保存，只有自己的舰船能为自己的轨道战备提供支援。

已有舰船时，再次输入呼叫指令可选择 `← 撤离` 或 `→ 方位`。方位选项再次提供四个方向，以玩家此时的位置确定新的支援区域；撤离与重新定位均等待三秒；撤离时舰船在约 0.1 秒内加速飞离，同维度重新定位直接从当前舰位高速跃迁至目标位置。跨维度后须在当前维度重新定位。

无舰船、舰船正在跃迁、或玩家超出支援范围时，轨道战备显示“超级驱逐舰不可用”与灰白图标。关闭战备冷却不会取消舰船支援要求。信标落点也必须位于支援范围内。

## 轨道精准打击

输入 `→ → ↑` 激活并投掷信标。默认在信标展开 3 秒后开炮，同时从舰船方向播放原版舰炮闷响；随后炮弹飞行 1.5 秒，从舰船方向斜向飞来，接近落点后检测方块与生物碰撞；障碍物可能提前截住炮弹。直击伤害 640，爆炸伤害 240，内半径 4 格，外半径 12 格。内半径内为全额爆炸伤害，外圈随距离衰减。

开炮后开始冷却，满升级默认 68.4 秒。它使用普通战备冷却，不消耗飞鹰次数，也不参与飞鹰补给。

## 配置

退出世界或关闭服务器后，修改存档的 `serverconfig/sbw_helldiver-server.toml`：

| 分区 | 字段 | 默认值 | 含义 |
| --- | --- | --- | --- |
| `orbital` | `supportRadius` | 1024 | 水平支援半径 |
| `orbital` | `shipAltitude` | 900 | 舰船相对呼叫者的高度 |
| `orbital` | `shipOffset` | 700 | 舰船朝所选方位的水平偏移 |
| `orbital.precisionStrike` | `firingDelaySeconds` | 3 | 信标展开至开炮的秒数，不包含飞行 |
| `orbital.precisionStrike` | `flightSeconds` | 1.5 | 开炮至命中的飞行秒数 |
| `orbital.precisionStrike` | `directDamage` | 640 | 炮弹直击伤害 |
| `orbital.precisionStrike` | `explosionDamage` | 240 | 全额爆炸伤害 |
| `orbital.precisionStrike` | `innerRadius` | 4 | 全额伤害半径 |
| `orbital.precisionStrike` | `outerRadius` | 12 | 爆炸外半径 |
| `stratagems.orbital_precision_strike` | `cooldownSeconds` | 68.4 | 抵达后冷却秒数 |

外半径必须大于内半径；若填反，运行时将内半径限制在外半径以内。

跃迁音效从舰船所在方位传来，远距离采用方位提示声源以保持可辨识度。轨道精准打击默认在信标展开 3 秒后开炮，同时从舰船方向播放原版舰炮闷响；随后炮弹飞行 1.5 秒，飞行末段播放原版轨道精准打击的空间呼啸声，命中后移除信标。旧的 `arrivalSeconds` 已由 `firingDelaySeconds` 与 `flightSeconds` 替代。

Warp audio uses a spatial cue from the ship’s bearing, projected into an audible range. Orbital Precision Strike fires 3 seconds after beacon activation, followed by 1.5 seconds of shell flight with the original Helldivers 2 incoming whistle in the final approach. The beacon remains until impact. The former `arrivalSeconds` setting is replaced by `firingDelaySeconds` and `flightSeconds`.


## 轨道加特林火力网 / Orbital Gatling Barrage

指令 → ↓ ← ↑ ↑。需要己方超级驱逐舰处于支援范围内，默认等待 2 秒后开火。
连续发射 5×60 发，间隔 0.045 秒，轮次间不暂停；每 3 发普通弹之后为 1 发高爆弹。
直击 30，高爆弹额外造成 60 爆炸伤害，内半径 1.5 米、外半径 4.5 米。
默认满升级冷却为 59.85 秒。可在配置 `orbital.gatlingBarrage` 调整伤害、轮数、射速和游走范围。

解包 `BombardmentComponentData` 的初始游走半径为 1，步长范围 1–2，区域参数为 7，轮次间隔为 0。
MC 实现以信标为中心进行有界连续游走，使用上述参数作为米制距离；这是针对 MC 的实现，并非声称还原了未经验证的原版内部游走公式。
原版记录为 4 轮，此处按要求采用满升级的 5 轮。远距离航迹只绘制曳光，接近地面后生成真实碰撞弹体。

Code: Right, Down, Left, Up, Up. Requires an owned Super Destroyer in range; fires after a two-second delay.
Fires 300 rounds continuously at 0.045 s intervals, with one HE round after every three regular rounds.
Each round deals 30 direct damage; HE adds 60 blast damage with 1.5 m inner / 4.5 m outer radii.
Fully upgraded default cooldown: 59.85 s. Settings are under `orbital.gatlingBarrage`.
The bounded walk adapts the native start radius 1, step range 1–2 and area parameter 7 to Minecraft metres;
the original internal walk formula has not been verified. The native base record has four rounds; this implementation uses five as requested.
Only terminal flight segments are collision entities, avoiding distant ship-chunk loading.

同维度调整方位时，驱逐舰从当前舰位直接高速跃迁至目标舰位。红色战备不再要求周围 3×3 空地；实体空投战备保留空间检查。
Within one dimension, ship relocation warps directly between its current and new anchors. Red stratagems bypass the 3×3 deployment-clearance requirement; hellpod deployments retain it.


## 轨道空爆攻击 / Orbital Airburst Strike

指令 → → →。需要己方超级驱逐舰支援，信标展开 2 秒后开炮。共 4 轮，每轮间隔 4 秒，每轮一枚母弹，空爆后分裂为 12 枚子弹。
母弹与子弹均为 50 直击伤害、60 爆炸伤害。母弹全额／外半径为 0.2／0.5 格，子弹为 3／6 格；外圈伤害随距离衰减。
信标保持到最后一轮子弹命中，冷却从首次开炮开始，满升级默认 85.5 秒。

本地原版弹药表记录母弹寿命 0.95 秒、初速 400 m/s，分裂数量 12、锥角参数 35°，子弹初速 300 m/s。
这些记录没有直接给出固定的离地空爆高度；MC 默认在信标上方 20 格的入射轨迹上空爆。
分裂围绕母弹飞行方向散布，使用均匀立体角分层采样，这是 MC 适配实现。

Code: Right, Right, Right. Requires an owned Super Destroyer in range. Fires after two seconds, with four single-shell salvos four seconds apart, each splitting into 12 bomblets.
Both carrier and bomblets deal 50 direct and 60 blast damage; inner/outer radii are 0.2/0.5 m and 3/6 m respectively.
The beacon remains through the final bomblet impacts. Cooldown starts with the first shot; fully upgraded default: 85.5 seconds.

The local native ammunition records specify a 0.95 s carrier lifetime at 400 m/s, 12 fragments, a 35° cone parameter and 300 m/s bomblets.
They do not directly specify a fixed burst altitude. Minecraft defaults to 20 blocks above the beacon, on the incoming trajectory, with stratified solid-angle sampling around the incoming direction.

配置分区 / Configuration: `orbital.airburstStrike`

| 字段 / Field | 默认 / Default | 含义 / Meaning |
| --- | --- | --- |
| `rounds` | 4 | 轮数 / Salvos |
| `salvoIntervalSeconds` | 4 | 发射间隔 / Firing interval |
| `firingDelaySeconds` | 2 | 展开至开炮 / Call-in delay |
| `flightSeconds` | 1.5 | 母弹飞行时间 / Carrier flight |
| `burstHeight` | 20 | 信标上方空爆高度 / Burst height above beacon |
| `spreadAngleDegrees` | 35 | 入射方向散布锥角 / Spread cone angle |
| `bombletsPerShell` | 12 | 子弹数量 / Bomblets |
| `bombletSpeed` | 300 | 格/秒 / Blocks per second |
| `directDamage` / `explosionDamage` | 50 / 60 | 母弹伤害 / Carrier damage |
| `innerRadius` / `outerRadius` | 0.2 / 0.5 | 母弹半径 / Carrier radii |
| `bombletDirectDamage` / `bombletExplosionDamage` | 50 / 60 | 子弹伤害 / Bomblet damage |
| `bombletInnerRadius` / `bombletOuterRadius` | 3 / 6 | 子弹半径 / Bomblet radii |

冷却配置 / Cooldown: `stratagems.orbital_airburst_strike.cooldownSeconds`.


## 120mm、380mm与游走火力网 / HE and Walking Barrages

三种战备均需己方超级驱逐舰支援，每次发射5轮、每轮3发，共15发。调用期间保存本次攻击参数与全部预定落点，玩家移动、转身或存档重载不会改变弹幕方向或重置发射进度。信标保留到最后一枚真实炮弹命中。

| 战备 / Stratagem | 指令 / Code | 开炮前等待 / Call-in | 散布半径 / Radius | 同轮发射间隔 / Shot gap | 轮间停顿 / Round pause | 满升级冷却 / Upgraded cooldown |
| --- | --- | --- | --- | --- | --- | --- |
| 轨道120mm高爆弹火力网 / Orbital 120mm HE Barrage | → → ↓ ← → ↓ | 5 s | 27 m | 0.75 s | 2 s | 153.9 s |
| 轨道380mm高爆弹火力网 / Orbital 380mm HE Barrage | → ↓ ↑ ↑ ← ↓ ↓ | 6 s | 36 m | 1.5 s | 3 s | 205.2 s |
| 轨道游走火力网 / Orbital Walking Barrage | → ↓ → ↓ → ↓ | 3 s | 25 m，移动圆心 / moving center | 1.5 s | 3 s | 205.2 s |

轮间停顿从上一轮最后一发计至下一轮第一发。120mm从首发到末发共15.5秒，380mm和游走为27秒；默认每枚炮弹飞行1.5秒。冷却从首次开炮开始。

120mm造成560直击、200爆炸伤害，内半径3.3m、外半径10m。380mm与游走直接使用轨道精准打击炮弹，共享其伤害配置；默认640直击、240爆炸、内半径4m、外半径12m。

游走方向为投掷位置指向信标落点的水平连线，在信标展开时固定。解包的 `BombardmentComponentData` 中，游走火力网 `speed=3`、`area_size=25`，MC据此采用每秒推进3格、半径25格的移动散布区域，默认末发圆心前移81格。每发在当时的圆形区域内独立、均匀取样；这是MC中的散布实现，组件数据不能证明原版内部使用相同的随机算法。

炮弹从本次呼叫确定的驱逐舰位置射来。远距离飞行以发光航迹表示，末段生成真实碰撞炮弹；可被沿途地形或生物截住，无需加载远方舰位区块。落点按当地地表高度确定。

All three barrages require an owned Super Destroyer in range and fire five salvos of three shells. Settings, planned impact positions and firing progress persist across reloads. Player movement and turning do not change the walking direction. The beacon remains until the final real shell hits.

The round pause runs from a salvo's last shot to the next salvo's first. The first-to-last-shot span is 15.5 s for 120mm and 27 s for 380mm/Walking. Each shell travels for 1.5 s by default; cooldown begins with the first shot.

120mm deals 560 direct and 200 blast damage with 3.3/10 m inner/outer radii. 380mm and Walking use the actual Precision Strike projectile and share its damage settings, defaulting to 640 direct, 240 blast and 4/12 m radii.

Walking advances along the horizontal throw-origin-to-beacon line, fixed when the beacon deploys. Native component values `speed=3` and `area_size=25` are adapted to 3 blocks/s and a 25-block radius; the last shot's center is 81 blocks forward by default. Each impact independently samples a uniform-area disk around the current center. The original internal sampling algorithm is not available from these component records.

Flight originates at the fixed ship position, using glowing distant traces and real terminal collision projectiles. Terrain and creatures can intercept incoming shells without loading distant ship chunks. Targets use the local surface height.

配置 / Configuration:

- `orbital.he120mmBarrage`, `orbital.he380mmBarrage`, `orbital.walkingBarrage`: `rounds`, `shotsPerRound`, `shotIntervalSeconds`, `roundPauseSeconds`, `spreadRadius`, `firingDelaySeconds`, `flightSeconds`.
- `orbital.walkingBarrage.advanceSpeed`: 推进格/秒 / advance in blocks/s.
- `orbital.he120mmBarrage`: `directDamage`, `explosionDamage`, `innerRadius`, `outerRadius`.
- 380mm和游走共用 / 380mm and Walking share: `orbital.precisionStrike` damage/radius settings.
- 冷却 / Cooldown: `stratagems.orbital_120mm_he_barrage.cooldownSeconds`, `stratagems.orbital_380mm_he_barrage.cooldownSeconds`, `stratagems.orbital_walking_barrage.cooldownSeconds`.
