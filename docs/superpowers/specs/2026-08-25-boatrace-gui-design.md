# BoatRace 全功能 GUI 设计规格

## 目标

为 BoatRace 增加一个通过 `/race gui` 打开的可配置箱子 GUI，主菜单标题为“冰船系统”，覆盖现有比赛、自由计时、赛道管理和排行榜功能；管理员可以通过 `/race gui view <菜单名>` 拖动整理菜单布局，关闭编辑视图时自动保存槽位配置。

## 范围与现有功能映射

GUI 只包装项目已有业务，不新增跨服、经济、物品奖励或其他无关功能。现有指令映射如下：

- 玩家比赛：`create`、`join`、`start`、`leave`、`cancel`、`status`、`last`。
- 赛道管理：`track create|edit|list|info|delete`。
- 场地编辑：`edit pos1|pos2|start`、`checkpoint add|set|remove|move`、`slot add|remove`、`preview on|off`、`save`、`cancel`。
- 管理操作：`force start|cancel|stoptrial`、比赛暂停/继续/结束。

## 页面结构

所有页面放在 `plugins/BoatRace/gui/`，每页独立 YAML：

- `main.yml`：标题“冰船系统”，创建、加入、开始、退出、取消、状态、最近比赛、排行榜和管理员入口。
- `race-control.yml`：标题“冰船系统 · 比赛控制”，当前房间、参赛者、暂停、继续、结束、取消；仅当前比赛成员查看，控制按钮需要 `boatrace.admin`。
- `track-list.yml`：标题“冰船系统 · 赛道管理”，赛道列表、赛道信息、创建、编辑、删除入口。
- `track-editor.yml`：标题“冰船系统 · 场地编辑”，位置 1/2、起点、记录点、发车位、预览、保存、取消。
- `confirm.yml`：标题“冰船系统 · 操作确认”，结束比赛、删除赛道等不可逆操作的确认页。

菜单采用 PlayerMenu 风格的槽位配置，但使用 BoatRace 自己的解析器：

```yaml
title: "冰船系统"
size: 54
permission: "boatrace.gui"
menu:
  "20":
    index: 20
    name: "<aqua><bold>比赛排行榜</bold></aqua>"
    material: GOLD_INGOT
    lore:
      - "<gray>查看当前比赛排名。</gray>"
      - "<yellow>点击查看</yellow>"
    action: "race.leaderboard"
    permission: "boatrace.gui.race"
```

字段要求：`index` 为 0–53 的槽位；`material` 必须是有效 Bukkit 材质；`name`、`lore` 支持 MiniMessage、`&` 和 `§`；`action` 只能使用内置白名单动作；无效动作、材质或槽位输出中文警告并跳过该按钮，不得导致服务器崩溃。

## 分隔板

每个 GUI 的分隔板使用用户指定的固定字段，不覆盖用户自定义的布局：

```yaml
name: §e✧ 闪闪发光的边框 ✧
material: WHITE_STAINED_GLASS_PANE
lore:
  - §7嘿嘿嘿～
  - §7戳我干嘛呀小坏蛋～
isEnchant: false
custom-model-data: 0
hideFlag: true
hideEnchant: true
```

解析时关闭分隔板名称和 Lore 的斜体。分隔板不绑定 action，点击始终取消。

## 权限

- `boatrace.gui`：打开 `/race gui`，默认 `true`。
- `boatrace.gui.race`：查看比赛、排行榜和当前房间，默认 `true`。
- `boatrace.gui.control`：暂停、继续、结束、取消比赛，默认 `op`。
- `boatrace.gui.track`：创建、编辑、保存、删除赛道，默认 `op`。
- `boatrace.gui.view`：使用 `/race gui view <菜单名>` 整理布局，默认 `op`。
- `boatrace.admin`：管理员总权限，继续作为已有管理权限节点并默认 `op`。

`boatrace.admin` 在权限判断中覆盖三个管理员 GUI 权限，但不改变普通玩家的比赛与排行榜可见性。

## 比赛控制行为

- 暂停：冻结比赛计时、比赛船控制、记录点判定和倒计时任务；状态写入当前会话。
- 继续：恢复暂停前的阶段与计时基准，不重置已通过记录点。
- 结束：必须打开确认页；确认后未完成玩家统一标记 DNF，释放比赛船和房间资源，并保存本场结果。
- 取消：等待中的房间直接取消；进行中的比赛沿用现有退出/清理逻辑，不绕过资源释放。
- 排行榜：当前比赛参与者可查看，内容从现有比赛状态和结果服务读取。
- 第一名冲过终点时，在其实体所在区域播放一次烟花效果；烟花由当前比赛参与者可见，后续玩家冲线不重复触发。烟花生成在实体所有者上下文执行，不阻塞比赛流程。
- 比赛倒计时每秒通过 Title 显示剩余时间，副标题显示“准备出发”；最后一秒使用不同音高。正式开始时通过 Title 显示“出发！”，播放开始音效。音效名称进入 `config.yml`，无效音效回退到安全默认值。
- 第一名冲线同时播放一次首名音效和烟花；其他玩家冲线只显示个人成绩和普通完成音效，不播放烟花。
- 比赛开始后使用 ActionBar 实时显示当前第一名；至少每 5 tick 刷新一次，第一名变化立即反映。没有玩家完成记录时显示“暂时没有冲线记录”。ActionBar 只用于实时排名，不用于倒计时主提示。
- 比赛最终结束时向全服公屏广播冠军；有完成者广播第一名玩家和用时，无完成者广播本场无人完成。广播只触发一次，并通过全局调度器执行。

