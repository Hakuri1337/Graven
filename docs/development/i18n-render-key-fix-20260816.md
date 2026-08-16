# Render 翻译键修复

## 根因

Render 模块的语言节点从 `graven.modules` 提前闭合后被写到了 `graven` 下，因此模块生成的 `graven.modules.*` 键无法命中。两份内置语言文件尾部也缺少根对象闭合，资源重载解析会提前失败。

## 修复

- 将 Render 模块节点保持在 `graven.modules` 对象内。
- 补齐 `en_us.json` 和 `zh_cn.json` 的根对象闭合。
- 语言管理器在首次资源重载前从 classpath 读取内置表，并保留 ResourceManager 资源优先级。
- 翻译组件按语言表修订号刷新缓存，保证重载后已创建组件同步更新。

## 验证

- `python -m json.tool common/src/main/resources/assets/graven/i18n/en_us.json`
- `python -m json.tool common/src/main/resources/assets/graven/i18n/zh_cn.json`
- `common/src/main/resources/assets/graven/i18n/en_us.json` 解析到 `graven.modules.anti alias` 和 `graven.elements.dynamic island.release type`。
- `:common:compileJava` 构建成功。
