# BoatRace

![Minecraft](https://img.shields.io/badge/Minecraft-26.2-2d2d2d)
![Server](https://img.shields.io/badge/Server-Lophine%20%2F%20Folia-4b8bbe)
![Java](https://img.shields.io/badge/Java-25-e76f00)
![PlaceholderAPI](https://img.shields.io/badge/PlaceholderAPI-optional-7b61ff)

BoatRace 是一款面向 Lophine 26.2 的 Folia 兼容冰船竞速插件。它不生成冰道地图，而是把服务器已经建好的冰船道路配置成可计时、可排名、可开房间的竞速赛道。

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
- 支持 PlaceholderAPI 前七名占位符，方便制作全息图

## 环境要求

- Lophine 26.2 build 651
- Java 25
- PlaceholderAPI 2.12.3 或更高版本，可选

插件目标 API 为 `26.2`，并声明了 `folia-supported: true`。调度、传送、数据库和实体操作按 Folia 区域线程模型实现。

## 安装

1. 下载 `BoatRace-1.1.0.jar`。
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

创建者使用以下指令开始：

```text
/race start
```

玩家会被传送到人工发车位，自动进入比赛船并进行倒计时。比赛完成后，玩家留在终点位置。

## 玩家指令

| 指令 | 说明 |
| --- | --- |
| `/race help` | 查看完整帮助 |
| `/race create` | 在起点范围内创建正式比赛并生成代码 |
| `/race join <代码>` | 加入指定比赛 |
| `/race leave` | 退出等待房间、正式比赛或自由计时 |
| `/race start` | 创建者开始比赛 |
| `/race cancel` | 创建者取消等待中的比赛 |
| `/race status` | 查看当前比赛状态和人数 |
| `/race last [赛道id]` | 查看最近一场正式比赛结果 |

Shift 下船会被拦截。比赛中需要退出时使用 `/race leave`。

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
/race force start <比赛代码>
/race force cancel <比赛代码>
/race force stoptrial <赛道id>
/race reload
```

## 权限

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `boatrace.use` | 玩家 | 使用玩家侧指令 |
| `boatrace.create` | 玩家 | 创建正式比赛房间 |
| `boatrace.admin` | OP | 创建赛道、编辑赛道和管理比赛 |

## PlaceholderAPI

假设赛道 ID 为 `ice-loop`，可使用以下占位符：

```text
%boatrace_ice-loop_top_1%
%boatrace_ice-loop_top_1_name%
%boatrace_ice-loop_top_1_time%
```

排名序号支持 `1` 至 `7`：

```text
%boatrace_ice-loop_top_1%
%boatrace_ice-loop_top_2%
%boatrace_ice-loop_top_3%
%boatrace_ice-loop_top_4%
%boatrace_ice-loop_top_5%
%boatrace_ice-loop_top_6%
%boatrace_ice-loop_top_7%
```

组合字段格式为：

```text
1. 玩家名 - 00:12.345
```

该榜单只统计自由计时的个人最佳成绩，正式比赛结果不会写入自由计时榜单。

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

在 Java 25 环境中执行：

```powershell
$env:GRADLE_OPTS='-Dhttps.proxyHost=localhost -Dhttps.proxyPort=7897 -Dhttp.proxyHost=localhost -Dhttp.proxyPort=7897'
.\gradlew.bat clean test shadowJar
```

正式插件位于：

```text
build/libs/BoatRace-1.1.0.jar
```

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

BoatRace 由 cloudfl4re 制作，面向 Lophine 26.2/Folia 区域线程服务端设计。
