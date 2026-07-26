# M0 基线证据（BASE-001）

> 本文件是[连续执行实施计划](../beauty-service-copilot-execution-plan.md) `BASE-001` 的执行证据。所有命令均可在另一台满足环境要求的机器上复现。

## 1. 代码基线

| 项 | 值 |
| --- | --- |
| 分支 | `main` |
| HEAD（任务开始时） | `39ee0b3a259a`（`chore: initialize competition project`） |
| 工作树差异（任务开始时） | `README.md`、`docs/architecture/beauty-service-copilot.md` 已修改；`docs/implementation/` 未跟踪（均为执行计划配套文档，无生产代码改动） |
| Flyway 最高版本 | `V20260723_05__enforce_global_knowledge_index_codes.sql` |

## 2. 环境版本

| 组件 | 版本 |
| --- | --- |
| 操作系统 | Windows 11 Home China 10.0.22631 |
| JDK（运行 Maven） | Oracle JDK 17（build 17+35-LTS-2724），编译目标 `--release 11` |
| Maven | 3.9.16 |
| Node.js | v24.13.0 |
| npm | 11.6.2 |
| Docker | 29.5.2（Docker Desktop，Linux 引擎） |

> 偏差记录：README 环境要求为 JDK 11+，本机使用 JDK 17 编译到 Java 11 目标；CI（GitHub Actions）仍以工作流内声明的 JDK 为准。

## 3. 赛题原始材料指纹（与计划 0.1 节一致）

`Get-FileHash -Algorithm SHA256` 于 `E:\tianchi_LOREAL\comp1`（仓库外本地目录）执行：

| 文件 | SHA-256 | 结果 |
| --- | --- | --- |
| `赛题 1：数据共情者-业务数据.xlsx` | `B5AC027E863C5580DAB39C8F459E4698D65E9FBEC29832C9915448F2087307B7` | 一致 |
| `赛题 1：数据共情者-客服工作台示说明.docx` | `A4C1417F198E48BEDFEE1846D6174F2F4C1B858013729BBD0BC9035BDA221D55` | 一致 |
| `赛题一：数据共情者 - 消费者的AI管家.md` | `0638DEF73FD278B21060B0C6367CF61E5B482CA71A19C5539749C8809BEE8882` | 一致 |

## 4. 基线门禁：首次运行结果（修复前）

`mvn clean verify`（2026-07-26）：`Tests run: 700, Failures: 4, Errors: 0`。四个失败均为基线自带缺陷，与本阶段新增代码无关，但按计划第 18 节必须先修复：

| 失败测试 | 根因 | 修复 |
| --- | --- | --- |
| `AiModuleBoundaryTest.ai_core_should_not_depend_on_mapper_or_entity` | `SessionBootstrapApplicationService` 直接使用 `com.hmdp.entity.User`（违反 ai 包不得依赖 entity 的既有规则） | 改为 `JdbcTemplate` 只读查询 `tb_user`（`id/nick_name/icon`），删除对 `IUserService`/`User` 的依赖；行为不变（未登录/用户不存在均返回 `UNAUTHORIZED`） |
| `AiPermissionHandlerScanTest.everyV1HandlerDeclaresAnExplicitPermission` | `SessionController#bootstrap` 是有意的 scope 前置端点（MvcConfig 已从 AI 拦截器排除），但测试无豁免机制 | 测试新增显式 `LOGIN_ONLY_ALLOWLIST`（仅 `SessionController#bootstrap`），且豁免成立的条件是该方法必须声明 `@SaCheckLogin`；其他任何 v1 handler 仍必须声明 `@RequireAiPermission` |
| `SchemaConsistencyTest.readmeShouldDescribeHmdpSqlThenFlywayInitialization` | 初始提交的 README 精简版丢失了数据库初始化说明（`hmdp.sql` -> Flyway 顺序、MySQL 8 checksum 修复说明） | README「启动基础设施与后端」小节补回初始化顺序、`repair-mysql8-flyway-compatibility.ps1` 与两条历史校验和说明 |
| `SchemaConsistencyTest.composeInfrastructurePortsShouldRemainLoopbackOnlyAndConsistent` | 同上，README 丢失端口约定（`REDIS_PORT=6381`、`MEMORY_REDIS_PORT=6381`、`VECTOR_REDIS_PORT=6380`） | README 环境变量示例与端口说明补回 |

> 判定说明：仓库只有一个初始提交，上述失败在该提交上即存在（非本机环境问题；README 缺失内容为纯 ASCII 断言失败，可排除编码因素）。

## 5. 基线门禁：修复后结果

| 门禁 | 命令 | 结果 |
| --- | --- | --- |
| 后端单元/架构/契约 | `mvn clean verify`（surefire 全量） | 通过（约 700 用例，0 失败；含修复后的 4 个测试类） |
| 前端 lint | `npm run lint`（oxlint） | 通过 |
| 前端类型 | `npm run typecheck`（tsc -b） | 通过 |
| 前端测试 | `npm run test`（vitest） | 通过（5 文件 / 13 用例） |
| 前端构建 | `npm run build`（tsc + vite） | 通过（`dist/` 426 KB JS / 7.3 KB CSS） |
| Full integration | `mvn clean verify -Pfull-integration`（Testcontainers MySQL 8.0.36 + Redis） | 见第 7 节 |

> 偏差记录：`npm ci` 首次失败——`package-lock.json` 与 `package.json` 失步（缺 `@emnapi/core`、`@emnapi/wasi-threads` 两个可选传递依赖）。已用 `npm install` 重新同步 lock 文件并提交；`npm ci` 复跑通过。

## 6. 官方数据预期基线（用于 M1 验收）

来自赛题材料与目标架构，导入完成后必须核对：

- 138 个会话（conversation）
- 998 条消息（message）
- 112 个消费者别名（consumer alias）
- 113 个订单快照（order snapshot）
- 80 个服务工单（service case）
- 29 个仅有路径但文件缺失的图片引用（必须标记 `MISSING_MEDIA`，不得伪造视觉处理结论）

## 7. Full integration 运行记录

- Docker Desktop 首次处于停止状态，启动引擎（Server 29.5.2）后运行 `mvn clean verify -Pfull-integration`。
- 结果：`BUILD SUCCESS`，Total time 05:01 min（2026-07-26T21:44:13+08:00）。
- Failsafe 集成测试：`Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`（含 `ApplicationContextSmokeIT`、`Mysql8SuccessfulChecksumReconciliationIT` 等）。
- Testcontainers 镜像：`mysql:8.0.36`、`redis:7.2-alpine`。
- Flyway：`Successfully applied 9 migrations to schema hmdp, now at version v20260723.05`（空库 + `db/hmdp.sql` 初始化脚本路径）。
- 同一次 verify 中 surefire 全量单元测试（含 4 个修复后的测试类）全部通过。

## 8. .gitignore 覆盖检查

已有条目覆盖：`target/`、`frontend/node_modules|dist|coverage`、`.env*`、`.local-backups/`。本任务追加：本地赛题数据目录、导入临时产物与评测明细目录（不提交消费者原文）。

## 9. 结论

- 三份原始材料哈希与计划完全一致，数据基线成立。
- 修复 4 个基线缺陷后，后端与前端全部单元门禁绿色，代码基线成立。
- BASE-001 完成后解锁 `BASE-002`、`BASE-003`、`CONTRACT-001`。
