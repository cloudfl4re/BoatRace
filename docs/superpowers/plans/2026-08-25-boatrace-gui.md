# BoatRace 全功能 GUI 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 增加以“冰船系统”为主菜单标题的全功能箱子 GUI、管理员布局编辑器、比赛暂停/继续/结束控制和第一名烟花效果。

**架构：** GUI 配置、解析、页面状态、动作注册、点击分发和布局保存分别由独立组件负责。GUI 只调用现有 `RaceManager`、`EditorManager`、`TrackService`、`LeaderboardService`，不在菜单层复制比赛业务。管理员布局编辑视图使用独立 Holder 标识，拖动只修改槽位，关闭时异步原子保存 YAML。

**技术栈：** Paper API 1.21.11、Folia 调度、Bukkit Inventory API、Adventure MiniMessage/Legacy Serializer、SnakeYAML 通过 Bukkit `YamlConfiguration`、JUnit 5。

---

## 文件职责

- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiMenuConfig.java`，不可变菜单配置与按钮定义。
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiConfigService.java`，`plugins/BoatRace/gui/*.yml` 的生成、加载、校验、reload 和原子保存。
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiHolder.java`，区分普通页面、确认页和布局编辑页。
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiSession.java`，按玩家 UUID 保存当前页面、确认上下文和编辑状态。
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiAction.java`，白名单 action 枚举与动作元数据。
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiService.java`，页面创建、按钮渲染、动态比赛/赛道/排行榜内容和返回栈。
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiListener.java`，点击、拖动、关闭、退出、死亡事件处理。
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiLayoutEditor.java`，管理员 `view` 编辑视图和槽位保存。
- 修改：`src/main/java/cn/cloudfl4re/boatrace/command/RaceCommand.java`，增加 `gui` 与 `gui view <menu>` 入口及补全。
- 修改：`src/main/java/cn/cloudfl4re/boatrace/BoatRacePlugin.java`，装配 GUI 服务并注册监听器。
- 修改：`src/main/java/cn/cloudfl4re/boatrace/model/RacePhase.java`，增加 `PAUSED` 阶段。
- 修改：`src/main/java/cn/cloudfl4re/boatrace/model/RaceSession.java`，增加暂停时间、暂停前阶段、pause/resume、运行时间快照和一次性首名状态。
- 修改：`src/main/java/cn/cloudfl4re/boatrace/service/RaceManager.java`，增加暂停/继续/管理员结束、暂停期间判定保护、首名烟花和 GUI 所需快照方法。
- 修改：`src/main/java/cn/cloudfl4re/boatrace/service/EditorManager.java`，补充 GUI 对当前位置、记录点、发车位和保存取消动作的安全调用入口。
- 修改：`src/main/resources/plugin.yml`，声明 `gui` usage、GUI 权限节点。
- 创建：`src/main/resources/gui/main.yml`、`race-control.yml`、`track-list.yml`、`track-editor.yml`、`confirm.yml`。
- 修改：`src/main/resources/messages.yml`，增加 GUI 标题、按钮反馈、权限、暂停、Title、烟花和布局保存消息。
- 修改：`src/main/resources/config.yml`，增加 GUI 点击、倒计时、发车、首名冲线和比赛结束音效配置。
- 修改：`src/test/java/cn/cloudfl4re/boatrace/model/RaceSessionTest.java`，暂停/继续/首名状态测试。
- 创建：`src/test/java/cn/cloudfl4re/boatrace/gui/GuiConfigServiceTest.java`，菜单 YAML、槽位和 action 校验测试。
- 创建：`src/test/java/cn/cloudfl4re/boatrace/gui/GuiLayoutEditorTest.java`，布局移动、保存和异常回滚测试。

---

### 任务 1：建立权限、资源和 GUI 配置骨架

