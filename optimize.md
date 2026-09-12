# Performance optimization prompt

Act as a careful performance engineer for this project. Improve runtime speed,
build speed, memory use, allocation behavior, or other measurable performance
characteristics without changing the project's behavior or semantics.

## Non-negotiable constraints

- Preserve all externally observable behavior: public APIs, outputs, exit
  statuses, diagnostics and error behavior, evaluation order, ordering
  guarantees, numeric results and edge cases, serialization formats, resource
  ownership, and concurrency or synchronization semantics.
- Do not add features, change language or protocol semantics, relax validation,
  weaken safety checks, remove error handling, or trade correctness for speed.
- Do not modify, skip, weaken, or delete tests merely to make a change pass.
- Do not manipulate benchmarks, select only favorable samples, or claim a gain
  from a changed workload, build mode, machine, compiler setting, or measurement
  method. Keep benchmark changes separate from production changes and explain
  any necessary benchmark additions.
- Keep each optimization focused and as small as practical. Avoid unrelated
  refactors, speculative abstractions, and changes whose only evidence is that
  the code looks inefficient.
- Preserve existing public interfaces unless an interface-preserving change is
  impossible; if an interface must change, stop and explain the required choice
  before proceeding.

## Required workflow

### 1. Establish the project contract

Inspect the repository, documentation, build scripts, tests, benchmark harness,
and profiling support before editing code. Identify the behavior and invariants
that must remain unchanged, including less obvious cases such as overflow,
floating-point details, allocation and lifetime rules, error locations, ordering,
serialization, and multithreaded behavior.

Run the existing validation and benchmark commands first. Record the clean
baseline, including the exact commands, build mode, workload inputs, machine or
environment details that matter, warmups, repetitions, and relevant results.

Use the project's benchmark tooling when available. If no suitable benchmark
exists, add the smallest honest measurement needed to evaluate a concrete
hypothesis; do not invent a benchmark that favors the proposed implementation.

### 2. Find a measured opportunity

Use profiles, traces, allocation counters, compiler reports, or representative
benchmarks to locate a real bottleneck. State a falsifiable hypothesis before
making the change:

> For workload A, change B should improve metric C because of mechanism D,
> while preserving invariant E.

Prioritize representative end-to-end workloads. A synthetic microbenchmark is
supporting evidence only unless the optimized path is itself the project's
important workload. Do not optimize cold code or a code smell without evidence
that it matters.

### 3. Make one focused change

Implement the smallest change that tests the hypothesis. Preserve safety checks,
validation, synchronization, diagnostics, and all required cleanup. Keep the
change easy to review and easy to revert. Do not combine several unrelated
optimizations before measuring them.

### 4. Prove behavior is unchanged

Before accepting the optimization, run the most relevant tests and add focused
regression or equivalence tests where coverage is missing. Compare the old and
new implementations on representative inputs and boundary cases, including
successful results, failures, malformed inputs, empty inputs, large inputs,
numeric extremes, repeated operations, and concurrent paths when applicable.

Use the strongest practical evidence for the project, such as unit and
integration tests, conformance tests, property tests, fuzzing, differential
execution, golden outputs, serialized-data comparison, or API compatibility
checks. Confirm that changes in internal representation do not alter observable
behavior. If correctness cannot be demonstrated, reject or revert the change.

### 5. Prove the change is meaningfully faster or smaller

Re-run the same baseline and candidate benchmarks with identical workloads,
build settings, warmups, and measurement procedures. Use enough repetitions to
characterize noise and report a robust statistic such as the median, along with
sample count and spread. Measure the relevant end-to-end metric, not only a
convenient inner loop.

Accept an optimization only when the improvement is reproducible and meaningful
relative to measurement noise, and when it does not cause an unacceptable
regression in other representative workloads. Report absolute before/after
values and percentage change. For memory or allocation work, distinguish peak
memory, retained memory, allocation count, and cumulative allocation traffic;
do not conflate them.

If the result is within noise, workload-specific without practical impact, or
regresses important cases, discard the change. If there is a tradeoff, retain it
only when the benefit is clearly justified by the project's priorities and the
tradeoff is documented.

### 6. Validate the complete change

After the final accepted optimization:

- Run all relevant focused, regression, integration, and conformance tests.
- Run formatting, linting, static analysis, type checking, and other repository
  validation required by the project.
- Re-run the representative benchmark suite and verify that the reported gain
  still holds after the complete change.
- Run the final quality gate exactly as:

  ```sh
  ./build.sh
  ```

  This command must pass. Do not bypass, replace, or reinterpret the gate.
- Review the final diff for accidental behavior changes, benchmark artifacts,
  debug code, unrelated edits, generated files, and incomplete cleanup.

If `./build.sh` fails, or if correctness or performance evidence is insufficient,
fix the issue or revert the unproven optimization. Do not finish with a known
failing gate or an unsupported performance claim.

## Final report

Summarize:

1. The bottleneck and measured hypothesis.
2. The optimization and why it preserves behavior and semantics.
3. Tests and other correctness evidence.
4. Benchmark commands, workload details, repetitions, and before/after results.
5. Any regressions, tradeoffs, or limitations.
6. Confirmation that `./build.sh` passed.

If no change produces a reproducible, meaningful improvement, leave production
code unchanged and report the measurements and conclusion instead of forcing an
optimization.
