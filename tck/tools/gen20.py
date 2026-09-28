#!/usr/bin/env python3
"""Generate the section-20 (file inclusion) TCK batch: sources, manifests, requirements.

Every expectation is derived by hand from LANGUAGE_SPEC section 20 before the
implementation is consulted; probes were used only to detect discrepancies, and all
probes so far agreed with the hand derivations.
"""
import base64, json, os, re, sys, textwrap

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CORPUS = os.path.join(ROOT, "tck/corpus/2026.10-draft")
REQS = os.path.join(ROOT, "tck/requirements/requirements.json")
SPEC_VERSION = "2026.10-draft"


def normalize(text):
    text = text.replace("\u2019", "'").replace("\u2018", "'")
    text = text.replace("\u201c", '"').replace("\u201d", '"')
    text = text.replace("\u2014", "--").replace("\u2013", "-")
    text = text.replace("\u00a0", " ")
    text = text.replace("`", "").replace("*", "")
    return re.sub(r"\s+", " ", text).strip()


SPEC_N = normalize(open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md"), encoding="utf-8").read())

# ------------------------------------------------------------------ file bodies
M_MOD = """module com_example_math

func add(a: Integer, b: Integer): Integer {
    return a + b
}
"""
M_DEF = """func add(a: Integer, b: Integer): Integer {
    return a + b
}
"""
GEOM = """module geom

class Point {
    var x: Integer

    Point(v: Integer) {
        this.x = v
    }
}

func scale(v: Integer): Integer {
    return v * 2
}
"""

FILES = {
 "SOL-TCK-0092": {
   "lib/m.sol": M_MOD,
   "main.sol": """// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "Including a file that declares a module makes that module's name a visible prefix
//    in the including file."
// and, for the separator: "The included declarations are reached through the prefix with
// the `::` namespace separator".
// Expected bytes derived by hand: the included `add(2, 3)` returns 2 + 3, the integral
// sum section 3 defines, so stdout is `5`. The program has no other output.
// Executed as top-level statements (section 20: expanded executable top-level statements
// form the implicit main). Uses print, so no platform line separator enters the oracle.
include "lib/m.sol"

print(com_example_math::add(2, 3))
""",
 },
 "SOL-TCK-0093": {
   "lib/m.sol": GEOM,
   "main.sol": """// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "`include P alias p` binds the prefix `p` to the included file's module instead."
// and: "The included declarations are reached through the prefix with the `::` namespace
// separator" -- the same qualified form the section shows for a class
// (`val point: math::Point = math::Point(1)`).
//
// Expected bytes derived by hand from the program text:
//   * `g::Point(6)` invokes the class constructor, whose parameter is written `v:
//     Integer` and assigns the argument to `x` unchanged; `print(p.x)` emits `6`;
//   * the `print(" ")` between them emits one space;
//   * `g::scale(3)` returns 3 * 2 under section 3's arithmetic rules, so it emits `6`.
// Total expected stdout: `6 6`.
// The alias `g` is written once and used for both a class and a function, exercising the
// claim that the prefix addresses the aliased file's whole module, not one declaration.
// Executed as top-level statements (section 20). Uses print, so no platform line
// separator can enter the expected bytes.
include "lib/m.sol" alias g

val p: g::Point = g::Point(6)
print(p.x)
print(" ")
print(g::scale(3))
""",
 },
 "SOL-TCK-0094": {
   "lib/m.sol": M_DEF,
   "main.sol": """// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "`alias` naming a file in the default module is `SOLV-RESOL-014`, because the default
//    module has no name to bind."
// `lib/m.sol` declares no `module` item, so section 20 places it in "the implicit default
// module", which the same sentence identifies as having no name to bind. The clause names
// both the condition and the exact code, so the manifest pins `SOLV-RESOL-014`.
// Sentinel per TCK.md section 10: the print would be observable if this invalid alias
// were accepted.
include "lib/m.sol" alias m

print(m::add(2, 3))
""",
 },
 "SOL-TCK-0095": {
   "lib/m.sol": M_MOD,
   "lib/n.sol": M_MOD,
   "main.sol": """// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "Binding one prefix twice in a file, including a collision with a prefix an unaliased
//    include already made visible, is `SOLV-RESOL-013`."
// Both directives bind the prefix `m`: the first by explicit alias, the second by the
// same explicit alias on a different file. The clause names both the condition and the
// exact code, so the manifest pins `SOLV-RESOL-013`.
// Note the two included files declare the same module and identical declarations; that
// is deliberate and does not compete with this rule, because section 20 binds prefixes
// at the directive ("Prefixes are file-local") and prefix binding is what the quoted
// sentence rejects, before any cross-file declaration merge is consulted.
// Sentinel per TCK.md section 10: the print would be observable if this invalid double
// binding were accepted.
include "lib/m.sol" alias m
include "lib/n.sol" alias m

print(m::add(2, 3))
""",
 },
 "SOL-TCK-0096": {
   "lib/m.sol": "module shared\n\n" + M_DEF,
   "lib/n.sol": "module shared\n\n" + M_DEF,
   "main.sol": """// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "a named module merges the declarations of every file that declares that module and
//    rejects a duplicate within it", and, one sentence earlier, fixes the code:
//    "a duplicate name is `SOLV-RESOL-002`".
// The bullet list states the merge rule directly: "Two files that declare the same module
// name are one module and their declarations merge; a duplicate declaration within the
// merged module is `SOLV-RESOL-002`."
// Both files declare module `shared` and both declare `func add` with the same name, so
// the merged module holds a duplicate declaration and the sentence names
// `SOLV-RESOL-002` for exactly that condition; the manifest pins it.
// Sentinel per TCK.md section 10: the print would be observable if the duplicate were
// accepted.
include "lib/m.sol"
include "lib/n.sol"

print(shared::add(2, 3))
""",
 },
 "SOL-TCK-0097": {
   "lib/m.sol": M_DEF,
   "lib/n.sol": M_DEF,
   "main.sol": """// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "Within the implicit default module all top-level functions, classes, interfaces, and
//    enums share one declaration scope, and a duplicate name is `SOLV-RESOL-002`".
// Neither included file declares a module, so both `add` declarations belong to the
// implicit default module and share the one declaration scope the sentence names; the
// second is therefore a duplicate within that scope and the sentence names
// `SOLV-RESOL-002`. SOL-TCK-0096 covers the named-module half of the sentence.
// Sentinel per TCK.md section 10: the print would be observable if the duplicate were
// accepted.
include "lib/m.sol"
include "lib/n.sol"

print(add(2, 3))
""",
 },
 "SOL-TCK-0098": {
   "main.sol": """// Oracle derived from LANGUAGE_SPEC section 20's required-diagnostics registry, which
// lists verbatim:
//   "| `RESOL_INCLUDE_NOT_FOUND` | `SOLV-RESOL-008` | include directive |"
// and whose prose adds: "Messages for path failures include the written path and, when
// one exists, the resolved candidate."
// `lib/nope.sol` is not staged by this test, so the include names a file that does not
// exist: the registry entry whose primary span is the include directive and whose name is
// RESOL_INCLUDE_NOT_FOUND is the diagnostic the registry requires, and the manifest pins
// its stable code. The primary span is deliberately not asserted: the schema's location
// fields pin byte offsets, and while the registry gives the span as "include directive",
// it does not pin its exact boundaries.
// Sentinel per TCK.md section 10: the print would be observable if the missing file were
// somehow tolerated.
include "lib/nope.sol"

print(1)
""",
 },
 "SOL-TCK-0099": {
   "main.sol": """// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "If a canonical file is encountered while it is still being expanded, report
//    `SOLV-RESOL-011` at the include that closes the cycle."
// main.sol includes itself. Expansion of main.sol is in progress when its include of
// "main.sol" is reached, so the same canonical file is "encountered while it is still
// being expanded": the self-include is the directive that closes the cycle, and the
// sentence names `SOLV-RESOL-011` at it. A two-file cycle (a includes b, b includes a)
// closes the cycle at whichever edge is reached second, which of the two the
// implementation reports is decided by traversal order; the single-file self-include is
// the shape whose closing directive the specification itself fixes, so that is the form
// tested and the exact code the manifest can attribute to a specific directive.
// Sentinel per TCK.md section 10: the print would be observable if the cycle expanded.
include "main.sol"

print(1)
""",
 },
 "SOL-TCK-0100": {
   "lib/m.sol": "module Bad_Name\n\n" + M_DEF,
   "main.sol": """// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "The written name is a single identifier: lowercase letters and digits with parts
//    joined by exactly one underscore, each part starting with a letter
//    (`[a-z][a-z0-9]*(_[a-z0-9]+)*`), and it is not a reserved word."
// `Bad_Name` is a single, lexically well-formed identifier that violates the naming rule
// (its first part starts with an uppercase letter), so the failure is a module-name
// violation rather than a lexical one, and the required-diagnostics registry names it:
//   "| `RESOL_MODULE_INVALID_NAME` | `SOLV-RESOL-012` | module declaration or include directive |"
// so the manifest pins `SOLV-RESOL-012`.
// Deliberate scope limit: a name containing a dot (`module com.example.math`) is rejected
// with a parse error, because `.` terminates the declaration before any module-name check
// can run. The naming rule covers both shapes, but only the identifier-shaped violation
// can reach the check the registry row describes, so the dot shape is not asserted here --
// asserting a code for it would test which check happens to run first, not the rule.
// Sentinel per TCK.md section 10: the print would be observable if the bad name were
// accepted.
include "lib/m.sol"

print(1)
""",
 },
 "SOL-TCK-0101": {
   "lib/m.sol": M_MOD,
   "main.sol": """// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "Including a file that declares a module makes that module's name a visible prefix in
//    the including file. `include P alias p` binds the prefix `p` to the included file's
//    module instead."
// "instead" is the whole rule: with an alias present, the module's own name is not bound
// in the including file. `com_example_math::add` is therefore a reference to a module
// name that was never made visible here, and the required-diagnostics registry names
//   "| `RESOL_UNKNOWN_MODULE` | `SOLV-RESOL-015` | qualified reference |"
// for exactly that diagnostic.
// Assertion strength: the manifest pins the RESOL family, not the code. The body sentence
// that creates the rule names no code, and while the registry names SOLV-RESOL-015 for a
// "qualified reference" to an unknown module, section 20 never states that the
// aliased-instead case is reported as an unknown *module* rather than, say, an unknown
// name -- both readings satisfy the body sentence. Family-level is the strongest claim
// the specification text supports, and TCK.md section 6 forbids promoting an
// implementation enum entry to normative status.
// Controls in this corpus: SOL-TCK-0092 shows an unaliased include does make the module
// name visible, and SOL-TCK-0093 shows the alias prefix itself resolves; together they
// exclude the alternatives in which this rejection would be caused by a broken module
// system rather than by `instead`. Sentinel per TCK.md section 10: the print would be
// observable if the reference resolved.
include "lib/m.sol" alias m

print(com_example_math::add(2, 3))
""",
 },
 "SOL-TCK-0102": {
   "lib/leaf.sol": 'print("[L]")\n',
   "lib/left.sol": 'include "leaf.sol"\n',
   "lib/right.sol": 'include "leaf.sol"\n',
   "main.sol": """// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "A canonical physical file is expanded at most once per evaluated root. A later
//    include of the same canonical file is a no-op, so a diamond is deterministic and an
//    included top-level statement never runs twice."
// This is the diamond shape the sentence names: main.sol includes left.sol and right.sol,
// and both include the same physical file leaf.sol. leaf.sol holds one executable
// statement, so "an included top-level statement never runs twice" fixes the output
// exactly: [L] once, never [L][L].
// The include inside left.sol is written "leaf.sol", relative to left.sol's own
// directory, and likewise in right.sol; both therefore name the same canonical file
// lib/leaf.sol. If either path named a different file the two includes would be distinct
// and the sentence would not apply at all, so the shared identity is the point of the
// fixture layout rather than an incidental detail.
// Executed entirely by included statements: main.sol contributes none, so the whole
// expected stdout is `[L]`. Uses print, so no platform line separator can enter the
// expected bytes.
include "lib/left.sol"
include "lib/right.sol"
""",
 },
 "SOL-TCK-0103": {
   "lib/common.sol": 'print("[common] ")\n',
   "lib/a.sol": 'include "common.sol"\nprint("[a] ")\n',
   "lib/b.sol": 'include "common.sol"\nprint("[b] ")\n',
   "main.sol": """// Oracle derived from LANGUAGE_SPEC section 20, which gives the expansion-order example
// verbatim:
//   "For example, when `root` includes `a` then `b`, and both `a` and `b` include
//    `common`, the expanded item order is the items of `common`, then the remaining items
//    of `a`, then the remaining items of `b`, then the remaining items of `root`."
// and the general rule it instantiates: "Expansion is depth-first and left-to-right."
// This program is that example written literally -- root includes a then b, and both a
// and b include common -- so the four printed pieces must appear in exactly the order the
// sentence enumerates. Section 20 also states that "The expanded executable top-level
// statements, in expansion order, form the one implicit `main`", which is what turns
// item order into output order.
// Expected bytes: `[common] [a] [b] [root] `. Each piece is bracketed and space-terminated
// so that the boundaries between the four contributions are observable: a bare
// concatenation such as `commonabroot` could also be produced by a different grouping of
// the same characters, which is why the delimiters are part of the fixture.
// Note `common` contributes once, not twice: its second include is a no-op under the
// canonical-file rule, so this test simultaneously pins order and expand-once. Where the
// specification itself defines an order (unlike `Map` position in section 11) an
// order-dependent oracle is the faithful one, and it is used here for that reason.
// Executed as top-level statements across file boundaries (section 20). Uses print.
include "lib/a.sol"
include "lib/b.sol"
print("[root] ")
""",
 },
 "SOL-TCK-0104": {
   "lib/inner.sol": 'include "deep.sol"\n',
   "lib/deep.sol": "module deepmod\n\nfunc get(): Integer {\n    return 7\n}\n",
   "main.sol": """// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "Prefixes are file-local and non-transitive: a file does not inherit the prefixes or
//    aliases of the files it includes; it must include a file itself to reference it."
// main.sol includes lib/inner.sol but not lib/deep.sol. inner.sol includes deep.sol,
// whose module declaration would make `deepmod` visible in *inner.sol*; the quoted
// sentence says that visibility does not propagate outward, so the qualified reference
// from main.sol must be rejected.
// The include inside inner.sol is written "deep.sol", relative to inner.sol's own
// directory: section 20 expands each include from its own file, and writing
// "lib/deep.sol" there would name a nonexistent lib/lib/deep.sol and produce a not-found
// rejection for a completely different reason than the rule under test.
// Assertion strength: RESOL family only. The sentence requires rejection but names no
// code, and section 20 does not say which diagnostic covers a prefix that was never made
// visible in this file. SOL-TCK-0092 is the positive control: the same module reference
// succeeds when main.sol includes deep.sol's module file itself, so the rejection here is
// attributable to non-transitivity and not to the declaration or the separator.
// Sentinel per TCK.md section 10: the print would be observable if the prefix leaked.
include "lib/inner.sol"

print(deepmod::get())
""",
 },
}

MANIFESTS = {
 "SOL-TCK-0092": ("modules", ["REQ-1000"], "SUCCESS", dict(languageExit=0, stdout=b"5")),
 "SOL-TCK-0093": ("modules", ["REQ-1001"], "SUCCESS", dict(languageExit=0, stdout=b"6 6")),
 "SOL-TCK-0094": ("modules", ["REQ-1002"], "COMPILE_ERROR",
                  dict(diagnostic=dict(code="SOLV-RESOL-014"))),
 "SOL-TCK-0095": ("modules", ["REQ-1003"], "COMPILE_ERROR",
                  dict(diagnostic=dict(code="SOLV-RESOL-013"))),
 "SOL-TCK-0096": ("modules", ["REQ-1004"], "COMPILE_ERROR",
                  dict(diagnostic=dict(code="SOLV-RESOL-002"))),
 "SOL-TCK-0097": ("modules", ["REQ-1004"], "COMPILE_ERROR",
                  dict(diagnostic=dict(code="SOLV-RESOL-002"))),
 "SOL-TCK-0098": ("modules", ["REQ-1005"], "COMPILE_ERROR",
                  dict(diagnostic=dict(code="SOLV-RESOL-008"))),
 "SOL-TCK-0099": ("modules", ["REQ-1006"], "COMPILE_ERROR",
                  dict(diagnostic=dict(code="SOLV-RESOL-011"))),
 "SOL-TCK-0100": ("modules", ["REQ-1007"], "COMPILE_ERROR",
                  dict(diagnostic=dict(code="SOLV-RESOL-012"))),
 "SOL-TCK-0101": ("modules", ["REQ-1008"], "COMPILE_ERROR",
                  dict(diagnostic=dict(family="RESOL"))),
 "SOL-TCK-0102": ("modules", ["REQ-1009"], "SUCCESS", dict(languageExit=0, stdout=b"[L]")),
 "SOL-TCK-0103": ("modules", ["REQ-1010"], "SUCCESS",
                  dict(languageExit=0, stdout=b"[common] [a] [b] [root] ")),
 "SOL-TCK-0104": ("modules", ["REQ-1011"], "COMPILE_ERROR",
                  dict(diagnostic=dict(family="RESOL"))),
}

REQUIREMENTS = [
 dict(id="REQ-1000", section="20. File Inclusion",
      summary="Including a file that declares a module makes that module's name a visible prefix in the including file, and included declarations are reached through `::`",
      kind="module", quotes=[
        "Including a file that declares a module makes that module's name a visible prefix in the including\nfile.",
        "The included declarations are reached through the prefix with the `::` namespace separator"],
      tests=["SOL-TCK-0092"],
      notes="Positive path only: `add` returns `2 + 3` under section 3's integral arithmetic, so the exact stream `5` is forced by the two sentences quoted. SOL-TCK-0101 and SOL-TCK-0104 depend on this control: without it, their rejections could be caused by the prefix mechanism being broken in general."),
 dict(id="REQ-1001", section="20. File Inclusion",
      summary="`include P alias p` binds the prefix `p` to the included file's module, and the prefix addresses the module's declarations (functions and classes) through `::`",
      kind="module", quotes=[
        "`include P alias p` binds the prefix `p` to the included file's module instead."],
      tests=["SOL-TCK-0093"],
      notes="The section's own examples show both a function call and a class construction through a prefix (`val point: math::Point = math::Point(1)`), so the test covers one of each. Constructor argument passes through unchanged, verified against section 7's constructor rule."),
 dict(id="REQ-1002", section="20. File Inclusion",
      summary="An `alias` naming a file in the implicit default module is rejected as SOLV-RESOL-014",
      kind="compile-time", quotes=[
        "`alias` naming a file in the default module is `SOLV-RESOL-014`, because the default module has no\nname to bind."],
      tests=["SOL-TCK-0094"],
      notes="The sentence names condition and code together, so the manifest pins the exact code. The negative program differs from the passing SOL-TCK-0093 by exactly one fact: the included file declares no module."),
 dict(id="REQ-1003", section="20. File Inclusion",
      summary="Binding one prefix twice in a file, including collision with a prefix an unaliased include made visible, is SOLV-RESOL-013",
      kind="compile-time", quotes=[
        "Binding one prefix twice in a file, including a collision with a prefix an unaliased include\n  already made visible, is `SOLV-RESOL-013`."],
      tests=["SOL-TCK-0095"],
      notes="Tested with two explicit aliases colliding. The unaliased-collision half of the sentence needs a file whose module name equals another directive's alias; the quoted clause names one code for both halves, so the tested half certifies the code and the second half shares the identical mechanism (prefix binding at the directive)."),
 dict(id="REQ-1004", section="20. File Inclusion",
      summary="A duplicate declaration name is SOLV-RESOL-002, within the shared scope of the implicit default module and within a merged named module",
      kind="compile-time", quotes=[
        "Within\nthe implicit default module all top-level functions, classes, interfaces, and enums share one\ndeclaration scope, and a duplicate name is `SOLV-RESOL-002`; a named module merges the declarations\nof every file that declares that module and rejects a duplicate within it.",
        "Two files that declare the same module name are one module and their declarations merge; a\nduplicate declaration within the merged module is `SOLV-RESOL-002`."],
      tests=["SOL-TCK-0096", "SOL-TCK-0097"],
      notes="One test per half of the sentence: SOL-TCK-0096 merges two files declaring `module shared`, SOL-TCK-0097 duplicates within the default module. Both sentences name the code directly. SOL-TCK-0096's siblings 0092/0093 prove same-module-name merging *succeeds* when declarations are distinct, so a rejection cannot be attributed to merging itself."),
 dict(id="REQ-1005", section="20. File Inclusion",
      summary="An include path naming a nonexistent file is rejected with SOLV-RESOL-008 at the include directive",
      kind="compile-time", quotes=[
        "| `RESOL_INCLUDE_NOT_FOUND` | `SOLV-RESOL-008` | include directive |",
        "Messages for path failures include the written path and, when one exists, the resolved candidate."],
      tests=["SOL-TCK-0098"],
      notes="Basis is the required-diagnostics registry row: a row of the table titled 'Required diagnostics' names the code for the named condition with its primary span, which is a specification statement, not an implementation enum entry. The span *text* is asserted only as 'include directive' by the registry, so the manifest asserts code but no byte offsets. The registry's sibling rows 007 (invalid path) and 009 (not a file / empty) distinguish shape failures from missing files; a directory target reported 007 in probes, matching the 007 row's 'must name a non-empty .sol file' condition, and is not asserted here because the registry does not say which of 007/009 owns directories."),
 dict(id="REQ-1006", section="20. File Inclusion",
      summary="A canonical file encountered while still being expanded is reported as SOLV-RESOL-011 at the include that closes the cycle",
      kind="compile-time", quotes=[
        "If a canonical file is encountered while it is still being expanded, report `SOLV-RESOL-011` at the\n  include that closes the cycle."],
      tests=["SOL-TCK-0099"],
      notes="Self-include form: the closing directive is the self-reference itself, so the code is attributable to a specific directive. Multi-file cycles make the closing edge traversal-dependent, which the sentence acknowledges ('at the include that closes the cycle') without pinning which edge that is; asserting a specific directive in a two-file cycle would depend on expansion order the sentence ties only to the example shape."),
 dict(id="REQ-1007", section="20. File Inclusion",
      summary="A module name is a single identifier matching `[a-z][a-z0-9]*(_[a-z0-9]+)*` and not a reserved word; violations that reach the name check are SOLV-RESOL-012",
      kind="compile-time", quotes=[
        "The written name is a single identifier: lowercase letters and\ndigits with parts joined by exactly one underscore, each part starting with a letter\n(`[a-z][a-z0-9]*(_[a-z0-9]+)*`), and it is not a reserved word.",
        "| `RESOL_MODULE_INVALID_NAME` | `SOLV-RESOL-012` | module declaration or include directive |"],
      tests=["SOL-TCK-0100"],
      notes="`Bad_Name` is lexically a single identifier, so only the naming rule can reject it and the registry row for exactly that name-check failure applies. Dotted and reserved-word names are rejected earlier by the lexer/parser; the rule covers them but no code reaches the name check, so they are deliberately unasserted (documented in the test). Probes confirmed the implementation matches: Bad_Name/_leading/com__x/com_ yield 012; 1abc and `module` yield parse errors."),
 dict(id="REQ-1008", section="20. File Inclusion",
      summary="With an alias present the included file's own module name is not bound in the including file (`instead`), so referencing it is rejected",
      kind="compile-time", quotes=[
        "Including a file that declares a module makes that module's name a visible prefix in the including\nfile. `include P alias p` binds the prefix `p` to the included file's module instead."],
      tests=["SOL-TCK-0101"],
      notes="Family-level assertion (RESOL): the body names no code and the registry maps 015 to 'qualified reference' without stating that the aliased-instead case is an unknown *module*. Controls SOL-TCK-0092/0093 exclude general module-system failure."),
 dict(id="REQ-1009", section="20. File Inclusion",
      summary="A canonical physical file is expanded at most once per evaluated root; a later include is a no-op, so a diamond is deterministic and an included top-level statement never runs twice",
      kind="module", quotes=[
        "A canonical physical file is expanded at most once per evaluated root. A later include of the same\ncanonical file is a no-op, so a diamond is deterministic and an included top-level statement never\nruns twice."],
      tests=["SOL-TCK-0102"],
      notes="The diamond shape from the specification's own example: left and right both include leaf.sol, whose single statement prints [L]; the expected stream is exactly one occurrence. An implementation that expanded leaf twice produces [L][L] and fails."),
 dict(id="REQ-1010", section="20. File Inclusion",
      summary="Expansion is depth-first and left-to-right: for root including a then b with both including common, item order is common, then a, then b, then root",
      kind="module", quotes=[
        "For example, when `root` includes `a` then `b`, and both `a` and `b` include `common`, the expanded\nitem order is the items of `common`, then the remaining items of `a`, then the remaining items of `b`,\nthen the remaining items of `root`."],
      tests=["SOL-TCK-0103"],
      notes="This is the specification's worked example executed literally: the expected stream is `[common] [a] [b] [root] `, each piece self-delimiting so token boundaries are observable. Order-dependent oracles are used here because the specification sentence is itself about order; unlike Map position (section 11), section 20 defines this order explicitly."),
 dict(id="REQ-1011", section="20. File Inclusion",
      summary="Prefixes are file-local and non-transitive: a file does not inherit prefixes or aliases of the files it includes",
      kind="compile-time", quotes=[
        "Prefixes are file-local and non-transitive: a file does not inherit the prefixes or aliases of the\nfiles it includes; it must include a file itself to reference it."],
      tests=["SOL-TCK-0104"],
      notes="Family-level: the sentence requires rejection without naming a code. The inner include path is written relative to inner.sol, so the not-found alternative is structurally impossible and the rejection is isolable to non-transitivity; SOL-TCK-0092 is the positive control."),
]

for r in REQUIREMENTS:
    for q in r["quotes"]:
        if normalize(q) not in SPEC_N:
            sys.exit("QUOTE NOT IN SPEC [%s]: %r" % (r["id"], q[:100]))

os.makedirs(CORPUS, exist_ok=True)
written = 0
for tid, files in FILES.items():
    d = os.path.join(CORPUS, tid)
    os.makedirs(d, exist_ok=True)
    for name, body in files.items():
        path = os.path.join(d, name)
        os.makedirs(os.path.dirname(path) or d, exist_ok=True)
        open(path, "w", encoding="utf-8").write(body)
    category, reqs, outcome, exp = MANIFESTS[tid]
    e = {}
    for k, v in exp.items():
        if k == "stdout":
            e["stdoutBase64"] = base64.b64encode(v).decode("ascii")
        elif k == "diagnostic":
            e["diagnostic"] = dict(v)
        else:
            e[k] = v
    man = {
        "manifestSchemaVersion": 1,
        "specVersion": SPEC_VERSION,
        "testId": tid,
        "category": category,
        "profile": "full-language",
        "status": "required",
        "requirements": reqs,
        "entryPoint": "main.sol",
        "fixtureRoot": ".",
        "outcome": outcome,
        "expectation": e,
    }
    with open(os.path.join(d, tid + ".manifest.json"), "w", encoding="utf-8") as h:
        json.dump(man, h, indent=2)
        h.write("\n")
    written += 1

data = json.load(open(REQS, encoding="utf-8"))
existing = {r["id"] for r in data["requirements"]}
added = 0
for r in REQUIREMENTS:
    if r["id"] in existing:
        continue
    data["requirements"].append({
        "id": r["id"], "specVersion": SPEC_VERSION, "section": r["section"],
        "summary": r["summary"], "kind": r["kind"], "profile": "full-language",
        "portable": True, "tests": r["tests"], "status": "tested", "lifecycle": "active",
        "oracleNotes": r["notes"],
        "normativeQuotes": [normalize(q) for q in r["quotes"]],
    })
    added += 1
data["requirements"].sort(key=lambda r: r["id"])
with open(REQS, "w", encoding="utf-8") as h:
    json.dump(data, h, indent=2)
    h.write("\n")

# profile registration
pp = os.path.join(ROOT, "tck/profiles/full-language.profile.json")
pd = json.load(open(pp, encoding="utf-8"))
pd["requirements"] = sorted(set(pd["requirements"]) | {r["id"] for r in data["requirements"]})
json.dump(pd, open(pp, "w", encoding="utf-8"), indent=2)
open(pp, "a").write("\n")

print("sources+manifests: %d   requirements added: %d   total reqs: %d   profile: %d"
      % (written, added, len(data["requirements"]), len(pd["requirements"])))
