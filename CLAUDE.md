# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build all modules
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Run all tests
mvn test

# Run tests for specific module
mvn test -pl flow-forge-core-model

# Run single test class
mvn test -Dtest=WorkflowDefinitionTest

# Run single test method
mvn test -Dtest=WorkflowDefinitionTest#shouldParseSimpleWorkflow
```

## Project Architecture

Flow-Forge is an enterprise-grade DAG (Directed Acyclic Graph) workflow engine designed for private deployment. The architecture follows a multi-module Maven structure with clear separation of concerns.

### Module Dependencies

```
flow-forge-api (REST layer)
  ├── flow-forge-visualizer
  ├── flow-forge-trigger
  ├── flow-forge-nodes
  │     ├── flow-forge-engine
  │     │     ├── flow-forge-core-model
  │     └── flow-forge-infrastructure
  └── flow-forge-infrastructure
```

### Core Architecture Concepts

**DAG Execution Model**:
- Workflows are defined as JSON DSL containing `nodes` and `edges`
- The `InDegreeScheduler` tracks node dependencies using AtomicInteger counters
- When a node completes, successor node in-degrees are decremented; nodes with in-degree=0 are dispatched
- `WorkflowDispatcher` uses Java 21 virtual threads for high-concurrency execution

**Variable Resolution**:
- Variables use `{{expression}}` syntax
- Supported patterns: `{{nodeId.output}}`, `{{global.varName}}`, `{{.input.key}}`, `{{system.executionId}}`
- `VariableResolver` handles JSONPath-style nested access: `{{node1.output.data.userId}}`

**Graph Structure**:
- `WorkflowDefinition` wraps JGraphT `DefaultDirectedAcyclicGraph` (transient, not serialized)
- Graph validation includes cycle detection, isolated node detection, and reference integrity
- `CycleDetector` uses JGraphT's `CycleDetector` + Johnson's algorithm for finding simple cycles

### Key Classes by Module

**flow-forge-core-model** (domain layer):
- `WorkflowDefinition` - Main DAG container with JGraphT graph, validation, and traversal methods
- `Node` / `Edge` - Graph primitives with `NodeType` enum (HTTP, LOG, SCRIPT, IF, MERGE, WEBHOOK, WAIT, START, END)
- `ExecutionContext` - Runtime state with `nodeResults` map, variable resolution, checkpoint support
- `NodeResult` - Execution outcome with status, output, duration, large result handling
- `CheckpointData` - Serialized state for recovery (in-degree snapshot, completed nodes)
- `WorkflowDslParser` - JSON DSL parser with Jackson
- `JsonDslValidator` - Pre-validation with detailed error messages

**flow-forge-engine** (scheduling):
- `InDegreeScheduler` - Topological sort, in-degree tracking, ready node detection, snapshot/restore for checkpoints
- `CycleDetector` - JGraphT wrapper with detailed cycle reporting
- `VariableResolver` - JSONPath-based variable resolution with sanitization
- `CheckpointService` / `CheckpointRecoveryService` - Persistence and recovery (to be implemented)

**flow-forge-nodes** (execution):
- `NodeExecutor` interface - All node types implement this
- `NodeExecutorFactory` - Spring-managed factory returning executor by NodeType
- HTTP/Log/Script/Condition/Merge/Wait executors (to be implemented)
- `GraalSandbox` - Secure polyglot execution with resource limits (to be implemented)

**flow-forge-infrastructure** (persistence):
- JPA entities for `workflow_execution_history`, `node_execution_log`
- PostgreSQL JSONB for context storage
- Multi-tenant filtering via `TenantContext` (ThreadLocal)
- MinIO for large results (>2MB threshold)

### Variable Reference Patterns

| Pattern | Resolves To |
|---------|-------------|
| `{{nodeId}}` | Complete NodeResult object |
| `{{nodeId.output}}` | Node's output map |
| `{{nodeId.output.data.userId}}` | Nested value via JSONPath |
| `{{global.apiKey}}` | Global variable |
| `{{.input.userId}}` | Input parameter |
| `{{system.executionId}}` | System variable (executionId, workflowId, tenantId, currentTime, startTime, status) |

### Workflow JSON DSL Structure

```json
{
  "id": "workflow-id",
  "name": "Workflow Name",
  "tenantId": "tenant-001",
  "globalVariables": {"key": "value"},
  "nodes": [
    {
      "id": "unique-id",
      "name": "Display Name",
      "type": "http|log|script|if|merge|webhook|wait|start|end",
      "config": {"typeSpecific": "values"},
      "retryCount": 3,
      "timeout": 5000
    }
  ],
  "edges": [
    {
      "sourceNodeId": "node-1",
      "targetNodeId": "node-2",
      "condition": "{{node1.output.status}} == 200"  // optional
    }
  ]
}
```

## Development Status

**Completed** (Week 1-2): Core models, DSL parser, cycle detection, in-degree scheduling, variable resolution

**In Progress**: Week 3+ - Node executors, GraalVM sandbox, checkpoint services, triggers, multi-tenant, API layer

See `plan.md` for detailed task breakdown.

## Technology Constraints

- **Java 21** with virtual threads - avoid `synchronized`, prefer `ReentrantLock`
- **GraalVM** 23.1.0 for polyglot script execution - requires local installation or Docker
- **No Flyway** - SQL scripts manual execution (in `flow-forge-infrastructure/src/main/resources/db/`)
- **Multi-tenant** - All tables have `tenant_id`, API requires `X-Tenant-ID` header
- **Large results** (>2MB) auto-stored in MinIO, context holds only `blob_id` reference

## GraalVM Sandbox Integration Guidelines

When working with the GraalVM sandbox (`GraalSandbox`), follow these patterns:

### 1. Virtual Thread Compatibility
**Use platform thread executor for all GraalVM Context operations.** GraalVM's optimizing Truffle runtime does not support virtual threads. Execute all Context operations on platform threads, while virtual threads only submit tasks and wait for results.

```java
private static final ExecutorService PLATFORM_EXECUTOR = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors(),
        r -> new Thread(r, "GraalSandbox-Platform"));

