# Collection Benchmarks

These programs measure the built-in collection runtime. Each prints a single checksum so the same
file doubles as a correctness fixture: a change that alters an output is a semantics change, not an
optimization.

Run them with:

```bash
./benchmarks/run.sh                 # every benchmark, 3 rounds, prints a table
./benchmarks/run.sh set-build       # one benchmark by name
```

`run.sh` uses the same GraalVM discovery rules as `build.sh` and runs against
`standalone/target/modules`, so run `./build.sh` (or at least build the language and assemble the
module directory) first.

Every program is sized so the collection work clearly dominates process startup, which costs about
0.3 s and otherwise hides a real regression or a real gain. `list-add` against `list-add-erased`, and
`list-get` against `list-get-erased`, are the pairs used to read the integral element-storage effect:
the workloads are identical and only the resolved element type differs.

## Programs

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