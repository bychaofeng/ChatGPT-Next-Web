# 康复决策练习 · v0.3 开发原型

这是**独立安卓子项目 + 可离线运行的 Kotlin 训练核心**。目录为现有仓库的 `rehab-trainer/`；不修改原有 NextChat/Next.js 代码。新用户不用填写背景表，系统从未知状态开始，依据各自的作答和帮助记录安排后续任务。

**实际交付状态（2026-09-26）**

- 已实现核心代码，Kotlin/JVM 真实编译通过；68 项离线回归测试通过。
- 已运行两个用户的不同作答轨迹，下一题路径不同；文件重新加载后状态一致。
- 已写安卓原生界面、BYOK 加密保存、HTTPS 适配器和 GitHub Actions 构建流程。
- 本地环境尚未执行 Android SDK 编译和设备测试。**云端构建与 APK 状态以当前提交的 Actions 记录为准；构建成功不等于真机或临床验证。**
- **模型调用在本轮测试中全部是 mock；未使用真实密钥，未真实 API 联调。**
- 开发分支：`feat/rehab-adaptive-mvp`，目标仓库：`bychaofeng/ChatGPT-Next-Web`。早期的连接写权限问题已通过重新授权解决；保持独立分支，不自动合并到 `main`。

## 内容范围：不要把演示认作临床题库

内置 8 个能力主题、16 道**人工编写、虚构、未医学审核**的过程推理题。离线选项判定只是可体验的参考答案，不评价开放理由，不是适用于真实患者的诊断/训练建议。

主题包括目标、信息收集、比较解释、可执行计划、复评、沟通、安全边界和依据核查。题目范围很小，但学习档案和适配规则不是按某一个人写死的。

所有练习观察都是暂定信息。`verifiedIndependent` 在本版始终为 0；没有“已认证”“专家”“满级”。AI 改写目前只在已有蓝图内改写问题问法，不是已实现无限新病例生成。没有真实病例入口、3D 解剖、语音、云同步和付费会员。

## 项目结构

```text
rehab-trainer/
  core/src/main/kotlin/cn/rehab/trainer/core/
    Models.kt       数据、画像投影；不从聊天制造掌握证据
    Content.kt      演示题库 + 冷启动/自适应选题规则
    Engine.kt       保存作答、提示、评价、争议、摘要与调用额度
    Persistence.kt  整体事务式本地快照和结构化序列化
    Prompts.kt      四类真正被运行时代码使用的提示词
    Ai.kt           HTTPS聊天补全适配、结构校验、引用核对
    Json.kt         有深度/大小限制的无依赖JSON编解码
  core/src/test/.../CoreTests.kt
  app/              原生安卓界面、密钥保险箱、应用级单请求队列
  scripts/          无Gradle的离线编译测试和电脑演示入口
  docs/             实测日志、状态说明、架构与待完成项
```

`core` 不依赖 Android SDK，可以离线测试。为先验证行为与状态，本版界面使用 Android 平台 View，而不是 Compose；保存使用应用私有目录里的同目录原子替换快照，而不是 Room。`Repository` 接口把存储与训练逻辑隔开，后续可换成 Room；不要把当前实现当成已完成数据库迁移/大规模持久化。

## 先跑核心（已在当前环境实际运行）

需要 JDK 17+、Kotlin CLI 1.9+：

```bash
cd rehab-trainer
bash scripts/test-core.sh
bash scripts/build-demo.sh
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar core/build/rehab-demo.jar
```

- `test-core.sh` 编译并运行 68 项测试，没有网络依赖。
- `build-demo.sh` 编译电脑演示，并运行两个学习者的 smoke test。
- 交互演示使用 A/B/C 作答；H 提示；F 固定病例资料；P 画像；N 下一题；U 新档案；Q 退出。
- 默认写入用户主目录 `rehab-trainer-demo.json`；可用 `--data /path/state.json` 选择位置。不要把自己的学习文件提交到仓库。
- 标准输出显式使用UTF-8，避免无locale环境中中文变成问号。

## 安卓构建

固定构建组合：**JDK17、Gradle8.9、AGP8.7.2、Kotlin2.1.21、compileSdk/targetSdk35、minSdk26**。这是开发原型的固定组合，不宣称是最新版本或满足上架政策。

当前包未伪造/附带 Gradle wrapper 二进制；使用已安装的 Gradle 8.9，或在有网络的开发环境生成 wrapper 并校验发行包：

```bash
cd rehab-trainer
# 本机需要ANDROID_HOME，或不入库的local.properties里配置sdk.dir
sdkmanager "platforms;android-35" "build-tools;34.0.0"
gradle --no-daemon :core:regressionTest :app:assembleDebug :app:lintDebug
# 首次真实构建通过后，调试包预计在：
# app/build/outputs/apk/debug/app-debug.apk
```

仓库的 `.github/workflows/rehab-android.yml` 为开发分支的 push/PR 配置了同一构建。工作流只读仓库，不需要模型Key、不自动发布、不自动合并。执行结果与调试APK请查看相应提交的 Actions；Android SDK/runner差异可能需要调整。

