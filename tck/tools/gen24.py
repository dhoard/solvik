#!/usr/bin/env python3
"""Generate the section 6/7 core-gap TCK batch.

Section 6 (Functions) and section 7 (Classes) carry many normative obligations that
the earlier batches did not enumerate: explicit parameter types, the reserved
implicit `main`, no overloading, same-scope redeclaration, statement-position
expression rules, the constructor/member name rules, implicit-constructor and
`super` rules, the `toString` override contract, property definite initialization,
static zero values, and lazy class initialization with inheritance ordering.

Each expectation is derived from the quoted specification text. Rejections whose
rule the specification names no code for are asserted BARE (a compile-time error
and nothing about its code); the two static rules whose codes are tabulated in
section 7 pin `SOLV-SEM-046` and `SOLV-SEM-048`.
"""
import base64
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md"), encoding="utf-8").read()
CORPUS = os.path.join(ROOT, "tck/corpus/2026.09-draft")
REQUIREMENTS = os.path.join(ROOT, "tck/requirements/requirements.json")
PROFILE = os.path.join(ROOT, "tck/profiles/full-language.profile.json")
SPEC_VERSION = "2026.09-draft"


def norm(t):
    t = (t.replace("\u2019", "'").replace("\u201c", '"').replace("\u201d", '"')
         .replace("\u2014", "--").replace("\u2013", "-").replace("\u00a0", " "))
    return re.sub(r"\s+", " ", t.replace("`", "").replace("*", "")).strip()


SPEC_N = norm(SPEC)