## 场地编辑行为

管理员站到目标位置后点击 GUI 按钮记录当前位置。所有位置操作复用现有 `EditorManager`：

- 设置选择点 1、设置选择点 2。
- 保存起点范围。
- 添加、更新、删除、移动记录点。
- 添加、删除发车位。
- 开关粒子预览。
- 保存或取消草稿。

GUI 不直接拼接 SQL 或修改赛道模型；它只解析点击动作并调用已有服务。

## 管理员布局编辑

`/race gui view <菜单名>` 打开指定 YAML 的编辑视图。编辑视图具有独立 `LAYOUT_EDITOR` 类型：

1. 加载菜单按钮和固定分隔板，并给按钮写入不可见的内部标识。
2. 点击、拖动和交换只改变当前 Inventory 槽位，不执行比赛 action。
3. 关闭 GUI 时读取槽位，将按钮 `index` 按当前布局顺序写回对应 YAML。
4. 新增按钮或无法识别的物品不自动转为任意 action；仅保留原有已注册按钮。
5. 保存成功后重新加载对应页面；失败时保留旧文件并向管理员提示原因。
6. 编辑会话关闭、退出、死亡和插件禁用时清理会话锁。

布局保存采用原子临时文件替换，避免服务器关闭时产生半写入 YAML。配置文件 IO 在异步线程执行，保存结果通过玩家实体调度器反馈。

## GUI 动作白名单

动作以 Java 枚举/注册表形式定义，至少包括：`open-main`、`open-race-control`、`open-track-list`、`open-track-editor`、`open-confirm`、`race-create`、`race-join`、`race-start`、`race-leave`、`race-cancel`、`race-status`、`race-last`、`race-leaderboard`、`race-pause`、`race-resume`、`race-end`、`track-create`、`track-info`、`track-edit`、`track-delete`、`edit-pos1`、`edit-pos2`、`edit-start`、`checkpoint-add`、`checkpoint-set`、`checkpoint-remove`、`checkpoint-move`、`slot-add`、`slot-remove`、`preview-toggle`、`edit-save`、`edit-cancel`、`back`、`close`。

未注册 action 只记录中文警告并取消点击，禁止执行 YAML 中的任意命令、脚本或反射调用。

## Folia 与生命周期

- GUI 打开、点击、拖动、关闭和 Inventory 修改必须在玩家实体所有者上下文执行。
- YAML 和数据库读写通过现有统一调度器异步执行，完成后回到玩家实体上下文更新页面或发送消息。
- GUI 会话使用玩家 UUID，不长期持有 Player、Inventory 或旧插件实例。
- 所有重复任务、确认超时、编辑锁和页面引用在关闭、退出、死亡、reload 和 onDisable 时清理。
- 不使用同步等待、跨 Region 锁或同步 teleport。

## 错误处理

- 菜单文件缺失、布局大小非法、槽位越界、材质无效、缺少 action 或权限配置错误时记录明确中文日志。
- 单个按钮配置错误只跳过该按钮，其他菜单继续显示。
- 结束比赛、删除赛道等危险操作缺少确认上下文时拒绝执行。
- 数据库或 YAML 保存失败时继续保留旧状态，不向玩家暴露堆栈。

## 视觉与听觉美化

- 主菜单标题固定为纯文本 `冰船系统`，不附加颜色代码；子页面可以使用简短中文副标题。
- GUI 按钮使用冰蓝、淡紫、深蓝主题，普通功能 Lore 说明用途和点击结果，危险操作使用红色风险提示。
- 比赛中使用聊天消息、Title、实时排名 ActionBar、粒子和音效反馈；ActionBar 只承载当前第一名信息。
- 默认音效包括倒计时音符盒、发车音符盒、GUI 点击、首名冲线和比赛结束；所有音效均可在配置中替换。

## 测试要求

- YAML 菜单默认生成、缺失节点补全和 reload 保留用户配置。
- 槽位范围、菜单大小、材质和 action 白名单校验。
- 分隔板固定字段、非斜体和不可点击行为。
- 权限过滤：普通玩家可见比赛/排行榜，管理员可见控制和场地设置。
- 暂停/继续冻结与恢复计时、船、记录点和倒计时。
- 结束确认后 DNF、资源释放和结果保存。
- 布局编辑拖动、关闭保存、异常回滚和会话清理。
- MiniMessage、`&`、`§` 文本解析。
- Folia 调度扫描、任务取消和插件关闭清理。
