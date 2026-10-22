# 战备冷却与飞鹰补给

冷却由服务器保存，按玩家独立计时。更换信标球、切换维度、死亡或重新登录不会重置；服务器运行时离线玩家的计时继续，服务器关闭时暂停。

成功投掷后先等待信标展开，再进入抵达倒计时。图标从上到下褪去颜色；实际抵达时立即进入冷却，图标从上到下恢复颜色。冷却图标使用白色炮塔与弹药剪影、灰色背景；飞鹰剩余次数显示在图标右下方的白色方框内。部署失败不消耗次数、不进入冷却。已有存档和指令召唤的演示实体不会追溯扣除次数。

左上角会在战备激活、信标投出及等待抵达期间自动弹出对应战备行，保留战备槽位置，切换手持物品后仍会显示。抵达后切换为冷却倒计时，继续显示 3 秒再收起；冷却剩余最后 3 秒时再次弹出，结束后依次显示指令箭头，全部出现后再停留 1 秒收起。抵达倒计时与冷却最后 3 秒均有 UI 提示音。

## 默认时间

默认值采用满舰船升级：防御战备基础时间 × 0.9 × 0.95；飞鹰周转基础时间 × 0.5 × 0.95。游戏以每秒 20 tick 计时，最终取整到最近一个 tick。

| 战备 | 冷却时间（秒） |
| --- | ---: |
| 机枪哨戒炮、防护罩生成器 | 76.95 |
| 加特林、火箭、自动机炮、激光哨戒炮 | 128.25 |
| 普通、电磁、毒气迫击炮；重机枪支架、反坦克炮台 | 153.9 |
| 火焰哨戒炮 | 85.5 |
| 特斯拉塔、榴弹墙、四种地雷 | 102.6 |
| 飞鹰空袭、飞鹰机枪扫射（共享周转） | 7.125（实际 7.15） |

基础时间和使用次数来自 [Helldivers2 RawData 的 StratagemSettings](https://github.com/Darctor/Helldivers2_RawData/blob/main/Data/settings/generated_stratagem_settings.json)。升级效果参照 [Synthetic Supplementation](https://helldivers.wiki.gg/wiki/Synthetic_Supplementation)、[Morale Augmentation](https://helldivers.wiki.gg/wiki/Morale_Augmentation)、[Liquid-Ventilated Cockpit](https://helldivers.wiki.gg/wiki/Liquid-Ventilated_Cockpit) 和 [Eagle Rearm](https://helldivers.wiki.gg/wiki/Eagle_Rearm)。

## 飞鹰

- 空袭每次补给后可使用 **3 次**，机枪扫射 **5 次**，分别扣除。
- 一种战备耗尽时，其他仍有次数的飞鹰战备可以继续使用；所有携带的飞鹰战备耗尽后，自动重新武装，默认 **114 秒**。
- 携带飞鹰战备的信标球顶部自动显示“重新武装‘飞鹰’”，输入 **↑ ↑ ← ↑ →** 立即补给，无需投掷。只要携带的任一种飞鹰还有余弹，手动补给默认 **102.6 秒**。
- 重新武装期间全部飞鹰不可调用，完成后所有飞鹰战备的次数补满。已进入攻击的飞机仍会完成攻击。
- 携带范围为玩家背包及副手中所有战备信标球的已装载飞鹰战备；同一种卡片放入多个信标球仍共享同一份次数。

## 配置

世界的 `serverconfig/sbw_helldiver-server.toml` 中增加 `stratagems` 分组，服务器统一控制。在服务器停止后修改并重新启动；现有世界会自动补齐新增配置项。

```toml
[stratagems.machine_gun_sentry]
cooldownSeconds = 76.95

[stratagems.eagle_airstrike]
cooldownSeconds = 7.125
uses = 3

[stratagems.eagle_strafing_run]
cooldownSeconds = 7.125
uses = 5

[stratagems.eagle_rearm]
automaticSeconds = 114.0
manualSeconds = 102.6
```

每个战备都可分别设置 `cooldownSeconds`；`0` 表示不等待。飞鹰的 `uses` 是每次补给后的次数。调整配置不会追溯改动已经开始的冷却。

## 指令

需要管理员权限。

| 指令 | 作用 |
| --- | --- |
| `/helldivers cooldown` | 切换自己的冷却开关 |
| `/helldivers cooldown 玩家名` | 切换指定玩家的冷却开关，支持选择器 |
| `/helldivers cooldown set false` | 关闭自己的冷却 |
| `/helldivers cooldown set true 玩家名` | 开启指定玩家的冷却 |

关闭时清除正在等待的冷却和补给、补满飞鹰次数，并绕过冷却与使用次数限制。重新开启后从可用状态开始。开关随世界保存。

抵达与冷却的计时音仅在剩余 3、2、1 秒时播放；就绪提示使用独立音效。
