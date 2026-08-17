# NameTags 文本指标崩溃修复

## 根因

`NameTags` 使用 `WorldToScreen.calcScale(anchor)` 生成 Lumin 文本缩放。切换服务器或渲染帧边界期间，摄像机投影矩阵/目标深度可能暂时产生 `0`、负数、无穷或 `NaN`。Lumin `UiTextMetrics` 要求文本和缩放值必须有效且缩放大于零，原实现直接调用 `textHeight`，因此触发 `IllegalArgumentException: text metric input is invalid`。

## 修复

- `WorldToScreen.calcWorld2Screen` 过滤非有限屏幕坐标和深度。
- `WorldToScreen.calcScale` 校验深度、投影矩阵缩放和最终结果，异常时返回 `0`。
- `NameTags` 在创建文本布局前跳过非有限或非正的最终缩放。

正常投影和标签布局保持不变；仅异常摄像机帧中的标签被丢弃，下一帧自动恢复。

## 验证

使用 Microsoft JDK 25 执行：

```text
gradlew.bat :common:compileJava --no-daemon --console=plain
gradlew.bat :fabric:compileJava --no-daemon --console=plain
```
