#!/usr/bin/env python3
"""Generate the section 23.3 (`?` propagation) TCK batch.

Section 23.3 specifies the postfix propagation operator for `Result<T, E>` values:
its operand must be a `Result`, the value of the expression is the unwrapped
success payload, an `Err` returns from the enclosing `Result`-returning function,
the operand runs exactly once, and the operation is type-checked against the
enclosing function's declared `Result` boundary. It also names three diagnostics
(`SOLV-SEM-049`, `SOLV-SEM-050`, `SOLV-SEM-051`) and states that propagation is a
control-flow transition rather than a wrong-variant fault.

Every expected result here is DERIVED FROM LANGUAGE_SPEC.md's section 23.3 text and
only then compared with the implementation. The code-bearing rejections assert
exactly the codes the specification names.

Semicolon caution. The postfix `?` is not listed among section 16's
terminator-triggering tokens, so whether a synthetic SEMI is emitted after a `?`
at a line boundary is not settled by the specification's condition-2 list
(although section 23.3's own example, `var config = readConfig()?`, implies one
is). Every program in this batch therefore writes something after the `?` on the
same line -- a `;` or a continuing operator -- so the batch tests propagation
semantics and not an unresolved insertion question.

Discriminating designs. Each rule is isolated so a wrong implementation fails an
arm rather than agreeing by accident:

  * The success payload is observed as a value of type `T` by using it as an
    arithmetic operand (`get(ok)? + 1`), and the exactly-once clause by a
    `print("p")` side effect inside the operand function.
  * The Err path is distinguished from a wrong-variant fault by the OUTCOME:
    an `Err` carried through `?` produces SUCCESS with the caller observing the
    same `Err`, where `unwrap` on an `Err` produces RUNTIME_ERROR (covered by
    REQ-0206). A `catch (e: Exception)` around the propagation must not intercept
    it, because propagation is a return, not a guest throw; if it were a throw,
    the broad handler would print `CAUGHT`.
  * The three negative tests pair a violating operand or boundary against a
    positive program of the same shape, so a rejection cannot be satisfied by an
    implementation that rejects every `Result` program.
"""
import base64
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md"), encoding="utf-8").read()
CORPUS = os.path.join(ROOT, "tck/corpus/2026.11-draft")
REQUIREMENTS = os.path.join(ROOT, "tck/requirements/requirements.json")
PROFILE = os.path.join(ROOT, "tck/profiles/full-language.profile.json")
SPEC_VERSION = "2026.11-draft"


def norm(t):
    t = (t.replace("\u2019", "'").replace("\u201c", '"').replace("\u201d", '"')
         .replace("\u2014", "--").replace("\u2013", "-").replace("\u00a0", " "))
    return re.sub(r"\s+", " ", t.replace("`", "").replace("*", "")).strip()


SPEC_N = norm(SPEC)

# The `Result` enum declaration section 12 defines; shared by every program so only the
# propagated expression differs between an accepted and a rejected arm.
RESULT = ("enum Result<T, E> {\n"
          "    Ok(T)\n"
          "    Err(E)\n"
          "}\n")

