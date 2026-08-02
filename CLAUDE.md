# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Online Judge (在线判题系统) backend: Spring Boot 2.7.2 / Java 1.8 / MyBatis-Plus 3.5.2 / MySQL / Hutool / Knife4j / Lombok. Single Maven module (`com.hjl.oj`, artifact `my-oj`). All code comments and the README are in Chinese.

## Commands

- Build: `./mvnw.cmd clean package` (Windows) — jar lands at `target/my-oj-0.0.1.jar`
- Run: `java -jar target/my-oj-0.0.1.jar` (default profile is `dev`)
- API docs (Knife4j): `http://localhost:8102/api/doc.html`
- There is **no test directory** (`src/test` is absent); `spring-boot-starter-test` is on the classpath but nothing uses it yet.
- Docker: `docker compose up -d` builds the backend image + MySQL 8 (`my-oj-db`, host port 3307). Note the Dockerfile expects `my-oj-0.0.1.jar` copied to the repo root before building — the jar is not produced there by default.

## Configuration

- `application.yml` is the dev profile (port **8102**, `codesandbox.type: remote`, `codesandbox.url: http://localhost:8100`); `application-prod.yml` uses port 8101 and connects to the compose DB host `my-oj-db`. The README's "8101" is stale.
- Redis is disabled: the spring-data-redis deps are commented out in `pom.xml` and session `store-type: redis` is commented in the yml. Sessions are in-memory HTTP sessions.
- `codesandbox.type` switches the sandbox: `remote` | `thirdParty` | `example` (note camelCase `thirdParty`, not "thirdparty" as the README says).
- The `codesandbox.url` points at a **separate code-sandbox service** (not in this repo) that actually compiles/runs user code in Docker.

## Architecture

Layered Spring Boot app: `controller` → `service/impl` (MyBatis-Plus `ServiceImpl` + `QueryWrapper`) → `mapper` → MySQL. `common/` holds `BaseResponse` + `ResultUtils` + `ErrorCode` + `PageRequest`; `exception/` holds `BusinessException` + `GlobalExceptionHandler` + `ThrowUtils` — services throw `BusinessException(ErrorCode.X)` and the handler renders the unified `BaseResponse` shape.

### Judging pipeline (the core, spans multiple packages)

Submission flow, `QuestionSubmitServiceImpl.doQuestionSubmit` → `JudgeService.doJudge`:

1. `doQuestionSubmit` validates language/question, saves a `QuestionSubmit` with `status=WAITING`, then fires judging **asynchronously** via `CompletableFuture.runAsync` — the submit endpoint returns immediately with the submit id.
2. `JudgeServiceImpl.doJudge(questionSubmitId)` fetches submit + question, refuses to re-judge unless status is WAITING, flips status to RUNNING, then:
   - Builds `ExecuteCodeRequest` from the user's code + the question's `judgeCase` JSON (inputs only).
   - Gets a `CodeSandbox` via `CodeSandboxFactory.newInstance(type)` (factory pattern), wrapped in `CodeSandboxProxy` (proxy pattern — just request/response logging).
   - Packs results into a `JudgeContext` and delegates to `JudgeManager.doJudge`, which picks a `JudgeStrategy` by language: `JavaLanguageJudgeStrategy` vs `DefaultJudgeStrategy` (strategy pattern). Java gets a +2000ms time allowance for JVM startup.
3. The strategy compares sandbox `JudgeInfo` (time/memory) and outputs against the question's `judgeConfig` limits and `judgeCase` expected outputs, returning a verdict (`JudgeInfoMessageEnum`: ACCEPTED / WRONG_ANSWER / COMPILE_ERROR / TIME_LIMIT_EXCEEDED / …).
4. Final status + `judgeInfo` (JSON) are written back to the `QuestionSubmit` row.

To extend: add an implementation of `CodeSandbox` and a case in `CodeSandboxFactory`; add a language by implementing `JudgeStrategy` and registering it in `JudgeManager`.

### Sandbox implementations

- `RemoteCodeSandbox` (the real one): HTTP POST to `codesandbox.url + /executeCode` (Java) or `/executeCodeByAI` (other languages) via Hutool `HttpUtil`, with a hardcoded `auth: secretKey` header. Auth keys are hardcoded constants, not config.
- `ExampleCodeSandbox`: fake success for wiring/testing.
- `ThirdPartyCodeSandbox`: placeholder.

### Auth

Session-based: `UserServiceImpl.userLogin` stores the `User` object in `request.getSession()` under `USER_LOGIN_STATE`; `getLoginUser(request)` reads it back. Passwords are MD5 of `SALT + password` (SALT constant is `"nelson"`). Role checks are done with the `@AuthCheck(mustRole = "admin")` annotation, enforced by the `AuthInterceptor` AOP aspect; roles are `user`/`admin`/`ban` (`UserRoleEnum`).

## Data conventions

- **`map-underscore-to-camel-case: false`** — DB columns are already camelCase (`userId`, `questionId`, `isDelete`). Entities use camelCase fields matching column names exactly; never write snake_case column names.
- Global **logic delete** on `isDelete` (0 = alive, 1 = deleted) — always pass `isDelete = false` in hand-built `QueryWrapper`s (see `getQueryWrapper` in the services).
- JSON is stored in `text` columns: `question.judgeCase` (list of `{input, output}`), `question.judgeConfig` (`{timeLimit, memoryLimit, stackLimit}`), `question.tags` (array), `question_submit.judgeInfo`. Serialize/parse with Hutool `JSONUtil` (`JSONUtil.toJsonStr` / `JSONUtil.toList`).
- Submit status enum (`QuestionSubmitStatusEnum`): 0-WAITING, 1-RUNNING, 2-SUCCEED, 3-FAILED.
- `QuestionSubmitVO` desensitizes code: only the submit owner or an admin sees the code (others get `null`).
- Schema bootstrap: `sql/create_table.sql` (creates `oj_db` + tables + seeded admin user, password `12345678`).
