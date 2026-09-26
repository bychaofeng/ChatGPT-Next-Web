# 仓库接入说明

本源码是独立子项目，仓库原来的 NextChat 功能不受修改。

仅新增 `rehab-trainer/` 和 `.github/workflows/rehab-android.yml`；不修改原来的 package.json、路由、工作流或依赖锁文件。

## 获取开发分支

```bash
git fetch origin
git switch --track origin/feat/rehab-adaptive-mvp
# 已有本地分支时使用：git switch feat/rehab-adaptive-mvp
cd rehab-trainer
bash scripts/test-core.sh
```

安卓构建见 README。云端工作流运行结果、日志和调试APK以对应提交的 Actions 记录为准；PR 保留为草稿，不自动合并。

源码不含编译产物、模型Key、个人学习档案或患者资料。需要真实API时，由使用者在本机设置Key；不要写入仓库、工作流或开发对话。

早期连接返回403的写权限问题已在重新授权后恢复。源码发布、Android构建成功、真机运行和医学内容验证是不同阶段，不能互相代替。