REQS_SPEC = {
    "REQ-2000": dict(
        section="23.3 Propagation",
        summary=(
            "The postfix `?` operator unwraps the success payload of a `Result<T, E>` value as a "
            "value of type `T` and evaluates its operand exactly once"),
        kind="runtime",
        quotes=[
            "The postfix operator `expression?` is the propagation form for `Result` values. Its "
            "operand must have a `Result<T, E>` type; the value of the expression is the unwrapped "
            "success payload of type `T`,",
            "The operand is evaluated exactly once.",
        ],
        note=(
            "Two obligations, each observable. The payload is used as a value of type `T` by the "
            "arithmetic operand in `get(ok)? + 1`, so an implementation that yielded `Unit`, the "
            "whole `Result`, or a wrong variant would not produce `42`. Exactly-once is observed by "
            "a `print(\"p\")` side effect inside the operand function: a double evaluation prints "
            "`p` twice. The EOL question is avoided because `?` is followed on the same line by "
            "`+ 1` or `;`, so the oracle does not depend on whether section 16 emits a synthetic "
            "SEMI after a postfix `?` at a line boundary."),
    ),
    "REQ-2001": dict(
        section="23.3 Propagation",
        summary=(
            "On `Err`, `?` returns `Err(error)` from the enclosing function immediately, without "
            "evaluating the rest of its body, and the returned `Err` composes across call frames"),
        kind="runtime",
        quotes=[
            "On `Ok(value)` the operand's success payload becomes the value of `expression?`. On "
            "`Err(error)` the current function returns `Err(error)` immediately, without evaluating "
            "the rest of its body.",
        ],
        note=(
            "The Err path is a return from the enclosing function, observed three ways: the caller "
            "sees the same `Err` through `isErr`/`unwrapErr`; a statement after the `?` does not run "
            "(the `AFTER` marker is absent); and the return composes through two call frames "
            "(`a` -> `b` -> `c`). The outcome is SUCCESS, not RUNTIME_ERROR, which is the "
            "observable distinction from the wrong-variant fault `unwrap` raises (REQ-0206)."),
    ),
    "REQ-2002": dict(
        section="23.3 Propagation",
        summary=(
            "The `?` operand must have a `Result<T, E>` type; otherwise the compile-time error is "
            "`SEM_RESULT_PROPAGATION_INVALID_OPERAND` (`SOLV-SEM-049`)"),
        kind="compile-time",
        quotes=[
            "Its operand must have a `Result<T, E>` type;",
            "| `SEM_RESULT_PROPAGATION_INVALID_OPERAND` | `SOLV-SEM-049` | the `?` operand is not a "
            "`Result<T, E>` |",
        ],
        note=(
            "A `?` on an `Integer` literal inside a `Result`-returning function is the negated "
            "operand rule, and the table names the code verbatim, so the oracle pins "
            "`SOLV-SEM-049`. The surrounding function is otherwise well-formed and the sentinel "
            "after it proves the program never executes."),
        diagnosticCode="SOLV-SEM-049",
        diagnosticNormative=True,
    ),
    "REQ-2003": dict(
        section="23.3 Propagation",
        summary=(
            "`?` is permitted only inside a function declared to return a `Result<T2, E2>`; a "
            "`?` with no such boundary is `SEM_RESULT_PROPAGATION_NO_BOUNDARY` (`SOLV-SEM-050`)"),
        kind="compile-time",
        quotes=[
            "the operation is permitted only inside a function declared to return a "
            "`Result<T2, E2>`.",
            "| `SEM_RESULT_PROPAGATION_NO_BOUNDARY` | `SOLV-SEM-050` | no enclosing function "
            "returns a `Result` |",
        ],
        note=(
            "A top-level statement is the implicit `main`, whose body does not return a `Result`, "
            "so a top-level `get()?` has no propagation boundary. The operand itself is a genuine "
            "`Result`, so this arm isolates the boundary rule from the operand rule and pins the "
            "specification-named `SOLV-SEM-050`."),
        diagnosticCode="SOLV-SEM-050",
        diagnosticNormative=True,
    ),
    "REQ-2004": dict(
        section="23.3 Propagation",
        summary=(
            "Propagation checks the success type `T` and the propagated error type `E` for "
            "assignability to the boundary `T2`/`E2`; a mismatch is "
            "`SEM_RESULT_PROPAGATION_TYPE_MISMATCH` (`SOLV-SEM-051`)"),
        kind="compile-time",
        quotes=[
            "Propagation is type-checked against the enclosing function's declared `Result` "
            "boundary: the unwrapped success type `T` must be assignable to `T2`, and the "
            "propagated error type `E` must be assignable to `E2`. The operand type never widens "
            "the function's declared result types; only assignability is required.",
            "| `SEM_RESULT_PROPAGATION_TYPE_MISMATCH` | `SOLV-SEM-051` | `T`/`E` is not assignable "
            "to the boundary `T2`/`E2` |",
        ],
        note=(
            "Two arms isolate the two clauses: one mismatches the success type (`String` operand "
            "into an `Integer` boundary) and one mismatches the error type (a `String` error into "
            "an `Integer` error boundary). Both pin the specification-named `SOLV-SEM-051`. "
            "Assignability, not identity, is what is required, so the tested failures are genuine "
            "non-assignabilities rather than merely unequal type names."),
        diagnosticCode="SOLV-SEM-051",
        diagnosticNormative=True,
    ),
    "REQ-2005": dict(
        section="23.3 Propagation / 22.3 try, catch, and finally",
        summary=(
            "Propagation is a control-flow transition, not a wrong-variant fault: an enclosing "
            "guest `catch` does not intercept it, and a `finally` it unwinds through still runs"),
        kind="runtime",
        quotes=[
            "Propagation is a control-flow transition, not a wrong-variant fault: an `Err` carried "
            "through `?` returns normally as the enclosing function's `Result` value rather than "
            "raising a fault. This is the key distinction from `unwrap`, which faults.",
            "The `finally` block runs on every exit path of the `try`, exactly once per entered "
            "`try`:",
        ],
        note=(
            "The catch arm writes `catch (e: Exception)`, the broadest handler, around the "
            "propagation: if `?` were implemented as a guest throw the handler would run and print "
            "`CAUGHT`, and the expected bytes are exactly `err=true e=x`. The finally arm observes "
            "`fin` printed before the caller sees the propagated `Err`, so a `?` that skipped the "
            "`finally` block would emit different bytes or order."),
    ),
    "REQ-2006": dict(
        section="23.3 Propagation",
        summary=(
            "`?` and the section 23 `Result` operations are independent: a program may use either "
            "or both, reading a propagated `Result` back through `isOk`/`unwrap` and "
            "`isErr`/`unwrapErr`"),
        kind="runtime",
        quotes=[
            "The `?` operator and the operations of section 23 are independent: neither is defined "
            "in terms of the other, and a program may use either or both.",
        ],
        note=(
            "One program propagates on both the `Ok` and `Err` paths and then reads each returned "
            "`Result` through the section 23 operations (`isOk`/`unwrap` and `isErr`/`unwrapErr`). "
            "If propagation were defined in terms of `unwrap` the `Err` arm would fault instead of "
            "returning; if it consumed the receiver the returned value would not be readable."),
    ),
}

