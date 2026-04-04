```markdown
# gateway Development Patterns

> Auto-generated skill from repository analysis

## Overview
This skill teaches the development conventions and workflows used in the `gateway` Java repository. You'll learn how to structure files, write imports and exports, follow commit message guidelines, and implement and run tests according to the project's standards. This guide is ideal for contributors seeking to maintain consistency and quality in the codebase.

## Coding Conventions

### File Naming
- Use **PascalCase** for all file names.
  - **Example:** `UserService.java`, `OrderController.java`

### Import Style
- Use **relative imports** within the project.
  - **Example:**
    ```java
    import com.gateway.services.UserService;
    ```

### Export Style
- Use **named exports** (i.e., explicitly declare public classes or interfaces).
  - **Example:**
    ```java
    public class PaymentGateway {
        // class implementation
    }
    ```

### Commit Messages
- Follow **conventional commit** patterns.
- Use prefixes such as `refactor`.
- Keep commit messages descriptive (average length: ~93 characters).
  - **Example:**
    ```
    refactor: update UserService to improve error handling and logging
    ```

## Workflows

### Refactoring Code
**Trigger:** When you need to improve code structure or readability without changing functionality  
**Command:** `/refactor`

1. Identify the code section that requires refactoring.
2. Make improvements (e.g., rename variables, extract methods, reorganize logic).
3. Ensure all tests pass after changes.
4. Commit using the `refactor:` prefix and a descriptive message.
   - Example: `refactor: extract validation logic into separate method in OrderController`
5. Push your changes and open a pull request if required.

## Testing Patterns

- Test files follow the `*.test.*` pattern (e.g., `UserService.test.java`).
- The specific testing framework is **unknown**, so refer to existing test files for structure.
- Place test files alongside or within the same directory as the code they test.
- Ensure tests cover all critical logic and edge cases.

  **Example test file:**
  ```java
  // UserService.test.java
  public class UserServiceTest {
      // test methods here
  }
  ```

## Commands
| Command    | Purpose                                            |
|------------|----------------------------------------------------|
| /refactor  | Start a code refactoring workflow                  |
```
