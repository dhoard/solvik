#!/usr/bin/env python3
"""Generate the section 3C TCK batch: the universal equals/hashCode members --
their required declaration shape, the pairing rule, reserved names, nullable-receiver
calls, and the invalidity of a bare member read.

Oracles are DERIVED FROM LANGUAGE_SPEC.md and only then compared with the
implementation.

Why this batch pins locations rather than only codes. The oracle matches a diagnostic
by finding ANY reported diagnostic satisfying the declared fields, and a bare `{}`
therefore accepts any single diagnostic at all. Section 3 makes a countable claim that
cannot be expressed any other way: "Each violation is a compile-time error reported on
the single unpaired member, so a class missing one of the two produces one diagnostic."
A bare `{}` would accept an implementation that reported the violation somewhere else,
or that reported several. Each pairing program below is therefore constructed so the
rule under test is the ONLY thing that can produce a diagnostic -- the class overrides
nothing else wrongly, and where an inherited member is re-overridden the parent members
are declared `open` so the finality rule is not also violated -- and the manifest then
pins the exact byte span, which is the member declaration itself. The span is computed
from the program text at generation time as the offset of the member declaration through
its closing brace, so it is derived from the requirement's wording rather than copied
from observed output.

Code-pinning, as in the previous batches, is decided per code against the specification:

  * `SOLV-SEM-044` and `SOLV-SEM-045` are named in the specification's diagnostic table,
    with the table's own description matching these rules ("the `hashCode` override
    declared without `equals`" and the converse), so they are pinned.
  * `SOLV-SEM-037` is named, but only as the diagnostic for declaring `message` or
    `getMessage` on a guest exception class. Section 3 states the reserved-name rule for
    `equals`/`hashCode` in prose without naming a code, so those rejections are asserted
    BARE. Reusing the exception rule's code here would make the TCK's expectation a
    transcription of an implementation's decision to share one diagnostic between two
    different specification rules.
  * `SOLV-TYPE-024`, `SOLV-SEM-011`, `SOLV-SEM-013` and `SOLV-SEM-014` occur ZERO times in
    the specification, so every declaration-shape and nullable-receiver rejection here is
    bare.
  * `SOLV-TYPE-014` IS named, but only as "a bare member read of a `Result` operation".
    Section 3 says a bare `value.equals` read is invalid "exactly like a bare
    `value.toString` read", which establishes the prohibition but not that code, so these
    rejections are bare.

Discriminating designs:

  * The shape rules are rejected from four distinct directions for `equals` -- missing
    `override`, parameter typed `Any` instead of `Any?`, a second parameter, return type
    `Boolean?` -- and two for `hashCode`, so an implementation performing only a name
    check, or only an arity check, fails at least one arm. A program declaring the exact
    shape is the control for all six.
  * The pairing diagnostic's span is pinned because section 3 states a location as
    fact -- "reported on the single unpaired member, so a class missing one of the two
    produces one diagnostic" -- and the oracle has no way to count diagnostics, so the
    only way to express "one diagnostic, on that member" is to name the member's exact
    span in a program built so nothing else can produce a diagnostic. The span is
    computed from the program text as the member declaration without its leading
    indentation, which is the reading the specification's own phrase supports: it is the
    member that is reported on, not the whitespace before it. This is the only place in
    this batch that pins an offset, and it is the only place the specification states one.
  * The inheritance clause is pinned against its own opposite. A subclass of a class that
    overrides BOTH members needs no override of its own and must be accepted, while a
    subclass that adds only one must be rejected; the accepted arm is what stops the
    rejection from being satisfied by an implementation that rejects every override in a
    subclass, and it is the arm an implementation that checked pairing by inheritance
    rather than per declaration would reject.
  * Reserved names are pinned for all three declaration kinds the specification lists --
    a property, a delegate, and an interface member -- because it states the property and
    delegate prohibition and the interface prohibition as separate clauses.
  * Nullable receiver calls are pinned on both sides: `?.` is accepted with the result
    type the specification names (`Boolean?` for equals, `Integer?` for hashCode, each
    bound to a declaration of exactly that type, so the result type is checked and not
    merely printed), and a direct `.` call on a possibly-null receiver is rejected.
"""
import base64, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md")).read()
CORPUS = os.path.join(ROOT, "tck/corpus/2026.09-draft")