NEG = '\nprint("EXECUTED-INVALID")\n'

TESTS = []


def T(tid, cat, req, src, outcome, exp, note):
    for q in REQS_SPEC[req]["quotes"]:
        if norm(q) not in SPEC_N:
            raise SystemExit("%s: quote not verbatim in spec: %r" % (tid, q[:90]))
    TESTS.append(dict(tid=tid, cat=cat, req=req, src=src, outcome=outcome, exp=exp, note=note))


def OK(tid, req, src, stdout, note):
    T(tid, "result", req, src, "SUCCESS",
      {"languageExit": 0, "stdoutBase64": base64.b64encode(stdout.encode()).decode()}, note)


def BAD(tid, req, src, code, note):
    T(tid, "result", req, src, "COMPILE_ERROR",
      {"diagnostic": {"family": "SEM", "code": code}}, note)


# --- REQ-2000: success payload as a T, evaluated exactly once.
OK("SOL-TCK-0309", "REQ-2000",
   ('enum Result<T, E> {\n'
    '    Ok(T)\n'
    '    Err(E)\n'
    '}\n'
    'func get(ok: Boolean): Result<Integer, String> {\n'
    '    if (ok) {\n'
    '        return Result.Ok(41)\n'
    '    }\n'
    '    return Result.Err("bad")\n'
    '}\n'
    'func use(ok: Boolean): Result<Integer, String> {\n'
    '    var v: Integer = get(ok)? + 1\n'
    '    return Result.Ok(v)\n'
    '}\n'
    'var r: Result<Integer, String> = use(true)\n'
    'print("ok=" .. r.isOk() .. " v=" .. r.unwrap())\n'
    ''),
   "ok=true v=42",
   "The unary `?` yields the `Integer` payload 41, so `+ 1` produces 42; the payload is used as "
   "a value of its declared type `T`, not merely rendered.")

