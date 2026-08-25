# BoatRace 1.21.x / 26.x Folia 兼容设计

## 目标

让同一个 BoatRace JAR 同时运行于 Minecraft 1.21.x Folia 与 26.x Folia 核心。

## 方案

以 Paper/Folia 1.21 API 作为编译基线，Java 编译目标设为 21。源码只使用两代核心共同提供的 Bukkit、Paper 与 Folia 调度接口；26.x 运行时依赖其对 1.21 API 的向后兼容，不引入 26.x 独有类型。

## 构建与元数据

- 将编译期服务端 API 替换为 Paper 1.21.11 API。
- Java toolchain 与 `--release` 统一为 21，确保可在 Java 21（1.21 Folia 常用运行时）启动。
- `plugin.yml` 的 `api-version` 调整为 `1.21`，避免 1.21 服务端拒绝加载；26.x 仍可加载旧版本插件元数据。
- 更新 README 的支持矩阵、构建命令和运行时要求。

## 调度兼容

保留现有 `SchedulerFacade` 的 Folia 区域/实体/全局/异步调度抽象，并通过 Paper 的 `ServerBuildInfo` 检测 Folia。非 Folia 分支继续使用 Bukkit 调度器，便于本地测试。

## 验证

运行单元测试与 shadow JAR 构建，确认 Java 21 编译、资源展开和现有测试全部通过。使用 `javap`/依赖解析检查产物不包含服务端 API 类。
