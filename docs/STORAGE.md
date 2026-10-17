# 备份与本地清理

Git 管理源码、运行资源、当前模型源文件与地图制作脚本；Git LFS 管理 `.bbmodel` 和 `.geo.json`。GitHub Release `v0.3.72` 保存当前可安装产物。历史资料迁移到私有归档 Release，按路径清单和 SHA-256 校验恢复。

归档为去重 ZIP 对象库：`manifest.json` 中每个原路径对应一个 SHA-256 对象。恢复工具见 `tools/restore_workspace.py`。下载全部归档分卷后可恢复指定路径或目录；恢复命令不会覆盖现有不同内容文件。

后续可删除 `build/`、`bin/`、`.gradle/`、`.kotlin/` 以及人工建立的临时测试输出。游戏存档、当前模型、音频原始库和有用工具应保留；历史版本需要时从 Git 标签或归档 Release 取回。

## 本次归档

私有附件地址：https://github.com/BAQjun/sbw-helldivers/releases/tag/workspace-archive-2026-10-17

21,407 个历史文件约 16.649 GiB，通过分离 ZIP/JAR 的原始压缩记录和重复内容去重，生成 30 个归档分卷，合计约 3.748 GiB。每个原文件都逐一重建并验证 SHA-256；也使用公开在本仓库的恢复工具实际恢复了一个旧版完整 JAR。

生成物、测试浏览器配置和测试截图不作为历史源文件保留；原始音频库、制作工具、当前交付、游戏主存档和 D 盘离线依赖缓存保留。