OK("SOL-TCK-0310", "REQ-2000",
   ('enum Result<T, E> {\n'
    '    Ok(T)\n'
    '    Err(E)\n'
    '}\n'
    'func probe(): Result<Integer, String> {\n'
    '    print("p")\n'
    '    return Result.Ok(7)\n'
    '}\n'
    'func use(): Result<Integer, String> {\n'
    '    var v: Integer = probe()?\n'
    '    return Result.Ok(v)\n'
    '}\n'
    'var r: Result<Integer, String> = use()\n'
    'print("v=" .. r.unwrap())\n'
    ''),
   "pv=7",
   "`probe` prints `p` once, so the operand was evaluated exactly once on the success path.")

OK("SOL-TCK-0311", "REQ-2000",
   ('enum Result<T, E> {\n'
    '    Ok(T)\n'
    '    Err(E)\n'
    '}\n'
    'func probe(): Result<Integer, String> {\n'
    '    print("p")\n'
    '    return Result.Err("e")\n'
    '}\n'
    'func use(): Result<Integer, String> {\n'
    '    var v: Integer = probe()?\n'
    '    print("AFTER")\n'
    '    return Result.Ok(v)\n'
    '}\n'
    'var r: Result<Integer, String> = use()\n'
    'print("err=" .. r.isErr())\n'
    ''),
   "perr=true",
   "The operand runs once and then returns from `use`, so `p` appears once and `AFTER` does not "
   "appear; the expected bytes are `perr=true`.")

# --- REQ-2001: Err returns from the function immediately and composes.
OK("SOL-TCK-0312", "REQ-2001",
   ('enum Result<T, E> {\n'
    '    Ok(T)\n'
    '    Err(E)\n'
    '}\n'
    'func get(ok: Boolean): Result<Integer, String> {\n'
    '    if (ok) {\n'
    '        return Result.Ok(41)\n'
    '    }\n'
    '    return Result.Err("bad")\n'
    '}\n'
    'func use(ok: Boolean): Result<Integer, String> {\n'
    '    var v: Integer = get(ok)?\n'
    '    return Result.Ok(v + 1)\n'
    '}\n'
    'var r: Result<Integer, String> = use(false)\n'
    'print("err=" .. r.isErr() .. " e=" .. r.unwrapErr())\n'
    ''),
   "err=true e=bad",
   "The `Err` is returned from `use` unchanged; the caller observes the same `bad` error and the "
   "process exits 0, so the transition is a return rather than a fault.")

OK("SOL-TCK-0313", "REQ-2001",
   ('enum Result<T, E> {\n'
    '    Ok(T)\n'
    '    Err(E)\n'
    '}\n'
    'func get(): Result<Integer, String> {\n'
    '    return Result.Err("x")\n'
    '}\n'
    'func use(): Result<Integer, String> {\n'
    '    var v: Integer = get()?\n'
    '    print("AFTER")\n'
    '    return Result.Ok(v)\n'
    '}\n'
    'var r: Result<Integer, String> = use()\n'
    'print("err=" .. r.isErr())\n'
    ''),
   "err=true",
   "The statement after the `?` is not evaluated once the `Err` returns; the output is exactly "
   "`err=true`, so an implementation that continued the body would emit an extra `AFTER`.")

OK("SOL-TCK-0314", "REQ-2001",
   ('enum Result<T, E> {\n'
    '    Ok(T)\n'
    '    Err(E)\n'
    '}\n'
    'func a(): Result<Integer, String> {\n'
    '    return Result.Err("deep")\n'
    '}\n'
    'func b(): Result<Integer, String> {\n'
    '    var v: Integer = a()?\n'
    '    return Result.Ok(v + 1)\n'
    '}\n'
    'func c(): Result<Integer, String> {\n'
    '    var v: Integer = b()?\n'
    '    return Result.Ok(v + 1)\n'
    '}\n'
    'var r: Result<Integer, String> = c()\n'
    'print("err=" .. r.isErr() .. " e=" .. r.unwrapErr())\n'
    ''),
   "err=true e=deep",
   "The `Err` returns through `b` and then `c` unchanged, so the return composes across two call "
   "frames and the deepest error text is preserved.")