def norm(t):
    return re.sub(r"\s+", " ", t).strip()


SPEC_N = norm(SPEC)

REQS = {
 "REQ-1800": dict(
  section="3. Equality and reference identity",
  summary="A user class may declare equals only as exactly `override func equals(other: Any?): Boolean`, so the override keyword, one parameter typed exactly Any?, and a return type of exactly Boolean are each required",
  kind="compile-time",
  notes="Four rejections attack the declaration from four directions -- no override keyword, a parameter typed Any rather than Any?, an extra parameter, and a Boolean? return -- so an implementation performing only a name match, or only an arity check, fails at least one arm; each is paired with the accepted program that declares the shape exactly. All four rejections are bare because section 3 states the requirement without naming a code and the override-conformance codes it reports occur zero times in the specification.",
  quotes=["A user class may declare exactly:",
          "The compiler requires `override`, exactly one explicit parameter typed exactly `Any?`, and return type exactly `Boolean`."]),
 "REQ-1801": dict(
  section="3. Equality and reference identity",
  summary="A user class may declare hashCode only as exactly `override func hashCode(): Integer`, requiring the override keyword, no parameters, and a return type of exactly Integer",
  kind="compile-time",
  notes="The same attack pattern as the equals shape, from the parameter and return directions plus the missing override keyword, with the accepted exact-shape control shared with the equals arms. Bare rejections for the same reason: the codes reported for these cases appear nowhere in the specification.",
  quotes=["declared by a user class only as exactly `override func hashCode(): Integer`, requiring `override`, no parameters, and return type exactly `Integer`"]),
 "REQ-1802": dict(
  section="3. Equality and reference identity",
  summary="The equals/hashCode pairing is checked per class declaration and is never satisfied by inheritance, while a subclass of a class overriding both members needs no override of its own",
  kind="compile-time",
  notes="The rule is pinned against its own opposite. A subclass whose parent overrides both members and which declares nothing of its own must be accepted, which is the arm an implementation that enforced pairing by looking at inherited members would reject, and it is also the arm that stops the rejection from being satisfied by any implementation hostile to subclass overrides. The violation arm is a subclass adding only equals over a parent declaring both as open, and because these two requirements are simultaneously true the program carries exactly one diagnostic, so the manifest pins the code and the exact span of the unpaired member rather than a bare expectation -- the alternative of accepting any single diagnostic would not distinguish 'reported on the single unpaired member' from 'reported somewhere'.",
  quotes=["The rule is checked per declaration and is never satisfied by inheritance.",
          "A class that overrides neither member inherits both root defaults, which is valid because both root defaults are reference identity.",
          "Each violation is a compile-time error reported on the single unpaired member, so a class missing one of the two produces one diagnostic."]),
 "REQ-1803": dict(
  section="3. Equality and reference identity",
  summary="The names equals and hashCode are reserved as members, so a property or delegate may not use either name and an interface may not redeclare either",
  kind="compile-time",
  notes="All three declaration kinds the specification names are refused -- a property, a delegate, and an interface member -- because the property/delegate prohibition and the interface prohibition are stated as separate clauses and an implementation might enforce only one. Rejections are bare: the specification names `SOLV-SEM-037` only for declaring `message` or `getMessage` on a guest exception class, and borrowing that code here would encode an implementation's choice to share one diagnostic across two different specification rules.",
  quotes=["An interface cannot redeclare `equals`, and a property or delegate cannot use the reserved name `equals`.",
          "An interface cannot redeclare `hashCode`, and a property or delegate cannot use the reserved name `hashCode`."]),
 "REQ-1804": dict(
  section="3. Equality and reference identity",
  summary="A call on a possibly-null receiver must use the safe-call form, which yields a nullable result, so `value?.equals(other)` is safe with result Boolean?, `value?.hashCode()` yields Integer?, and a direct member call on a possibly-null receiver is an error",
  kind="compile-time",
  notes="The accepted arms bind the safe-call result to a declaration of exactly the nullable type the specification names, so the result type is checked by the type system rather than only rendered, which a print alone would not establish. The rejected arms use the same possibly-null receiver with a direct member access, differing from the accepted arms in only the call operator, so the pair constrains the receiver rule and nothing else.",
  quotes=["A direct call on a nullable receiver follows ordinary nullable-member rules: `value?.equals(other)` is safe and has result `Boolean?`, while `value.equals(other)` is an error when `value` may be null.",
          "a call on a possibly-null receiver must use `?.`, producing `Integer?`."]),
 "REQ-1805": dict(
  section="3. Equality and reference identity",
  summary="A bare member read of equals or hashCode without a call is invalid, exactly like a bare toString read",
  kind="compile-time",
  notes="All three universal members are refused in the same position, which is what the phrase 'exactly like' makes observable: an implementation that treated the three members alike in the call path but not in the member-read path would accept one of them. Rejections are bare because the code reported for a bare member read is named in the specification only for Result operations.",
  quotes=["A bare `value.equals` member read is invalid, exactly like a bare `value.toString` read.",
          "A bare `value.hashCode` member read is invalid, exactly like a bare `value.equals` or `value.toString` read"]),
}