REQS_SPEC = {
    "REQ-2200": dict(
        section="6. Functions",
        summary="Parameter types must be explicit in the initial implementation, so a parameter "
                "written without a type is a compile-time error",
        kind="syntax",
        quotes=["Parameter types must be explicit in the initial implementation."],
        note="A parameter written without a type is the negated clause. The specification names no "
             "code for the rule, so the rejection is bare; the program would print a sentinel if it "
             "executed."),
    "REQ-2201": dict(
        section="6. Functions",
        summary="Declaring a function named `main` explicitly is a compile-time error, because the "
                "entry point is always the implicit main formed by top-level statements",
        kind="compile-time",
        quotes=["The entry point is always implicit: declaring a function named `main` explicitly, "
                "in the root or in any included file, is a compile-time error."],
        note="The declaration is the exact shape the sentence forbids. No code is specified for it, "
             "so the rejection is bare and the sentinel shows non-execution."),
    "REQ-2202": dict(
        section="6. Functions",
        summary="Functions are not overloaded: two functions with the same name in one scope are a "
                "compile-time error",
        kind="compile-time",
        quotes=["Functions are not overloaded in the initial language: two functions with the same "
                "name in one scope are a compile-time error."],
        note="Two same-named functions whose parameter types differ are exactly an overload pair; the "
             "implementation must not select by argument type. Bare rejection: the specification "
             "names no code for overloading."),
    "REQ-2203": dict(
        section="6. Functions",
        summary="Redeclaration in the same scope is an error, so a second binding of the same name "
                "at top level is rejected while a nested block may shadow it",
        kind="compile-time",
        quotes=["Names use lexical scope. Redeclaration in the same scope is an error. A nested "
                "block may shadow an outer declaration."],
        note="The negative arm redeclares a top-level binding in the same scope. Bare rejection: no "
             "code is named. A shadowing positive control would be redundant with the many existing "
             "block-scope tests, so only the negative arm is authored."),
    "REQ-2204": dict(
        section="6. Functions",
        summary="A call may be used as a statement, but another value-producing expression cannot "
                "stand alone as a statement",
        kind="compile-time",
        quotes=["A call may be used as a statement. Other value-producing expressions cannot stand "
                "alone as statements."],
        note="A bare arithmetic expression in statement position is the negated half. Bare "
             "rejection: no code is named."),
    "REQ-2205": dict(
        section="7. Classes",
        summary="A class member other than the constructor cannot have the same name as its class, "
                "so a property named after the class is a compile-time error",
        kind="compile-time",
        quotes=["A class member declaration other than the constructor cannot have the same name as "
                "its class."],
        note="A property whose name equals the class name is the forbidden declaration. Bare "
             "rejection: no code is named."),
    "REQ-2206": dict(
        section="7. Classes",
        summary="A class has at most one constructor declaration, so a second constructor is a "
                "compile-time error",
        kind="compile-time",
        quotes=["A class has at most one constructor declaration."],
        note="Two constructors differing only in parameters are the negated clause. No code is "
             "specified, so the rejection is bare."),
    "REQ-2207": dict(
        section="7. Classes",
        summary="A constructor cannot be invoked as a method call such as `this.User(...)`",
        kind="compile-time",
        quotes=["is not declared by an interface, is not forwarded by a `delegate`, and cannot be "
                "invoked as `this.User(...)`."],
        note="The constructor body invokes itself as `this.User()`, the exact form the sentence "
             "forbids. Bare rejection: no code is named."),
    "REQ-2208": dict(
        section="7. Classes",
        summary="A class with no explicit constructor has an implicit zero-argument initializer "
                "only when all properties have declaration initializers",
        kind="compile-time",
        quotes=["A class with no explicit constructor has an implicit zero-argument initializer only "
                "when all properties have declaration initializers."],
        note="A class with an uninitialized property and no constructor is constructed with no "
             "arguments, which the rule forbids. Bare rejection: no code is named."),
    "REQ-2209": dict(
        section="7. Classes",
        summary="A subclass constructor must invoke `super(arguments)` as its first statement when "
                "the superclass has no zero-argument initializer",
        kind="compile-time",
        quotes=["A subclass constructor must invoke `super(arguments)` as its first statement when "
                "the superclass has no zero-argument initializer; otherwise `super()` is implicit."],
        note="The superclass declares only a one-argument constructor and the subclass constructor "
             "omits `super(...)`. Bare rejection: no code is named."),
    "REQ-2210": dict(
        section="7. Classes",
        summary="`toString` on a class must be declared with `override` and keep the exact "
                "signature; declaring it without `override` is a compile-time error",
        kind="compile-time",
        quotes=["declaring `toString` without `override`, changing its parameter list, or returning "
                "a type other than `String` is a compile-time error, and a stored member may not "
                "reuse the reserved name `toString`."],
        note="The method omits `override`, the first forbidden form. Bare rejection: no code is "
             "named."),
    "REQ-2211": dict(
        section="7. Classes",
        summary="Every property without a declaration initializer must be assigned exactly once on "
                "every successful constructor path before it is read",
        kind="compile-time",
        quotes=["Every property without a declaration initializer must be assigned exactly once on "
                "every successful constructor path before it is read; a `val` property cannot be "
                "assigned afterward."],
        note="The negative arm assigns the property only on the true branch, so a false-path "
             "instance is missing an assignment and the program is rejected bare; the positive arm "
             "assigns on both branches and reads the value, so acceptance is observed as stdout. The "
             "pair differs only in the missing else-assignment."),
    "REQ-2212": dict(
        section="7. Static members and class initialization",
        summary="Every static cell begins at its declared type's zero value whether or not the "
                "declaration supplies an initializer",
        kind="runtime",
        quotes=["Each cell begins at its declared type's zero value -- `0` for every integer type, "
                "`0.0` for `Float`/`Double`, `false` for `Boolean`, the NUL character `'\\0'` for "
                "`Character`, and `null` for every reference type -- whether or not the declaration "
                "supplies an initializer,"],
        note="One static property per named zero value is read without any assignment; the expected "
             "bytes are derived from the sentence's own list (`0`, `false`, `0.0`, `null`), not "
             "captured from the implementation."),
    "REQ-2213": dict(
        section="7. Static members and class initialization",
        summary="A class declares at most one class initializer block; a second block is the "
                "compile-time error `SEM_DUPLICATE_STATIC_BLOCK` (`SOLV-SEM-046`)",
        kind="compile-time",
        quotes=["A class declares **at most one** class initializer block. A second block is "
                "`SOLV-SEM-046`, reported on the later block.",
                "| `SEM_DUPLICATE_STATIC_BLOCK` | `SOLV-SEM-046` | a class declares more than one "
                "class initializer block |"],
        note="Two `static` blocks are the exact shape; the section names the code verbatim, so the "
             "oracle pins `SOLV-SEM-046`.",
        diagnosticCode="SOLV-SEM-046",
        diagnosticNormative=True,
    ),
    "REQ-2214": dict(
        section="7. Static members and class initialization",
        summary="A static member may not mention a type parameter of its enclosing class, which is "
                "`SEM_TYPE_PARAMETER_IN_STATIC_MEMBER` (`SOLV-SEM-048`)",
        kind="compile-time",
        quotes=["A static member may not mention a type parameter of its enclosing class, which is "
                "`SOLV-SEM-048`: the member is reached through the bare class name, where no "
                "instantiation of that parameter exists.",
                "| `SEM_TYPE_PARAMETER_IN_STATIC_MEMBER` | `SOLV-SEM-048` | a static member mentions "
                "a type parameter of its class |"],
        note="A static method with a parameter and return type drawn from the class's type parameter "
             "is the exact shape; the section names the code verbatim, so the oracle pins "
             "`SOLV-SEM-048`.",
        diagnosticCode="SOLV-SEM-048",
        diagnosticNormative=True,
    ),
    "REQ-2215": dict(
        section="7. Static members and class initialization",
        summary="A static member is not inherited, so a subclass does not expose its superclass's "
                "static members",
        kind="compile-time",
        quotes=["A static member is **not inherited** and is **not overridable**. It is reached only "
                "through the name of the class that declares it, so a superclass and a subclass may "
                "each declare a static member of the same name as two independent members, and a "
                "subclass does not expose its superclass's static members."],
        note="Reading the superclass's static property through the subclass name is the forbidden "
             "access. Bare rejection: no code is named."),
    "REQ-2216": dict(
        section="7. Static members and class initialization",
        summary="A class that is never actively used is never initialized, and its first active use "
                "runs its initializer once, in superclass-first order",
        kind="runtime",
        quotes=["A class that is never actively used is never initialized: an unused class's "
                "`static` block does not run, and its static cells keep their type defaults.",
                "On the first active use, and before that use reads any cell or evaluates any call "
                "argument, the class runs its initializer once, and only once, in this order:"],
        note="Two classes with observable initializer blocks: the unused class's block must not run, "
             "and the used class's block must run exactly once at the first read of its static "
             "property. The expected bytes follow the source order and the rule, not a captured "
             "trace."),
    "REQ-2217": dict(
        section="7. Static members and class initialization",
        summary="A class's direct superclass is initialized before the class itself on first active "
                "use",
        kind="runtime",
        quotes=["its direct superclass is initialized first, transitively up to the root, so a base "
                "class is always set up before a derived one that relies on it;"],
        note="Constructing the subclass is its first active use; both classes' initializer blocks "
             "print, and the expected order is the superclass's marker then the subclass's."),
}

