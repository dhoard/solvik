#!/usr/bin/env python3
"""Generate the section 22 (unchecked exceptions) TCK batch.

Oracles are DERIVED FROM LANGUAGE_SPEC.md and only then compared against the
implementation. `EXPECT` below is written from the specification's own semantics before any
probe of the built launcher; a mismatch is investigated as a candidate implementation defect,
never resolved by copying observed output into the expectation. One such mismatch during
authoring turned out to be a real defect and was fixed in the implementation first (catch
matching for handlers written on the root `Exception` type); the tests here pin the
specification's rule, not the previously shipped behavior.
"""
import base64, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md")).read()
CORPUS = os.path.join(ROOT, "tck/corpus/2026.11-draft")


def norm(t):
    return re.sub(r"\s+", " ", t).strip()


SPEC_N = norm(SPEC)

# Reusable declaration fragments. Classes are final by default (section 7), so a class that
# this batch subclasses must be declared `open`; the spec's own exception example uses a
# final class only because nothing extends it.
PE = 'mutable class ParseError extends RuntimeException {\n}\n'

# ---------------------------------------------------------------- requirements
REQS = {
 "REQ-1300": dict(
  section="22.1 Exception types / 22.3 `try`, `catch`, and `finally`",
  summary="A guest exception type is a class whose declared superclass chain reaches one of the three built-in bases directly or transitively, and a handler whose type is a built-in base catches a thrown value whose chain reaches it",
  kind="runtime",
  notes="The three built-in bases have no source declaration, so a reachability graph built only from declarations truncates every chain at RuntimeException or ApplicationException and a handler on the root Exception type catches nothing. This is the load-bearing test for that rule: the observable is the handler actually running and printing the message, which no truncated graph can produce. Rejection halves of the same reachability question are SOLV-SEM-054 (an unrelated class is not a handler type) and the SOLV-SEM-055 unreachable clause.",
  quotes=["Every class whose declared superclass chain reaches one of the three built-in bases \u2014 directly or transitively \u2014 is\nitself a guest exception type.",
          "A handler type `H` matches a thrown runtime class `C` when `C` is `H` or `C` is a subtype of `H` in the exception\nhierarchy \u2014 that is, when `C`'s superclass chain reaches `H`."]),
 "REQ-1301": dict(
  section="22.1 Exception types",
  summary="Construction of a guest exception type accepts a single optional trailing String? message, and the synthesized getMessage() returns that message or null when no message argument was supplied",
  kind="runtime",
  notes="Both halves are needed: a construction with no message argument must store nothing and report null rather than an empty string, and a supplied message must be reported back exactly. Expected bytes are derived from section 5's rule that print renders null as the literal null and appends no separator, so the two tests are separate programs rather than one concatenated stream.",
  quotes=["Construction accepts a single optional trailing `String?` argument",
          "A construction with no message argument stores no message; `getMessage()` then returns `null`.",
          "func getMessage(): String?   // synthesized on every guest exception type"]),
 "REQ-1302": dict(
  section="22.1 Exception types",
  summary="The message is a compiler-synthesized private slot and never a readable member, so reading e.message on an exception is the compile-time error SOLV-RESOL-004",
  kind="compile-time",
  notes="The specification names the code for exactly this access, and the class here is a real exception type so the failure cannot be a misresolution of an unrelated name. Asserting the code is not capture-from-IUT: section 22.1 states it directly.",
  quotes=["It is a compiler-synthesized slot that is private by construction: it is never a readable member, so `e.message` is not a member of any exception class and is reported as `SOLV-RESOL-004`."]),
 "REQ-1303": dict(
  section="22.1 Exception types / 22.6 Required diagnostics",
  summary="A message argument must be assignable to String? or the construction is SOLV-TYPE-001, and supplying more than one extra trailing argument is the arity error SOLV-TYPE-003",
  kind="compile-time",
  notes="Two different codes for two different violations of the same construction, so they are separate requirements-level obligations with separate programs: a wrong-typed single argument and a well-typed surplus argument. Both codes are named for this exact rule in the section 22.6 paragraph.",
  quotes=["A message argument must be assignable to `String?`; anything else is `SOLV-TYPE-001`, and supplying more than one extra trailing argument is an arity error (`SOLV-TYPE-003`).",
          "a non-`String?` message is `SOLV-TYPE-001` (`TYPE_MISMATCH`) on the argument, an extra argument beyond one is `SOLV-TYPE-003` (`TYPE_ARITY_MISMATCH`) on the call"]),
 "REQ-1304": dict(
  section="22.1 Exception types",
  summary="The message is independent of the class's declared constructor, so a subclass keeps its own declared parameters and the message is still the single trailing argument",
  kind="runtime",
  notes="The oracle requires both channels to be observed separately from one construction: the declared parameter reaching the constructor and the trailing argument becoming the message. Printing the declared field first and the message second is what distinguishes this from a construction that ignored the declared parameter or swallowed the message.",
  quotes=["The message is independent of the class's declared constructor, so a subclass keeps its own declared parameters and `super(...)` forwarding unchanged; the message is still the single trailing argument",
          "`SubError(7, \"sub message\")` passes `7` to the declared constructor and stores the message."]),
 "REQ-1305": dict(
  section="22.1 Exception types / 22.3 `try`, `catch`, and `finally`",
  summary="getMessage() is available on every guest exception type, including on a handler written on a base type, so a handler typed on a base can read the message of a value caught as that base",
  kind="runtime",
  notes="The handler is typed on the base rather than on the thrown class, which is what makes this a distinct obligation from reachability: the synthesized accessor must exist on the handler's own declared type, not merely on the runtime class. Expected bytes are the message text alone, derived from the message-argument rule.",
  quotes=["`getMessage()` is available on every exception type, including a handler written on a base type, and",
          "A handler written on a base type therefore catches every subclass thrown at runtime, including subclasses whose chain passes through a built-in base."]),
 "REQ-1306": dict(
  section="22.1 Exception types / 22.6 Required diagnostics",
  summary="The names message and getMessage are reserved on every guest exception class and declaring either is SOLV-SEM-037, while on a class that is not a guest exception both remain ordinary user-declarable members",
  kind="compile-time",
  notes="The reservation is bounded, so both halves are asserted: a declaration on an exception type is rejected with the named code, and the identical declarations on an unrelated class must compile and be readable. An implementation that reserved the names globally would pass the rejection test and fail this one. The off-hierarchy program reads the field and calls the method, so the acceptance is not merely a parse.",
  quotes=["The names `message` and `getMessage` are reserved on every guest exception class, so a user member may",
          "declaring `message` or `getMessage` on an exception class is `SOLV-SEM-037` (`SEM_RESERVED_MEMBER`) on the member.",
          "On a class that is not a guest exception, both names remain ordinary user-declarable members."]),
 "REQ-1307": dict(
  section="22.1 Exception types",
  summary="The built-in bases Exception, RuntimeException and ApplicationException have no declaration and are not constructible, and serve instead as handler and superclass types",
  kind="compile-time",
  notes="The non-constructibility half is asserted as a bare rejection: the specification states the rule but names no diagnostic code for it, and the implementation reports a code that occurs nowhere in the specification, so pinning that code would be capture-from-IUT. The accepted half uses ApplicationException as a handler type and is kept separate from the root-type reachability test so a failure names which base misbehaved.",
  quotes=["The built-in bases (`Exception`, `RuntimeException`, `ApplicationException`) have no declaration and are\nnot constructible; they serve as handler and superclass types, and the message pattern applies to the\nuser-defined exception classes that derive from them."]),
 "REQ-1308": dict(
  section="22.3 `try`, `catch`, and `finally`",
  summary="When the try block throws, the catch clauses are tried in source order and the first clause whose declared type matches the thrown runtime class runs",
  kind="runtime",
  notes="The discriminating detail is ordering: the specific clause is written first so exactly one marker is printed, and a last-match or declaration-sorted implementation would print the base marker instead. Printing only the winner, with the two markers distinct, makes the first-match-wins rule the sole explanation.",
  quotes=["the `catch` clauses are tried in source order and the **first** clause whose declared type matches `C` runs."]),
 "REQ-1309": dict(
  section="22.3 `try`, `catch`, and `finally` / 22.6 Required diagnostics",
  summary="A catch clause whose handler type is a subtype of an earlier clause's handler type can never run and is the compile-time error SOLV-SEM-055",
  kind="compile-time",
  notes="The clauses are ordered with the root Exception type first and RuntimeException second, which is unreachable only if the graph records the built-in base edge. The same missing edge that broke matching also silenced this diagnostic, so this test pins the second symptom independently: an implementation that fixed matching but not unreachability analysis fails here.",
  quotes=["Because selection is first-match-wins in source order, a clause whose handler type is a subtype of an",
          "earlier clause's handler type can never run. Such an unreachable clause is the compile-time error",
          "| `SEM_UNREACHABLE_CATCH` | `SOLV-SEM-055` | the unreachable `catch` clause's type reference |"]),
 "REQ-1310": dict(
  section="22.3 `try`, `catch`, and `finally`",
  summary="A catch binding has the clause's declared type, is scoped to that handler body only, is not visible after the clause, and may shadow an outer name",
  kind="compile-time",
  notes="Invisibility after the clause is observable as an unknown-name resolution at the use site; section 4's reference rule names SOLV-RESOL-001 for a bare name resolving to nothing, and the binding name is unique in the program so no other rule can produce the failure.",
  quotes=["The binding has the clause's declared type and is scoped to that handler body only; it is not visible after the clause and may shadow an outer name.",
          "it denotes a local, a parameter, a function, or a top-level declaration, and if none is visible\nthe reference is `SOLV-RESOL-001`."]),
 "REQ-1311": dict(
  section="22.3 `try`, `catch`, and `finally`",
  summary="Because each handler body has its own scope, two clauses in the same try may reuse the same binding name without conflict",
  kind="runtime",
  notes="Both clauses bind `e` and the first one runs, so the oracle is the thrown message printed once plus a marker proving the try completed. A single-scope implementation would reject the program as a duplicate name, so acceptance itself is the observable.",
  quotes=["Because each\nhandler body has its own scope, two clauses in the same `try` may reuse the same binding name without\nconflict."]),
 "REQ-1312": dict(
  section="22.3 `try`, `catch`, and `finally`",
  summary="The catch binding is initialized before the handler body runs, so the handler may read it immediately and rethrow it with throw e",
  kind="runtime",
  notes="The inner handler rethrows the same value, which the outer handler observes with its message intact. This distinguishes an initialized binding from one merely declared but empty, which could not carry the message across.",
  quotes=["The binding is\ninitialized before the handler body runs, exactly as a parameter is, so the handler may read it\nimmediately and rethrow it with `throw e`."]),
 "REQ-1313": dict(
  section="22.3 `try`, `catch`, and `finally` / 22.6 Required diagnostics",
  summary="Declaring a handler type that is not a guest exception type is the compile-time error SOLV-SEM-054, reported on the type reference",
  kind="compile-time",
  notes="The handler names an ordinary class whose superclass chain reaches none of the built-in bases, which is exactly the shape the rule rejects. Distinct from the throw-operand rule, which has its own code and its own requirement.",
  quotes=["Declaring a handler type that is not a guest exception type is the compile-time error",
          "`SOLV-SEM-054` (`SEM_INVALID_CATCH_TYPE`), reported on the type reference.",
          "| `SEM_INVALID_CATCH_TYPE` | `SOLV-SEM-054` | the `catch` clause's type reference |"]),
 "REQ-1314": dict(
  section="22.3 `try`, `catch`, and `finally`",
  summary="If no clause matches, the thrown value keeps propagating outward after the finally clause runs, and an enclosing handler above the throw site still receives it",
  kind="runtime",
  notes="The ordering between the finally body and the outer handler is the whole content of this rule, so the oracle is a single concatenated stream in which the release marker strictly precedes the handler marker. A finally that ran after propagation, or a value converted at the inner boundary, would reorder or destroy the sequence.",
  quotes=["If no clause matches, the value keeps propagating outward after the `finally` clause\n(if any) runs."]),
 "REQ-1315": dict(
  section="22.3 `try`, `catch`, and `finally`",
  summary="A try consists of a try block, zero or more catch clauses and an optional finally clause, so a try with no catch clause and a finally clause is accepted and the finally body runs on normal completion",
  kind="runtime",
  notes="This is the acceptance half of the rule whose rejection half is already covered: a try needs a catch *or* a finally, so a finally-only try must compile and must run its finally body. The marker after the whole statement proves the try completed normally rather than aborting.",
  quotes=["A `try` consists of a `try` block, zero or more `catch` clauses, and an optional `finally` clause. A",
          "`try` with neither a `catch` clause nor a `finally` clause is the compile-time error"]),
 "REQ-1316": dict(
  section="22.2 `throw`",
  summary="A throw completes abruptly and produces no value, so a throw as the final statement of a value-returning function satisfies the value-on-all-paths rule the same way return does",
  kind="runtime",
  notes="The function is value-returning and one of its paths ends in a throw rather than a return, so the program only compiles if the abrupt path counts as covering the value obligation. Printing the returned value proves the accepted path ran; an implementation that demanded a literal return would reject the program outright.",
  quotes=["A `throw` completes abruptly (section 21.1) and produces no value, so a `throw` as the final statement of a",
          "value-returning function satisfies the value-on-all-paths rule the same way `return` does."]),
 "REQ-1317": dict(
  section="22.4 Propagation across call boundaries",
  summary="A thrown value crosses function-call boundaries during unwinding and is caught by a handler in any dynamically enclosing frame, and an ordinary call target must not itself terminate the program",
  kind="runtime",
  notes="The throw site and the handler are separated by two call frames, so the oracle proves the value travelled across both. Conversion at the inner call target would surface as an uncaught failure rather than the handler's own output.",
  quotes=["A `throw` inside a called function is\ncaught by a handler in any dynamically enclosing function frame, including the caller and its\nancestors.",
          "A call target for an ordinary function does not itself terminate the guest program; it only\nruns the function body and lets an uncaught thrown value continue unwinding toward the boundary."]),
}


