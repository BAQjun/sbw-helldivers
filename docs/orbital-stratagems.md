# 超级驱逐舰与轨道战备

将“轨道精准打击”装入战备信标球后，战备列表顶部自动出现“呼叫超级驱逐舰”。按住战备键输入 `↑ ↑ ↓ ↓ ← → ← →`，再输入一个方向：`↑ 北`、`← 西`、`→ 东`、`↓ 南`。三秒后舰船跃迁至所选方位。

舰船相对呼叫地点的默认高度为 900 格，水平方位偏移 700 格。支援区域以呼叫时的地面位置为圆心，默认水平半径 1024 格。舰船不随玩家移动，也不需要加载远方的舰船区块。每位玩家呼叫的舰船单独保存，只有自己的舰船能为自己的轨道战备提供支援。

已有舰船时，再次输入呼叫指令可选择 `← 撤离` 或 `→ 方位`。方位选项再次提供四个方向，以玩家此时的位置确定新的支援区域；撤离与重新定位均等待三秒。跨维度后须在当前维度重新定位。

无舰船、舰船正在跃迁、或玩家超出支援范围时，轨道战备显示“超级驱逐舰不可用”与灰白图标。关闭战备冷却不会取消舰船支援要求。信标落点也必须位于支援范围内。

## 轨道精准打击

输入 `→ → ↑` 激活并投掷信标。默认在信标展开 3 秒后开炮，随后炮弹飞行 1.5 秒，从舰船方向斜向飞来，接近落点后检测方块与生物碰撞；障碍物可能提前截住炮弹。直击伤害 640，爆炸伤害 240，内半径 4 格，外半径 12 格。内半径内为全额爆炸伤害，外圈随距离衰减。

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

跃迁音效从舰船所在方位传来，远距离采用方位提示声源以保持可辨识度。轨道精准打击默认在信标展开 3 秒后开炮，随后炮弹飞行 1.5 秒，飞行末段播放原版轨道精准打击的空间呼啸声，命中后移除信标。旧的 `arrivalSeconds` 已由 `firingDelaySeconds` 与 `flightSeconds` 替代。

Warp audio uses a spatial cue from the ship’s bearing, projected into an audible range. Orbital Precision Strike fires 3 seconds after beacon activation, followed by 1.5 seconds of shell flight with the original Helldivers 2 incoming whistle in the final approach. The beacon remains until impact. The former `arrivalSeconds` setting is replaced by `firingDelaySeconds` and `flightSeconds`.
