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
| `lifetimeTicks` | 工作时限，20 tick = 1 秒；`-1` 使用全局 `sentryLifetimeTicks`，`0` 不因时间回收。自动炮台默认 `-1`；人员操作型炮位忽略该字段，只在弹药耗尽且玩家离开后自动收回。不改变空投和展开动画时长。 |
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

## 自动收回 / Automatic retraction

进入世界后打开“模组 → SBW Helldivers → 配置 → 战备自动收回”。默认开启；关闭后，战备不会因弹药耗尽或工作时限到达而自动缩回，重新开启会恢复自动收回。不会补充弹药，不影响摧毁效果和玩家投送出仓回收。

该开关为服务端世界设置 `autoRetract`，保存于 `serverconfig/sbw_helldiver-server.toml`，立即生效。多人游戏仅管理员（权限等级 2）可修改；其他玩家可以查看当前值。

In a world, open Mods → SBW Helldivers → Config → Automatic retraction. Enabled by default. Disabling it keeps deployed stratagems extended after ammunition or operating time runs out. Re-enabling resumes automatic retraction. It does not refill ammunition or prevent destruction, and player delivery cleanup is unaffected.

This is the server-owned `autoRetract` world setting in `serverconfig/sbw_helldiver-server.toml`. Changes apply immediately. Multiplayer changes require operator permission level 2; other players can view the setting.


## 无限弹药 / Unlimited ammunition

进入世界后打开“模组 → SBW Helldivers → 配置 → 炮台无限弹药”，默认关闭。该世界的 `infiniteTurretAmmo` 设置仅允许管理员或单人世界主人修改，保存后立即生效。

覆盖机枪、加特林、自动机炮、火箭、三种迫击炮、火焰哨戒炮，以及重机枪支架、反坦克炮台和掷弹兵榴弹墙。开启后不消耗弹药或燃料，已耗尽但仍展开的武器可恢复使用；关闭后恢复正常消耗。火箭仍会打空当前弹仓并完成装填，激光的过热机制和自动炮台的工作时限保持不变。若还需要取消自动炮台的时限回收，可同时关闭“战备自动收回”。弹药容量设为 0 的武器仍不会开火。

人员操作型炮位不再按工作时限自动收回。启用自动收回时，弹药耗尽且操作者离开后才开始收回；关闭自动收回时仍会保持展开。

In a world, open Mods → SBW Helldivers → Config → Unlimited turret ammunition. The world setting `infiniteTurretAmmo` defaults to false, applies immediately after saving, and can only be changed by an operator or the singleplayer owner.

Covers ballistic sentries, all three mortar sentries, flame fuel, and the HMG, Anti-Tank and Grenadier manned emplacements. Enabling it preserves ammunition/fuel and reactivates empty weapons that are still deployed. Disabling it resumes normal consumption. Rocket magazines still empty and reload; laser heat and autonomous sentry lifetimes remain unchanged. Disable Automatic retraction as well if autonomous sentries should remain deployed beyond their lifetime. Weapons configured with zero capacity remain unable to fire.

Manned emplacements ignore operating lifetimes. With automatic retraction enabled, they retract only after running out of ammunition and the operator dismounts. Disabling automatic retraction keeps them deployed.

## 模组列表搜索 / Mod list search

Forge 模组列表支持搜索 `SBW`、`Helldivers`、`sbw_helldiver`、`绝地潜兵` 或 `地狱潜兵`，不区分英文大小写，也兼容名称中的空格、下划线与连字符。

The Forge mod list accepts `SBW`, `Helldivers`, `sbw_helldiver`, `绝地潜兵` and `地狱潜兵`, ignoring case, spaces, underscores and hyphens.

## 爆炸破坏方块 / Explosion block damage

进入世界后打开“模组 → SBW Helldivers → 配置 → 战备爆炸破坏方块”。世界设置 `stratagemExplosionBlockDamage` **默认关闭**，保存后立即生效，只有房主或权限等级 2 的管理员可以修改。

关闭时，战备爆炸保留生物伤害、击退、音效和粒子，但不破坏方块。开启后允许对应弹药破坏地形，不受卓越前线爆炸总开关影响，也不会改变卓越前线武器自身的设置；本来不破坏地形的地雷、区域效果等不会因此获得破坏能力。该选项不控制空投落地、地火或直接命中的独立机制。

In a world, open Mods → SBW Helldivers → Config → Stratagem explosion block damage. The server-owned `stratagemExplosionBlockDamage` setting **defaults to false**, applies immediately after saving, and can only be changed by the host or an operator with permission level 2.

When disabled, stratagem explosions retain entity damage, knockback, sounds and particles without destroying blocks. Enabling allows the relevant payloads to damage terrain independently of Superb Warfare's global explosion setting, without changing SBW weapons' own settings. Inherently non-destructive mines and area effects remain non-destructive. This setting does not control independent hellpod landing, ground-fire or direct-hit mechanics.