# ---------------------------------------------------------------- test programs
# Each oracle is written from the specification before probing the launcher:
#   * `print` appends no separator (section 5), so expected stdout is a concatenation
#     of exactly the values the program prints, in program order;
#   * `..` renders each operand through toString and `null` renders as the four
#     characters "null" (section 3), so a bracketed message is spec-derivable and is
#     also self-delimiting, which keeps each expected stream independently derivable
#     from its own requirement rather than from a bare value another test may share;
#   * getMessage() yields the constructed message, or null when none was supplied
#     (section 22.1);
#   * rejection expectations name only codes the specification names for the rule.
NEG = '\nprint("EXECUTED-INVALID")\n'

S = {}
EXPECT = {}

def add(tid, src, outcome, **exp):
    S[tid] = src
    EXPECT[tid] = {"outcome": outcome, **exp}

# --- REQ-1300: a handler written on the root base catches a class under a built-in base.
add("SOL-TCK-0142", PE + '''func guard() {
    try {
        throw ParseError("bad int")
    }
    catch (e: Exception) {
        print("handled")
        print(e.getMessage())
    }
}
guard()
''', "SUCCESS", stdout="handledbad int")

add("SOL-TCK-0143", PE + '''mutable class DeepError extends ParseError {
}
func guard() {
    try {
        throw DeepError("deep")
    }
    catch (e: Exception) {
        print(e.getMessage())
    }
}
guard() 
''', "SUCCESS", stdout="deep")