NEG = '\nprint("EXECUTED-INVALID")\n'
S, EXPECT, CATEGORY, REQ_FOR, LOCATE = {}, {}, {}, {}, {}


def add(tid, req, category, src, outcome, locate=None, **exp):
    S[tid] = src
    CATEGORY[tid] = category
    REQ_FOR[tid] = req
    EXPECT[tid] = {"outcome": outcome, **exp}
    if locate:
        LOCATE[tid] = locate


# A class declaring the exact shape of both universal members: the shared control.
EXACT = ('class Exact {\n'
         '    Exact() {\n'
         '    }\n'
         '\n'
         '    override func equals(other: Any?): Boolean {\n'
         '        return true\n'
         '    }\n'
         '\n'
         '    override func hashCode(): Integer {\n'
         '        return 7\n'
         '    }\n'
         '}\n')
# Prefix for shape-violation programs: equals declared exactly, so the only possible
# diagnostic is the malformed hashCode declaration that follows.
EQ_OK = ('class Q {\n'
         '    Q() {\n'
         '    }\n'
         '\n'
         '    override func equals(other: Any?): Boolean {\n'
         '        return true\n'
         '    }\n'
         '\n')

# --- REQ-1800 the equals declaration shape.
add("SOL-TCK-0265", "REQ-1800", "types", EXACT + 'val p = Exact()\nprint("shape" .. (p == p))\n',
    "SUCCESS", stdout="shapetrue")
add("SOL-TCK-0266", "REQ-1800", "types",
    'class Q {\n    Q() {\n    }\n\n    func equals(other: Any?): Boolean {\n        return true\n    }\n\n'
    '    override func hashCode(): Integer {\n        return 1\n    }\n}\nprint(1)\n' + NEG,
    "COMPILE_ERROR", diag={})
add("SOL-TCK-0267", "REQ-1800", "types",
    'class Q {\n    Q() {\n    }\n\n    override func equals(other: Any): Boolean {\n        return true\n    }\n\n'
    '    override func hashCode(): Integer {\n        return 1\n    }\n}\nprint(1)\n' + NEG,
    "COMPILE_ERROR", diag={})
add("SOL-TCK-0268", "REQ-1800", "types",
    'class Q {\n    Q() {\n    }\n\n    override func equals(other: Any?, n: Integer): Boolean {\n        return true\n    }\n\n'
    '    override func hashCode(): Integer {\n        return 1\n    }\n}\nprint(1)\n' + NEG,
    "COMPILE_ERROR", diag={})
add("SOL-TCK-0269", "REQ-1800", "types",
    'class Q {\n    Q() {\n    }\n\n    override func equals(other: Any?): Boolean? {\n        return null\n    }\n\n'
    '    override func hashCode(): Integer {\n        return 1\n    }\n}\nprint(1)\n' + NEG,
    "COMPILE_ERROR", diag={})

# --- REQ-1801 the hashCode declaration shape.
add("SOL-TCK-0270", "REQ-1801", "types", EXACT + 'val p = Exact()\nprint("hash" .. p.hashCode())\n',
    "SUCCESS", stdout="hash7")
