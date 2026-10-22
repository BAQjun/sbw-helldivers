# 炮台属性配置

适用于 0.3.85 及以上版本。炮台参数由服务端决定，并同步给连接的客户端。

首次进入世界后生成 `serverconfig/sbw_helldiver-server.toml`，位于对应存档目录内。先退出该存档或关闭服务器，再编辑此文件并重新进入。整合包可将准备好的文件放入 `defaultconfigs/`，作为新世界的初始配置；这不会覆盖已有世界。

每种炮台都有 `[turrets.<id>]` 分区。更新旧世界时会自动补齐新字段，原有全局设置保留。字段附带中英文注释及允许范围；不要超出文件标注的范围。

## 炮台分区

| id | 炮台 |
| --- | --- |
| `machine_gun_sentry` | 机枪哨戒炮 |
| `gatling_sentry` | 加特林哨戒炮 |
| `rocket_sentry` | 火箭哨戒炮 |
| `autocannon_sentry` | 自动机炮哨戒炮 |
| `mortar_sentry` | 迫击炮哨戒炮 |
| `gas_mortar_sentry` | 毒气迫击炮 |
| `ems_mortar_sentry` | 电磁迫击炮 |
| `flame_sentry` | 火焰哨戒炮 |
| `laser_sentry` | 激光哨戒炮 |
| `tesla_tower` | 特斯拉塔 |
| `hmg_emplacement` | 重机枪支架 |
| `anti_tank_emplacement` | 反坦克炮台 |
| `grenadier_battlement` | 掷弹兵榴弹墙 |

## 通用字段

| 字段 | 含义 |
| --- | --- |
| `maxHealth` | 本体最大生命值，1～100000。 |
| `explosionResistance` | 爆炸减伤比例，0～1；默认 `0.5`，即减伤 50%。不影响直击伤害。 |
| `lifetimeTicks` | 工作时限，20 tick = 1 秒；`-1` 使用全局 `sentryLifetimeTicks`，`0` 不因时间回收。自动炮台默认 `-1`，手动炮台默认 `0`。不改变空投和展开动画时长。 |
| `damage` | 每发直击伤害。火焰为每次射流伤害，激光为每次伤害结算，特斯拉塔为每次放电对每个目标的伤害。爆炸、附带燃烧、地火和毒气持续伤害另行计算。 |
| `rpm` | 每分钟射击／射流／伤害结算／放电次数。连发武器会同比缩放组内间隔和组间停顿；预热、换弹和重新瞄准仍会影响实际输出。 |
| `ammunition` | 非能量炮台的总弹药或燃料，0～100000。火焰每次射流仍消耗 2 单位燃料。激光与特斯拉塔不使用此字段。 |
| `range` | 自动炮台最大索敌距离，0～256 格；火焰同时用作射流长度。此字段不改变子弹速度或炮弹弹道，迫击炮还需满足弹道可达条件。手动炮台没有自动索敌，因此不生成此字段。 |
| `minimumRange` | 自动炮台最小索敌距离，单位为格；实际不会超过 `range`。 |

服务器每秒运行 20 tick，非整数间隔通过相邻 tick 分配，长期平均射速不因简单取整而降低。各类武器的射速上限写在配置注释中，避免设置超出对应射击机制的能力。

提高生命或弹药容量不会给已部署炮台回血或补弹；读取旧存档时，超过新上限的数值会被限制。新部署的炮台使用新的完整容量。提高总弹药也不会增加旧火箭炮的备弹。

## 专用字段

| 字段 | 适用炮台与含义 |
| --- | --- |
| `magazineSize` | 火箭哨戒炮的单次装填容量；不超过总弹药，其余弹药为备弹。 |
| `explosionDamage` | 火箭、自动机炮、普通／电磁迫击炮、反坦克炮台、榴弹墙的每发爆炸伤害。 |
| `explosionInnerRadius` | 上述炮台的爆炸满伤内半径，单位为格；超出外半径时按外半径限制。 |
| `explosionOuterRadius` | 爆炸伤害衰减至零的外半径，0～128 格；`0` 关闭范围伤害。 |
| `scatterRadius` | 三类迫击炮落点的散布半径，0～64 格；`0` 取消随机散布。 |
| `beamRange` | 激光光束和已锁定目标的最远距离，0～512 格；实际至少等于激光索敌距离。 |
| `maxHeat` | 激光炮台过热自爆阈值；警告阈值为其 80%。 |
| `heatPerSecond` | 激光炮台开火时每秒升温量；`0` 表示不累积热量。环境散热规则保留。 |
| `jumpRange` | 特斯拉塔相邻电弧目标间的跳跃距离，0～128 格。 |
| `maxVictims` | 特斯拉塔每次放电的目标数量上限，1～32。 |

榴弹墙仍不会因为打空弹药而收起；如果希望它定时收起，可单独给 `lifetimeTicks` 设置正值。

## 修改示例

以下仅展示需要修改的分区与字段；在自动生成的文件中编辑对应值，保留其他字段即可。

```toml
[turrets.machine_gun_sentry]
maxHealth = 180.0
damage = 10.0
rpm = 700
ammunition = 400
range = 80.0
explosionResistance = 0.5
lifetimeTicks = 3600

[turrets.grenadier_battlement]
ammunition = 90
damage = 5.0
explosionDamage = 60.0
explosionInnerRadius = 3.5
explosionOuterRadius = 7.5
rpm = 160
lifetimeTicks = 0
```

火焰默认 `damage = 2.5`、`rpm = 240`，直击 DPS 为 `2.5 × 240 ÷ 60 = 10`。激光默认 `damage = 7.5`、`rpm = 240`，直射 DPS 为 30。此处均不包含附带燃烧伤害。

这些设置针对炮台本体与武器，不修改其他战备：地雷、护盾生成器、飞鹰和空投砸击仍使用各自规则。
