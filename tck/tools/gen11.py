#!/usr/bin/env python3
"""Generate the section-11 (generics/collections) TCK batch: requirements + manifests.

Oracle-independent by construction: expected stdout strings are the literal values that
were derived by hand in each main.sol and then confirmed against that derivation; the
implementation is never consulted here. Every normativeQuote is machine-verified to occur
verbatim (after the same normalization test_oracle_quotes.py applies) in LANGUAGE_SPEC.md
before anything is written.
"""
import base64, json, os, re, sys, glob

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = os.path.join(ROOT, "docs/LANGUAGE_SPEC.md")
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


SPEC_N = normalize(open(SPEC, encoding="utf-8").read())

# ---------------------------------------------------------------- requirements
Q = {
    "list_ops": ("`List<T>`: `val isEmpty: Boolean`, `val size: Integer`, "
                 "`func add(element: T)`, `func get(index: Integer): T`, "
                 "`func removeAt(index: Integer): T`, `func set(index: Integer, element: T)`, "
                 "`func clear()`. An invalid index\nraises a Solvik runtime bounds error."),
    "set_ops": ("`Set<T>`: `val isEmpty: Boolean`, `val size: Integer`, "
                "`func add(element: T): Boolean`,\n`func contains(element: T): Boolean`, "
                "`func remove(element: T): Boolean`, `func clear()`."),
    "map_ops": ("`Map<K, V>`: `val isEmpty: Boolean`, `val size: Integer`, "
                "`func put(key: K, value: V)`,\n`func get(key: K): V`, "
                "`func containsKey(key: K): Boolean`, `func remove(key: K): Boolean`,\n"
                "`func clear()`. `get` for a missing key raises a Solvik collection error."),
    "stack_ops": ("`Stack<T>`: `val isEmpty: Boolean`, `val size: Integer`, "
                  "`func push(element: T)`, `func peek(): T`,\n`func pop(): T`, `func clear()`. "
                  "`peek` and `pop` on an empty stack raise a Solvik collection error."),
    "construction": ("A collection is constructed with a class-style call. The type arguments may be written explicitly\n"
                     "(`List<Integer>(1, 2, 3)`)"),
    "no_args": ("a construction that writes neither is a compile-time\nerror. A call with no value arguments constructs an empty collection (`List<Integer>()`)."),
    "elements": ("For `List`, `Set`, and `Stack`, the value arguments are the initial elements and each must be\n"
                 "assignable to the element type; `Set` keeps only the first of equal elements. `Map` takes\n"
                 "`key: value` entries, each key assignable to `K` and each value assignable to `V`; a repeated key\n"
                 "keeps its position and takes the latest value. A `key: value` entry is meaningful only in a `Map`\n"
                 "construction, and a positional value is not valid in a `Map`\nconstruction."),
    "nominal": ("`List<T>`, `Set<T>`, `Stack<T>`, and `Map<K, V>` are the initial built-in mutable collection types. They are nominal generic types deriving from `Any`; their type arguments are invariant and erased at"),
    "invariant": "Generic type arguments are invariant. The initial runtime uses erasure while preserving complete compile-time checking. A runtime type test against a non-reified type argument is a compile-time error.",
}

for name, q in Q.items():
    if normalize(q) not in SPEC_N:
        sys.exit("QUOTE NOT IN SPEC [%s]: %r" % (name, q[:90]))

