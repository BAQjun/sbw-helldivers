# 地火持续时间 / Ground-fire lifetime

## 中文

地火按内圈至外圈的距离使用六档寿命，外圈先熄灭，内圈最后熄灭。轨道凝固汽油弹、飞鹰凝固汽油弹、燃烧地雷和火焰哨戒炮燃料爆炸均使用该规则。每个火区独立计时；重叠处使用仍然有效的火源，新的燃料可以重新点燃并续燃。单格射流及轨道激光残火使用完整的普通地火寿命。

默认最长时间（从最后一次点燃该位置起，以正常 20 TPS 计）：

| 火源 | 最长时间 | 内圈 / 外圈 |
| --- | --- | --- |
| 轨道凝固汽油弹 | 13.2 秒 / 264 刻 | 3.3 m / 10 m |
| 飞鹰凝固汽油弹 | 13.2 秒 / 264 刻 | 2 m / 10 m |
| 燃烧地雷 | 10.55 秒 / 211 刻 | 1.2 m / 4 m |
| 火焰哨戒炮燃料爆炸 | 10.55 秒 / 211 刻 | 0.4 m / 3–4 m |
| 火焰哨戒炮射流残火 | 10.55 秒 / 211 刻 | 单格 |
| 轨道激光残火 | 10.55 秒 / 211 刻 | 单格，共用普通地火配置 |

这些是单次火源的上限。持续喷射、重复轰炸及多个火区覆盖可以延长实际连续燃烧时间；离开火焰后的普通燃烧 3 秒、重型燃烧 1 秒不属于地火寿命，伤害及叠加规则不变。存档保留火区总寿命、剩余时间和内圈半径，不会在加载时重新点燃已经熄灭的外圈。

`orbital.napalmBarrage.groundFireSeconds` 控制轨道火内圈的最长时间。已有配置不会被覆盖；若旧配置仍为 21 秒，需要改为 `13.2` 才采用本版默认值。`groundFireRadius` 是地火外半径，`innerRadius` 同时决定轨道火的内圈半径。

### 原生数据核对

旧版 15 / 21 秒是本模组原有设置，其中 21 秒依据旧枚举注释，不能作为当前原生计时的证据。本次使用本机解密设置表和公开恢复的原生代码静态核对：

- 原生模板表 RVA `0x1FEBF80`：模板 3 为燃料 40 / 强度 80，模板 4 为燃料 50 / 强度 100。这些数值不是秒数。
- 原生寿命计算 RVA `0x11438A0`：`(fuel * 256 + max(0, intensity - fuel) * 8) / 1000`，得到模板 3 的 10.56 秒、模板 4 的 13.2 秒。Minecraft 将其取整为 211 / 264 刻。
- 活动地火按约 256 ms 更新；有燃料时强度每步减 1，燃料耗尽后每步减 32。实际可见时间还受强度阈值、边缘平方衰减及时间量化影响，不等于每处都燃烧到名义上限。
- 本机表中轨道弹爆炸 63 / 74、飞鹰凝固汽油弹爆炸 360 指向模板 4；燃烧地雷爆炸 72、火焰哨戒炮喷射组件指向模板 3。轨道火使用其自身的 3.3 / 10 m 参数，飞鹰火使用自身的 2 / 10 m 参数。
- 轨道激光残火在本模组共用普通地火寿命；尚未独立确认原版轨道激光的专用地火模板。火焰哨戒炮燃料爆炸同样共用普通地火；匹配其 0.4 / 4 m 的原生爆炸记录使用模板 3，但该爆炸记录的实体归属尚未独立确定。

公开原生代码来源：[HWG90/hd2-reverse](https://github.com/HWG90/hd2-reverse/tree/main/game-dll)。核对快照 SHA-256：`bcad43f462e452407d7ac41ae4d7bf17810802720f6aea325afd1d99b1e8ce00`。仅静态读取相关已恢复页面，不执行映像、不修改原版文件。

## English

Area fire extinguishes in six radial lifetime bands, from the outside inward. Orbital and Eagle Napalm, incendiary mines, and destroyed Flame Sentry fuel use this rule. Overlapping sources retain their own deadlines; fresh fuel can reignite expired rings. Saves preserve elapsed age and radial settings.

Maximum default lifetime since the last ignition is **13.2 s (264 ticks)** for Eagle/Orbital Napalm and **10.55 s (211 ticks)** for ordinary ground fire: incendiary mines, Flame Sentry jet trails and fuel explosions, and the mod's Orbital Laser trails. Smaller outer-ring lifetimes do not change burn damage, the 3-second ordinary burn tail or the 1-second heavy burn tail. Continuous ignition can keep an area burning longer than one source's lifetime.

The native lifetime formula above yields 10.56 / 13.2 s for templates 3 / 4; these replace the mod's previous 15 / 21-second defaults. Six-band extinction is a Minecraft adaptation of native radial falloff, not a claim that the original uses six rings. The mod's laser/fuel-fire shared profile is distinguished from independently confirmed native source links above.

Existing configuration is preserved. Set `orbital.napalmBarrage.groundFireSeconds = 13.2` to replace an old 21-second override. This is the inner-ring maximum; outer rings expire earlier. Configured `innerRadius` controls the orbital fire's inner plateau and `groundFireRadius` its outer bound.
