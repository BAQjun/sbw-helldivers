# 模型质量 / Model quality

在主菜单或暂停菜单打开“模组”，选择 **SBW Helldivers → 配置**，即可切换 **低质量模型**。默认关闭，使用已优化的精细模型：清理完全遮挡的面、退化三角形与重复属性数据，并合并同色共面细节，保留原有可见外形与曲面精度。点击“保存”后重新加载资源，场上的模型也会切换；取消或按 Esc 不会保存更改。

此开关仅影响当前客户端的炮台、空投仓、地雷、迫击炮弹与飞鹰模型。原有贴图、动画、伤害、碰撞与服务器规则保持一致，不要求其他玩家采用相同设置。实际性能收益取决于场景、设备和光影。

也可以在关闭游戏后编辑 `config/sbw_helldiver-client.toml` 中的 `lowQualityModels`。设置为 `true` 使用低质量模型，设置为 `false` 使用原精度模型。

Open **Mods → SBW Helldivers → Config** from the main or pause menu, then toggle **Low Quality Models**. It is off by default. The default full-detail models remove fully hidden faces, degenerate triangles, and duplicate attribute records, and merge uniformly colored coplanar details while retaining visible surfaces and curved-surface precision. **Save** reloads resources and updates existing models; **Cancel** or Esc discards unsaved changes.

The setting affects only local turret, Hellpod, mine, mortar round and Eagle geometry. Textures, animations, damage, collisions and server rules stay the same. Other players can use a different quality setting. Performance gains depend on the scene, hardware and shaders.

Alternatively, close the game and edit `lowQualityModels` in `config/sbw_helldiver-client.toml`: `true` enables reduced models; `false` uses full detail.

落地空投仓会根据当前地形跳过完全埋在实心方块内的几何。这些部分仍保存在完整模型中；挖开地面后会在下一次绘制时恢复显示。空投下落、光影阴影通道以及观察者位于遮挡体积内时不使用这项跳过规则。

Landed Hellpods skip geometry fully enclosed by the current solid terrain. The complete geometry remains in the model and is rendered again on the next render pass after excavation. This rule does not apply during descent, in shader shadow passes, or when the camera is inside the occluding volume.
