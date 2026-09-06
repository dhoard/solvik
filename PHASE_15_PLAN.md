# Phase 15 — Semaphore

Status: **implemented (Python reference + Go + Rust).** Adds a counting
semaphore to the Phase 14 concurrency API. Built-in type names follow
`NAMING_CONSISTENCY_PLAN.md`.

## 1. Goal

Provide the missing middle ground between `Mutex` (binary, ownership-checked)
and raw condition waiting (deferred). A semaphore bounds concurrency (fixed
worker pools, resource limits) and supports producer/consumer signaling
without exposing condition variables.

Semantics follow POSIX counting semaphores (`sem_init`/`sem_wait`/`sem_post`):

- The counter starts at a caller-chosen non-negative value; zero is legal and
  yields a pure signaling semaphore.
- `acquire()` blocks until the counter is positive, then decrements it.
- `release()` increments the counter and is **unbounded**: there is no
  maximum and releasing from any thread is legal (no ownership tracking,
  unlike `Mutex`). Over-releasing is therefore *not* an error.
- A negative initial count is the only misuse: it raises catchable **E080**
  (the analogue of `sem_init` returning `EINVAL`).

## 2. API

```text
semaphore(count: Int) -> Semaphore   // count < 0 raises E080
s.acquire() -> Void                  // blocks until count > 0, then decrements
s.release() -> Void                  // increments; unbounded; any thread
```

- `Semaphore` is an opaque identity handle exactly like `Thread`, `Mutex`,
  and `Process`: copies preserve identity, equality compares identity, and
  display is the stable label `<semaphore>` (never a host address).
- Wrong argument count or a non-integer argument to `semaphore()` is a plain
  catchable runtime error, consistent with the other builtins.
- No fairness or deadlock freedom is promised. A thread blocked in
  `acquire()` can prevent runtime shutdown, exactly as a deadlocked mutex
  does (Phase 14 §2.6 policy unchanged: shutdown waits for outstanding
  threads).
- Memory ordering matches the Phase 14 model: a successful `acquire()` after
  a `release()` observes the releaser's prior writes.

## 3. Usage

Bounded worker pool: at most `width` workers run at once; the result is
independent of scheduling because every update is mutex-protected.

```solvik
package main

func main() -> Int {
    mut total: Int = 0
    lock: Mutex = mutex()
    gate: Semaphore = semaphore(3)
    worker: Func<Int> = func() -> Int {
        gate.acquire()
        try {
            lock.lock()
            try {
                total = total + 1
            } finally {
                lock.unlock()
            }
        } finally {
            gate.release()
        }
        return 0
    }
    mut i: Int = 0
    while i < 10 {
        Thread.start(ThreadDef { body: worker })
        i = i + 1
    }
    // (handles joined by the shutdown policy; see Phase 14 §2.6)
    if total != 10 {
        return 1
    }
    return 0
}
```

## 4. Implementation

Mirror the `Mutex` registration path in every backend:

- **Python** (`solvik.py`): `SemaphoreValue` wrapping
  `threading.Semaphore`; E080 on negative count; `Semaphore` added to
  `BUILTIN_TYPE_NAMES`, `PUBLIC_TYPE_NAMES`, display/identity/type-match
  tables, method signatures, and the core builtin table.
- **Go** (`internal/reference`): `semaphoreValue` in `concurrency.go`
  (counter + `sync.Cond`); registration in `types.go`, `validator.go`,
  `validator_expr.go`, `values.go`, `interp.go`, `builtins.go`.
- **Rust** (`rust/src`): `Value::Semaphore(Arc<SemaphoreState>)` with
  `Mutex`+`Condvar`; registration in `semantic_runtime.rs`,
  `semantic_types.rs`, `semantic_validator.rs`.

Blocking natives must release the backend heap lock while blocked, exactly
like `Mutex.lock()` does in Phase 14.

## 5. Tests and docs

- `test/reference/semaphore_pool.sol` — bounded pool with deterministic
  total; only main prints.
- `test/reference/runtime_errors/semaphore_misuse.sol` — E080 negative
  count.
- `test/reference/type_names.sol` — `typeOf`/`isType` for `Semaphore`.
- `test/reference/valid/thread_mutex_process_shape.sol` — extended with the
  semaphore surface (handle in a struct payload, both methods).
- Docs: `LANGUAGE.md`, `SEMANTICS.md` (E080 row), `PARITY.md`, `README.md`,
  Sublime grammar, `PHASE_STATUS.md`.

## 6. Validation gates

Same gates as Phase 14: `python3 tools/parity.py --reference-only`;
`./build.sh` full three-backend differential parity; Go race-detector sweep
of the concurrency fixtures; bounded repeated concurrency runs;
`./benchmark.sh --runs 2`.

Recorded validation (all three backends):

- `python3 tools/parity.py --reference-only` — pass.
- `./build.sh` (fresh Go/Rust binaries) — pass; full differential parity:
  "parity: all selected checks passed".
- Bounded repeated runs: `semaphore_pool` 20× Rust / 15× Go — zero
  failures.
- Go race-detector sweep (`go build -race`) over `semaphore_pool`,
  `thread_shared`, and `mutex_pool` (3× each) — no data races.
- `./benchmark.sh --runs 2` — no regression (Rust ~21.5× Python,
  Go ~0.55× Python).
- Argument checking: `semaphore()` with a wrong arity or non-`Int` argument
  raises the stable catchable error `semaphore expects an Int count` on all
  backends; `semaphore(-1)` raises E080 on all backends.

## 7. Deferred

Condition variables (`wait`/`notify`), timed acquire, weighted/priority
semaphores, and semaphore-based long-lived pool frameworks remain out of
scope.