REQUIREMENTS = [
 dict(id="REQ-0900", section="11. Generics",
      summary="`List<T>` exposes the operations and return types its operation table gives, and construction with explicit type arguments populates it",
      kind="library",
      quotes=[Q["list_ops"], Q["construction"]],
      tests=["SOL-TCK-0076"],
      notes=("The table is the only normative source for `List` behavior, so it is quoted in full rather than "
             "paraphrased. Two of its entries -- `add` and `set` -- take `element: T` and declare no return type, "
             "so they are Unit-returning and SOL-TCK-0076 uses them only as statements: section 11 gives them no "
             "value that could be printed, and asserting a rendering for `Unit` would invent semantics. The "
             "printed stream is therefore restricted to the entries the table types as returning a value. "
             "Deliberate scope limit: section 11's table lists `isEmpty` for `List` but gives `List` no way to "
             "observe order, so no order-dependent assertion is made."),
      category="collections"),
 dict(id="REQ-0901", section="11. Generics",
      summary="`Set<T>` de-duplicates its initial elements and its Boolean-valued operations report insertion, membership, and removal",
      kind="library",
      quotes=[Q["set_ops"], Q["elements"]],
      tests=["SOL-TCK-0077"],
      notes=("The sentence `Set` keeps only the first of equal elements` fixes the cardinality of a set built "
             "from a duplicated element, and the table fixes the return type of every operation as Boolean, so "
             "each printed value is forced. Section 11 gives `Set` no iteration order, so the oracle asserts only "
             "order-independent facts; asserting an element position would invent deferred semantics. "
             "`clear` and the `isEmpty` transition to true are not separately exercised because the table gives "
             "`clear` no return value and the empty rendering is covered by the Map and Stack tests."),
      category="collections"),
 dict(id="REQ-0902", section="11. Generics",
      summary="`Map<K, V>` accepts `key: value` construction entries, keeps a repeated key's latest value, and reports size, membership, and removal",
      kind="library",
      quotes=[Q["map_ops"], Q["elements"], Q["no_args"]],
      tests=["SOL-TCK-0078", "SOL-TCK-0088"],
      notes=("SOL-TCK-0078 covers the `put`/`get`/`containsKey`/`remove` sequence on a map built by the "
             "empty-construction form; SOL-TCK-0088 covers a repeated key supplied as construction entries, the "
             "form the sentence about repeated keys actually describes. Splitting them keeps the two observable "
             "consequences of a repeated key -- the entry count does not grow and the value is the latest written "
             "-- attributable to construction rather than to `put`. \"keeps its position\" is deliberately not "
             "asserted: section 11 gives `Map` no iteration order in this revision, so no position-derived "
             "observable exists and asserting one would invent deferred semantics."),
      category="collections"),
 dict(id="REQ-0903", section="11. Generics",
      summary="`Stack<T>` reports last-in first-out order through `peek` and `pop`, both typed to return the element",
      kind="library",
      quotes=[Q["stack_ops"], Q["no_args"]],
      tests=["SOL-TCK-0079"],
      notes=("The table types `peek(): T` and `pop(): T`, so both are printable and the sequence forces "
             "`peek` to leave the stack unchanged while `pop` removes the element it reported; that "
             "size 2 -> 1 transition is what distinguishes last-in-first-out from a queue without the "
             "specification having to name either. The empty-stack failure half of the same sentence is "
             "REQ-0905."),
      category="collections"),
 dict(id="REQ-0904", section="11. Generics",
      summary="A type argument may be written explicitly or inferred from the declared type of the left-hand side, but a construction writing neither is a compile-time error",
      kind="compile-time",
      quotes=[Q["construction"], Q["no_args"]],
      tests=["SOL-TCK-0084"],
      notes=("The sentence enumerates exactly three alternatives and names the third as a compile-time error, so "
             "the test constructs precisely that third alternative. The manifest asserts a compile-time rejection "
             "with no code and no family: the sentence names no stable code, and the phase is not forced by the "
             "specification's vocabulary, so pinning one would test an implementation choice. Deliberate scope "
             "limit: the specification names no SOLV-* code for this rule."),
      category="collections"),
 dict(id="REQ-0905", section="11. Generics",
      summary="Collection failure modes raise: an invalid `List` index, `Map.get` for a missing key, and `peek` or `pop` on an empty stack",
      kind="runtime",
      quotes=[Q["list_ops"], Q["map_ops"], Q["stack_ops"]],
      tests=["SOL-TCK-0080", "SOL-TCK-0081", "SOL-TCK-0082", "SOL-TCK-0083"],
      notes=("Four tests because the specification states three distinct sentences and the `Stack` sentence names "
             "two operations: an implementation that rejects only `pop` while returning a value from `peek` would "
             "pass a single-test corpus, so each named operation is tested separately. Each program is static "
             "under section 11's own signatures, so the failure cannot be attributed to a different rule. The "
             "specification names no stable code for a Solvik runtime bounds error or a Solvik collection error, "
             "so the manifests assert the protocol runtime category (protocol.md section 4.1) rather than a "
             "SOLV-* code, and record no process exit status: the specification says only that a failure is "
             "raised."),
      category="collections"),
 dict(id="REQ-0906", section="11. Generics",
      summary="Construction initial elements must be assignable to the element type, and a positional value is not valid where a `Map` requires `key: value` entries",
      kind="compile-time",
      quotes=[Q["elements"]],
      tests=["SOL-TCK-0085", "SOL-TCK-0086"],
      notes=("The two rejections differ in how much the specification forces. Element assignability is a typing "
             "relation, so SOL-TCK-0086 asserts the TYPE family: no conforming implementation can decide "
             "assignability outside type checking. The clause that a positional value 'is not valid in a `Map` "
             "construction' is about construction form and names no phase, so SOL-TCK-0085 asserts only 'rejected "
             "at compile time'. The specification names no SOLV-* code for either rule."),
      category="collections"),
 dict(id="REQ-0907", section="11. Generics",
      summary="Type arguments are invariant, and a runtime type test against a non-reified type argument is a compile-time error",
      kind="compile-time",
      quotes=[Q["invariant"], Q["nominal"]],
      tests=["SOL-TCK-0087", "SOL-TCK-0090", "SOL-TCK-0091"],
      notes=("SOL-TCK-0091 rejects `List<Base> = List<Derived>` for a genuine nominal subtype, and SOL-TCK-0090 "
             "is its positive control, identical except for the declared type argument, so the rejection is "
             "isolated to invariance rather than to the class declarations or to `List` as a declared type. A "
             "numeric pair was rejected for this role: section 4 states that numeric types remain siblings under "
             "`Number`, so `List<Number>` rejects `List<Integer>` for reasons independent of invariance and would "
             "pass under a covariant implementation. SOL-TCK-0087 covers the non-reified type-test sentence, for "
             "which the specification names no code and forces no phase, so it asserts a bare compile-time "
             "rejection."),
      category="generics"),
 dict(id="REQ-0908", section="11. Generics",
      summary="A user-declared nominal generic class binds its type parameter per use, so independent instantiations hold independent typed storage",
      kind="runtime",
      quotes=[Q["invariant"]],
      tests=["SOL-TCK-0089"],
      notes=("Section 11's own `class Box<T> { mutable val value: T }` example is the declaration under test; the program "
             "adds the constructor that section 7 requires because section 2 rejects a `mutable val` property with no "
             "initializer, which changes no generics semantics. Two different type arguments on the same declared "
             "class are the point: a runtime that shared one storage cell across instantiations could not print "
             "both the `String` and the incremented `Integer`. The type-test sentence is quoted because it is the "
             "only normative sentence in section 11 that speaks to user-declared generic types."),
      category="generics"),
]