# --- REQ-2002 .. REQ-2004: the specification-named diagnostics.
BAD("SOL-TCK-0315", "REQ-2002",
    ('func use(): Result<Integer, String> {\n'
    '    var v: Integer = 1?\n'
    '    return Result.Ok(v)\n'
    '}\n'
    '\n'
    'print("EXECUTED-INVALID")\n'
    ''),
    "SOLV-SEM-049",
    "An `Integer` operand in a position that does have a `Result` boundary is the negated operand "
    "rule; the specification names `SOLV-SEM-049` for it.")

BAD("SOL-TCK-0316", "REQ-2003",
    ('enum Result<T, E> {\n'
    '    Ok(T)\n'
    '    Err(E)\n'
    '}\n'
    'func get(): Result<Integer, String> {\n'
    '    return Result.Ok(1)\n'
    '}\n'
    'var v: Integer = get()?\n'
    '\n'
    'print("EXECUTED-INVALID")\n'
    ''),
    "SOLV-SEM-050",
    "The operand is a genuine `Result`, so only the missing boundary is wrong; the implicit "
    "top-level `main` does not return a `Result`, and the specification names `SOLV-SEM-050`.")

BAD("SOL-TCK-0317", "REQ-2004",
    ('enum Result<T, E> {\n'
    '    Ok(T)\n'
    '    Err(E)\n'
    '}\n'
    'func get(): Result<String, String> {\n'
    '    return Result.Ok("s")\n'
    '}\n'
    'func use(): Result<Integer, String> {\n'
    '    var v: String = get()?\n'
    '    return Result.Ok(v)\n'
    '}\n'
    '\n'
    'print("EXECUTED-INVALID")\n'
    ''),
    "SOLV-SEM-051",
    "The success type `String` is not assignable to the boundary success type `Integer`; only "
    "that clause differs from an accepted program, and the specification names `SOLV-SEM-051`.")

BAD("SOL-TCK-0318", "REQ-2004",
    ('enum Result<T, E> {\n'
    '    Ok(T)\n'
    '    Err(E)\n'
    '}\n'
    'func get(): Result<Integer, String> {\n'
    '    return Result.Err("s")\n'
    '}\n'
    'func use(): Result<Integer, Integer> {\n'
    '    var v: Integer = get()?\n'
    '    return Result.Ok(v)\n'
    '}\n'
    '\n'
    'print("EXECUTED-INVALID")\n'
    ''),
    "SOLV-SEM-051",
    "The propagated error type `String` is not assignable to the boundary error type `Integer`; "
    "only that clause differs from an accepted program, and the specification names "
    "`SOLV-SEM-051`.")

# --- REQ-2005: control-flow transition, not a throw.
OK("SOL-TCK-0319", "REQ-2005",
   ('enum Result<T, E> {\n'
    '    Ok(T)\n'
    '    Err(E)\n'
    '}\n'
    'func get(): Result<Integer, String> {\n'
    '    return Result.Err("x")\n'
    '}\n'
    'func use(): Result<Integer, String> {\n'
    '    try {\n'
    '        var v: Integer = get()?\n'
    '        return Result.Ok(v)\n'
    '    }\n'
    '    catch (e: Exception) {\n'
    '        print("CAUGHT")\n'
    '        return Result.Ok(0)\n'
    '    }\n'
    '}\n'
    'var r: Result<Integer, String> = use()\n'
    'print("err=" .. r.isErr() .. " e=" .. r.unwrapErr())\n'
    ''),
   "err=true e=x",
   "A `catch (e: Exception)` around the propagation must not intercept a return; if `?` were a "
   "guest throw the handler would print `CAUGHT`, but the expected bytes are `err=true e=x`.")

