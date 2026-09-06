# Phase 13 — Concurrency: in-process peers and typed channels

Status: **historical / superseded by Phase 14.** The `Channel<T>`,
`channel()`, `Peer.worker`, and mailbox APIs described below were removed
without compatibility shims; see `PHASE_14_PLAN.md` for the replacement
shared-heap thread/mutex/process model. This document is retained as the
historical record of the Phase 13 design.

## 1. Objective

Give Solvik the ability to do work **"out of band"** from the caller and to
talk to whatever does that work **by passing messages** — the one capability
most backend languages treat as first-class.

This phase implements the **in-process** half of that goal: a worker runs on a
background OS thread and communicates with its creator by sending messages over
a **typed channel** `Channel<T>`. There is no shared mutable memory across
peers — state lives inside a single execution path — so data races are
structurally impossible and the same code migrates to a remote machine later
with no rewrite.

The design is **message-passing only**. `Peer.process` / `process.run`
(OS subprocesses, addressed the same way) are intentionally **deferred** to a
later phase so this one lands a single, well-understood primitive that all three
implementations can match bit-for-bit.

### 1.1 Why message passing

- **It dodges the hard problem.** Shared-memory parallelism needs a memory
  model (coherence, visibility, atomicity, ordering). Message passing never
  takes on that problem: there is exactly one sequence of mutations per peer.
- **Distribution is free later.** The boundary is "messages, not memory," so a
  thread peer can become a process peer and then a remote peer without a
  rewrite.
- **Races are structurally impossible** because no two peers touch the same
  mutable object.

### 1.2 What "true parallelism" means here

Workers run on real OS threads (`threading.Thread` / `std::thread` / Go
goroutines or threads). On Go and Rust this is genuine multi-core parallelism;
the Python reference runs them on OS threads over the GIL, which still exercises
every scheduling race without pretending to CPU parallelism.

## 2. API

### 2.1 The channel type

`Channel<T>` is a typed FIFO pipe. The element type comes from the annotation
(the constructor takes **no** type arguments, like `stack()` — writing
`Channel<Int>()` is a compile error, C061).

```solvik
func adder() -> Channel<Int> {
    return Peer.worker(func(ch: Channel<Int>) -> Int {
        while true {
            x: Int? = ch.recv()
            if x != null {
                ch.respond(x + 10)
            } else {
                break
            }
        }
        return 0
    })
}

func main() -> Int {
    ch: Channel<Int> = adder()
    r: Int? = ch.request(5)   // sends 5, blocks until respond(), gets 15
    ch.close()                // signal no more messages
    e: Int? = ch.join()       // wait for the body to finish (exit code)
    return 0
}
```

A `Channel<T>` is a first-class value: storable, copyable (value semantics), and
passable as a message — which is how a peer **carries a reply channel** inside
its request struct for peer-to-peer rendezvous.

### 2.2 `Peer.worker`

`Peer.worker(body: func(Channel<T>) -> Int) -> Channel<T>` registers a worker
by its body, starts it on a background thread, and returns the handle. The
body loops `while true { msg = ch.recv(); ... }`, processing messages until
`recv()` returns `null` (channel closed **and** drained), then returns its exit
code. No name-based discovery in this phase; a peer is reached only through the
handle it was handed.

### 2.3 Channel methods

| method | caller side | body side | meaning |
| --- | --- | --- | --- |
| `send(T)` | ✔ | | enqueue a one-way message (non-blocking) |
| `respond(T)` | | ✔ | reply to the pending `request()` |
| `request(T) -> T?` | ✔ | | `send` + block for the next `respond` |
| `recv() -> T?` | | ✔ | dequeue a message; `null` when closed+drained |
| `close()` | ✔ | | mark closed; pending `request`s get `null` |
| `join() -> Int?` | ✔ | | wait for the body; its exit code, or `null` |
| `is_done() -> Bool` | ✔ | | body has exited |
| `status() -> Int?` | ✔ | | exit code if done, else `null` |

A struct payload may carry a `Channel<R>` field, so a request can embed its own
reply channel (the peer-to-peer idiom).

## 3. Semantics

- **Blocking is safe and non-pinning.** `recv`/`request`/`close`/`join` block
  the *caller* only while that call runs. Between `Peer.worker` and the first
  `recv`/`request` the caller is free to do other work, and many peers can be
  in flight at once — so this is not cooperative async.
- **`close()` drains, it does not truncate.** Undelivered messages remain
  readable after `close()` (Go-channel semantics); a body still running drains
  them before its `recv()` returns `null`. Only *pending* `request()` callers
  receive `null`.
- **A body `throw` becomes exit code 1.** The panic is swallowed by the worker
  runtime; it is observed via `join()`, never via stdout/stderr.
