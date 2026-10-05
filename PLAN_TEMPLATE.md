# Solvik Implementation Plan — <outcome-focused title>

**Status:** <Draft / In progress / Complete / Blocked / Deferred / Superseded>
**Request scope:** <Plan only / Implementation requested; cite the user request>
**Affected subsystems:** <Compiler stages, runtime, launcher, tests, documentation>
**Related:** <Issue, prerequisite plans, superseded plans, or none>
**Evidence baseline:** <Date, branch/commit, and relevant worktree changes>

> Read `AGENTS.md` and copy this template to `docs/<DESCRIPTIVE-NAME>-PLAN.md`, unless the
> user specifies a location. Preserve an existing plan's path when updating it. Replace
> placeholders with concrete findings and decisions; scale detail to the change. Keep goals,
> evidence, authority, scope, implementation steps, coverage, acceptance, and validation records.
> Mark irrelevant sections not applicable with a reason. Remove these authoring notes.
>
> Use authorization already supplied by the user; writing a plan does not authorize unrelated
> implementation. Historical plans record earlier decisions, not current behavior. Reinspect
> source and tests. Do not mark implementation complete until `./build-all.sh` passes.

## 1. Goal and observable behavior

<Describe the problem and requested outcome. Distinguish reported symptoms from verified
defects. State behavior precisely enough to decide it with a test.>

- **Required behavior:** <Accepted Solvik programs and their observable results.>
- **Required rejection:** <Invalid programs, compilation phase, diagnostic code and source span.>
- **Preserved behavior:** <Existing semantics and GraalVM/Truffle integration affected by the change.>

For a language feature, include minimal positive and negative `.sol` examples that obey the
specification's physical-line and brace-placement rules. For tooling or documentation work,
describe the equivalent observable contract without inventing language changes.

## 2. Authority and semantic contract

Read `docs/LANGUAGE_SPEC.md` and `docs/ARCHITECTURE.md` before language-design changes.
Authority is `AGENTS.md` > language specification > architecture > this plan.

| Authority | Applicable section / requirement | Consequence for this change |
| --- | --- | --- |
| `AGENTS.md` | <Repository constraint> | <Implementation or validation requirement> |
| `docs/LANGUAGE_SPEC.md` | <Section and current revision, or not applicable with reason> | <Normative syntax/semantics> |
| `docs/ARCHITECTURE.md` | <Section, or not applicable with reason> | <Compiler/runtime boundary> |

**Conflicts / unresolved semantics:** <None, or exact conflicting requirements or unspecified
choice. Stop dependent work and report it; do not invent semantics or implement deferred features.>

If the user explicitly requests a specification revision, describe the authorized contract change
and corresponding documentation/TCK impact. A plan alone cannot override the current specification.

## 3. Current implementation and evidence

Inspect the current worktree before choosing an approach. Use real paths and symbols, not stale
line numbers or claims copied from earlier plans.

| Evidence | Location / exact command | Verified finding |
| --- | --- | --- |
| <Implementation> | <Path and symbol> | <Current behavior and owning phase> |
| <Existing coverage / reproducer> | <Test name or command> | <Actual result, or explicitly not run> |
| <Build / distribution integration> | <Wrapper or configuration path> | <Retained infrastructure and affected consumers> |

- **Root cause / gap:** <Causal path; label unverified hypotheses.>
- **Infrastructure to retain:** <Truffle specialization, primitive storage, shapes, interop,
  instrumentation, source mapping, build/launcher support, as applicable.>
- **Behavior to replace / remove:** <Legacy syntax, dynamic semantics, public identity, or none.>
- **Baseline:** <Pre-existing failures, modified/untracked files to preserve, and unknowns.>

## 4. Scope and compiler/runtime design

**In scope:** <Smallest coherent vertical slice, including required integration and documentation.>

**Out of scope:** <Unrelated cleanup, deferred features, or follow-ups.>

**Affected files / modules:** <Actual paths in `language`, `launcher`, `standalone`, `tck`, or docs.>

Describe the affected path through the required pipeline and the owner of each decision:

```text
Solvik source
  -> lexer / physical-line separation
  -> parser
  -> language AST
  -> symbol/name resolution
  -> static type analysis
  -> semantic validation
  -> typed/lowered representation
  -> Truffle AST execution
```