for r in REQUIREMENTS:
    assert all(normalize(q) in SPEC_N for q in r["quotes"]), r["id"]

# ------------------------------------------------------------------- manifests
SUCCESS = "SUCCESS"; RUNTIME = "RUNTIME_ERROR"; COMPILE = "COMPILE_ERROR"
BARE = {}
TESTS = {
 "SOL-TCK-0076": ("collections", ["REQ-0900"], SUCCESS,
   dict(languageExit=0, stdout=b"3 40 99 20 3 false true")),
 "SOL-TCK-0077": ("collections", ["REQ-0901"], SUCCESS,
   dict(languageExit=0, stdout=b"3 false true 4 true true false false")),
 "SOL-TCK-0078": ("collections", ["REQ-0902"], SUCCESS,
   dict(languageExit=0, stdout=b"true 2 2 7 2 false true false 1")),
 "SOL-TCK-0079": ("collections", ["REQ-0903"], SUCCESS,
   dict(languageExit=0, stdout=b"true 2 2 2 1 false")),
 "SOL-TCK-0080": ("collections", ["REQ-0905"], RUNTIME,
   dict(runtimeCategory="INDEX_OUT_OF_BOUNDS", stdout=b"")),
 "SOL-TCK-0081": ("collections", ["REQ-0905"], RUNTIME,
   dict(runtimeCategory="COLLECTION_FAILURE", stdout=b"")),
 "SOL-TCK-0082": ("collections", ["REQ-0905"], RUNTIME,
   dict(runtimeCategory="COLLECTION_FAILURE", stdout=b"")),
 "SOL-TCK-0083": ("collections", ["REQ-0905"], RUNTIME,
   dict(runtimeCategory="COLLECTION_FAILURE", stdout=b"")),
 "SOL-TCK-0084": ("collections", ["REQ-0904"], COMPILE, dict(diagnostic=BARE)),
 "SOL-TCK-0085": ("collections", ["REQ-0906"], COMPILE, dict(diagnostic=BARE)),
 "SOL-TCK-0086": ("collections", ["REQ-0906"], COMPILE,
   dict(diagnostic=dict(family="TYPE"))),
 "SOL-TCK-0087": ("generics", ["REQ-0907"], COMPILE, dict(diagnostic=BARE)),
 "SOL-TCK-0088": ("collections", ["REQ-0902"], SUCCESS,
   dict(languageExit=0, stdout=b"2 9 2")),
 "SOL-TCK-0089": ("generics", ["REQ-0908"], SUCCESS,
   dict(languageExit=0, stdout=b"hi 8")),
 "SOL-TCK-0090": ("generics", ["REQ-0907"], SUCCESS,
   dict(languageExit=0, stdout=b"base=1")),
 "SOL-TCK-0091": ("generics", ["REQ-0907"], COMPILE, dict(diagnostic=BARE)),
}