# --- REQ-1301: optional trailing message; getMessage() yields it or null.
add("SOL-TCK-0144", PE + '''func guard() {
    try {
        throw ParseError()
    }
    catch (e: ParseError) {
        print("[" .. e.getMessage() .. "]")
    }
}
guard()
''', "SUCCESS", stdout="[null]")

add("SOL-TCK-0145", PE + '''func guard() {
    try {
        throw ParseError("bad int")
    }
    catch (e: ParseError) {
        print("[" .. e.getMessage() .. "]")
    }
}
guard()
''', "SUCCESS", stdout="[bad int]")

# --- REQ-1302: the message is never a readable member.
add("SOL-TCK-0146", PE + '''func guard() {
    try {
        throw ParseError("m")
    }
    catch (e: ParseError) {
        print(e.message)
    }
}
guard()
''' + NEG, "COMPILE_ERROR", diag={"family": "RESOL", "code": "SOLV-RESOL-004"})

# --- REQ-1303: message type and arity.
add("SOL-TCK-0147", PE + 'throw ParseError(7)\n' + NEG,
    "COMPILE_ERROR", diag={"family": "TYPE", "code": "SOLV-TYPE-001"})

add("SOL-TCK-0148", PE + 'throw ParseError("a", "b")\n' + NEG,
    "COMPILE_ERROR", diag={"family": "TYPE", "code": "SOLV-TYPE-003"})

