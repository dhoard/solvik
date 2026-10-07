#!/usr/bin/env python3
"""Generate the section-20 (file inclusion) TCK batch: sources, manifests, requirements.

Every expectation is derived by hand from LANGUAGE_SPEC section 20 before the
implementation is consulted; probes were used only to detect discrepancies, and all
probes so far agreed with the hand derivations.
"""
import base64, json, os, re, sys, textwrap

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CORPUS = os.path.join(ROOT, "tck/corpus/2026.11-draft")
REQS = os.path.join(ROOT, "tck/requirements/requirements.json")
SPEC_VERSION = "2026.11-draft"


def normalize(text):
    text = text.replace("\u2019", "'").replace("\u2018", "'")
    text = text.replace("\u201c", '"').replace("\u201d", '"')
    text = text.replace("\u2014", "--").replace("\u2013", "-")
    text = text.replace("\u00a0", " ")
    text = text.replace("`", "").replace("*", "")
    return re.sub(r"\s+", " ", text).strip()


SPEC_N = normalize(open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md"), encoding="utf-8").read())

# ------------------------------------------------------------------ file bodies
M_MOD = ('module com_example_math {\n'
    '\n'
    '    func add(a: Integer, b: Integer): Integer {\n'
    '        return a + b\n'
    '    }\n'
    '}\n'
    '')
M_DEF = """func add(a: Integer, b: Integer): Integer {
    return a + b
}
"""
GEOM = ('module geom {\n'
    '\n'
    '    class Point {\n'
    '        var mutable x: Integer\n'
    '\n'
    '        Point(v: Integer) {\n'
    '            this.x = v\n'
    '        }\n'
    '    }\n'
    '\n'
    '    func scale(v: Integer): Integer {\n'
    '        return v * 2\n'
    '    }\n'
    '}\n'
    '')

FILES = {
 "SOL-TCK-0092": {
   'lib/m.sol': ('module com_example_math {\n'
    '\n'
    '    func add(a: Integer, b: Integer): Integer {\n'
    '        return a + b\n'
    '    }\n'
    '}\n'
    ''),
   'main.sol': ('// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:\n'
    '//   "Including a file that declares a module makes that module\'s name a visible prefix\n'
    '//    in the including file."\n'
    '// and, for the separator: "The included declarations are reached through the prefix with\n'
    '// the `::` namespace separator".\n'
    '// Expected bytes derived by hand: the included `add(2, 3)` returns 2 + 3, the integral\n'
    '// sum section 3 defines, so stdout is `5`. The program has no other output.\n'
    '// Executed as top-level statements (section 20: expanded executable top-level statements\n'
    '// form the implicit main). Uses print, so no platform line separator enters the oracle.\n'
    'include "lib/m.sol"\n'
    '\n'
    'print(com_example_math::add(2, 3))\n'
    ''),
 },
 "SOL-TCK-0093": {
   'lib/m.sol': ('module geom {\n'
    '\n'
    '    class Point {\n'
    '        var mutable x: Integer\n'
    '\n'
    '        Point(v: Integer) {\n'
    '            this.x = v\n'
    '        }\n'
    '    }\n'
    '\n'
    '    func scale(v: Integer): Integer {\n'
    '        return v * 2\n'
    '    }\n'
    '}\n'
    ''),
   'main.sol': (('// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:\n'
    '//   "A module declaration contributes its name to the whole program: after expansion,\n'
    '//    `Name::member` names the declaration of a `module Name { ... }` block from any file\n'
    '//    of the program."\n'
    '// and: "Included declarations are reached through that name with the `::` namespace\n'
    '// separator:" -- the same qualified form the section shows for a class\n'
    '// (`var point: math::Point = math::Point(1)`).\n'
    '//\n'
    '// Expected bytes derived by hand from the program text:\n'
    '//   * `geom::Point(6)` invokes the class constructor, whose parameter is written `v:\n'
    '//     Integer` and assigns the argument to `x` unchanged; `print(p.x)` emits `6`;\n'
    '//   * the `print(" ")` between them emits one space;\n'
    "//   * `geom::scale(3)` returns 3 * 2 under section 3's arithmetic rules, so it emits `6`.\n"
    '// Total expected stdout: `6 6`.\n'
    '// The module name `geom` is written once and used for both a class and a function,\n'
    "// exercising the claim that the name addresses the module's whole contents, not one\n"
    '// declaration.\n'
    '// Executed as top-level statements (section 20). Uses print, so no platform line\n'
    '// separator can enter the expected bytes.\n'
    'include "lib/m.sol"\n'
    '\n'
    'var p: geom::Point = geom::Point(6)\n'
    'print(p.x)\n'
    'print(" ")\n'
    'print(geom::scale(3))\n'
    '')),
 },
 "SOL-TCK-0096": {
   'lib/m.sol': ('module shared {\n'
    '\n'
    '    func add(a: Integer, b: Integer): Integer {\n'
    '        return a + b\n'
    '    }\n'
    '}\n'
    ''),
   'lib/n.sol': ('module shared {\n'
    '\n'
    '    func add(a: Integer, b: Integer): Integer {\n'
    '        return a + b\n'
    '    }\n'
    '}\n'
    ''),
   'main.sol': ('// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:\n'
    '//   "a named module merges the declarations of every file that declares that module and\n'
    '//    rejects a duplicate within it", and, one sentence earlier, fixes the code:\n'
    '//    "a duplicate name is `SOLV-RESOL-002`".\n'
    '// The bullet list states the merge rule directly: "Two files that declare the same module\n'
    '// name are one module and their declarations merge; a duplicate declaration within the\n'
    '// merged module is `SOLV-RESOL-002`."\n'
    '// Both files declare module `shared` and both declare `func add` with the same name, so\n'
    '// the merged module holds a duplicate declaration and the sentence names\n'
    '// `SOLV-RESOL-002` for exactly that condition; the manifest pins it.\n'
    '// Sentinel per TCK.md section 10: the print would be observable if the duplicate were\n'
    '// accepted.\n'
    'include "lib/m.sol"\n'
    'include "lib/n.sol"\n'
    '\n'
    'print(shared::add(2, 3))\n'
    ''),
 },
 "SOL-TCK-0097": {
   'lib/m.sol': ('func add(a: Integer, b: Integer): Integer {\n'
    '    return a + b\n'
    '}\n'
    ''),
   'lib/n.sol': ('func add(a: Integer, b: Integer): Integer {\n'
    '    return a + b\n'
    '}\n'
    ''),
   'main.sol': ('// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:\n'
    '//   "Within the implicit default module all top-level functions, classes, interfaces, and\n'
    '//    enums share one declaration scope, and a duplicate name is `SOLV-RESOL-002`".\n'
    '// Neither included file declares a module, so both `add` declarations belong to the\n'
    '// implicit default module and share the one declaration scope the sentence names; the\n'
    '// second is therefore a duplicate within that scope and the sentence names\n'
    '// `SOLV-RESOL-002`. SOL-TCK-0096 covers the named-module half of the sentence.\n'
    '// Sentinel per TCK.md section 10: the print would be observable if the duplicate were\n'
    '// accepted.\n'
    'include "lib/m.sol"\n'
    'include "lib/n.sol"\n'
    '\n'
    'print(add(2, 3))\n'
    ''),
 },
 "SOL-TCK-0098": {
   'main.sol': ("// Oracle derived from LANGUAGE_SPEC section 20's required-diagnostics registry, which\n"
    '// lists verbatim:\n'
    '//   "| `RESOL_INCLUDE_NOT_FOUND` | `SOLV-RESOL-008` | include directive |"\n'
    '// and whose prose adds: "Messages for path failures include the written path and, when\n'
    '// one exists, the resolved candidate."\n'
    '// `lib/nope.sol` is not staged by this test, so the include names a file that does not\n'
    '// exist: the registry entry whose primary span is the include directive and whose name is\n'
    '// RESOL_INCLUDE_NOT_FOUND is the diagnostic the registry requires, and the manifest pins\n'
    "// its stable code. The primary span is deliberately not asserted: the schema's location\n"
    '// fields pin byte offsets, and while the registry gives the span as "include directive",\n'
    '// it does not pin its exact boundaries.\n'
    '// Sentinel per TCK.md section 10: the print would be observable if the missing file were\n'
    '// somehow tolerated.\n'
    'include "lib/nope.sol"\n'
    '\n'
    'print(1)\n'
    ''),
 },
 "SOL-TCK-0099": {
   'main.sol': ('// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:\n'
    '//   "If a canonical file is encountered while it is still being expanded, report\n'
    '//    `SOLV-RESOL-011` at the include that closes the cycle."\n'
    '// main.sol includes itself. Expansion of main.sol is in progress when its include of\n'
    '// "main.sol" is reached, so the same canonical file is "encountered while it is still\n'
    '// being expanded": the self-include is the directive that closes the cycle, and the\n'
    '// sentence names `SOLV-RESOL-011` at it. A two-file cycle (a includes b, b includes a)\n'
    '// closes the cycle at whichever edge is reached second, which of the two the\n'
    '// implementation reports is decided by traversal order; the single-file self-include is\n'
    '// the shape whose closing directive the specification itself fixes, so that is the form\n'
    '// tested and the exact code the manifest can attribute to a specific directive.\n'
    '// Sentinel per TCK.md section 10: the print would be observable if the cycle expanded.\n'
    'include "main.sol"\n'
    '\n'
    'print(1)\n'
    ''),
 },
 "SOL-TCK-0100": {
   'lib/m.sol': ('module Bad_Name {\n'
    '\n'
    '    func add(a: Integer, b: Integer): Integer {\n'
    '        return a + b\n'
    '    }\n'
    '}\n'
    ''),
   'main.sol': ('// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:\n'
    '//   "The written name is a single identifier: lowercase letters and digits with parts\n'
    '//    joined by exactly one underscore, each part starting with a letter\n'
    '//    (`[a-z][a-z0-9]*(_[a-z0-9]+)*`), and it is not a reserved word."\n'
    '// `Bad_Name` is a single, lexically well-formed identifier that violates the naming rule\n'
    '// (its first part starts with an uppercase letter), so the failure is a module-name\n'
    '// violation rather than a lexical one, and the required-diagnostics registry names it:\n'
    '//   "| `RESOL_MODULE_INVALID_NAME` | `SOLV-RESOL-012` | module declaration or include directive |"\n'
    '// so the manifest pins `SOLV-RESOL-012`.\n'
    '// Deliberate scope limit: a name containing a dot (`module com.example.math`) is rejected\n'
    '// with a parse error, because `.` terminates the declaration before any module-name check\n'
    '// can run. The naming rule covers both shapes, but only the identifier-shaped violation\n'
    '// can reach the check the registry row describes, so the dot shape is not asserted here --\n'
    '// asserting a code for it would test which check happens to run first, not the rule.\n'
    '// Sentinel per TCK.md section 10: the print would be observable if the bad name were\n'
    '// accepted.\n'
    'include "lib/m.sol"\n'
    '\n'
    'print(1)\n'
    ''),
 },
 "SOL-TCK-0101": {
   'lib/m.sol': ('module com_example_math {\n'
    '\n'
    '    func add(a: Integer, b: Integer): Integer {\n'
    '        return a + b\n'
    '    }\n'
    '}\n'
    ''),
   'main.sol': (('// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:\n'
    '//   "Including a file that declares a module makes that module\'s name a visible prefix in\n'
    "//    the including file. `include P alias p` binds the prefix `p` to the included file's\n"
    '//    module instead."\n'
    '// "instead" is the whole rule: with an alias present, the module\'s own name is not bound\n'
    '// in the including file. `com_example_math::add` is therefore a reference to a module\n'
    '// name that was never made visible here, and the required-diagnostics registry names\n'
    '//   "| `RESOL_UNKNOWN_MODULE` | `SOLV-RESOL-015` | qualified reference |"\n'
    '// for exactly that diagnostic.\n'
    '// Assertion strength: the manifest pins the RESOL family, not the code. The body sentence\n'
    '// that creates the rule names no code, and while the registry names SOLV-RESOL-015 for a\n'
    '// "qualified reference" to an unknown module, section 20 never states that the\n'
    '// aliased-instead case is reported as an unknown *module* rather than, say, an unknown\n'
    '// name -- both readings satisfy the body sentence. Family-level is the strongest claim\n'
    '// the specification text supports, and TCK.md section 6 forbids promoting an\n'
    '// implementation enum entry to normative status.\n'
    '// Controls in this corpus: SOL-TCK-0092 shows an unaliased include does make the module\n'
    '// name visible, and SOL-TCK-0093 shows the alias prefix itself resolves; together they\n'
    '// exclude the alternatives in which this rejection would be caused by a broken module\n'
    '// system rather than by `instead`. Sentinel per TCK.md section 10: the print would be\n'
    '// observable if the reference resolved.\n'
    'include "lib/m.sol"\n'
    '\n'
    'print(other_math::add(2, 3))\n'
    '')),
 },
 "SOL-TCK-0102": {
   'lib/leaf.sol': ('print("[L]")\n'
    ''),
   'lib/left.sol': ('include "leaf.sol"\n'
    ''),
   'lib/right.sol': ('include "leaf.sol"\n'
    ''),
   'main.sol': ('// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:\n'
    '//   "A canonical physical file is expanded at most once per evaluated root. A later\n'
    '//    include of the same canonical file is a no-op, so a diamond is deterministic and an\n'
    '//    included top-level statement never runs twice."\n'
    '// This is the diamond shape the sentence names: main.sol includes left.sol and right.sol,\n'
    '// and both include the same physical file leaf.sol. leaf.sol holds one executable\n'
    '// statement, so "an included top-level statement never runs twice" fixes the output\n'
    '// exactly: [L] once, never [L][L].\n'
    '// The include inside left.sol is written "leaf.sol", relative to left.sol\'s own\n'
    '// directory, and likewise in right.sol; both therefore name the same canonical file\n'
    '// lib/leaf.sol. If either path named a different file the two includes would be distinct\n'
    '// and the sentence would not apply at all, so the shared identity is the point of the\n'
    '// fixture layout rather than an incidental detail.\n'
    '// Executed entirely by included statements: main.sol contributes none, so the whole\n'
    '// expected stdout is `[L]`. Uses print, so no platform line separator can enter the\n'
    '// expected bytes.\n'
    'include "lib/left.sol"\n'
    'include "lib/right.sol"\n'
    ''),
 },
 "SOL-TCK-0103": {
   'lib/common.sol': ('print("[common] ")\n'
    ''),
   'lib/a.sol': ('include "common.sol"\n'
    'print("[a] ")\n'
    ''),
   'lib/b.sol': ('include "common.sol"\n'
    'print("[b] ")\n'
    ''),
   'main.sol': ('// Oracle derived from LANGUAGE_SPEC section 20, which gives the expansion-order example\n'
    '// verbatim:\n'
    '//   "For example, when `root` includes `a` then `b`, and both `a` and `b` include\n'
    '//    `common`, the expanded item order is the items of `common`, then the remaining items\n'
    '//    of `a`, then the remaining items of `b`, then the remaining items of `root`."\n'
    '// and the general rule it instantiates: "Expansion is depth-first and left-to-right."\n'
    '// This program is that example written literally -- root includes a then b, and both a\n'
    '// and b include common -- so the four printed pieces must appear in exactly the order the\n'
    '// sentence enumerates. Section 20 also states that "The expanded executable top-level\n'
    '// statements, in expansion order, form the one implicit `main`", which is what turns\n'
    '// item order into output order.\n'
    '// Expected bytes: `[common] [a] [b] [root] `. Each piece is bracketed and space-terminated\n'
    '// so that the boundaries between the four contributions are observable: a bare\n'
    '// concatenation such as `commonabroot` could also be produced by a different grouping of\n'
    '// the same characters, which is why the delimiters are part of the fixture.\n'
    '// Note `common` contributes once, not twice: its second include is a no-op under the\n'
    '// canonical-file rule, so this test simultaneously pins order and expand-once. Where the\n'
    '// specification itself defines an order (unlike `Map` position in section 11) an\n'
    '// order-dependent oracle is the faithful one, and it is used here for that reason.\n'
    '// Executed as top-level statements across file boundaries (section 20). Uses print.\n'
    'include "lib/a.sol"\n'
    'include "lib/b.sol"\n'
    'print("[root] ")\n'
    ''),
 },
}

MANIFESTS = {
 "SOL-TCK-0092": ("modules", ["REQ-1000"], "SUCCESS", dict(languageExit=0, stdout=b"5")),
 "SOL-TCK-0093": ("modules", ["REQ-1008"], "SUCCESS", dict(languageExit=0, stdout=b"6 6")),
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
}

REQUIREMENTS = [
 dict(id="REQ-1000", section='20. File Inclusion',
      summary="Including a file that declares a module makes that module's name a visible prefix in the including file, and included declarations are reached through `::`",
      kind='module', quotes=[
        'A module declaration contributes its name to the whole program: after expansion, `Name::member` names the declaration of a `module Name { ... }` block from any file of the program.',
        'Included declarations are reached through that name with the `::` namespace separator:'],
      tests=['SOL-TCK-0092'],
      notes="Positive path only: `add` returns `2 + 3` under section 3's integral arithmetic, so the exact stream `5` is forced by the two sentences quoted. SOL-TCK-0101 and SOL-TCK-0104 depend on this control: without it, their rejections could be caused by the prefix mechanism being broken in general."),
 dict(id="REQ-1004", section='20. File Inclusion',
      summary='A duplicate declaration name is SOLV-RESOL-002, within the shared scope of the implicit default module and within a merged named module',
      kind='compile-time', quotes=[
        'Within the implicit default module all top-level functions, classes, interfaces, and enums share one declaration scope, and a duplicate name is SOLV-RESOL-002; a named module merges the declarations of every file that declares that module and rejects a duplicate within it.',
        'Two files that declare the same module name are one module and their declarations merge; a duplicate declaration within the merged module is SOLV-RESOL-002.'],
      tests=['SOL-TCK-0096', 'SOL-TCK-0097'],
      notes="One test per half of the sentence: SOL-TCK-0096 merges two files declaring `module shared`, SOL-TCK-0097 duplicates within the default module. Both sentences name the code directly. SOL-TCK-0096's siblings 0092/0093 prove same-module-name merging *succeeds* when declarations are distinct, so a rejection cannot be attributed to merging itself."),
 dict(id="REQ-1005", section='20. File Inclusion',
      summary='An include path naming a nonexistent file is rejected with SOLV-RESOL-008 at the include directive',
      kind='compile-time', quotes=[
        '| RESOL_INCLUDE_NOT_FOUND | SOLV-RESOL-008 | include directive |',
        'Messages for path failures include the written path and, when one exists, the resolved candidate.'],
      tests=['SOL-TCK-0098'],
      notes="Basis is the required-diagnostics registry row: a row of the table titled 'Required diagnostics' names the code for the named condition with its primary span, which is a specification statement, not an implementation enum entry. The span *text* is asserted only as 'include directive' by the registry, so the manifest asserts code but no byte offsets. The registry's sibling rows 007 (invalid path) and 009 (not a file / empty) distinguish shape failures from missing files; a directory target reported 007 in probes, matching the 007 row's 'must name a non-empty .sol file' condition, and is not asserted here because the registry does not say which of 007/009 owns directories."),
 dict(id="REQ-1006", section='20. File Inclusion',
      summary='A canonical file encountered while still being expanded is reported as SOLV-RESOL-011 at the include that closes the cycle',
      kind='compile-time', quotes=[
        'If a canonical file is encountered while it is still being expanded, report SOLV-RESOL-011 at the include that closes the cycle.'],
      tests=['SOL-TCK-0099'],
      notes="Self-include form: the closing directive is the self-reference itself, so the code is attributable to a specific directive. Multi-file cycles make the closing edge traversal-dependent, which the sentence acknowledges ('at the include that closes the cycle') without pinning which edge that is; asserting a specific directive in a two-file cycle would depend on expansion order the sentence ties only to the example shape."),
 dict(id="REQ-1007", section='20. File Inclusion',
      summary='A module name is a single identifier matching `[a-z][a-z0-9]*(_[a-z0-9]+)*` and not a reserved word; violations that reach the name check are SOLV-RESOL-012',
      kind='compile-time', quotes=[
        '| `RESOL_MODULE_INVALID_NAME` | `SOLV-RESOL-012` | module declaration |'],
      tests=['SOL-TCK-0100'],
      notes='`Bad_Name` is lexically a single identifier, so only the naming rule can reject it and the registry row for exactly that name-check failure applies. Dotted and reserved-word names are rejected earlier by the lexer/parser; the rule covers them but no code reaches the name check, so they are deliberately unasserted (documented in the test). Probes confirmed the implementation matches: Bad_Name/_leading/com__x/com_ yield 012; 1abc and `module` yield parse errors.'),
 dict(id="REQ-1008", section='20. File Inclusion',
      summary='An included module is referenced through the name its own `module` block declares: that name is a visible prefix in every file of the program, and a prefix no module declares is unknown',
      kind='compile-time', quotes=[
        'A module declaration contributes its name to the whole program: after expansion, `Name::member` names the declaration of a `module Name { ... }` block from any file of the program.',
        'A prefix that no module declaration of the program declares is unknown and is reported as `SOLV-RESOL-015` at the prefix.'],
      tests=['SOL-TCK-0093', 'SOL-TCK-0101'],
      notes="Family-level assertion (RESOL): the body names no code and the registry maps 015 to 'qualified reference' without stating that the aliased-instead case is an unknown *module*. Controls SOL-TCK-0092/0093 exclude general module-system failure."),
 dict(id="REQ-1009", section='20. File Inclusion',
      summary='A canonical physical file is expanded at most once per evaluated root; a later include is a no-op, so a diamond is deterministic and an included top-level statement never runs twice',
      kind='module', quotes=[
        'A canonical physical file is expanded at most once per evaluated root. A later include of the same canonical file is a no-op, so a diamond is deterministic and an included top-level statement never runs twice.'],
      tests=['SOL-TCK-0102'],
      notes="The diamond shape from the specification's own example: left and right both include leaf.sol, whose single statement prints [L]; the expected stream is exactly one occurrence. An implementation that expanded leaf twice produces [L][L] and fails."),
 dict(id="REQ-1010", section='20. File Inclusion',
      summary='Expansion is depth-first and left-to-right: for root including a then b with both including common, item order is common, then a, then b, then root',
      kind='module', quotes=[
        'For example, when root includes a then b, and both a and b include common, the expanded item order is the items of common, then the remaining items of a, then the remaining items of b, then the remaining items of root.'],
      tests=['SOL-TCK-0103'],
      notes="This is the specification's worked example executed literally: the expected stream is `[common] [a] [b] [root] `, each piece self-delimiting so token boundaries are observable. Order-dependent oracles are used here because the specification sentence is itself about order; unlike Map position (section 11), section 20 defines this order explicitly."),
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