| Stage / boundary | Planned change, or unchanged with reason | Contract / invariant |
| --- | --- | --- |
| Lexer / physical-line separation / parser | <Grammar and layout handling> | <Newline, same-line separator, braces, raw-string delimiters> |
| Syntax AST / source identity | <Nodes and source spans> | <Syntax separate from executable nodes; physical-file identity retained> |
| Resolution / static types / semantic validation | <Symbols, assignability, flow and validation facts> | <Errors rejected before lowering; precise diagnostics> |
| Typed representation / lowering | <Checked facts consumed and executable nodes produced> | <Error-free programs only; no repeated semantic analysis> |
| Truffle runtime / objects / calls | <Specializations, storage, dispatch, evaluation order> | <Execution semantics and primitive representations preserved> |
| Interop / instrumentation / launcher / native image | <Consumers and distribution assets> | <Source mapping, public identity and GraalVM integration preserved> |

### Decisions and constraints

| Decision | Chosen design | Rationale / alternatives considered |
| --- | --- | --- |
| <Implementation choice within the authoritative contract> | <Concrete approach and owner> | <Why it satisfies the contract> |

- Reuse useful GraalVM/Truffle infrastructure; this is an in-place conversion, not a new VM.
- Add no SimpleLanguage compatibility flag, dual parser, legacy mode, or dynamic typing escape.
  Remaining upstream names are allowed only in copyright notices and historical attribution.
- Use the Truffle AST backend only. Do not add a second execution backend.
- Preserve primitive specialization/storage; do not box primitives merely because they have class types.
- Preserve strong static typing, final-by-default classes, single class inheritance, and explicit
  composition/delegation. Do not introduce implicit `switch` fallthrough.
- Apply the specification's physical-line rules: a newline ends a complete statement; `;` only
  separates constructs on the same physical line and never terminates one. Do not add JavaScript ASI.
- Follow the Rust-style raw-string delimiter model in the specification.
- Edit `Solvik.g4`, never generated ANTLR parser output. New compiler-front-end code uses `org.solvik`.

## 5. Ordered implementation steps

List dependency-ordered, buildable vertical slices. Each step identifies files/symbols, behavior,
positive and negative coverage, and a completion condition. Avoid broad unfinished scaffolding.

1. **Contract and regression — `<paths / tests>`**
   - <Pin the specified behavior or reproduce the defect at the owning boundary.>
   - Done when: <The regression demonstrates the gap; unrelated baseline failures are identified.>
2. **Compiler / runtime slice — `<paths / symbols>`**
   - <Make the coherent change across necessary stages; retain reusable infrastructure.>
   - Done when: <Positive tests pass; invalid input is rejected at the correct phase and span.>
3. **Integration and documentation — `<consumers / corpus / docs / TCK>`**
   - <Align entry points, examples, golden outputs and authoritative documentation.>
   - Done when: <Embedded execution and shipped distributions agree with the semantic contract.>
4. **Review and final validation**
   - <Run focused checks, review the complete diff, fix findings, and run `./build-all.sh`.>
   - Done when: <Required checks pass; the execution record contains actual results.>

Reinspect `git status --short` before execution and preserve unrelated user work. Do not stage,
commit or push unless authorized. For documentation-only work, adapt these steps and explain why
semantic tests are not applicable.

## 6. Positive, negative, and boundary coverage

Add positive and negative tests for every semantic feature. Choose the lowest owning boundary,
then cover embedded `Context.eval` and shipped entry points where relevant. Use deterministic
fixtures and temporary resources owned by the tests.

| Scenario | Expected result / rejection | Test path and name / corpus entry |
| --- | --- | --- |
| Valid feature / normal execution | <Static type and observable value/output> | <Parser, semantic or execution test> |
| Invalid syntax / physical-line boundary | <Diagnostic code and source span> | <Parser/layout rejection test> |
| Invalid types / resolution / semantic rule | <Compile-time diagnostic; no executable call target> | <Negative semantic test> |
| Boundary values / evaluation order / control flow | <Specified result, ordering or short-circuit behavior> | <Execution regression> |
| Runtime failure, when mandated by the spec | <Category, source location, output/exit behavior> | <Runtime test> |
| Embedded API and distributions | <Golden stdout; rejection exits nonzero with empty stdout> | <JUnit `.sol` suite and launcher corpus> |
| Conformance, when affected | <Requirement/oracle and implementation agree> | <TCK requirement and corpus IDs> |

