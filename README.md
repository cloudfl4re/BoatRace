# BoatRace

BoatRace 是面向 Lophine 26.2 的 Folia 兼容冰船竞速插件。

## 环境

- Lophine 26.2 build 651
- Java 25
- PlaceholderAPI 2.12.3 或更高版本，可选

## 功能

- 指令配置永久赛道、起点长方体、顺序记录点和人工发车位
- 通过六位比赛代码创建、加入和开始正式比赛
- 空闲赛道多人连续单圈计时
- 允许直接触发更高序号记录点并自动跳过遗漏记录点
- 反向穿过更早记录点时倒计时返回最后有效记录点
- 比赛中拦截 Shift 下船，仅允许 `/race leave` 退出
- 每条赛道自由计时前七名 PlaceholderAPI 占位符
- Folia 实体、区域、全局和异步调度隔离
- SQLite 持久化赛道、最佳成绩和最近正式比赛

## 安装

将 `BoatRace-1.1.0.jar` 放入服务端 `plugins` 目录并完整重启服务端。

## 构建

在 Java 25 环境中执行：

```powershell
$env:GRADLE_OPTS='-Dhttps.proxyHost=localhost -Dhttps.proxyPort=7897 -Dhttp.proxyHost=localhost -Dhttp.proxyPort=7897'
.\gradlew.bat clean test shadowJar
```

正式插件位于 `build/libs/BoatRace-1.1.0.jar`。`integrationHarnessJar` 仅用于隔离服假人验收，不会进入正式插件。

## 玩家指令

- `/race create`
- `/race join <比赛代码>`
- `/race leave`
- `/race start`
- `/race cancel`
- `/race status`
- `/race last [赛道id]`

## 管理指令

- `/race track create <赛道id> <显示名>`
- `/race track edit <赛道id>`
- `/race track list`
- `/race track info <赛道id>`
- `/race track delete <赛道id> confirm`
- `/race edit pos1`
- `/race edit pos2`
- `/race edit start`
- `/race edit checkpoint add`
- `/race edit checkpoint set <序号>`
- `/race edit checkpoint remove <序号>`
- `/race edit checkpoint move <原序号> <新序号>`
- `/race edit slot add`
- `/race edit slot remove <序号>`
- `/race edit preview <on|off>`
- `/race edit save`
- `/race edit cancel`
- `/race force start <比赛代码>`
- `/race force cancel <比赛代码>`
- `/race force stoptrial <赛道id>`
- `/race reload`

## 权限

- `boatrace.use`
- `boatrace.create`
- `boatrace.admin`

## PlaceholderAPI

赛道 ID 为 `ice-loop` 时：

- `%boatrace_ice-loop_top_1%`
- `%boatrace_ice-loop_top_1_name%`
- `%boatrace_ice-loop_top_1_time%`

排名数字支持 1 至 7。正式比赛结果不进入该榜单。
