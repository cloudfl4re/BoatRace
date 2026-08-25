# Folia 1.21.x / 26.x 兼容实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让同一个 BoatRace JAR 同时运行于 1.21.x Folia 与 26.x Folia 核心。

**架构：** 以 Paper 1.21 API 为最低编译基线，保留现有 Folia 调度抽象；运行时仅调用两代核心共有的 Bukkit/Paper/Folia API。将 Java 字节码目标降为 21，使 Java 21 与更高版本均可加载。

**技术栈：** Gradle Kotlin DSL、Paper API 1.21.4、Java 21、JUnit 5、Shadow Jar。

---

### 任务 1：调整跨版本构建基线

**文件：**
- 修改：`build.gradle.kts`

- [ ] **步骤 1：替换服务端 API 依赖与 Java 目标**

将 `fun.bm.lophine:lophine-api:26.2.build.651-stable` 替换为 `io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT`，并将 toolchain 与 `options.release` 从 25 改为 21。

- [ ] **步骤 2：解析依赖并编译测试**

运行 `./gradlew clean test shadowJar`；预期依赖解析成功，测试与 JAR 任务完成。若 API 编译错误，只修复确实不属于 1.21 公共 API 的调用，不引入版本专属类型。

- [ ] **步骤 3：检查产物字节码与依赖内容**

运行 `javap -verbose build/classes/java/main/cn/cloudfl4re/boatrace/BoatRacePlugin.class | Select-String 'major version'`，预期显示 `major version: 65`；运行 `jar tf build/libs/BoatRace-1.1.0.jar | Select-String 'org/bukkit|io/papermc'`，预期无服务端 API 类。

### 任务 2：更新插件元数据与用户文档

**文件：**
- 修改：`src/main/resources/plugin.yml`
- 修改：`src/integrationHarness/resources/plugin.yml`
- 修改：`README.md`
- 修改：`src/main/java/cn/cloudfl4re/boatrace/BoatRacePlugin.java`

- [ ] **步骤 1：设置最低 API 版本**

将两个 `plugin.yml` 的 `api-version` 设置为 `'1.21'`，并把描述从仅指向 Lophine 26.2 改为说明 1.21.x/26.x Folia。

- [ ] **步骤 2：修正启动日志与 README 支持矩阵**

将启动日志改为 `BoatRace enabled (Paper/Folia 1.21.x+ / 26.x)`；README 的徽章、环境要求、兼容性说明和构建说明改为 Java 21、1.21.x Folia 与 26.x Folia。

- [ ] **步骤 3：运行资源处理与测试**

运行 `./gradlew test shadowJar`；预期 `plugin.yml` 中 `${version}` 仍被正确展开，所有测试通过。

### 任务 3：最终验证与提交

**文件：**
- 检查：`build/libs/BoatRace-1.1.0.jar`

- [ ] **步骤 1：执行完整验证**

运行 `./gradlew clean build`；预期 `BUILD SUCCESSFUL`。

- [ ] **步骤 2：检查工作树与关键元数据**

运行 `git diff --check`，并解压检查产物 `plugin.yml` 的 `api-version: '1.21'` 与 `folia-supported: true`。

- [ ] **步骤 3：提交实现**

运行 `git add build.gradle.kts src/main/resources/plugin.yml src/integrationHarness/resources/plugin.yml README.md src/main/java/cn/cloudfl4re/boatrace/BoatRacePlugin.java && git commit -m "feat: support Folia 1.21 and 26"`。
