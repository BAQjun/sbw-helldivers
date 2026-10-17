# 备份与本地清理

Git 管理源码、运行资源、当前模型源文件与地图制作脚本；Git LFS 管理 `.bbmodel` 和 `.geo.json`。GitHub Release `v0.3.72` 保存当前可安装产物。历史资料迁移到私有归档 Release，按路径清单和 SHA-256 校验恢复。

归档为去重 ZIP 对象库：`manifest.json` 中每个原路径对应一个 SHA-256 对象。恢复工具见 `tools/restore_workspace.py`。下载全部归档分卷后可恢复指定路径或目录；恢复命令不会覆盖现有不同内容文件。

后续可删除 `build/`、`bin/`、`.gradle/`、`.kotlin/` 以及人工建立的临时测试输出。游戏存档、当前模型、音频原始库和有用工具应保留；历史版本需要时从 Git 标签或归档 Release 取回。