## 第一次使用：不需要填写背景

1. 开始训练：建立未知状态，先呈现一个常见决策任务。
2. 选择选项可完全离线；写出开放答案时需配置自己的API。
3. 提交即冻结原始答案与已见事实/提示条件。出错或断网保留待评价，不记成错误。
4. 查看提示、解析或讨论回复会记录帮助；更正答案不会覆盖首次回答。
5. 一轮可以结束；未评价答案可明确保留为“未评价”，不会阻止退出也不会扣分。
6. 下一题读取当前证据：一次适度补练、探索其他未知能力、或安排复测。每三轮保留探索机会，避免锁死在早期判断里。
7. 学习记录展示实际观察和未验证状态，不显示虚构精确总分。

在设置中新建/切换本地学习档案，可检查不同用户共用同一程序、数据互不串用。没有后台账户系统。

## API 与提示词

在本机设置 HTTPS 基础地址、服务商模型名和自己的 Key。支持类似 `/chat/completions` 的非流式接口；JSON模式可关闭以适配部分服务，但返回内容仍必须是指定JSON。模型名不写死，按服务商实际支持填写。

密钥仅在 `Authorization` 请求头出现；运行时不把它放进提示词、学习档案或导出。Android版通过Keystore保护AES-GCM密钥，再加密保存BYOK；不宣称被攻破的设备绝对安全。域名/端口变化需要重新输入Key，避免把旧Key默默发送给另一个服务。

每次手动AI操作会发送当前任务相关的模拟信息；连接设置中必须确认发送范围。第三方服务的日志留存/训练使用政策需要另行核查。不要输入真实患者身份资料；不是本版的用途。

四组运行时提示词位于 `Prompts.kt`，实际输入/输出按 v0.3 代码，不应混用旧 v0.2 的JSON样例：

| 调用 | 输入关键点 | 结果作用 |
|---|---|---|
| generate | 当前蓝图、相关能力摘要、模式 | 仅候选问题问法；不改病例事实/评分点 |
| evaluate | 本次答案、提交时可见事实、评分点；无旧画像 | 引文和版本检查后记暂定练习评价 |
| coach | 当前题目、已见事实、近12条讨论、相关摘要 | 教学回复；展示时记录帮助 |
| absorb | 尚未处理的新消息、允许的能力ID | 待验证线索和续聊摘要；不能增加独立能力计数 |

批处理摘要：6轮完整问答、16,000字符阈值或主动结束时有新内容触发；单批最多24条消息，未处理原文仍保留。没有新消息不调用。原始消息立即保存；正式答案评价成功则立即更新相关投影，不等待摘要。

帮助后约3天、合格无提示练习后约7天安排再次观察：都是试用参数，不是最佳学习间隔的证据。到期不自动升级，也没有后台通知。

每台安装的滚动24小时请求尝试上限默认30，可设置1–200；生成、评价、讨论、摘要与失败重试都计数。切换或删除学习档案不重置全局计数。它是调用数限制，不是精确费用上限；只有服务商返回的token数才记录用量。

## 已验证与未验证

详见 `docs/STATUS.md` 和真实测试日志。测试证明代码对列出的输入按预期运行；不证明模型听话、评分公平、医学正确或用户能力提高。

重要的安全和完整性机制：
- 评价引用必须逐字来自本次回答；ID、版本、评分要点集合需匹配。
- 模型不能返回 `profile_patch` 等额外权限字段；收到会拒绝。
- `needs_review` 或不确定结果不形成有效画像观察。
- 同一提交重试只保留一份生效评价；晚到结果不覆盖新答案。
- 争议、撤回使相关暂定影响消失，但保留原始资料供核查。
- 用户主动获取事先定义的事实不算提示；模型看不到提交后才开放的事实。
- 聊天只形成待验证线索；不能把助手原话当成用户能力。
- 原始数据损坏/未来版本不自动清空；快照写入失败不发布内存新状态。
- HTTPS重定向不携带Key跟随；UI不显示任意服务商错误正文。

限制仍然重要：固定协议与引用检查不能发现所有医学错误或语义幻觉；本版没有校准过的测评量表，没有人工审核工作台，没有复杂多进程写入协议。JSON快照的单进程原子替换不是对所有设备断电恢复的保证；上规模前需迁移/测试数据库和备份恢复。

## 资料依据（仅用于实现接口和构建约束）

- Kotlin Gradle兼容表：https://kotlinlang.org/docs/gradle-configure-project.html
- Android AGP8.7兼容表：https://developer.android.com/build/releases/agp-8-7-0-release-notes
- DeepSeek JSON输出：https://api-docs.deepseek.com/guides/json_mode/
- Android Keystore：https://developer.android.com/privacy-and-security/keystore
- GitHub REST权限错误：https://docs.github.com/en/rest/using-the-rest-api/troubleshooting-the-rest-api

这些文档不验证内置演示题、评分规则或教学效果。