**文件：**
- 修改：`src/main/resources/plugin.yml`
- 创建：`src/main/resources/gui/main.yml`
- 创建：`src/main/resources/gui/race-control.yml`
- 创建：`src/main/resources/gui/track-list.yml`
- 创建：`src/main/resources/gui/track-editor.yml`
- 创建：`src/main/resources/gui/confirm.yml`
- 修改：`src/main/resources/messages.yml`

- [ ] **步骤 1：写入权限与命令声明**

在 `plugin.yml` 中将 usage 改为 `/race help`，增加 `boatrace.gui`（默认 true）、`boatrace.gui.race`（默认 true）、`boatrace.gui.control`、`boatrace.gui.track`、`boatrace.gui.view`（后三者默认 op），并保留 `boatrace.admin`。

- [ ] **步骤 2：创建主菜单 YAML**

`main.yml` 使用 `title: "冰船系统"`、`size: 54`、`permission: boatrace.gui`，按钮 action 只使用 `open-race-control`、`race-create`、`race-join`、`race-status`、`race-last`、`race-leaderboard`、`race-leave`、`open-track-list`、`close`；分隔板严格写入用户指定的 `name/material/lore/isEnchant/custom-model-data/hideFlag/hideEnchant` 字段。

- [ ] **步骤 3：创建比赛、赛道、确认页面 YAML**

分别写入 `race-control.yml`、`track-list.yml`、`track-editor.yml`、`confirm.yml`，所有功能按钮包含中文名称、用途 Lore、点击结果 Lore、危险操作红色风险提示和对应权限。

- [ ] **步骤 4：增加全部 GUI 消息键**

在 `messages.yml` 增加页面打开、无权限、无活动比赛、暂停成功、继续成功、结束确认、结束成功、冠军公屏广播、无人完成公屏广播、首名烟花、倒计时 Title 主标题/副标题、发车 Title 主标题/副标题、实时第一名 ActionBar、无冲线记录提示、编辑保存、编辑回滚、无效 action 和配置错误消息；所有玩家文本只从消息文件读取。

- [ ] **步骤 4.1：增加比赛反馈音效配置**

在 `config.yml` 增加并写中文注释：`sounds.gui-click: UI_BUTTON_CLICK`、`sounds.countdown: BLOCK_NOTE_BLOCK_HAT`、`sounds.countdown-final: BLOCK_NOTE_BLOCK_PLING`、`sounds.race-start: BLOCK_NOTE_BLOCK_PLING`、`sounds.first-finish: ENTITY_PLAYER_LEVELUP`、`sounds.race-finish: ENTITY_PLAYER_LEVELUP`、`sounds.race-end: BLOCK_NOTE_BLOCK_BASS`。无效值回退到对应默认值并记录中文警告。

- [ ] **步骤 5：运行资源检查**

运行 `./gradlew processResources`，预期所有 GUI YAML 被复制到 `build/resources/main/gui/`，资源处理成功。

- [ ] **步骤 6：Commit**

运行 `git add src/main/resources/plugin.yml src/main/resources/gui src/main/resources/messages.yml && git commit -m "feat: add BoatRace GUI configuration resources"`。

### 任务 2：实现菜单配置解析与动作白名单

**文件：**
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiAction.java`
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiMenuConfig.java`
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiConfigService.java`
- 创建：`src/test/java/cn/cloudfl4re/boatrace/gui/GuiConfigServiceTest.java`

- [ ] **步骤 1：先写失败测试**

覆盖 `size` 只能是 9 的倍数且范围 9–54、`index` 必须在菜单范围内、`material` 无效时跳过按钮、未注册 action 被拒绝、缺失 GUI 文件从默认资源生成、分隔板固定字段完整保留。

- [ ] **步骤 2：运行测试确认失败**

运行 `./gradlew test --tests cn.cloudfl4re.boatrace.gui.GuiConfigServiceTest`，预期因 GUI 类尚未存在而失败。

- [ ] **步骤 3：实现不可变配置和校验**

`GuiAction` 仅列出规格中的内置动作；`GuiMenuConfig.Button` 保存 slot、material、MiniMessage 文本、Lore、action、permission、confirm 标志；`GuiConfigService` 使用 `YamlConfiguration` 读取并返回不可变快照，单个按钮错误只记录中文警告。

- [ ] **步骤 4：实现默认资源生成和 reload**

首次启动创建 `dataFolder/gui` 并复制五个默认 YAML；reload 只补齐缺失文件，不覆盖已有用户布局；保存时写入同目录随机临时文件，再用 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` 替换原文件。

