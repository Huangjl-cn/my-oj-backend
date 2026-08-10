# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Spring Boot 3.5 backend built with Java 21 and Maven. Production code lives under `src/main/java/com/hjl/oj`: HTTP endpoints in `controller`, business rules in `service`, persistence interfaces in `mapper`, and domain types in `model/{entity,dto,vo,enums}`. Keep sandbox adapters and language strategies in the `judge` pipeline.

Resources are in `src/main/resources`; MyBatis XML mappings use `resources/mapper`, and settings use `application.yml` plus `application-{profile}.yml`. Tests mirror production packages under `src/test/java`. Database bootstrap SQL is in `sql/create_table.sql`; container files remain at the root.

## Architecture & Judge Workflow

The active submission endpoints are in `QuestionController` under `/question/question_submit/*`; `QuestionSubmitController` is deprecated and should not receive new behavior. Preserve this judging flow when changing submission code:

1. `QuestionSubmitServiceImpl.doQuestionSubmit` validates the language and question, stores a `WAITING` submission, then schedules `JudgeService.processSubmission` on the dedicated Java 21 virtual-thread `judgeExecutor`. The HTTP response contains only the generated submission ID.
2. `JudgeServiceImpl` atomically claims the task with `WAITING -> RUNNING`; a conditional update prevents duplicate execution. It then runs the sandbox and result pipeline.
3. `JudgeServiceImpl` validates structured judge cases and encodes each case as `cases[].args[]`. `CodeSandboxFactory` selects `remote`, `example`, or `thirdParty`; `CodeSandboxProxy` adds logging. The remote adapter sends every supported language to `/executeCode`, while the sandbox only compiles and executes the exact argument vectors.
4. `JudgeManager.applyStrategy` resolves an enum-keyed language strategy. `AbstractJudgeStrategy` owns execution-error and resource-limit checks, language strategies only supply justified resource allowances, and the shared `JudgeOutputComparator` parses JSON output and compares typed values.
5. A completed pipeline conditionally changes `RUNNING -> SUCCEED`; an exception attempts `RUNNING -> FAILED`. `SUCCEED` means judging completed, not Accepted. The verdict, first wrong case, or sandbox diagnostic is carried in `judgeInfo.message`.

Keep `WAITING -> RUNNING -> SUCCEED|FAILED` and the sandbox boundary explicit. Any concurrency, retry, or failure-handling change must test duplicate execution and terminal states. Question fields `tags`, `judgeCase`, and `judgeConfig`, plus submission `judgeInfo`, are JSON stored in text columns. Structured `judgeCase` values use `JudgeCaseDataService`; preserve the existing conversions for the other fields.

## Build, Test, and Development Commands

- `mvn clean package` compiles, tests, and creates `target/my-oj-0.0.1.jar`.
- `mvn spring-boot:run` starts the default `dev` profile on port 8102 with the `/api` context path.
- `mvn test` runs the complete JUnit 5 suite.
- `mvn "-Dtest=!CodeSandboxTest" test` runs the offline suite without the live sandbox integration class.
- `mvn test -Dtest=CodeSandboxTest` runs one test class.
- `docker compose up -d` starts MySQL and the packaged backend; follow the Dockerfile's root-JAR requirement first.

Local development requires JDK 21, Maven, MySQL, and the schema from `sql/create_table.sql`. Development API docs are at `http://localhost:8102/api/doc.html`.

## Coding Style & Naming Conventions

Use four-space indentation and existing Spring/Lombok patterns. Name classes in PascalCase, methods and fields in camelCase, constants in UPPER_SNAKE_CASE, and tests as `*Test`. Put request models in `model/dto/<feature>`. Preserve controller-service-mapper boundaries and use `BaseResponse`, `ResultUtils`, and `BusinessException` for API behavior. No formatter or linter is configured; match nearby code.

## Testing Guidelines

Use JUnit 5 with Spring Boot Test. Add focused tests beside the corresponding package and cover both success and validation paths. `CodeSandboxTest.executeCode` calls an external sandbox and will fail when that service is unavailable; prefer the `example` sandbox for offline tests. There is no configured coverage threshold.

## Commit & Pull Request Guidelines

History contains only short initialization messages, so no formal convention is established. Use concise, imperative subjects such as `fix judge status transition`. Pull requests should explain behavior changes, list configuration or schema impacts, link issues, and include commands run. Add examples when API contracts change.

## Security & Configuration

Do not commit real database passwords, sandbox credentials, or production addresses. Keep profile-specific values out of shared configuration and override sensitive deployment settings through the deployment environment. Never run submitted code inside this backend; use the isolated remote sandbox boundary.
