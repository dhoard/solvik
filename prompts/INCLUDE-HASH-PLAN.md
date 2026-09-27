# Include Hash Clause — Implementation Plan

## 1. Objective

Add an optional `hash=` clause to the `include` directive so a programmer can
assert that an included file's resolved bytes match a specific content digest.
This provides compile-time provenance verification (catches substitution and
corruption of vendored/registry sources) with **zero runtime cost** — the check
runs entirely in the module/include-resolution phase, which finishes before
semantic analysis and lowering.

This is a design-only document. No implementation changes are made here; it
records decisions, affected files, test strategy, and acceptance criteria for
the implementing work.

---

## 2. Scope

### In scope

- Language specification update (section 20 of `LANGUAGE_SPEC.md`).
- Grammar extension in `Solvik.g4`.
- AST builder handling (`SolvikAstBuilder.java`).
- Include resolution and verification (`IncludeResolver.java` / source access).
- Diagnostic codes and messages for hash-related failures.
- Positive, negative, and corpus tests covering all new paths.
- Example `.sol` programs demonstrating usage.
- `ARCHITECTURE.md` note on the added verification phase.

### Out of scope (future work, explicitly deferred)

- A separate `hinclude` keyword (discussed and **rejected** — see §4.2).
- Content-addressed filenames / `hash` embedded in the path
  (discussed and **rejected** — see §4.3).
- Cryptographic algorithm selection flag or pluggable hash algorithms — the
  initial implementation fixes SHA-256; a future flag is documented as deferred.
- A language-level lockfile mechanism — the hash is asserted in-source; an
  external manifest/lockfile workflow is documented as optional tooling, not
  part of the language.
- Content-based dedup across different paths with the same hash — no special
  handling beyond per-path verification (see §5.4).

---

## 3. Design Decisions

### 3.1 Syntax: `=` assignment clauses

```solvik
include "lib/math.sol" alias=math hash=0x3a7f8c9d2e1b0f45...
```

Rationale (from the design discussion):

- **Order-independent**: `alias=` and `hash=` bind values explicitly, so clause
  order does not change meaning and is not a source of errors.
- **Compact and legible**: fewer tokens than keyword-space-separated form while
  keeping full `alias`/`hash` keywords (no cryptic abbreviations).
- **Replaces the `alias <value>` form**: the existing space-separated syntax
  `include "path" alias math` is superseded by `include "path" alias=math`.
  This is a **breaking change** for any `.sol` file that uses `alias` on an
  include; all such files must be updated to the `=` form.
- **Deterministic SLR(1)** grammar; no lookahead ambiguity because `alias` and
  `hash` are reserved keywords and cannot start an expression.
- **No runtime representation**: the clause is consumed during resolution; no
  node survives into the typed/lowered representation.

### 3.2 Hash literal format

A fixed-precision `0x` hex literal of exactly 64 nibbles (full SHA-256 output):

```text
HashLiteral
  : '0x' HEXDIGIT{64}
  ;
```

The `0x` prefix and exact width make precision a syntactic guarantee — there is
no ambiguity about how many bits are being checked. A malformed literal
(wrong width or non-hex characters) is a lexical/parse error reported at the
clause.

### 3.3 Verification target

The digest is computed over the **resolved file's raw bytes as read** by the
Truffle environment's file access. Not over a canonicalized or AST-derived form:
canonicalization doubles parse work and byte-level determinism is what
supply-chain tooling expects. A content change of any kind (code, comment,
whitespace) changes the digest — correct for hermeticity, potentially noisy for
formatting edits (documented in the spec as expected behavior).

### 3.4 Trust model

- `include "path"` → trust-by-**identity**: the path is the trust anchor.
- `include "path" hash=0x...` → trust-by-**content**: the asserted digest is
  the trust anchor. Substitution is prevented because an attacker cannot
  produce a file matching your externally-derived expectation without knowing
  it in advance.

### 3.5 Dedup semantics (unchanged)

Path canonicalization remains the **primary** dedup key. The `hash=` clause is
a **secondary gate**:

- Same canonical path, same or different hash → deduplicated on path alone.
- Same path with two *conflicting* hash assertions in one program → error
  (`SOLV-RESOL-018`), because the developer asserts incompatible expected
  contents for one location.
- Different paths resolving to identical content (same asserted hash) → both
  included as separate includes; the invariant is verified per-path, not merged.

---

## 4. Options Considered and Rejected

### 4.1 `hash` clause as optional suffix on `include` (Design A)

The original proposal: `include "path" alias math hash 0x...`. Kept as the
baseline. The final decision adopts `=` assignment form (`alias=math hash=0x...`)
rather than space-separated keywords, for order-independence and explicit
association (§3.1). Functionally equivalent; the `=` form is preferred.

### 4.2 `hinclude` keyword (rejected)

A distinct keyword `hinclude "path" 0x...` was considered and **rejected**. The
same outcome is achieved more simply by composing `include ... hash=...`, and
introducing a second keyword would duplicate resolution infrastructure (two
parsing paths, two diagnostics families, dual docs) for marginal readability
gain. The design keeps one directive with an optional clause.

### 4.3 Content-addressed filenames / hash in path (rejected)

Embedding the digest into the filename (`/foo/bar_<hash>.sol`) or as a path
suffix (`"lib/math.sol@0x..."`) was **rejected**:

- Fuses content identity with location, violating the principle that a path
  answers *where* and a hash answers *what*.
- Breaks organic file organization: renaming/moving a file regenerates its
  address; `math.sol` → `core_math.sol` becomes a different include.
- Makes verification **tautological/self-signed**: it catches accidental
  corruption of files whose names you already trust, but cannot prevent a
  substitution attack where an attacker provides a file with a different,
  self-consistent content address. Design A's externally-asserted digest
  prevents substitution.
- Breaks filesystem-tool ergonomics (`find`, `grep`, editor navigation).

---

## 5. Affected Components and Changes

### 5.1 Language specification — `LANGUAGE_SPEC.md` (§20)

**Current text to extend.** Section 20 currently documents:

- The `include` directive with `alias`.
- Modules and namespaces (`module` declarations, `::` qualification).
- Path resolution rules (home prefix, absolute/relative, canonicalization).
- Expansion, duplicates, and cycles.

**Add a new subsection "Content integrity (hash clause)"** after the alias
documentation and before "Modules and namespaces", covering:

1. The `hash=<HashLiteral>` clause syntax and its order-independence from `alias=`.
2. Required fixed-width `0x` hex format and malformed-literal handling.
3. Verification semantics: raw bytes of the resolved file vs asserted digest;
   mismatch → compile error with expected + actual digests.
4. Trust model note (trust-by-identity vs trust-by-content).
5. Dedup interaction (path-primary, hash-secondary; conflicting-assertion error).
6. Hermeticity note: any content change alters the digest — formatting edits
   require re-asserting the hash.
7. Deferred items: algorithm selection flag, external lockfile tooling.

Also update the include directive production in §20 to show `hash=` as an optional
clause and refresh the summary table of resolution diagnostics to add the new
codes (§5.5).

### 5.2 Grammar — `Solvik.g4`

**File:** `language/src/main/antlr4/org/solvik/parser/Solvik.g4` (generated parser
source; edit only the grammar, never generated output).

**Current production (conceptual):**

```
includeDirective
    : 'include' stringLiteral ('alias' identifier)? SEMI
    ;
```

**New production:**

```
includeDirective
    : 'include' stringLiteral includeClause* SEMI
    ;

includeClause
    : 'alias' '=' identifier
    | 'hash'  '=' hashLiteral
    ;

hashLiteral
    : '0x' HEXDIGIT HEXDIGIT HEXDIGIT ... (64 nibbles total)
    ;
```

Implementation note: ANTLR does not support `{n}` quantifiers on terminal
fragments directly in all versions; the 64-nibble constraint may be enforced in
the AST builder (`SolvikAstBuilder`) by counting `HEXDIGIT` tokens after `0x` and
rejecting widths other than 64 with `SOLV-RESOL-015`. The grammar enforces at
least one nibble; the width check is a semantic gate.