add("SOL-TCK-0271", "REQ-1801", "types",
    EQ_OK + '    override func hashCode(x: Integer): Integer {\n        return 1\n    }\n}\nprint(1)\n' + NEG,
    "COMPILE_ERROR", diag={})
add("SOL-TCK-0272", "REQ-1801", "types",
    EQ_OK + '    override func hashCode(): Long {\n        return 1L\n    }\n}\nprint(1)\n' + NEG,
    "COMPILE_ERROR", diag={})
add("SOL-TCK-0273", "REQ-1801", "types",
    EQ_OK + '    func hashCode(): Integer {\n        return 1\n    }\n}\nprint(1)\n' + NEG,
    "COMPILE_ERROR", diag={})

# --- REQ-1802 pairing per declaration, never by inheritance.
add("SOL-TCK-0274", "REQ-1802", "types",
    'open class Both {\n    Both() {\n    }\n\n    override func equals(other: Any?): Boolean {\n        return true\n    }\n\n'
    '    override func hashCode(): Integer {\n        return 5\n    }\n}\n'
    'class Plain extends Both {\n    Plain() {\n    }\n}\n'
    'val s = Plain()\nprint("inh" .. (s == s) .. s.hashCode())\n',
    "SUCCESS", stdout="inhtrue5")
add("SOL-TCK-0275", "REQ-1802", "types",
    'open class Both2 {\n    Both2() {\n    }\n\n    open override func equals(other: Any?): Boolean {\n        return true\n    }\n\n'
    '    open override func hashCode(): Integer {\n        return 5\n    }\n}\n'
    'class OnlyEq extends Both2 {\n    OnlyEq() {\n    }\n\n    override func equals(other: Any?): Boolean {\n        return false\n    }\n}\n'
    'val s = OnlyEq()\nprint(s)\n' + NEG,
    "COMPILE_ERROR",
    diag={"family": "SEM", "code": "SOLV-SEM-045"},
    locate="override func equals(other: Any?): Boolean {\n        return false\n    }")

# --- REQ-1803 reserved member names.
add("SOL-TCK-0276", "REQ-1803", "types",
    'class C {\n    val equals: Integer\n\n    C(equals: Integer) {\n        this.equals = equals\n    }\n}\nprint(1)\n' + NEG,
    "COMPILE_ERROR", diag={})
add("SOL-TCK-0277", "REQ-1803", "types",
    'interface I {\n    func hashCode(): Integer\n}\nval x = 1\nprint(x)\n' + NEG,
    "COMPILE_ERROR", diag={})
add("SOL-TCK-0278", "REQ-1803", "types",
    'interface I {\n    func get(): Integer\n}\n'
    'class Impl implements I {\n    Impl() {\n    }\n\n    func get(): Integer {\n        return 1\n    }\n}\n'
    'class Holder {\n    delegate val hashCode: I\n\n    Holder(i: I) {\n        this.hashCode = i\n    }\n}\n'
    'print(1)\n' + NEG,
    "COMPILE_ERROR", diag={})

# --- REQ-1804 nullable receiver calls.
add("SOL-TCK-0279", "REQ-1804", "types",
    'val s: String? = "ab"\nval t = "ab"\nval r: Boolean? = s?.equals(t)\nprint("eq" .. r)\n',
    "SUCCESS", stdout="eqtrue")
add("SOL-TCK-0280", "REQ-1804", "types",
    # The null-receiver arm is present so the safe-call operator is shown to skip the call
    # rather than merely to compile against a nullable result type.
    'val s: String? = "ab"\nval r: Integer? = s?.hashCode()\nval n: String? = null\nval q: Integer? = n?.hashCode()\n'
    'print("hc" .. (r != null) .. (q == null))\n',
    # Derived from the specification, not observed: `s` is non-null so the safe call runs
    # and yields an Integer (`r != null` is true); `n` is null so the safe call is skipped
    # and yields null, and `null == null` is the algorithm's first step, so `q == null` is
    # also true. Binding each result to an `Integer?` declaration is what proves the result
    # TYPE; the comparisons show the safe call is genuinely skipped on a null receiver
    # rather than dispatching and returning something non-null.
    "SUCCESS", stdout="hctruetrue")
