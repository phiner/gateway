---
name: gateway-conventions
description: Development conventions and patterns for gateway. Java project with mixed commits.
---

# Gateway Conventions

> Generated from [phiner/gateway](https://github.com/phiner/gateway) on 2026-03-22

## Overview

This skill teaches Claude the development patterns and conventions used in gateway.

## Tech Stack

- **Primary Language**: Java
- **Architecture**: hybrid module organization
- **Test Location**: separate

## When to Use This Skill

Activate this skill when:
- Making changes to this repository
- Adding new features following established patterns
- Writing tests that match project conventions
- Creating commits with proper message format

## Commit Conventions

Follow these commit message conventions based on 56 analyzed commits.

### Commit Style: Mixed Style

### Prefixes Used

- `feat`
- `chore`
- `fix`
- `refactor`

### Message Guidelines

- Average message length: ~33 characters
- Keep first line concise and descriptive
- Use imperative mood ("Add feature" not "Added feature")


*Commit message example*

```text
feat: 优化历史记录提取，实现防抖、合并及单次通知机制 (#45)
```

*Commit message example*

```text
refactor: change position publication log level to DEBUG (#42)
```

*Commit message example*

```text
chore: update min-versions-to-keep to 3 in cd-deploy (#33)
```

*Commit message example*

```text
fix: normalize Order request DTOs and add MessagePack diagnostics (#31)
```

*Commit message example*

```text
ci: optimize workflow to run tests on develop and build only on main (#23)
```

*Commit message example*

```text
Optimizar y refactorizar el codigo
```

*Commit message example*

```text
完善了项目文档 (#58)
```

*Commit message example*

```text
Develop (#57)
```

## Architecture

### Project Structure: Single Package

This project uses **hybrid** module organization.

### Source Layout

```
src/
├── main/
├── test/
```

### Configuration Files

- `.github/workflows/cd-deploy.yml`
- `.github/workflows/ci-test.yml`

### Guidelines

- This project uses a hybrid organization
- Follow existing patterns when adding new code

## Code Style

### Language: Java

### Naming Conventions

| Element | Convention |
|---------|------------|
| Files | PascalCase |
| Functions | camelCase |
| Classes | PascalCase |
| Constants | SCREAMING_SNAKE_CASE |

### Import Style: Relative Imports

### Export Style: Named Exports


*Preferred import style*

```typescript
// Use relative imports
import { Button } from '../components/Button'
import { useAuth } from './hooks/useAuth'
```

*Preferred export style*

```typescript
// Use named exports
export function calculateTotal() { ... }
export const TAX_RATE = 0.1
export interface Order { ... }
```

## Common Workflows

These workflows were detected from analyzing commit patterns.

### Feature Development

Standard feature implementation workflow

**Frequency**: ~15 times per month

**Steps**:
1. Add feature implementation
2. Add tests for feature
3. Update documentation

**Files typically involved**:
- `**/*.test.*`
- `**/api/**`

**Example commit sequence**:
```
docs: add GitHub best practices and branching strategy rules (#1)
feat: add multi-platform support to jib (amd64/arm64) (#2)
feat: enforce redis connection as a precondition for starting jforex client (#3)
```

### Refactoring

Code refactoring and cleanup workflow

**Frequency**: ~6 times per month

**Steps**:
1. Ensure tests pass before refactor
2. Refactor code structure
3. Verify tests still pass

**Files typically involved**:
- `src/**/*`

**Example commit sequence**:
```
deploy main (#15)
deploy (#16)
Develop (#18)
```

### Dto Addition Or Field Extension

Add new DTOs or extend existing DTOs with new fields, and update related tests and documentation.

**Frequency**: ~3 times per month

**Steps**:
1. Modify or add Java DTO class in src/main/java/phiner/de5/net/gateway/dto/
2. Update related service/strategy/listener code if needed
3. Update docs/redis_api.md with new fields or DTO structure
4. Add or update corresponding test in src/test/java/phiner/de5/net/gateway/dto/

**Files typically involved**:
- `src/main/java/phiner/de5/net/gateway/dto/*.java`
- `docs/redis_api.md`
- `src/test/java/phiner/de5/net/gateway/dto/*.java`

**Example commit sequence**:
```
Modify or add Java DTO class in src/main/java/phiner/de5/net/gateway/dto/
Update related service/strategy/listener code if needed
Update docs/redis_api.md with new fields or DTO structure
Add or update corresponding test in src/test/java/phiner/de5/net/gateway/dto/
```

### Feature Implementation With Tests And Docs

Implement a new feature or significant enhancement, touching main logic, tests, and documentation.

**Frequency**: ~3 times per month

**Steps**:
1. Implement or modify feature logic in src/main/java/phiner/de5/net/gateway/ (strategy, service, listener, etc.)
2. Update or add tests in src/test/java/phiner/de5/net/gateway/
3. Update docs/redis_api.md or other relevant docs

**Files typically involved**:
- `src/main/java/phiner/de5/net/gateway/**/*.java`
- `src/test/java/phiner/de5/net/gateway/**/*.java`
- `docs/redis_api.md`

**Example commit sequence**:
```
Implement or modify feature logic in src/main/java/phiner/de5/net/gateway/ (strategy, service, listener, etc.)
Update or add tests in src/test/java/phiner/de5/net/gateway/
Update docs/redis_api.md or other relevant docs
```

### Ci Cd Workflow Update

Update CI/CD workflow files, often to fix, optimize, or add new automation steps.

**Frequency**: ~2 times per month

**Steps**:
1. Modify .github/workflows/*.yml files
2. Sometimes update pom.xml or related build files

**Files typically involved**:
- `.github/workflows/*.yml`
- `pom.xml`

**Example commit sequence**:
```
Modify .github/workflows/*.yml files
Sometimes update pom.xml or related build files
```

### Documentation Update

Update project documentation, either for new features, API changes, or general improvements.

**Frequency**: ~2 times per month

**Steps**:
1. Edit docs/redis_api.md, README.md, or other docs files

**Files typically involved**:
- `docs/redis_api.md`
- `README.md`
- `README_CN.md`

**Example commit sequence**:
```
Edit docs/redis_api.md, README.md, or other docs files
```

### Refactor Or Optimize Core Logic

Refactor or optimize multiple core logic files, often with corresponding test updates.

**Frequency**: ~2 times per month

**Steps**:
1. Modify several files in src/main/java/phiner/de5/net/gateway/ (strategy, service, listener, util, etc.)
2. Update related tests in src/test/java/phiner/de5/net/gateway/

**Files typically involved**:
- `src/main/java/phiner/de5/net/gateway/**/*.java`
- `src/test/java/phiner/de5/net/gateway/**/*.java`

**Example commit sequence**:
```
Modify several files in src/main/java/phiner/de5/net/gateway/ (strategy, service, listener, util, etc.)
Update related tests in src/test/java/phiner/de5/net/gateway/
```


## Best Practices

Based on analysis of the codebase, follow these practices:

### Do

- Use PascalCase for file names
- Prefer named exports

### Don't

- Don't deviate from established patterns without discussion

---

*This skill was auto-generated by [ECC Tools](https://ecc.tools). Review and customize as needed for your team.*