NEG = '\nprint("EXECUTED-INVALID")\n'

TESTS = []


def T(tid, cat, req, src, outcome, exp, note):
    for q in REQS_SPEC[req]["quotes"]:
        if norm(q) not in SPEC_N:
            raise SystemExit("%s: quote not verbatim in spec: %r" % (tid, q[:90]))
    TESTS.append(dict(tid=tid, cat=cat, req=req, src=src, outcome=outcome, exp=exp, note=note))


def OK(tid, cat, req, src, stdout, note):
    T(tid, cat, req, src, "SUCCESS",
      {"languageExit": 0, "stdoutBase64": base64.b64encode(stdout.encode()).decode()}, note)


def BAD(tid, cat, req, src, diag, note):
    T(tid, cat, req, src, "COMPILE_ERROR", {"diagnostic": diag}, note)


# --- section 6.
BAD("SOL-TCK-0322", "syntax", "REQ-2200",
    'func f(a) {\n    print(a)\n}\nf(1)\n' + NEG, {},
    "The parameter has no type; the declaration is rejected before execution.")
BAD("SOL-TCK-0323", "syntax", "REQ-2201",
    'func main() {\n    print("main")\n}\nprint("EXECUTED-INVALID")\n', {},
    "An explicit main declaration is the exact forbidden shape.")
BAD("SOL-TCK-0324", "names", "REQ-2202",
    'func f(a: Integer): Integer {\n    return a\n}\nfunc f(a: String): String {\n    return a\n}\n'
    'print(f(1))\n' + NEG, {},
    "Two same-named functions with different parameter types are an overload pair.")
BAD("SOL-TCK-0325", "names", "REQ-2203",
    'val x = 1\nval x = 2\nprint(x)\n' + NEG, {},
    "A second top-level binding of the same name is a same-scope redeclaration.")
BAD("SOL-TCK-0326", "evaluation", "REQ-2204",
    '1 + 1\nprint("EXECUTED-INVALID")\n', {},
    "A bare arithmetic expression in statement position is a value-producing non-call.")

# --- section 7.
BAD("SOL-TCK-0327", "objects", "REQ-2205",
    'class User {\n    val User: Integer = 1\n}\nval u = User()\nprint(u.User)\n' + NEG, {},
    "The property name equals the class name.")
