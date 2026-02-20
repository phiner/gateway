---
trigger: always_on
---

# GitHub 分支管理最佳实践 (GitHub Flow)

1. **禁止直接提交**: 严禁在 `main` 或 `master` 分支直接 Commit。`main` 分支受 GitHub 规则集（Rulesets）严格保护，必须创建功能分支并提交 PR，禁止尝试直接推送代码到 `main` 分支。
2. **功能分支命名**: 
   - 新功能: `feature/简短描述` (例: `feature/login-api`)
   - 修复 Bug: `fix/描述` (例: `fix/connection-leak`)
   - 性能优化: `perf/描述`
3. **Pull Request 流程**:
   - 必须先创建新分支。
   - 完成开发后，必须发起 Pull Request (PR)。
   - PR 必须通过 GitHub Actions 的自动化测试后才能合并。
4. **提交信息规范**: 遵循 Angular 规范 (feat: ..., fix: ..., docs: ...)。