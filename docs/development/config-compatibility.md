# 配置兼容迁移

Graven 使用 `%USERPROFILE%/.graven/configs/<profile>/` 保存配置。改名以前的 `.epsilon` 数据仍作为迁移源，不要求用户手动重建配置。

## 迁移顺序

`ConfigHolder.initConfig()` 按以下顺序处理：

1. 将 `.epsilon` 中缺少的文件复制到 `.graven`，不覆盖已经存在的新文件。
2. 对每个配置 profile，将旧 owner 目录 `epsilon` 中缺少的模块文件补入新 owner 目录 `graven`。
3. 读取旧版 `config.json`（若存在）并拆分为当前的模块文件格式。
4. 迁移 `.epsilon/friends.json`、`.epsilon/configs/default/friends.json` 以及重命名过渡期的 `.graven/friends.json`。
5. 后续写入只使用 `.graven` 和 `graven` owner；旧目录保留为备份源。

迁移是文件级的、幂等的，并且新文件优先，因此不会覆盖用户已经在 Graven 中保存的修改。模块 JSON 的 `version`、`enabled`、`keyBind`、`bindMode`、`hidden`、`settings` 和自定义状态字段保持原 schema。

## 兼容边界

- 旧 profile 名称和模块文件名保持不变。
- 内置 owner 从 `epsilon` 映射到 `graven`；第三方 Addon owner 不自动改名。
- 路径复制前执行规范化和目录边界校验，拒绝逃逸目标目录。
- 迁移失败会保留源文件并记录日志，下次启动可以重试。