public SandboxResult execute(String code, Map<String, Object> bindings) {
    Future<Object> future = PLATFORM_EXECUTOR.submit(() -> {
        // All Context operations here (on platform thread)
        Value result = context.eval(LANGUAGE_ID, wrappedCode);
        return new SandboxResult(...);
    });
    return future.get(timeoutMs, TimeUnit.MILLISECONDS); // virtual thread waits
}
```

### 2. Error Handling Strategy
**Distinguish between script errors and security violations.** Script errors (syntax, runtime) should return failed `SandboxResult` objects. Security violations (Access denied, resource limits) should throw `WorkflowException`.

```java
private SandboxResult handleExecutionException(Throwable e) {
    String message = e.getMessage();
    // Security violations - throw exception
    if (message.contains("Access denied") || message.contains("not allowed")) {
        throw new WorkflowException("Script security violation: " + message, e);
    }
    // Resource limits - throw exception
    if (message.contains("statement limit") || message.contains("memory limit")) {
        throw new WorkflowException("Script resource limit exceeded: " + message, e);
    }
    // Script errors - return failed result
    return new SandboxResult(null, message, 0, false);
}
```

### 3. Variable Access Pattern
**Flatten input variables for `__input.x` access, extract `__global` and `__system` for direct access.** Bindings Map is bound as `__bindings`, then the wrapper extracts special variables for direct script access.

```java
// ScriptNodeExecutor.prepareBindings() - flatten input
if (context.getInput() != null) {
    bindings.putAll(context.getInput()); // x, y directly accessible
}
bindings.put("__global", context.getGlobalVariables());
bindings.put("__system", systemVars);

// GraalSandbox.wrapCode() - extract for direct access
const __input = __bindings || {};
const __global = __input.__global || {};
const __system = __input.__system || __input.system || {};
```

## Exception Handling

Use `WorkflowException` for runtime errors and `WorkflowValidationException` for validation failures (e.g., cycle detection, missing nodes).

## Git Commit Conventions

**Commit Message Format**:
```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**:
- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation changes
- `test` - Adding or updating tests
- `refactor` - Code refactoring (no functional change)
- `perf` - Performance improvement
- `style` - Code style changes (formatting, etc.)
- `chore` - Build process or tooling changes
- `revert` - Revert a previous commit

**Examples**:
```
feat(nodes): implement HTTP node executor with variable resolution

feat(engine): add checkpoint service for workflow recovery

fix(scheduler): handle edge case when node has no outgoing edges

docs(api): update OpenAPI specification for execution endpoints

test(core-model): add unit tests for WorkflowDefinition validation

refactor(scheduler): extract in-degree calculation to separate method
```