- [ ] **步骤 5：运行测试确认通过**

运行同一个测试类，预期所有配置校验、默认生成和 action 白名单测试通过。

- [ ] **步骤 6：Commit**

运行 `git add src/main/java/cn/cloudfl4re/boatrace/gui src/test/java/cn/cloudfl4re/boatrace/gui/GuiConfigServiceTest.java && git commit -m "feat: add safe GUI config parser"`。

### 任务 3：实现比赛暂停、继续、管理员结束和首名烟花

**文件：**
- 修改：`src/main/java/cn/cloudfl4re/boatrace/model/RacePhase.java`
- 修改：`src/main/java/cn/cloudfl4re/boatrace/model/RaceSession.java`
- 修改：`src/main/java/cn/cloudfl4re/boatrace/service/RaceManager.java`
- 修改：`src/main/resources/messages.yml`
- 修改：`src/test/java/cn/cloudfl4re/boatrace/model/RaceSessionTest.java`

- [ ] **步骤 1：先写失败的模型测试**

增加测试：RUNNING 可暂停为 PAUSED；PAUSED 时 `advance/updatePosition/finish` 返回 false；恢复后运行时间扣除暂停时长；重复 pause/resume 被拒绝；只有第一次 `finish` 返回首名标记。

- [ ] **步骤 2：运行模型测试确认失败**

运行 `./gradlew test --tests cn.cloudfl4re.boatrace.model.RaceSessionTest`，预期新测试因 `PAUSED` 和模型方法不存在而失败。

- [ ] **步骤 3：实现 RaceSession 暂停状态**

增加 `PAUSED` 枚举与 `pauseNanos/pausedFromNanos/pausedBeforePhase/firstFinisher` 字段；实现 `pause(now)`, `resume(now)`, `elapsedNanos(now)` 和 `claimFirstFinisher()`，所有比赛判定方法只在 RUNNING 阶段工作。

- [ ] **步骤 4：实现 RaceManager 控制入口**

增加 `pauseByAdmin`, `resumeByAdmin`, `endByAdmin`；暂停时取消倒计时/刷新 UI 任务但保留会话，继续时用至少 1 tick 的实体/区域调度恢复；结束时将所有非终态参与者标记 DNF，复用现有结果保存和船只清理路径。

- [ ] **步骤 5：实现首名烟花**

在首次成功冲线的玩家实体上下文调用 `World.spawnFirework`，设置少量蓝紫色爆炸效果并播放 `sounds.first-finish`；通过 `claimFirstFinisher()` 保证烟花和首名音效只生成/播放一次。其他玩家冲线只播放 `sounds.race-finish`，不得生成烟花。不得在异步线程访问 World 或 Entity。

- [ ] **步骤 5.1：补充比赛开始 Title 与音效反馈**

倒计时每秒向参赛者发送配置化 Title 主标题/副标题，并在实体上下文播放 `sounds.countdown`；最后一秒播放 `sounds.countdown-final`；开始时发送“出发！” Title 并播放 `sounds.race-start`。Title 和音效任务必须可取消，暂停时停止，继续时按剩余阶段恢复；不使用 ActionBar 作为主要比赛提示。

- [ ] **步骤 5.2：增加实时第一名 ActionBar**