Explain material exclusions. Do not substitute runtime checks for conditions provable at compile
time, weaken type checking, or change golden outputs merely to make a failing implementation pass.
For TCK changes, inspect `TCK.md` and `tck/tools/README.md`; keep requirements, oracles,
manifests and generated assets consistent using the documented workflow.

## 7. Verification and acceptance

### Planned commands — not results

Run from the repository root. For focused Maven commands, select GraalVM for JDK 25 using the
same candidates as the wrappers: `GRAALVM_HOME`, a GraalVM `JAVA_HOME`, or `/opt/graalvm`. Verify
the selected installation, set `JAVA_HOME`, and prepend `$JAVA_HOME/bin` to `PATH`. Never use the
host JDK as a fallback. Replace the focused-test placeholders with actual modules/classes:

```bash
export JAVA_HOME=<verified-graalvm-jdk-25-path>
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -pl language -am -Dtest=<TestClass> -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
./build-all.sh
```

`./build.sh` and `./build-native.sh` each run a clean Maven package. Native validation is required
for runtime, registration, launcher, or native-image changes. `./build-all.sh` runs both wrappers
and is the mandatory final gate for all work, including documentation-only changes.

The final gate must exercise the JUnit suites, the `.sol`/`.output` corpus through
`standalone/target/solvik` and `standalone/target/solvik-native`, and the wrappers' TCK validation,
conformance and JVM/native differential checks. Inspect the current wrappers for the exact checks.
Do not claim full-language TCK certification merely because executed conformance tests pass.

Do not set `SOLVIK_SKIP_CORPUS=1`, `SOLVIK_SKIP_TCK=1`, or test-skip options for the final gate.
Compile-only or focused checks cannot substitute for it. Record any additional relevant checks
and their prerequisites, such as TCK regeneration verification when generated assets change.

### Acceptance checklist

- [ ] Requested behavior follows the authoritative semantic contract; no unresolved conflicts remain.
- [ ] Useful GraalVM/Truffle infrastructure is retained; no legacy compatibility or second backend added.
- [ ] Every changed semantic feature has positive and negative assertions at the appropriate phases.
- [ ] Diagnostic codes, source spans, evaluation order and primitive storage are verified as applicable.
- [ ] Embedded API, launcher corpus, affected TCK inputs and documentation agree.
- [ ] Focused checks and `./build-all.sh` pass without skipped required checks.
- [ ] Complete diff reviewed; `git diff --check` passes; unrelated user changes preserved.
- [ ] Actual results are recorded; no failed, blocked or unrun check is described as passed.

The repository's confidence goal is 100%. Keep implementation incomplete while a required check
fails, remains unrun, or an acceptance requirement remains unresolved.

## 8. Risks, open decisions, and follow-ups

| Risk / question | Impact | Evidence / mitigation / decision | Blocking? |
| --- | --- | --- | --- |
| <Semantic ambiguity, compiler boundary, integration or native-image risk> | <Failure mode> | <Evidence needed or resolved choice> | <Yes / no> |

Resolve behavior-defining questions before dependent implementation. Separate deferred
improvements from acceptance requirements. Describe reversion constraints where applicable.

## 9. Execution record and handoff

Keep actual execution separate from the proposed design. Update status based on evidence.

- **Implemented:** <Behavior and files changed, or “plan only; no implementation performed.”>
- **Deviations:** <Changes from the planned design and rationale, or none.>
- **Documentation / conformance assets updated:** <Paths, or none with reason.>
- **Diff review:** <Findings resolved and unrelated changes preserved.>

| Exact command actually run | Actual result | Evidence / failure detail |
| --- | --- | --- |
| <Command> | <Passed / failed / blocked> | <Result or log/report location> |

**Not run:** <Checks and concrete reasons; never label unrun checks as passed.>
**Remaining work:** <Unresolved acceptance items, blockers, limitations and deferred follow-ups.>
**Completion:** <Plan-only deliverable, or implementation complete only after all acceptance
requirements and `./build-all.sh` pass.>