**CRITICAL: Commit Message Rules**:
1. **NEVER** include tool signatures like `Co-Authored-By: Claude Opus` or similar
2. **NEVER** mention AI tools, Claude, GPT, or any automated code generation tools in commit messages
3. Keep subject line under 50 characters
4. Use imperative mood ("add" not "added" / "adding")
5. Reference issue/ticket numbers in footer if applicable

**Incorrect Examples** (DO NOT USE):
```
feat: add HTTP node executor

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

```
fix: fix node execution bug

Generated with Claude Code assistance
```

**Correct Example**:
```
feat(nodes): implement HTTP node executor

Add HttpNodeExecutor with support for GET/POST/PUT/DELETE methods,
custom headers, request body, timeout control, and variable resolution
using {{}} syntax.

Fixes #123
```

## Task Tracking & Plan Management

**MANDATORY: When working on tasks from plan.md, you MUST maintain task traceability.**

### Task Lifecycle Rules

1. **Task Start**: When beginning a task from `plan.md`:
   - Update the task checkbox from `🔲` to `🔄` (in progress)
   - Add a `# <commit-short-hash>` reference next to the task
   - Create a corresponding git commit with the task ID in the subject

2. **Task Completion**: When finishing a task:
   - Update the task checkbox from `🔄` to `✅` (completed)
   - Update the commit reference to `# abc1234` (final commit hash)

3. **Commit-Task Association**:
   - Every commit MUST reference the task ID from `plan.md` (e.g., `#3.1`, `#7.4`)
   - Format: `<type>(<scope>): <task-id> <brief description>`
   - The task ID enables bidirectional traceability between code and plan

### Task ID Format

Tasks in `plan.md` follow the format `W<week>-<task-number>`:
- `W3-1`: Week 3, Task 1 (NodeExecutor接口)
- `W3-3`: Week 3, Task 3 (HTTP节点)
- `W7-4`: Week 7, Task 4 (CheckpointService)

### Example Workflow

**Step 1: Start Task W3-3 (HTTP节点)**
```bash
# Update plan.md task status
🔲 | 3.3 | HTTP节点 |  →  🔄 | 3.3 | HTTP节点 | # (pending)

# Create feature branch (optional)
git checkout -b feature/w3-3-http-node
```

**Step 2: Implement and commit**
```bash
# During development, commit with task ID
git commit -m "feat(nodes): W3-3 implement HTTP node executor base structure

Add HttpNodeExecutor class with RestTemplate, timeout configuration,
and placeholder for variable resolution integration.

Task: W3-3"
```

**Step 3: Complete Task**
```bash
# Final commit
git commit -m "feat(nodes): W3-3 complete HTTP node with variable resolution

Integrate VariableResolver for config parsing, add full support for
GET/POST/PUT/DELETE methods, headers, body, and timeout.

Task: W3-3"

# Update plan.md
🔄 | 3.3 | HTTP节点 | # abc1234  →  ✅ | 3.3 | HTTP节点 | # abc1234
```

### plan.md Task Format

```markdown
| ID | 任务 | 文件路径 | 状态 | 提交记录 |
|----|------|----------|:----:|----------|
| 3.3 | HTTP节点 | .../http/HttpNodeExecutor.java | 🔲/🔄/✅ | # abc1234 |
```

**Status Indicators**:
- `🔲` - Pending (未开始)
- `🔄` - In Progress (进行中)
- `✅` - Completed (已完成)
- `❌` - Failed/Blocked (失败/阻塞)

### Required Agent Behavior

**BEFORE starting any task from plan.md**:
1. Read the current `plan.md` to understand task context
2. Update task status to `🔄`
3. Note the task ID for commit messages

**AFTER completing any task**:
1. Ensure all tests pass
2. Create final commit with task ID in subject
3. Get the commit short hash: `git rev-parse --short HEAD`
4. Update `plan.md` task status to `✅` with commit reference

**Template for plan.md updates**:
```markdown
### 📋 Week X: [Title]

**目标**: [Description]

| ID | 任务 | 文件路径 | 功能描述 | 注意事项 | 状态 | 提交 |
|----|------|----------|----------|----------|:----:|-----|
| X.Y | [Name] | [Path] | [Desc] | [Notes] | 🔲 | - |
```

### Why This Matters

- **Traceability**: Each line of code can be traced back to a planned task
- **Impact Analysis**: When bugs arise, git log with task IDs shows what was changed and why
- **Progress Tracking**: `plan.md` becomes a live dashboard of project status
- **Code Review**: Reviewers can see the full context via task ID in plan.md
