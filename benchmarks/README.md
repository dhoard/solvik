# Benchmarks

These programs measure the built-in collection runtime and the function-value call path. Each prints a
count rather than a timing, so the same file doubles as a correctness fixture: a change that alters an
output is a semantics change, not an optimization.

Run them with:

```bash
./benchmarks/run.sh                 # every benchmark, 3 rounds, prints a table
./benchmarks/run.sh set-build       # one benchmark by name
./benchmarks/run.sh call-direct call-named    # any subset
```

`run.sh` uses the same GraalVM discovery rules as `build.sh` and runs against
`standalone/target/modules`, so run `./build.sh` (or at least build the language and assemble the
module directory) first. It compares each program's output with its `.output` golden before timing it,
so a benchmark that changed behavior fails the run instead of producing a fast wrong number.

Every program is sized so the measured work clearly dominates process startup, which costs about
0.3 s and otherwise hides a real regression or a real gain. `list-add` against `list-add-erased`, and
`list-get` against `list-get-erased`, are the pairs used to read the integral element-storage effect:
the workloads are identical and only the resolved element type differs.

## Programs

### Collections

| Name | Measures |
| --- | --- |
| `loop-floor` | a counted loop with no collections: the baseline every other row is read against |
| `list-add` | `List.add` on a growing `List<Integer>`, which uses primitive element storage |
| `list-add-erased` | the same `add` workload on `List<Any`, which must keep erased `Object` storage |
| `list-get` | `List.get` over a populated `List<Integer>` |
| `list-get-erased` | the same `get` workload on `List<Any>` |
| `list-size` | the computed `size` property in a loop condition |
| `set-build` | `Set.add` while growing: the uniqueness scan per insertion |
| `set-contains` | `Set.contains` hits over a populated set |
| `map-build` | `Map.put` while growing: the key scan per insertion |
| `map-lookup` | `Map.get` over a populated map |
| `stack-push-pop` | `Stack.push`/`pop`, which should stay constant time |

### Function-value calls

Every row in this group performs the same body of work — add one to an accumulator once per iteration —
and differs only in how the callee is reached. `call-direct` is the baseline of the group, and it exists
for one reason: a statically resolved call must keep its existing lowering path when function values
exist, so a row that stays close to it is evidence that the value forms did not replace direct calls.

| Name | Measures |
| --- | --- |
| `call-direct` | a statically resolved call to a top-level function: the baseline of this group |
| `call-named` | the same call through a binding holding a named function's canonical value |
| `call-anonymous` | the same call through a non-capturing anonymous function value |
| `call-closure` | the same call through a closure whose captured value is a primitive |
| `call-bound` | the same call through a bound method value, whose hidden argument is the receiver |
| `call-polymorphic` | one call site that keeps meeting four distinct values, where the dispatch can no longer hold a single cached target |

The five monomorphic rows are the acceptance evidence for the calling convention: they reach the same
target every iteration, so a value form that boxed primitives or rebuilt state per call would separate
from `call-direct` visibly rather than subtly. `call-polymorphic` is deliberately not monomorphic — its
call site sees four distinct values, one per kind, and its four addends differ so a site that kept
serving the first target after the second arrived prints a different checksum instead of passing.

This is the group `FIRST_CLASS_FUNCTIONS.md` section 7.7 asks for, and its acceptance requirement has two
halves: a statically resolved call must keep its existing lowering path once values exist, and a
function-value call must not force boxed primitive storage in guest frames. The first half is not a timing
claim and is asserted structurally, by
`SolvikFunctionValueTest.takingAFunctionAsAValueDoesNotRouteItsDirectCallsThroughAValue`. The second half
has no structural witness either — guest code cannot observe the storage form of a value, which is why
section 7.7 points at benchmarks for it — and `call-closure` is the row shaped to expose it: a captured
`Integer` read out of the frame array on every iteration would separate from `call-direct` if reading it
boxed. What the table can show is only whether these rows stay together; their distance from each other
is not a language rule.

A measured run of the group, best of three rounds on the development machine, reads `call-direct` 1.5 s,
`call-named` 1.5 s, `call-anonymous` 1.5 s, `call-closure` 1.6 s, `call-bound` 1.7 s, and
`call-polymorphic` 6.4 s. These figures are evidence about the implementation, not language semantics:
nothing in `docs/LANGUAGE_SPEC.md` fixes a ratio between these rows, and no test asserts one.

## Scaling guards

`SolvikCollectionBenchmarkTest` in the language test suite runs `list-scaling.sol` at two element
counts in one JVM and asserts the large/small time ratio stays under 4.0. Positional `List` access is
the documented contract, so its ratio must stay near 1; a regression to a per-access scan reaches about
8 at these sizes and fails. The sizes are deliberately small so a failing run finishes in seconds rather
than hanging.

The `set-build`/`set-build-4x` and `map-build`/`map-build-4x` pairs are measurement fixtures, not
assertions. Their cost grows about fourfold per doubling of elements once the ~0.3 s process-startup
floor is subtracted, which is the expected quadratic shape of an equality scan. Solvik specifies that
behavior in docs/LANGUAGE_SPEC.md section 11, and section 3 now defines the `hashCode` contract that
makes a hash index safe; the index itself is not yet implemented, so no test asserts an improvement
these programs do not currently have. When an index lands, these pairs are the programs that show it.