# --- REQ-1304: the message is independent of the declared constructor.
add("SOL-TCK-0149", '''mutable class CodeError extends RuntimeException {
    val code: Integer

    CodeError(code: Integer) {
        this.code = code
    }
}
func guard() {
    try {
        throw CodeError(7, "sub message")
    }
    catch (e: CodeError) {
        print(e.code)
        print("[" .. e.getMessage() .. "]")
    }
}
guard()
''', "SUCCESS", stdout="7[sub message]")

# --- REQ-1305: getMessage() exists on a handler typed on a base.
add("SOL-TCK-0150", PE + '''func guard() {
    try {
        throw ParseError("via base")
    }
    catch (e: RuntimeException) {
        print("[" .. e.getMessage() .. "]")
    }
}
guard()
''', "SUCCESS", stdout="[via base]")

# --- REQ-1306: message/getMessage reserved on exception types, ordinary elsewhere.
add("SOL-TCK-0151", 'class Bad extends RuntimeException {\n    val message: String = "mine"\n}\n' + NEG,
    "COMPILE_ERROR", diag={"family": "SEM", "code": "SOLV-SEM-037"})

add("SOL-TCK-0152", 'class Bad extends RuntimeException {\n    func getMessage(): String {\n        return "mine"\n    }\n}\n' + NEG,
    "COMPILE_ERROR", diag={"family": "SEM", "code": "SOLV-SEM-037"})