在比赛运行 UI 刷新任务中每 5 tick 读取不可变参赛者快照，按完成排名和当前进度计算第一名，在玩家实体上下文发送 `leaderboard-actionbar`；没有完成者时发送 `leaderboard-empty-actionbar`。暂停时停止刷新，结束或取消时清理任务。

- [ ] **步骤 5.3：增加冠军全服广播**

在 `finishSession` 的一次性结束路径中从 `RaceResultEntry` 取第一名；有冠军时通过 `scheduler.runGlobal` 向全服发送 `race-winner-broadcast`，无冠军时发送 `race-no-winner-broadcast`。使用会话级布尔状态保证重复 cleanup、重复结束或 reload 不会重复广播。

- [ ] **步骤 6：运行测试与编译**

运行 `./gradlew test --tests cn.cloudfl4re.boatrace.model.RaceSessionTest`，再运行 `./gradlew compileJava`，预期全部通过。

- [ ] **步骤 7：Commit**

运行 `git add src/main/java/cn/cloudfl4re/boatrace/model src/main/java/cn/cloudfl4re/boatrace/service/RaceManager.java src/main/resources/messages.yml src/test/java/cn/cloudfl4re/boatrace/model/RaceSessionTest.java && git commit -m "feat: add race pause controls and first-place fireworks"`。

### 任务 4：实现 GUI 页面、会话和动作分发

**文件：**
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiHolder.java`
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiSession.java`
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiService.java`
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiListener.java`
- 修改：`src/main/java/cn/cloudfl4re/boatrace/BoatRacePlugin.java`

- [ ] **步骤 1：实现 Holder 与会话状态**

`GuiHolder` 保存页面名、GUI 类型和玩家 UUID；`GuiSession` 只保存 UUID、返回页面栈、确认 action、编辑菜单名和过期时间，不保存长期 Player/Inventory 引用。

- [ ] **步骤 2：实现普通页面渲染**

`GuiService.open(player, menu)` 在玩家实体上下文创建 Inventory，读取配置按钮，应用权限过滤、分隔板和动态内容；普通点击默认取消，按钮 action 交给注册表处理。

- [ ] **步骤 3：实现玩家比赛动作**

将 `race-create/join/start/leave/cancel/status/last/leaderboard` 映射到现有服务；没有必要参数时打开对应选择页面或发送配置化提示，不在 GUI 层拼接命令字符串。

- [ ] **步骤 4：实现管理员控制和场地动作**

将 `race-pause/resume/end`、`track-create/edit/delete`、`edit-pos1/pos2/start`、checkpoint、slot、preview、save、cancel 映射到 `RaceManager` 和 `EditorManager`，每次动作先校验权限、玩家上下文和确认状态。

- [ ] **步骤 5：实现确认页**

危险 action 只写入会话确认上下文并打开 `confirm.yml`；确认按钮检查 action、目标比赛代码/赛道 ID 和 10 秒过期时间，取消按钮清理上下文。

- [ ] **步骤 6：实现事件监听和清理**

`GuiListener` 处理 `InventoryClickEvent`、`InventoryDragEvent`、`InventoryCloseEvent`、`PlayerQuitEvent`、`PlayerDeathEvent`；普通页面禁止取出物品，编辑页面交给任务 5；所有会话在关闭/退出/死亡/禁用时清理。

- [ ] **步骤 7：注册 GUI 与命令入口**

在 `BoatRacePlugin` 构造 `GuiConfigService`、`GuiService`、`GuiListener` 并注册；在 `RaceCommand` 增加 `/race gui` 与 `/race gui view <menu>` 分支和补全。

- [ ] **步骤 8：运行编译与现有测试**

运行 `./gradlew test`，预期原有测试全部通过且 GUI 代码编译成功。

- [ ] **步骤 9：Commit**

运行 `git add src/main/java/cn/cloudfl4re/boatrace/gui src/main/java/cn/cloudfl4re/boatrace/BoatRacePlugin.java src/main/java/cn/cloudfl4re/boatrace/command/RaceCommand.java && git commit -m "feat: add full BoatRace GUI actions"`。