BAD("SOL-TCK-0328", "objects", "REQ-2206",
    'class User {\n    User() {\n    }\n\n    User(x: Integer) {\n    }\n}\n'
    'val u = User()\nprint("EXECUTED-INVALID")\n', {},
    "A second constructor declaration is the negated at-most-one rule.")
BAD("SOL-TCK-0329", "objects", "REQ-2207",
    'class User {\n    User() {\n        this.User()\n    }\n}\nval u = User()\n'
    'print("EXECUTED-INVALID")\n', {},
    "The constructor invokes itself as `this.User()`.")
BAD("SOL-TCK-0330", "objects", "REQ-2208",
    'class User {\n    var name: String\n}\nval u = User()\nprint("EXECUTED-INVALID")\n', {},
    "An uninitialized property without an explicit constructor removes the implicit zero-arg "
    "initializer.")
BAD("SOL-TCK-0331", "objects", "REQ-2209",
    'open class A {\n    A(x: Integer) {\n    }\n}\nclass B extends A {\n    B() {\n    }\n}\n'
    'val b = B()\nprint("EXECUTED-INVALID")\n', {},
    "The subclass constructor omits the required `super(...)` call.")
BAD("SOL-TCK-0332", "objects", "REQ-2210",
    'class A {\n    A() {\n    }\n\n    func toString(): String {\n        return "a"\n    }\n}\n'
    'val a = A()\nprint(a.toString())\n' + NEG, {},
    "`toString` is declared without `override`.")
BAD("SOL-TCK-0333", "objects", "REQ-2211",
    'class U {\n    val name: String\n\n    U(c: Boolean) {\n        if (c) {\n'
    '            this.name = "a"\n        }\n    }\n}\nval u = U(true)\n'
    'print("EXECUTED-INVALID")\n', {},
    "The property is assigned only on the true branch, so a false-path instance is not definitely "
    "initialized.")
OK("SOL-TCK-0334", "objects", "REQ-2211",
   'class U {\n    val name: String\n\n    U(c: Boolean) {\n        if (c) {\n'
   '            this.name = "a"\n        }\n        else {\n            this.name = "b"\n'
   '        }\n    }\n}\nval u = U(true)\nprint("def" .. u.name)\n',
   "defa",
   "Assigning on both branches satisfies definite initialization and the read observes the taken "
   "branch; the arm differs from the rejection only by the else-assignment.")
OK("SOL-TCK-0335", "objects", "REQ-2212",
   'class C {\n    static var i: Integer\n    static var b: Boolean\n    static var d: Double\n'
   '    static var s: String\n\n    C() {\n    }\n}\n'
   'print(C.i)\nprint(C.b)\nprint(C.d)\nprint(C.s)\n',
   "0false0.0null",
   "Each uninitialized static cell reads its declared type's zero value from the specification's "
   "list.")
BAD("SOL-TCK-0336", "objects", "REQ-2213",
    'class C {\n    static {\n        print("a")\n    }\n\n    static {\n        print("b")\n'
    '    }\n\n    C() {\n    }\n}\nval c = C()\nprint("EXECUTED-INVALID")\n',
    {"family": "SEM", "code": "SOLV-SEM-046"},
    "A second class initializer block pins the specification-named SOLV-SEM-046.")
BAD("SOL-TCK-0337", "objects", "REQ-2214",
    'class C<T> {\n    static func f(x: T): T {\n        return x\n    }\n\n    C() {\n    }\n}\n'
    'print("EXECUTED-INVALID")\n',
    {"family": "SEM", "code": "SOLV-SEM-048"},
    "A static member mentioning the class type parameter pins the specification-named SOLV-SEM-048.")
BAD("SOL-TCK-0338", "objects", "REQ-2215",
    'open class A {\n    static var n: Integer = 5\n}\nclass B extends A {\n    B() {\n    }\n}\n'
    'print(B.n)\n' + NEG, {},
    "The subclass name must not expose the superclass's static member.")
OK("SOL-TCK-0339", "objects", "REQ-2216",
   'class A {\n    static {\n        print("initA")\n    }\n\n    static var n: Integer = 5\n\n'
   '    A() {\n    }\n}\nclass B {\n    static {\n        print("initB")\n    }\n\n'
   '    B() {\n    }\n}\nprint("start")\nprint(A.n)\nprint("mid")\nprint("done")\n',
   "startinitA5middone",
   "The unused class B's initializer block does not run, and A's runs exactly once at the first "
   "read of its static property.")
