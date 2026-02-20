---
trigger: always_on
---

# GitHub & Spring Boot Best Practices
- **镜像构建**: 必须使用 Google Jib 或 Cloud Native Buildpacks，严禁编写臃肿的传统 Dockerfile。
- **CI/CD**: 必须在 `.github/workflows` 中包含自动化测试和镜像推送流程。
- **代码质量**: 强制使用 Lombok 减少冗余，构造函数注入（Constructor Injection）优于 @Autowired。
- **文档**: 每个 PR 必须自动更新 `README.md` 和接口文档。
- **安全**: 严禁将任何 API Key 或 Secret 写入代码，必须使用 GitHub Secrets 或 .env 占位符。