**Lexer addition:** no new lexer rules needed if `hashLiteral` reuses the
existing `HEXDIGIT` fragment and a `0x` prefix token. A dedicated `HASHLIT`
terminal covering `0x` + digits is cleaner; confirm the generated `HEXDIGIT`
fragment is accessible.

### 5.3 AST builder — `SolvikAstBuilder.java`

**File:** `language/src/main/java/org/solvik/parser/SolvikAstBuilder.java`

Changes:

- In `includeDirective()`, iterate `includeClause*` and record:
  - `alias=` value → existing alias-binding logic (unchanged semantics).
  - `hash=` value → store the 64-nibble hex string on the resolved include
    directive AST node / `IncludeDeclNode`.
- Validate nibble width == 64; emit `SOLV-RESOL-015` on mismatch.
- Detect duplicate `alias=` clauses (same prefix bound twice) → existing
  `SOLV-RESOL-013` range, extended to the `=` form.
- Detect duplicate `hash=` clauses → report `SOLV-RESOL-019` "duplicate hash
  clause" at the later clause.

No mutation into executable nodes; the stored digest is consumed by resolution
and discarded after verification (per AST policy: syntax AST represents source
semantics, not execution).

### 5.4 Include resolution / verification — `IncludeResolver.java` and source access

**Files:**
- `language/src/main/java/org/solvik/parser/IncludeResolver.java`
- `language/src/main/java/org/solvik/parser/IncludeSourceAccess.java` (or the
  Truffle-backed `TruffleIncludeSourceAccess.java`)
- `language/src/main/java/org/solvik/source/SourceFile.java`

Changes:

1. **After** path resolution and file canonicalization succeed, and **before**
   splicing items into the program AST, verify the digest if a `hash=` clause is
   present.
2. Read the resolved `SourceFile`'s bytes (via the same source access used by
   the parser) and compute SHA-256.
3. Compare the computed digest (as lowercase hex) against the asserted literal.
4. On mismatch, emit `SOLV-RESOL-017` ("include content mismatch") at the
   include directive span, reporting **both** expected and actual digests:

   ```
   error[SOLV-RESOL-017]: include content mismatch at src/lib/math.sol:3
     expected: 0x3a7f8c9d2e1b0f45...
     actual:   0x9b2c4e1a7f6d0c38...
   ```