add("SOL-TCK-0153", '''class Note {
    val message: String = "fine"

    func getMessage(): String {
        return this.message
    }
}
val n = Note()
print(n.message)
print(n.getMessage())
''', "SUCCESS", stdout="finefine")

# --- REQ-1307: the built-in bases are not constructible but do serve as handler types.
add("SOL-TCK-0154", 'throw RuntimeException("x")\n' + NEG,
    "COMPILE_ERROR", diag={})

add("SOL-TCK-0155", 'class ConfigError extends ApplicationException {\n}\nfunc guard() {\n    try {\n        throw ConfigError("no config")\n    }\n    catch (e: ApplicationException) {\n        print("[" .. e.getMessage() .. "]")\n    }\n}\nguard()\n',
    "SUCCESS", stdout="[no config]")

# --- REQ-1308: first matching clause in source order runs.
add("SOL-TCK-0156", PE + '''mutable class SubError extends ParseError {
}
func guard() {
    try {
        throw SubError("s")
    }
    catch (e: SubError) {
        print("specific")
    }
    catch (e: RuntimeException) {
        print("base")
    }
}
guard()
''', "SUCCESS", stdout="specific")

# --- REQ-1309: a clause made unreachable by an earlier base clause is SOLV-SEM-055.
add("SOL-TCK-0157", '''func guard() {
    try {
        print("trying")
    }
    catch (e: Exception) {
        print("root")
    }
    catch (e: RuntimeException) {
        print("runtime")
    }
}
guard()
''' + NEG, "COMPILE_ERROR", diag={"family": "SEM", "code": "SOLV-SEM-055"})

# --- REQ-1310: the catch binding is not visible after its clause.
add("SOL-TCK-0158", PE + '''func guard() {
    try {
        throw ParseError("x")
    }
    catch (e: ParseError) {
        print("caught")
    }
    print(e)
}
guard()
''' + NEG, "COMPILE_ERROR", diag={"family": "RESOL", "code": "SOLV-RESOL-001"})

# --- REQ-1311: two clauses may reuse the same binding name.
add("SOL-TCK-0159", PE + '''mutable class OtherError extends RuntimeException {
}
func guard() {
    try {
        throw ParseError("one")
    }
    catch (e: ParseError) {
        print("[" .. e.getMessage() .. "]")
    }
    catch (e: OtherError) {
        print("[" .. e.getMessage() .. "]")
    }
}
guard()
print("done")
''', "SUCCESS", stdout="[one]done")

# --- REQ-1312: the binding is initialized, so it can be rethrown.
add("SOL-TCK-0160", PE + '''func guard() {
    try {
        try {
            throw ParseError("kept")
        }
        catch (e: ParseError) {
            throw e
        }
    }
    catch (again: ParseError) {
        print("outer")
        print("[" .. again.getMessage() .. "]")
    }
}
guard()
''', "SUCCESS", stdout="outer[kept]")

# --- REQ-1313: a handler type must be a guest exception type.
add("SOL-TCK-0161", '''class Plain {
}
func guard() {
    try {
        print("trying")
    }
    catch (e: Plain) {
        print("caught")
    }
}
guard()
''' + NEG, "COMPILE_ERROR", diag={"family": "SEM", "code": "SOLV-SEM-054"})

# --- REQ-1314: finally runs before the value propagates to an enclosing handler.
add("SOL-TCK-0162", PE + '''func guarded() {
    try {
        throw ParseError("p")
    }
    finally {
        print("released")
    }
}
func driver() {
    try {
        guarded()
    }
    catch (e: Exception) {
        print("handled")
        print("[" .. e.getMessage() .. "]")
    }
}
driver()
''', "SUCCESS", stdout="releasedhandled[p]")

