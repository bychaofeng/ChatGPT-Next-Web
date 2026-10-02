# 康复训练项目文档

## 方案规划

**从 [方案规划入口](planning/README.md) 开始阅读。**

[产品总方案](planning/PRODUCT_PLAN.md) 说明训练目标、八项能力、无需背景表的自动适配、日常训练与病例讨论，以及解析和3D的后续方向。

[架构与更新机制](planning/ARCHITECTURE_AND_UPDATES.md) 说明程序和四组提示词的分工、画像更新时机、摘要频率、复测与当前实现差异。

[开发路线与验收](planning/ROADMAP_AND_ACCEPTANCE.md) 将现有代码、已有验证和待开发事项分开，列出手机验收、真实API联调和内容审核的路径。

[历史设计归档](planning/archive/README.md) 保存训练任务协议v0.1和完整提示词设计v0.2，避免方案只留在聊天里。

## 工程记录

- [原型状态记录](STATUS.md)：保留原版本日期；较新的基线与CI记录见开发路线及PR。
- [接入说明](INTEGRATION.md)
- [核心回归日志](core-test-output.txt)
- [双用户流程演示日志](demo-smoke-output.txt)

文档中的规划不等于功能已经实现，工程测试不等于医学验证。运行时字段和提示词以相应源码版本为准。

[返回项目首页](../README.md)