- **Values are copied across the boundary.** Every message is deep-copied on
  both the send/respond side and the recv/request side, so a sender's value is
  never shared with the receiver and vice-versa. This is what makes true
  parallelism safe.
- **Null narrowing works inside an `if`.** Use `if n != null { … }`, not
  `if n == null { break }` followed by use of `n` after the block.

## 4. Concurrency architecture (why there is *no* global lock)

A naive design serializes all interpreter execution behind one lock — then
workers can never run in parallel, which defeats the purpose. The realization:
the only shared *mutable* interpreter state during execution is the per-call
type-inference stacks (`type_bindings_stack` / `expected_return_stack`);
everything else (`packages`, `structs`, `INTERP`) is read-only during execution.

So:

1. The two inference stacks are **thread-local** — each worker infers types
   independently, with no contention.
2. There is **no global execution guard**. The only shared state is channel
   storage, protected by **each channel's own lock/condition variable**.
3. Blocking operations wait on that per-channel lock — never on a lock only
   their own thread holds — so nothing can deadlock by needing a lock the
   holder is blocked waiting to release.

This is simpler *and* faster than a guard, and it removes the entire class of
release/reacquire deadlocks.

**Go port notes.** Go has no thread-local storage, so the inference stacks are
emulated as `sync.Mutex`-guarded maps keyed by goroutine id (parsed from
`runtime.Stack`, mirroring `threading.get_ident`). Two traps were found while
porting:

- **The id parse must read the digit run after `"goroutine "`** — slicing to
the first space yields an empty token and silently collapses every goroutine
onto key 0, turning the per-goroutine stacks into one racing shared stack
(flaky `slice bounds out of range [:-1]` crashes under load).
- **`resolveRuntimeType` must not touch the goroutine-local stack on the hot
path.** It ran once per variable assignment (via `coerceForType`) and each
access costs a `runtime.Stack` capture + mutex, slowing loops ~80x and
convoying workers onto one core. It now gates on whether the type tree mentions
any declared generic type-parameter name (`paramNames`, populated at load) and
only then consults the stack; assignment hot paths stay lock- and capture-free.

Workers run as `go func()` on the bytecode VM (`bcVM.call`); each spawns its own
`bcVM` (per-goroutine stack) over the shared read-only compiled table.

**Rust port notes.** The Rust parity binary executes through the native
semantic bytecode VM (`bc_run`/`bc_call` over `BcCode`); worker bodies run that
same bytecode, never the tree walkers. Because the Rust value model is
Rc-based, no Rc value crosses a thread:

- Channels are `Arc<Mutex<_>>` + `Condvar` handles; messages are deep-copied
into a Send form (`SendValue`) at the boundary and rebuilt as Rc values on the
far side (channel handles become Arc clones, so reply channels carried in
struct payloads work across threads).
- `Peer.worker` spawns a real `std::thread`. The thread rebuilds a fresh
interpreter environment (builtins + declarations) from the same program ASTs
(`Program` is owned data, hence Send), binds the closure's captured free
variables (deep-copied; root-scope names are re-resolved from the rebuilt
environment), and runs the body via `bc_run`. Nested `Peer.worker` calls inside
worker bodies work because the rebuilt environment carries its own `Peer`.
- Worker `throw`/errors become exit code 1 (swallowed, observed via `join()`),
matching the other two backends.

## 5. Diagnostics

| code | kind | when |
| --- | --- | --- |
| C061 | static | `Channel<Int>()` — the constructor takes no type arguments |
| C126 | static | message payload is not serializable (a `Peer`/function value) — *deferred* |
| E074 | runtime | `send` to a channel that is closed |
| E031 | runtime | index out of range / other value errors (inherited) |

The channel constructor and `Peer.worker` are wired into the type system
(generics, method signatures, `builtin_method`), and the semantic validator
infers `Channel` call results.

## 6. Parity fixtures

`--reference-only` (Python oracle) coverage in `test/reference/`:

- `concurrency_peer_peer.sol` — reply-channel carried in a struct payload.
- `concurrency_fanout.sol` — round-robin fan-out across three peers (sum is
  schedule-independent → deterministic output).
- `concurrency_workers.sol` — nested sub-peers + a panicking body observed via
  `join()`.
- `valid/concurrency_channel_shape.sol` — full channel type surface.
- `runtime_errors/concurrency_send_closed.sol` — E074.

The fan-out and peer-to-peer programs print **only from `main`**, so output is
fully ordered and comparable bit-for-bit across the three backends.

## 7. Deferred (not this phase)

- `Peer.process` / OS-subprocess peers and remote (`process://`) schemes.
- Named discovery (`thread://<name>`, `Peer.open`), C125/C127.
- Non-serializable-payload checking (C126).
- A `Future`/`Promise` result handle — the channel model already supports
  "submit, compute, collect later," and callbacks are expressible by carrying a
  reply channel in a message, so a second result model was deliberately not
  added.