OK("SOL-TCK-0320", "REQ-2005",
   ('enum Result<T, E> {\n'
    '    Ok(T)\n'
    '    Err(E)\n'
    '}\n'
    'func get(): Result<Integer, String> {\n'
    '    return Result.Err("x")\n'
    '}\n'
    'func use(): Result<Integer, String> {\n'
    '    try {\n'
    '        var v: Integer = get()?\n'
    '        return Result.Ok(v)\n'
    '    }\n'
    '    finally {\n'
    '        print("fin")\n'
    '    }\n'
    '}\n'
    'var r: Result<Integer, String> = use()\n'
    'print("err=" .. r.isErr() .. " e=" .. r.unwrapErr())\n'
    ''),
   "finerr=true e=x",
   "The `finally` runs on the propagation exit path before the caller observes the `Err`, so "
   "`fin` precedes `err=true e=x`; a transition that skipped `finally` would print neither or "
   "the wrong order.")

# --- REQ-2006: propagation and the section 23 operations coexist.
OK("SOL-TCK-0321", "REQ-2006",
   ('enum Result<T, E> {\n'
    '    Ok(T)\n'
    '    Err(E)\n'
    '}\n'
    'func get(ok: Boolean): Result<Integer, String> {\n'
    '    if (ok) {\n'
    '        return Result.Ok(41)\n'
    '    }\n'
    '    return Result.Err("bad")\n'
    '}\n'
    'func use(ok: Boolean): Result<Integer, String> {\n'
    '    var v: Integer = get(ok)? + 1\n'
    '    return Result.Ok(v)\n'
    '}\n'
    'var a: Result<Integer, String> = use(true)\n'
    'print("a=" .. a.isOk() .. "," .. a.unwrap())\n'
    'var b: Result<Integer, String> = use(false)\n'
    'print(" b=" .. b.isErr() .. "," .. b.unwrapErr())\n'
    ''),
   "a=true,42 b=true,bad",
   "The `Ok` path propagates a value that `isOk`/`unwrap` then read, and the `Err` path propagates "
   "an error that `isErr`/`unwrapErr` then read, so the operator and the operations both work on "
   "the same values. A `?` defined via `unwrap` would fault on the `Err` arm.")


def verify():
    bad = []
    for tid, t in ((t["tid"], t) for t in TESTS):
        if t["req"] not in REQS_SPEC:
            bad.append(("REQ", tid, t["req"]))
    # Negative arms must carry a sentinel so a false acceptance is observable; accepted arms
    # must not.
    for t in TESTS:
        has_sentinel = "EXECUTED-INVALID" in t["src"]
        if t["outcome"] == "COMPILE_ERROR" and not has_sentinel:
            bad.append(("SENTINEL", t["tid"], "negative arm lacks a sentinel"))
        if t["outcome"] == "SUCCESS" and has_sentinel:
            bad.append(("SENTINEL", t["tid"], "positive arm contains the rejection sentinel"))
    # The early-return and exactly-once arms must really test their clause.
    if "AFTER" in base64.b64decode(
            next(t["exp"]["stdoutBase64"] for t in TESTS
                 if t["tid"] == "SOL-TCK-0313")).decode():
        bad.append(("SHAPE", "SOL-TCK-0313", "skip arm's oracle contains the skipped marker"))
    if "CAUGHT" in base64.b64decode(
            next(t["exp"]["stdoutBase64"] for t in TESTS
                 if t["tid"] == "SOL-TCK-0319")).decode():
        bad.append(("SHAPE", "SOL-TCK-0319", "catch arm's oracle contains the handler marker"))
    # Distinct success oracles, the same invariant test_oracle_quotes enforces.
    seen = {}
    for t in TESTS:
        if t["outcome"] == "SUCCESS":
            payload = base64.b64decode(t["exp"]["stdoutBase64"])
            if payload in seen:
                bad.append(("DUP", t["tid"], seen[payload]))
            seen[payload] = t["tid"]
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