### 任务 5：实现管理员布局整理视图

**文件：**
- 创建：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiLayoutEditor.java`
- 创建：`src/test/java/cn/cloudfl4re/boatrace/gui/GuiLayoutEditorTest.java`
- 修改：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiListener.java`
- 修改：`src/main/java/cn/cloudfl4re/boatrace/gui/GuiService.java`

- [ ] **步骤 1：先写失败测试**

测试管理员编辑视图只允许 `boatrace.gui.view`/`boatrace.admin`，拖动后按槽位更新按钮 index，分隔板保持固定字段，编辑视图点击不执行 action，保存失败时原文件内容不变。

- [ ] **步骤 2：运行测试确认失败**

运行 `./gradlew test --tests cn.cloudfl4re.boatrace.gui.GuiLayoutEditorTest`，预期因编辑器尚未实现而失败。

- [ ] **步骤 3：实现编辑视图渲染**

按 PlayerMenu 的 view 思路加载菜单快照，给每个按钮写入 NamespacedKey 内部标识；使用 `LAYOUT_EDITOR` Holder，点击和拖动只改变 Inventory，不调用 action 注册表。

- [ ] **步骤 4：实现关闭保存**

关闭时从 Inventory 读取带内部标识的按钮，构建新的 slot 映射并调用 `GuiConfigService.saveLayoutAsync`；只更新 `index`，不覆盖按钮名称、材质、Lore、权限和 action；文件保存完成后在玩家实体上下文发送成功或失败消息。

- [ ] **步骤 5：实现编辑锁和生命周期清理**

同一菜单同一时间只允许一个管理员编辑；关闭、退出、死亡、reload 和 onDisable 都释放锁。禁止保存无法识别的物品或任意 action。

- [ ] **步骤 6：运行编辑器测试与完整测试**

运行 `./gradlew test --tests cn.cloudfl4re.boatrace.gui.GuiLayoutEditorTest`，再运行 `./gradlew test`，预期全部通过。

- [ ] **步骤 7：Commit**

运行 `git add src/main/java/cn/cloudfl4re/boatrace/gui src/test/java/cn/cloudfl4re/boatrace/gui/GuiLayoutEditorTest.java && git commit -m "feat: add admin GUI layout editor"`。

### 任务 6：最终验证与交付

**文件：**
- 检查：`build/libs/BoatRace-1.2.0.jar`
- 检查：`src/main/resources/gui/*.yml`

- [ ] **步骤 1：运行完整构建**

运行 `./gradlew clean build --rerun-tasks`，预期 `BUILD SUCCESSFUL`、所有单元测试通过。

- [ ] **步骤 2：执行静态安全扫描**

运行 `rg -n "Bukkit\\.getScheduler|scheduleSync|scheduleAsync|teleport\\(|Future\\.get|CompletableFuture.*join|PlayerRespawnEvent|PlayerTeleportEvent|PlayerChangedWorldEvent|WorldLoadEvent|WorldUnloadEvent" src/main/java`，确认 GUI 代码没有直接使用传统调度器、同步传送、阻塞等待或 Folia 不可用事件。

- [ ] **步骤 3：检查资源和 JAR**

检查 `plugin.yml`、五个 GUI YAML、分隔板固定字段、主标题 `冰船系统`、`folia-supported: true`；运行 `jar tf build/libs/BoatRace-1.2.0.jar`，确认包含 GUI 资源且不包含服务端 API 类。

- [ ] **步骤 4：输出交付物**

复制 JAR、源码 ZIP 和各 GUI YAML 到 `outputs/`，计算 SHA-256，并明确说明未进行真实 Paper/Folia 服务端运行测试。

- [ ] **步骤 5：Commit**

运行 `git add . && git commit -m "feat: complete BoatRace GUI system"`。
