# 0.7.4-perf.3 模型修复

本轮以 0.7.4-perf.2 为基础，继续使用已确认的炮台减面修正版。火焰炮台仅修复隔热炮管及侧板浅凹槽；特斯拉仅恢复控制面板相关几何，其余部位继续保留减面。

迫击炮的无几何父骨骼须同步清空旧网格；空投仓活动展开板的各面不得作为静态遮挡面裁去。重机枪支架只绘制连续蒙皮弹链，隐藏原生刚性副本。

激光屏幕和重机枪弹链使用独立的附属网格文件。最终贴图压缩完成后，运行 `tools/export_attached_meshes.py`，从最终几何重新生成两者的贴图坐标；不得继续使用压缩前的坐标。常规和低画质模型同步保留完整骨架，远距离贴图同步更新。

反坦克及重机枪支架保留前后两个客户端瞄准采样，渲染时进行逐帧插值，避免同步数据提前到达造成插值端点相同。

源码与运行时资源摘要见 `runtime-repair-074-3.json`。

---

This update retains the reviewed reduced models from 0.7.4-perf.2, with localized flame barrel, side-panel and Tesla control-panel repairs.

Empty mortar rig parents must discard stale aggregate meshes. Animated Hellpod flap faces must remain available throughout deployment. The HMG uses one continuous skinned belt and suppresses its rigid duplicates.

After final atlas packing, run `tools/export_attached_meshes.py` to regenerate laser-display and HMG-belt UVs from the final runtime geometry. Keep normal, distant and low-quality resources synchronized.

Manned emplacements retain consecutive client aim samples for per-frame interpolation. Source and runtime resource hashes are recorded in `runtime-repair-074-3.json`.
