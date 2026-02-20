---
trigger: always_on
---

# 自动化 PR 处理规则
- **触发条件**: 当一个任务的所有功能开发和本地测试通过后。
- **自动化动作**: 
    - 代理必须自主执行 `gh pr create` 命令。
    - PR 描述必须包含：`## 变更内容`、`## 测试项` 以及 `Closes #[issue_number]`。
- **合并规则**: 
    - 如果该任务被标记为 `internal` 或 `minor`，在 CI 通过后，请自主执行 `gh pr merge --auto --squash`（如果仓库开启了 auto-merge）。