OK("SOL-TCK-0340", "objects", "REQ-2217",
   'open class A {\n    static {\n        print("A")\n    }\n\n    A() {\n    }\n}\n'
   'class B extends A {\n    static {\n        print("B")\n    }\n\n    B() {\n    }\n}\n'
   'print("start")\nval b = B()\nprint("end")\n',
   "startABend",
   "Constructing B triggers B's initialization, which initializes the direct superclass A first; "
   "the expected order is `A` then `B`, then the post-construction `end`.")


def verify():
    bad = []
    for t in TESTS:
        if t["req"] not in REQS_SPEC:
            bad.append(("REQ", t["tid"], t["req"]))
        has_sentinel = "EXECUTED-INVALID" in t["src"]
        if t["outcome"] == "COMPILE_ERROR" and not has_sentinel:
            bad.append(("SENTINEL", t["tid"], "negative arm lacks a sentinel"))
        if t["outcome"] == "SUCCESS" and has_sentinel:
            bad.append(("SENTINEL", t["tid"], "positive arm contains the rejection sentinel"))
    seen = {}
    for t in TESTS:
        if t["outcome"] == "SUCCESS":
            payload = base64.b64decode(t["exp"]["stdoutBase64"])
            if payload in seen:
                bad.append(("DUP", t["tid"], seen[payload]))
            seen[payload] = t["tid"]
    # The definite-initialization pair must differ only by the else-assignment.
    neg = next(t["src"] for t in TESTS if t["tid"] == "SOL-TCK-0333")
    pos = next(t["src"] for t in TESTS if t["tid"] == "SOL-TCK-0334")
    if "else" not in pos or "else" in neg:
        bad.append(("SHAPE", "REQ-2211", "the pair no longer isolates the else-assignment"))
    return bad


def main():
    bad = verify()
    if bad:
        for kind, tid, detail in bad:
            print("%s %s: %s" % (kind, tid, detail))
        return 1
    byreq = {}
    for t in TESTS:
        byreq.setdefault(t["req"], []).append(t["tid"])

    data = json.load(open(REQUIREMENTS, encoding="utf-8"))
    have = {r["id"] for r in data["requirements"]}
    byid = {r["id"]: r for r in data["requirements"]}
    for rid, spec in REQS_SPEC.items():
        assert byreq.get(rid), "%s has no test" % rid
        record = {
            "id": rid, "specVersion": SPEC_VERSION, "section": spec["section"],
            "summary": spec["summary"], "kind": spec["kind"], "profile": "full-language",
            "portable": True, "tests": byreq[rid], "status": "tested", "lifecycle": "active",
            "oracleNotes": spec["note"], "normativeQuotes": spec["quotes"]}
        if spec.get("diagnosticCode"):
            record["diagnosticCode"] = spec["diagnosticCode"]
            record["diagnosticNormative"] = spec["diagnosticNormative"]
        if rid in have:
            if byid[rid] != record:
                print("committed %s differs from this tool's record" % rid)
                return 1
            continue
        data["requirements"].append(record)
    data["requirements"].sort(key=lambda r: r["id"])
    with open(REQUIREMENTS, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2)
        handle.write("\n")

    profile = json.load(open(PROFILE, encoding="utf-8"))
    profile["requirements"] = sorted(set(profile["requirements"]) | set(REQS_SPEC))
    with open(PROFILE, "w", encoding="utf-8") as handle:
        json.dump(profile, handle, indent=2)
        handle.write("\n")

    for t in TESTS:
        d = os.path.join(CORPUS, t["tid"])
        os.makedirs(d, exist_ok=True)
        header = ("// Solvik TCK %s\n" % t["tid"]
                  + "".join("// %s\n" % ln for ln in t["note"].split("\n"))
                  + "//\n// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:\n"
                  + "".join("//   - %s\n" % q.replace("\n", " ")
                            for q in REQS_SPEC[t["req"]]["quotes"])
                  + "//\n")
        with open(os.path.join(d, "main.sol"), "w", encoding="utf-8") as handle:
            handle.write(header + t["src"])
        man = {"manifestSchemaVersion": 1, "specVersion": SPEC_VERSION, "testId": t["tid"],
               "category": t["cat"], "profile": "full-language", "status": "required",
               "requirements": [t["req"]], "entryPoint": "main.sol",
               "outcome": t["outcome"], "expectation": t["exp"]}
        with open(os.path.join(d, "%s.manifest.json" % t["tid"]), "w", encoding="utf-8") as handle:
            json.dump(man, handle, indent=2)
            handle.write("\n")
        print(t["tid"], t["outcome"], t["req"])
    print("wrote %d test directories, %d requirements" % (len(TESTS), len(REQS_SPEC)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
