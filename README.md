# BoatRace

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11%20%7C%2026.1.2%20%7C%2026.2-2d2d2d)
![Server](https://img.shields.io/badge/Server-Lophine%20%2F%20Folia-4b8bbe)
![Java](https://img.shields.io/badge/Java-21%20%7C%2025-e76f00)
![PlaceholderAPI](https://img.shields.io/badge/PlaceholderAPI-optional-7b61ff)

BoatRace 是一款面向 Lophine/Folia 的冰船竞速插件。它不生成冰道地图，而是把服务器已经建好的冰船道路配置成可计时、可排名、可开房间的竞速赛道。

作者：cloudfl4re

## 功能概览

- 使用指令创建永久赛道、起点范围、记录点和人工发车位
- 通过纯比赛代码创建和加入正式比赛，不需要告示牌
- 正式比赛自动传送玩家、生成比赛船、提前上船并进行倒计时发车
- 比赛中实时显示名次、用时和下一个记录点
- 比赛船不可破坏、不会掉落，参赛船之间不会互相碰撞
- 空闲赛道支持多人自由计时，完成一圈后自动开始下一圈
- 允许直接经过更高序号的记录点，自动跳过遗漏点
- 反向经过已经完成路线中的更早记录点时，倒计时后返回最后有效记录点
- 比赛中按 Shift 不会下船，使用 `/race leave` 才能退出活动
- 粒子显示赛道范围，比赛和自由计时中只显示当前下一个记录点
- SQLite 保存赛道、自由计时最佳成绩和最近一场正式比赛
- 支持 PlaceholderAPI 前十五名占位符和总记录数量，方便制作全息图

## 环境要求

- Lophine 26.2 build 651、Lophine 26.1.2 build 638，或 Folia 1.21.11 API
- Java 25 for 26.1.2/26.2, or Java 21 for 1.21.11
- PlaceholderAPI 2.12.3 或更高版本，可选

插件默认构建目标为 `26.2`，也支持通过 Gradle 属性构建 `26.1.2` 和 `1.21.11`。三个目标均声明了 `folia-supported: true`；调度、传送、数据库和实体操作按 Folia 区域线程模型实现。

## 安装

1. 按服务端版本下载对应的 `BoatRace-1.1.0-<目标版本>.jar`。
2. 将插件放入服务端的 `plugins` 目录。
3. 如果要使用全息图榜单，另外安装 PlaceholderAPI。
4. 完整重启服务端。

不要使用热重载替代完整重启。插件数据会保存在：

```text
plugins/BoatRace/boatrace.db
plugins/BoatRace/config.yml
plugins/BoatRace/messages.yml
```

## 快速开始

### 1. 创建赛道

以下管理指令需要 `boatrace.admin` 权限，默认只有 OP 拥有。

```text
/race track create ice-loop 冰湖赛道
```

### 2. 设置起点范围

站在起点门区的一个角落执行：

```text
/race edit pos1
```

再站到另一个对角，建议把冰面高度到玩家头顶都包含进去：

```text
/race edit pos2
/race edit start
```

起点范围同时用于：

- `/race create` 识别当前赛道
- 自由计时开始
- 完成全部记录点后的冲线

### 3. 设置记录点

每个记录点都用两个对角框选，然后添加：

```text
/race edit pos1
/race edit pos2
/race edit checkpoint add
```

记录点按照添加顺序编号。第一个记录点为 `1`，第二个记录点为 `2`，以此类推。

### 4. 设置发车位

发车位和起点范围是独立配置。站在每个发车位置并面向赛道执行：

```text
/race edit slot add
```

发车位数量就是正式比赛最大人数。发车位建议放在起点线后方，并且不要互相重叠。

### 5. 保存赛道

```text
/race edit preview on
/race edit save
/race track info ice-loop
```

编辑预览会显示起点、记录点和发车位粒子。保存前必须至少设置一个起点、一个记录点和一个发车位。

### 6. 创建正式比赛

创建者站在已保存的起点范围内执行：

```text
/race create
```

插件会生成六位比赛代码。其他玩家使用代码加入：

```text
/race join <比赛代码>
```

加入成功后，玩家会自动传送到该赛道起点范围的中心；如果传送失败，加入状态会自动回滚。

创建者在开始前必须设置本场比赛圈数：

```text
/race laps <圈数>
```

创建者使用以下指令开始：

```text
/race start
```

玩家会被传送到人工发车位，自动进入比赛船并进行倒计时。比赛完成后，玩家留在终点位置。

## 箱子 GUI

使用 `/race gui` 打开可配置的冰船系统菜单。菜单会根据玩家权限显示比赛、排行榜和赛道管理入口；按钮文字、材质、Lore 和槽位位于 `plugins/BoatRace/gui/`，执行 `/race reload` 后生效。

拥有 `boatrace.gui.view` 或 `boatrace.admin` 的管理员可以使用 `/race gui view <菜单名>` 打开布局编辑视图，拖动按钮后关闭菜单即可异步保存槽位。可编辑菜单名包括 `main`、`race-control`、`track-list`、`track-editor` 和 `confirm`。

## 玩家指令

| 指令 | 说明 |
| --- | --- |
| `/race help` | 查看完整帮助 |
| `/race gui` | 打开冰船系统 GUI |
| `/race create` | 在起点范围内创建正式比赛并生成代码 |
| `/race join <代码>` | 加入指定比赛 |
| `/race laps <圈数>` | 为当前正式比赛设置圈数，开始前必须配置 |
| `/race leave` | 退出等待房间、正式比赛或自由计时 |
| `/race start` | 创建者开始比赛 |
| `/race cancel` | 创建者取消等待中的比赛 |
| `/race status` | 查看当前比赛状态、圈数以及参赛玩家名称 |
| `/race spec join <比赛代码>` | 以观察者模式加入进行中的比赛 |
| `/race spec tp <玩家名>` | 传送到本场比赛指定玩家附近观战 |
| `/race spec leave` | 离开比赛观战并返回原位置 |
| `/race rank` | 比赛结算后查看最近一场正式比赛排名；未完成玩家显示圈数和用时 |
| `/race last [赛道id]` | 查看最近一场正式比赛结果 |

Shift 下船会被拦截。比赛中需要退出时使用 `/race leave`。

观战者可以在比赛进行或发车准备阶段使用 `/race spec join <比赛代码>` 加入，并用 `/race spec tp <玩家名>` 传送到指定参赛玩家上方安全位置。观战期间保持生存模式、飞行、隐身、无敌和不可碰撞；比赛结束或准备失败时会自动恢复原位置和玩家状态。观战 ActionBar 会实时显示当前排名、玩家和圈数。

正式比赛结算后，参赛玩家使用 `/race rank` 查看排名；已完成玩家显示名次和总用时，未完成玩家显示已完成圈数、总圈数和当前用时。

## 空闲赛道自由计时

当赛道没有正式比赛房间时，玩家可以驾驶自己的船进行自由计时：

1. 从起点范围外驶入起点，开始计时。
2. 按顺序或跳过错误设置的中间记录点，插件记录当前最高序号。
3. 完成最后一个记录点后再次驶过起点，完成一圈。
4. 冲线后会立即开始下一圈，适合连续刷圈。
5. 玩家使用 `/race leave` 或离开船只可以停止自由计时。

如果已经通过较高序号记录点，又反向驶过更早的记录点，插件会显示 5 秒 Title 倒计时，然后把玩家和船送回最后一个有效记录点。

正式比赛房间创建后，该赛道不再接受新的自由计时。赛道上已有自由计时时，创建正式比赛会被拒绝；管理员可以使用 `/race force stoptrial <赛道id>` 清理。

## 管理指令

### 赛道管理

```text
/race track create <赛道id> <显示名>
/race track edit <赛道id>
/race track list
/race track info <赛道id>
/race track delete <赛道id> confirm
```

### 赛道编辑

```text
/race edit pos1
/race edit pos2
/race edit start
/race edit checkpoint add
/race edit checkpoint set <序号>
/race edit checkpoint remove <序号>
/race edit checkpoint move <原序号> <新序号>
/race edit slot add
/race edit slot remove <序号>
/race edit preview <on|off>
/race edit save
/race edit cancel
```

### 管理员比赛操作

```text
/race force laps <比赛代码> <圈数>
/race force start <比赛代码>
/race force cancel <比赛代码>
/race force stoptrial <赛道id>
/race force delete-trial <赛道id> <玩家名> confirm
/race reload
```

BoatRace 反作弊会按违规次数施加 10 分钟、30 分钟、1 小时、2 小时、4 小时和 1 天冷却。管理员也可以执行永久封禁；`true` 会向全服公告，`false` 只通知目标玩家：

```text
/race admin ban <玩家名或UUID> true
/race admin ban <玩家名或UUID> false
/race admin unban <玩家名或UUID>
```

`unban` 同时解除临时冷却和管理员永久封禁，并重置违规次数。

删除指定玩家的自由计时个人最佳成绩（需要管理员权限）：

```text
/race trial delete <赛道id> <玩家名> confirm
```

删除完成后会立即刷新内存榜单和 PlaceholderAPI，全息图会在插件下一次更新时显示新排名。玩家名按大小写不敏感匹配。

## 权限

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `boatrace.use` | 玩家 | 使用玩家侧指令 |
| `boatrace.create` | 玩家 | 创建正式比赛房间 |
| `boatrace.admin` | OP | 创建赛道、编辑赛道和管理比赛 |

## PlaceholderAPI

BoatRace 会在检测到 PlaceholderAPI 后自动注册 `boatrace` 扩展，不需要执行 `/papi ecloud download`。假设赛道 ID 为 `ice-loop`：

自由计时个人最佳圈速榜：

```text
%boatrace_ice-loop_top_1%
%boatrace_ice-loop_top_1_name%
%boatrace_ice-loop_top_1_time%
```

也可以使用明确的自由计时前缀：

```text
%boatrace_trial_ice-loop_top_1%
%boatrace_trial_ice-loop_top_1_name%
%boatrace_trial_ice-loop_top_1_time%
```

排名序号支持 `1` 至 `15`：

```text
%boatrace_ice-loop_top_1%
%boatrace_ice-loop_top_2%
%boatrace_ice-loop_top_3%
%boatrace_ice-loop_top_4%
%boatrace_ice-loop_top_5%
%boatrace_ice-loop_top_6%
%boatrace_ice-loop_top_7%
%boatrace_ice-loop_top_8%
%boatrace_ice-loop_top_9%
%boatrace_ice-loop_top_10%
%boatrace_ice-loop_top_11%
%boatrace_ice-loop_top_12%
%boatrace_ice-loop_top_13%
%boatrace_ice-loop_top_14%
%boatrace_ice-loop_top_15%
```

总记录数量：

```text
%boatrace_trial_ice-loop_records%
%boatrace_trial_ice-loop_count%
```

正式比赛最近一场已结算结果榜：

```text
%boatrace_race_ice-loop_top_1%
%boatrace_race_ice-loop_top_1_name%
%boatrace_race_ice-loop_top_1_time%
%boatrace_race_ice-loop_top_1_laps%
%boatrace_race_ice-loop_top_1_status%
```

正式比赛排名支持 `1` 至 `100`。`race` 也可以写成 `formal`。

正式比赛最近一场的参赛人数：

```text
%boatrace_race_ice-loop_records%
%boatrace_race_ice-loop_count%
```

组合榜单字段格式为：

```text
1. 玩家名 - 00:12.345
```

正式比赛未完成记录会显示为：

```text
1. 玩家名 - 未完成 1/3 圈 - 00:45.678
```

自由计时榜统计每条赛道每位玩家的个人最佳成绩，展示前 15 名；`records`/`count` 返回该赛道已有个人最佳记录数量。正式比赛结果不会写入自由计时榜。没有数据时会返回配置中的空值。

个人信息占位符（以下示例仍使用赛道 ID `ice-loop`）：

```text
%boatrace_my_trial_ice-loop_best%       # 该赛道个人最佳用时
%boatrace_my_trial_ice-loop_rank%       # 个人排名/总记录数
%boatrace_my_trial_ice-loop_name%       # 记录中的玩家名
%boatrace_my_trial_ice-loop_track%      # 赛道 ID
%boatrace_my_trial_ice-loop_count%      # 该赛道总记录数
%boatrace_my_trial_ice-loop_line%       # “名次. 玩家名 - 用时”组合文本
```

个人最佳和排名通过异步数据库查询并缓存；第一次解析尚未查到缓存时会暂时返回配置中的空值，后续解析自动显示结果，不会在 PAPI 调用线程上阻塞数据库。

上一场包含该玩家的正式比赛：

```text
%boatrace_my_last_rank%
%boatrace_my_last_time%
%boatrace_my_last_laps%
%boatrace_my_last_track%
%boatrace_my_last_status%
%boatrace_my_last_name%
%boatrace_my_last_line%
```

个人处罚与当前状态：

```text
%boatrace_my_violations%                # 累计违规次数
%boatrace_my_cooldown%                  # 冷却剩余；无冷却时返回“无”
%boatrace_my_status%                    # 空闲/等待中/准备中/倒计时/比赛中/已暂停/计时中/冷却中/已封禁等
```

实时比赛和自由计时状态：

```text
%boatrace_race_rank%
%boatrace_race_laps%
%boatrace_race_time%
%boatrace_race_gap%
%boatrace_race_ahead%
%boatrace_race_status%
%boatrace_race_track%
%boatrace_trial_time%
%boatrace_trial_status%
%boatrace_trial_track%
%boatrace_trial_checkpoint%
```

测试占位符：

```text
/papi parse me %boatrace_ice-loop_top_1%
```

如果 `/papi parse` 仍显示原始占位符，请确认服务器实际加载的是本次构建的 JAR，并完整重启服务端。启动日志应包含 `BoatRace PlaceholderAPI expansion registered`；BoatRace 也会定期检查并重新注册自己的扩展，避免 PAPI 重载后丢失。

### Holograms 全息图示例

TheNextLvl Holograms 的文本行可以直接使用下面的占位符。站在目标位置执行：

```text
/hologram create boatrace-trial-ice-loop
/hologram line add boatrace-trial-ice-loop text <gradient:#55ff55:#55ffff><bold>冰湖赛道 · 自由计时</bold></gradient>
/hologram line add boatrace-trial-ice-loop text <gray>总记录：<white>%boatrace_trial_ice-loop_records%</white></gray>
/hologram line add boatrace-trial-ice-loop text <gold>%boatrace_ice-loop_top_1%</gold>
/hologram line add boatrace-trial-ice-loop text <gray>%boatrace_ice-loop_top_2%</gray>
/hologram line add boatrace-trial-ice-loop text <color:#cd7f32>%boatrace_ice-loop_top_3%</color>
/hologram line add boatrace-trial-ice-loop text <white>%boatrace_ice-loop_top_4%</white>
/hologram line add boatrace-trial-ice-loop text <white>%boatrace_ice-loop_top_5%</white>
/hologram line add boatrace-trial-ice-loop text <white>%boatrace_ice-loop_top_6%</white>
/hologram line add boatrace-trial-ice-loop text <white>%boatrace_ice-loop_top_7%</white>
/hologram line add boatrace-trial-ice-loop text <white>%boatrace_ice-loop_top_8%</white>
/hologram line add boatrace-trial-ice-loop text <white>%boatrace_ice-loop_top_9%</white>
/hologram line add boatrace-trial-ice-loop text <white>%boatrace_ice-loop_top_10%</white>
/hologram line add boatrace-trial-ice-loop text <white>%boatrace_ice-loop_top_11%</white>
/hologram line add boatrace-trial-ice-loop text <white>%boatrace_ice-loop_top_12%</white>
/hologram line add boatrace-trial-ice-loop text <white>%boatrace_ice-loop_top_13%</white>
/hologram line add boatrace-trial-ice-loop text <white>%boatrace_ice-loop_top_14%</white>
/hologram line add boatrace-trial-ice-loop text <white>%boatrace_ice-loop_top_15%</white>
```

正式比赛榜可以使用同样的结构，把占位符替换为 `%boatrace_race_ice-loop_top_<名次>%`，参赛人数行使用 `%boatrace_race_ice-loop_records%`。

#### 个人信息全息图

TheNextLvl Holograms 官方支持 PlaceholderAPI，并会按每位观看玩家分别解析占位符，因此同一个全息图会为不同玩家显示各自的数据。以下命令可直接创建一块包含当前状态、个人最佳、排名、上一场比赛和处罚信息的面板：

```text
/hologram create boatrace-player-ice-loop
/hologram line add boatrace-player-ice-loop text <gradient:#55ffff:#5555ff><bold>我的 BoatRace 信息</bold></gradient>
/hologram line add boatrace-player-ice-loop text <gray>当前状态：<white>%boatrace_my_status%</white></gray>
/hologram line add boatrace-player-ice-loop text <gray>冰湖最佳：<aqua>%boatrace_my_trial_ice-loop_best%</aqua></gray>
/hologram line add boatrace-player-ice-loop text <gray>冰湖排名：<yellow>%boatrace_my_trial_ice-loop_rank%</yellow></gray>
/hologram line add boatrace-player-ice-loop text <gray>赛道记录数：<white>%boatrace_my_trial_ice-loop_count%</white></gray>
/hologram line add boatrace-player-ice-loop text <dark_gray>────────────</dark_gray>
/hologram line add boatrace-player-ice-loop text <gray>上一场赛道：<white>%boatrace_my_last_track%</white></gray>
/hologram line add boatrace-player-ice-loop text <gray>上一场名次：<yellow>%boatrace_my_last_rank%</yellow></gray>
/hologram line add boatrace-player-ice-loop text <gray>上一场用时：<aqua>%boatrace_my_last_time%</aqua></gray>
/hologram line add boatrace-player-ice-loop text <gray>上一场圈数：<white>%boatrace_my_last_laps%</white></gray>
/hologram line add boatrace-player-ice-loop text <gray>违规次数：<red>%boatrace_my_violations%</red></gray>
/hologram line add boatrace-player-ice-loop text <gray>冷却剩余：<white>%boatrace_my_cooldown%</white></gray>
```

把命令中的 `ice-loop` 和“冰湖”替换为实际赛道 ID 与显示名称。个人成绩使用异步缓存，玩家第一次看见全息图时可能短暂显示空值，查询完成后会由 Holograms 后续的占位符更新自动显示真实数据。

常用维护命令遵循官方 Wiki 语法：

```text
# 把全息图移动到指定玩家当前位置（将 YourMinecraftName 替换为玩家名）
/hologram teleport boatrace-player-ice-loop YourMinecraftName

# 修改第 2 行（行号以插件实际显示为准）
/hologram line edit boatrace-player-ice-loop 2 set text <gray>状态：<white>%boatrace_my_status%</white></gray>

# 删除指定行
/hologram line remove boatrace-player-ice-loop 2

# 删除整个全息图
/hologram delete boatrace-player-ice-loop
```

官方文档：[Creating Holograms](https://thenextlvl.net/docs/holograms/creating-holograms)、[Managing Lines](https://thenextlvl.net/docs/holograms/managing-lines)、[Editing Lines](https://thenextlvl.net/docs/holograms/editing-lines)。

## 配置项

| 配置项 | 默认值 | 说明 |
| --- | ---: | --- |
| `countdown-seconds` | `5` | 正式比赛发车倒计时 |
| `backtrack-countdown-seconds` | `5` | 反向路线回退倒计时 |
| `race-timeout-seconds` | `900` | 正式比赛最长时间 |
| `trial-timeout-seconds` | `900` | 单圈自由计时最长时间 |
| `lobby-idle-timeout-seconds` | `1800` | 等待房间无操作自动取消时间 |
| `particle-period-ticks` | `5` | 粒子刷新周期 |
| `particle-view-distance` | `128.0` | 粒子显示距离 |
| `race-boat` | `OAK_BOAT` | 正式比赛生成的船类型 |

消息文本位于 `messages.yml`，支持 MiniMessage 格式。升级插件时，已有自定义消息会保留，新消息键会自动使用插件内置默认值。

## 构建

一次构建三个目标版本，在 Java 25 环境中执行；构建 1.21.11 时会使用 Java 21 工具链：

```powershell
$env:GRADLE_OPTS='-Dhttps.proxyHost=localhost -Dhttps.proxyPort=7897 -Dhttp.proxyHost=localhost -Dhttp.proxyPort=7897'
.\gradlew.bat clean build
```

构建完成后会同时生成：

```text
build/libs/BoatRace-1.1.0-26.2.jar
build/libs/BoatRace-1.1.0-26.1.2.jar
build/libs/BoatRace-1.1.0-1.21.11.jar
```

如果只需要构建单个目标，可以使用对应的 `targetPlatform`：

```powershell
# Lophine 26.1.2，Java 25
.\gradlew.bat clean test shadowJar "-PtargetPlatform=26.1.2"

# Folia/Lophine 1.21.11，Java 21
.\gradlew.bat clean test shadowJar "-PtargetPlatform=1.21.11"
```

上述三个 JAR 都是正式插件文件，按服务器版本选择对应文件安装。

`integrationHarnessJar` 只用于 Lophine 假人验收，不需要安装到正式服务器。

## 常见问题

### `/race create` 提示不在起点范围

`/race create` 不会创建赛道，只会创建比赛房间。请先完成赛道编辑并执行 `/race edit save`，然后站在起点长方体内部再创建比赛。

发车位不等于起点范围；发车位只决定正式比赛的传送位置。

### 记录点没有触发

确认船的移动线段确实穿过记录点长方体，并检查记录点所在世界和高度。编辑时可开启：

```text
/race edit preview on
```

### 想退出比赛

比赛中不能使用 Shift 下船，请执行：

```text
/race leave
```

### 想停止卡住的自由计时

管理员执行：

```text
/race force stoptrial <赛道id>
```

## 关于

BoatRace 由 cloudfl4re 制作，面向 Lophine/Folia 区域线程服务端设计。