# --- REQ-1315: a try with only a finally clause is accepted and runs it.
add("SOL-TCK-0163", PE + '''func guard() {
    try {
        print("body")
    }
    finally {
        print("fin")
    }
}
guard()
print("after")
''', "SUCCESS", stdout="bodyfinafter")

# --- REQ-1316: a trailing throw covers the value-on-all-paths obligation.
add("SOL-TCK-0164", PE + '''func pick(n: Integer): Integer {
    if (n > 0) {
        return n
    }
    throw ParseError("negative")
}
print("[" .. pick(5) .. "]")
''', "SUCCESS", stdout="[5]")

# --- REQ-1317: the value crosses call boundaries to an enclosing frame's handler.
add("SOL-TCK-0165", PE + '''func inner() {
    throw ParseError("two frames down")
}
func middle() {
    inner()
}
func driver() {
    try {
        middle()
    }
    catch (e: Exception) {
        print("handled")
        print("[" .. e.getMessage() .. "]")
    }
}
driver()
''', "SUCCESS", stdout="handled[two frames down]")


CATEGORY = {tid: "exceptions" for tid in S}
REQ_FOR = {
 "SOL-TCK-0142": "REQ-1300", "SOL-TCK-0143": "REQ-1300",
 "SOL-TCK-0144": "REQ-1301", "SOL-TCK-0145": "REQ-1301",
 "SOL-TCK-0146": "REQ-1302",
 "SOL-TCK-0147": "REQ-1303", "SOL-TCK-0148": "REQ-1303",
 "SOL-TCK-0149": "REQ-1304",
 "SOL-TCK-0150": "REQ-1305",
 "SOL-TCK-0151": "REQ-1306", "SOL-TCK-0152": "REQ-1306", "SOL-TCK-0153": "REQ-1306",
 "SOL-TCK-0154": "REQ-1307", "SOL-TCK-0155": "REQ-1307",
 "SOL-TCK-0156": "REQ-1308",
 "SOL-TCK-0157": "REQ-1309",
 "SOL-TCK-0158": "REQ-1310",
 "SOL-TCK-0159": "REQ-1311",
 "SOL-TCK-0160": "REQ-1312",
 "SOL-TCK-0161": "REQ-1313",
 "SOL-TCK-0162": "REQ-1314",
 "SOL-TCK-0163": "REQ-1315",
 "SOL-TCK-0164": "REQ-1316",
 "SOL-TCK-0165": "REQ-1317",
}
assert set(REQ_FOR) == set(S), "REQ_FOR and S disagree"
assert set(REQ_FOR.values()) <= set(REQS)

_by_req = {}
for _tid, _rid in REQ_FOR.items():
    _by_req.setdefault(_rid, []).append(_tid)
for _rid, _r in REQS.items():
    _r["tests"] = sorted(_by_req.get(_rid, []))
assert set(_by_req) == set(REQS), "REQ_FOR does not cover every requirement"


def verify_quotes():
    bad = []
    for rid, r in REQS.items():
        for q in r["quotes"]:
            if norm(q) not in SPEC_N:
                bad.append((rid, q[:70]))
    return bad


def main():
    bad = verify_quotes()
    if bad:
        for rid, q in bad:
            print("QUOTE NOT IN SPEC %s: %r" % (rid, q))
        sys.exit(1)
    print("all %d normative quotes verified verbatim in LANGUAGE_SPEC.md"
          % sum(len(r["quotes"]) for r in REQS.values()))
    for tid, src in sorted(S.items()):
        d = os.path.join(CORPUS, tid)
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "main.sol"), "w") as fh:
            fh.write(src)
        e = EXPECT[tid]
        man = {
            "manifestSchemaVersion": 1, "specVersion": "2026.11-draft", "testId": tid,
            "category": CATEGORY[tid], "profile": "full-language", "status": "required",
            "requirements": [REQ_FOR[tid]], "entryPoint": "main.sol",
            "outcome": e["outcome"], "expectation": (
                {"languageExit": 0, "stdoutBase64":
                 base64.b64encode(e["stdout"].encode()).decode()}
                if e["outcome"] == "SUCCESS" else {"diagnostic": e["diag"]}),
        }
        with open(os.path.join(d, tid + ".manifest.json"), "w") as fh:
            fh.write(json.dumps(man, indent=2) + "\n")
    print("wrote %d test directories, %d requirements" % (len(S), len(REQS)))


if __name__ == "__main__":
    main()