add("SOL-TCK-0281", "REQ-1804", "types",
    'val s: String? = "ab"\nval t = "ab"\nprint(s.equals(t))\n' + NEG, "COMPILE_ERROR", diag={})
add("SOL-TCK-0282", "REQ-1804", "types",
    'val s: String? = "ab"\nprint(s.hashCode())\n' + NEG, "COMPILE_ERROR", diag={})

# --- REQ-1805 bare member reads.
add("SOL-TCK-0283", "REQ-1805", "types",
    'val s = "ab"\nval f = s.equals\nprint(1)\n' + NEG, "COMPILE_ERROR", diag={})
add("SOL-TCK-0284", "REQ-1805", "types",
    'val s = "ab"\nval f = s.hashCode\nprint(1)\n' + NEG, "COMPILE_ERROR", diag={})
add("SOL-TCK-0285", "REQ-1805", "types",
    'val s = "ab"\nval f = s.toString\nprint(1)\n' + NEG, "COMPILE_ERROR", diag={})


def verify():
    bad = []
    for rid, r in REQS.items():
        for q in r["quotes"]:
            if norm(q) not in SPEC_N:
                bad.append(("QUOTE", rid, q))
    # Pinned spans are derived from the requirement's wording: the diagnostic must sit on
    # the member declaration itself. Compute from the program text, never from output.
    # Non-ASCII source would make character and byte offsets differ, which the encoded
    # lookup below handles; the check is kept explicit so a future non-ASCII program cannot
    # quietly produce a span that disagrees with the protocol's byte convention.
    for tid, needle in LOCATE.items():
        src = S[tid]
        n = src.count(needle)
        if n != 1:
            bad.append(("SPAN", tid, "needle occurs %d times" % n))
            continue
        # "reported on the single unpaired member" is only a checkable claim if the program
        # contains exactly one member the rule could report on. Assert that structurally:
        # the needle must be the only `override func equals` in the violating class body.
        viol = src[src.index("class OnlyEq"):]
        if viol.count("override func equals") != 1:
            bad.append(("SPAN", tid, "violating class has more than one candidate member"))
        # The protocol field is a UTF-8 BYTE offset (TCK.md section 3), so the offsets are
        # taken from the encoded program text rather than from Python character indices.
        encoded = src.encode("utf-8")
        nb = needle.encode("utf-8")
        if encoded.count(nb) != 1:
            bad.append(("SPAN", tid, "byte needle occurs %d times" % encoded.count(nb)))
            continue
        start = encoded.index(nb)
        EXPECT[tid]["diag"]["location"] = {
            "startByteOffset": start, "endByteOffset": start + len(nb)}
    return bad


def main():
    bad = verify()
    if bad:
        for kind, rid, detail in bad:
            print("%s %s: %r" % (kind, rid, detail))
        sys.exit(1)
    print("all %d normative quotes verified verbatim"
          % sum(len(r["quotes"]) for r in REQS.values()))
    print("pinned spans derived from program text: %s"
          % {t: EXPECT[t]["diag"]["location"] for t in LOCATE})
    for tid, src in sorted(S.items()):
        d = os.path.join(CORPUS, tid)
        os.makedirs(d, exist_ok=True)
        open(os.path.join(d, "main.sol"), "w").write(src)
        e = EXPECT[tid]
        man = {"manifestSchemaVersion": 1, "specVersion": "2026.09-draft", "testId": tid,
               "category": CATEGORY[tid], "profile": "full-language", "status": "required",
               "requirements": [REQ_FOR[tid]], "entryPoint": "main.sol",
               "outcome": e["outcome"],
               "expectation": ({"languageExit": 0, "stdoutBase64":
                                base64.b64encode(e["stdout"].encode()).decode()}
                               if e["outcome"] == "SUCCESS" else {"diagnostic": e["diag"]})}
        open(os.path.join(d, tid + ".manifest.json"), "w").write(
            json.dumps(man, indent=2) + "\n")
    print("wrote %d test directories, %d requirements" % (len(S), len(REQS)))


if __name__ == "__main__":
    main()
