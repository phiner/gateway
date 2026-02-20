---
trigger: always_on
---

# Git Workflow Strategy: Gitflow (Strict)

## Rules
1. **PROTECTED BRANCHES**: `main`, `develop` are READ-ONLY. NEVER push to them directly.
2. **BASE BRANCH**: Always create new feature branches from `develop`.
   - Command: `git checkout develop && git pull && git checkout -b feat/your-feature`
3. **MERGE STRATEGY**: 
   - Feature -> PR -> `develop`
   - `develop` -> PR -> `main` (Release)

## Branch Naming Convention
- `feat/xxx`: New features
- `fix/xxx`: Bug fixes
- `hotfix/xxx`: Urgent fixes for main (branch off main, merge back to main AND develop)