def build_manifest(tid, category, reqs, outcome, expectation):
    exp = {}
    for k, v in expectation.items():
        if k == "stdout":
            exp["stdoutBase64"] = base64.b64encode(v).decode("ascii")
        elif k == "diagnostic":
            exp["diagnostic"] = dict(v)
        else:
            exp[k] = v
    return {
        "manifestSchemaVersion": 1,
        "specVersion": SPEC_VERSION,
        "testId": tid,
        "category": category,
        "profile": "full-language",
        "status": "required",
        "requirements": list(reqs),
        "entryPoint": "main.sol",
        "outcome": outcome,
        "expectation": exp,
    }

for tid in TESTS:
    d = os.path.join(CORPUS, tid)
    src = os.path.join(d, "main.sol")
    if not os.path.exists(src):
        sys.exit("missing source for %s" % tid)
    man = build_manifest(tid, *TESTS[tid])
    with open(os.path.join(d, tid + ".manifest.json"), "w", encoding="utf-8") as h:
        json.dump(man, h, indent=2)
        h.write("\n")

# ----------------------------------------------------------------- requirements
data = json.load(open(REQS, encoding="utf-8"))
existing = {r["id"] for r in data["requirements"]}
added = 0
for r in REQUIREMENTS:
    if r["id"] in existing:
        continue
    data["requirements"].append({
        "id": r["id"],
        "specVersion": SPEC_VERSION,
        "section": r["section"],
        "summary": r["summary"],
        "kind": r["kind"],
        "profile": "full-language",
        "portable": True,
        "tests": r["tests"],
        "status": "tested",
        "lifecycle": "active",
        "oracleNotes": r["notes"],
        "normativeQuotes": [normalize(q) for q in r["quotes"]],
    })
    added += 1
data["requirements"].sort(key=lambda r: r["id"])
with open(REQS, "w", encoding="utf-8") as h:
    json.dump(data, h, indent=2)
    h.write("\n")

print("manifests written: %d   requirements added: %d   total requirements: %d"
      % (len(TESTS), added, len(data["requirements"])))