5. On failure, do **not** splice the file; treat it as a hard compile error
   (the program is rejected before semantic analysis — consistent with "no
   program with compile-time errors produces an executable call target").
6. Record resolved digests in a per-path set on the resolver to detect
   `SOLV-RESOL-018` "conflicting hash assertions for same include path" across
   the program (e.g., one file includes `math.sol hash=0xAAA` and another
   includes the same `math.sol hash=0xBBB`).

**No runtime node.** Module/include resolution and file reads finish before
semantic analysis (per §20); the digest check lives in that same pre-semantic
phase. Nothing survives into lowering or the Truffle backend. Update
`ARCHITECTURE.md` §"Source Identity and Inclusion" to note the optional
verification gate.

### 5.5 Diagnostic codes — `DiagnosticCode.java`

**File:** `language/src/main/java/org/solvik/diagnostic/DiagnosticCode.java`

Add the following codes to the `SOLV-RESOL-*` family (current used range ends at
`SOLV-RESOL-014`; allocate sequentially):

| Code | Meaning | Spanned at |
|---|---|---|
| `SOLV-RESOL-015` | Malformed hash literal (wrong nibble width or non-hex characters) | `hash=` clause |
| `SOLV-RESOL-016` | `hash=` clause present but path resolution failed earlier (no resolvable file) | include directive |
| `SOLV-RESOL-017` | Resolved file content does not match asserted digest | include directive |
| `SOLV-RESOL-018` | Conflicting hash assertions for the same canonical include path | each conflicting directive |
| `SOLV-RESOL-019` | Duplicate `hash=` clause on a single include directive | later `hash=` clause |

Also add the new codes to the diagnostic mapping table in `LANGUAGE_SPEC.md §20`
(the existing table through `SOLV-RESOL-014`).

### 5.6 Semantic analysis — `SolvikSemanticAnalyzer.java`

Minimal change: the analyzer already receives diagnostics from resolution; no
new analysis pass is needed. Document that conflicting-hash detection (code 018)
is performed during resolution rather than in semantic analysis, and note that
`CheckedProgram` does not carry digest information post-resolution (digests are
not part of the typed representation — they served their gate and were discarded).

### 5.7 Architecture documentation — `ARCHITECTURE.md`

**File:** `docs/ARCHITECTURE.md`

Update §"Source Identity and Inclusion" with a paragraph noting the optional
compile-time content-integrity gate on `include`:

- It runs in the resolution phase (before semantic analysis, after path
  canonicalization).
- It is not represented at runtime; no module/include node, no runtime I/O.
- Path identity remains the dedup key; digest is a secondary verification gate.

Also add a short note in §"Required Pipeline" that the typed-representation
boundary is unchanged (no new phase between resolution and semantic analysis).

---

## 6. Implementation Phases

### Phase 0 — Specification and grammar (document-first)

1. Write the section-20 subsection text into `LANGUAGE_SPEC.md` (draft only;
   formal sign-off in Phase 4).
2. Extend `Solvik.g4` with `includeClause`, `hashLiteral`.
3. **No implementation code.** Deliverable: updated spec + grammar diff for
   review.

### Phase 1 — Parsing and AST

4. Implement `includeClause`/`hashLiteral` handling in `SolvikAstBuilder.java`.
5. Width validation (64 nibbles) and storage of the asserted digest on the
   include directive node.
6. Build: `./mvnw clean compile -pl language -am`; confirm no generated-parser
   edits were introduced (grammar changes regenerate the parser).

### Phase 2 — Resolution and verification

7. Extend `IncludeResolver.java` / source access to perform byte-level SHA-256
   verification after path resolution, before splicing.
8. Emit `SOLV-RESOL-016`, `-017`, `-018`, `-019` as applicable.
9. Update `SourceCatalog`/`SourceFile` usage if a digest-only read helper is
   useful (avoid reading bytes twice).

### Phase 3 — Diagnostics and messages

10. Add codes to `DiagnosticCode.java`; write localized message strings.
11. Ensure mismatch diagnostic reports expected + actual digests.

### Phase 4 — Tests

12. Positive, negative, and corpus tests (§7).
13. Corpus / example updates (§8).

### Phase 5 — Documentation and validation

14. Finalize `LANGUAGE_SPEC.md`, `ARCHITECTURE.md`.
15. Run targeted tests, then `./build-all.sh` as final gate.
16. Diff review; confirm no SimpleLanguage compatibility retained, no generated
    parser output hand-edited, corpus passes in both distributions.

---

## 7. Test Plan

### 7.1 Positive tests (verification passes)

| Test | What it proves |
|---|---|
| `include "x.sol" hash=0x<correct>` resolves and splices | Basic verified include works end-to-end. |
| `include "x.sol" alias=m hash=0x<correct>` → `m::...` reachable | `alias=` + `hash=` compose; prefix maps to module. |
| `hash=` before `alias=` (reversed clause order) | Order-independence of clauses. |
| Same canonical file included twice with matching hashes, once with `hash=` and once bare | Dedup by path; secondary gate accepts existing file. |
| Included file whose content matches the digest under **both** explicit and synthesized semicolons | Semicolon insertion does not affect the clause. |
| Raw-string path with `hash=` (`include r#"x.sol"# hash=0x...`) | Raw-string paths accept the clause. |
| `include` with only `alias=` (no `hash=`) | Bare alias remains valid trust-by-identity; no regression. |

### 7.2 Negative tests (verification fails / malformed)

| Test | Expected code | What it proves |
|---|---|---|
| Digest mismatch (one byte flipped in file) | `SOLV-RESOL-017` | Substitution/corruption detected; both digests reported. |
| Wrong nibble width (e.g., 32 hex chars) | `SOLV-RESOL-015` | Width gate catches truncated digests. |
| Non-hex characters in literal | `SOLV-RESOL-015` | Malformed literal caught at clause, not later. |
| `hash=` on an include whose path does not resolve (`no-such-file.sol`) | `SOLV-RESOL-016` | No false-positive verification when the file is absent; diagnostic points to directive. |
| Two directives asserting different hashes for the same canonical path | `SOLV-RESOL-018` | Conflicting assertions rejected; message lists both. |
| Duplicate `hash=` clause on one directive | `SOLV-RESOL-019` | Duplicate clause rejected. |
| Duplicate `alias=` clause (same prefix) with `hash=` present | `SOLV-RESOL-013` | Alias duplication detection unaffected by `=` form. |
| Empty `hash=` (`hash=` with no literal) | parse/lexical error at clause | Missing value rejected. |

### 7.3 Corpus / regression programs

Add to `test-corpus.sh`-consumed sets:

- **New positive corpus program** `corpus/verified_include.sol`: includes a
  file from another directory with the correct `hash=`; must run and produce its
  golden `.output`.
- **New negative corpus program** `corpus/bad_hash.sol`: includes a file whose
  content no longer matches the asserted hash; must exit non-zero with **empty
  stdout** (no partial execution — rejection happens before the program runs).
- **Update an existing example** to demonstrate `hash=` usage; refresh its
  `.output` if it executes.

### 7.4 Test file locations

| Type | Location |
|---|---|
| Positive execution tests | `language/src/test/java/org/solvik/.../IncludeHashExecutionTest.java` (new) — or added to existing `IncludeExecutionTest`. |
| Negative/semantic tests | `language/src/test/java/org/solvik/.../IncludeHashNegativeTest.java` (new) — or added to `IncludeSemanticTest` / `NamespaceNegativeTest`. |
| Diagnostics/meta tests | Confirm existing diagnostic framework covers the new codes via `.error` fixture files; add fixtures under `tests/regression/` and `tests/diagnostics/`. |
| Corpus programs | `test-corpus/.../*.sol` + matching `.output` / `.error`. |

---

## 8. Example and Documentation Updates

### 8.1 New example — verified vendored include

**File:** `examples/verified_include/README.md` (new) + a small vendor tree:

```
examples/
  verified_include/
    main.sol          # include "vendor/math.sol" hash=0x...; print(vendor::sum(2,3))
    vendor/
      math.sol        # contains func sum(a: Integer, b: Integer): Integer { return a + b }
```

`main.sol` computes the correct digest of `vendor/math.sol` at authoring time and
pins it. The `.output` is `5`.

### 8.2 Updated example — alias + hash together

Update an existing namespace/alias example (e.g., `examples/namespace_aliases.sol`)
to show:

```solvik
include "lib/math.sol" alias=math hash=0x<digest>
include "lib/coll.sol" hash=0x<digest>         # alias omitted; module name is the prefix
math::add(1, 2)
```

### 8.3 Spec and architecture docs

- `LANGUAGE_SPEC.md §20` — new subsection + extended production + diagnostic table.
- `ARCHITECTURE.md` — §"Source Identity and Inclusion" paragraph + §"Required
  Pipeline" note (no new phase added).
- No change to `AGENTS.md` or build wrappers; corpus wrapper (`test-corpus.sh`)
  automatically picks up new `.sol`/`.error` files.

---

## 9. Risk Analysis

| Risk | Likelihood | Mitigation |
|---|---|---|
| SHA-256 chosen now becomes a locked-in algorithm; users want pluggable algorithms later. | Medium | Fix SHA-256 for v1 (it is standard, available in every GraalVM/JDK 25 via `MessageDigest`); document that a future global flag may select the algorithm — do **not** build pluggability now. |
| Formatting/whitespace changes break downstream builds (noisy hermeticity). | High | Document as expected behavior; provide a tooling tip in the example README for regenerating the digest (`sha256sum` one-liner). The correctness benefit for supply-chain security outweighs the formatting noise. |
| Reading file bytes twice (once for parse, once for digest) on every include. | Medium | Reuse the parsed `SourceFile`/`LoadedSource` bytes if available; otherwise verify the already-read content rather than re-reading from disk. |
| Cross-platform newline canonicalization affecting digests across editors. | Low–Medium | Digest the **raw bytes as stored**, not a normalized form; document that the same file must be distributed without CRLF↔LF transformation, or pin on the canonical form used in the repo. |
| Accidental `0x` prefix vs `0X`/uppercase nibble mismatches in expected literals. | Low | Accept both cases in comparison (normalize to lowercase before comparing); report digests in lowercase consistently. |
| Diagnostic message formatting inconsistency with existing codes. | Low | Follow the existing `DiagnosticCode`/message pattern; model the two-digest output on the closest existing diagnostic that reports pairs of values. |

---

## 10. Acceptance Criteria

### 10.1 Correctness

- [ ] A verified include whose digest matches resolves, splices, and executes normally.
- [ ] A one-byte content change with a pinned hash is rejected at compile time **before execution** (empty stdout for corpus programs).
- [ ] Malformed hash literals (wrong width, non-hex) are diagnosed at the clause with `SOLV-RESOL-015`.
- [ ] Conflicting hash assertions for one canonical path are diagnosed with `SOLV-RESOL-018`.
- [ ] Duplicate `hash=` clauses are diagnosed with `SOLV-RESOL-019`.
- [ ] Path-resolution failures take priority over hash verification (`SOLV-RESOL-016`, not a false-positive mismatch).

### 10.2 Specification and documentation

- [ ] `LANGUAGE_SPEC.md §20` documents the clause syntax, format, semantics, dedup interaction, and diagnostics.
- [ ] `ARCHITECTURE.md` notes the gate's phase placement and non-runtime nature.
- [ ] No normative language semantics are changed beyond this feature; no SimpleLanguage compatibility retained.

### 10.3 Tests

- [ ] All positive, negative, and corpus cases in §7 pass.
- [ ] Both distributions' corpus checks (`test-corpus.sh` via `./build.sh` and `./build-native.sh`) pass with the new programs.
- [ ] No disabled tests or skipped suites introduced; coverage minima still met.

### 10.4 Hygiene

- [ ] `git diff --check` clean (no trailing whitespace).
- [ ] No hand-edited generated parser output (`SolvikParser*`/`SolvikLexer*`).
- [ ] Both `./build.sh` and `./build-native.sh` pass individually.
- [ ] **Final gate: `./build-all.sh` passes** — JVM package + corpus, then native-image package + corpus, no failures.

---

## 11. Open Questions (to resolve before implementation)

1. **Report digests in lowercase only, or mirror the literal's case?**
   Decision under consideration: normalize to lowercase for both expected and actual
   in diagnostic output; accept either input case. Confirm with reviewer.
2. **Should a mismatch emit at the directive span or at the path literal sub-span?**
   Decision under consideration: directive span (it covers the whole clause, including
   the `hash=` keyword), consistent with how other include diagnostics are spanned.
3. **Is documenting an external lockfile workflow in the spec worthwhile?**
   Decision under consideration: keep the spec focused on the in-source assertion;
   add a brief "tooling note" pointing out that a lockfile/manifest is an orthogonal,
   out-of-band approach — do not encode it in the language.

---

## 12. Verification (post-implementation)

After all phases complete, run:

```bash
# Focused build + tests
JAVA_HOME=<graalvm-jdk25> PATH=<graalvm-jdk25>/bin:$PATH ./mvnw clean verify -pl language,launcher -am

# Final quality gate (JVM then native)
./build-all.sh
```

Then inspect:

- `git diff` — confirm only intended files changed; no generated parser edits.
- Coverage report — confirm the new branches are exercised and minima met.
- Corpus outputs — confirm both distributions run the new examples/programs and
  their `.output`/`.error` expectations match.
- `docs/LANGUAGE_SPEC.md` and `docs/ARCHITECTURE.md` render consistently with the
  code (no drifted prose).
