#!/usr/bin/env python3
"""Convert the anonymous-function and closure-capture obligations of phases 3 and 4 into tested requirements.

Phases 3 and 4 landed with in-process suites -- `SolvikAnonymousFunctionTest`, `SolvikCaptureTest` -- and no
TCK requirements at all, so two capability areas of section 6 had portable oracles for nothing: nothing outside
this repository could tell a correct closure implementation from one that captured implicitly, shared one value
per expression, or forgot that a capture list is not part of a function type.

Every normative quotation added here is quoted by exactly one requirement, and every one was already in the
specification and claimed by no earlier requirement -- checked against the committed inventory before any
oracle was written, because the alternative is inventing a sentence to make a quote list look independent. Two
sentences this batch could have used (`SOLV-TYPE-001` for the static-declaration initializer placement, and the
section-3 hashing invariant) are already owned by REQ-3316 and REQ-3313 and are not quoted a second time; the
oracles below lean on the requirements that do own them instead.

What each requirement asserts, and why it needs the tests it has:

  * REQ-3317 -- anonymous function form and position. The revision fixes the shape of the expression: explicit
    parameter types, an optional return type whose omission declares `Unit`, and a value-returning body that
    must write its return type and return on every normally completing path; and it says where the expression
    may not appear. SOL-TCK-0446 is the form positive at three types, including a closure written inside a call
    argument -- the position where Solvik's semicolon insertion is suppressed while a parenthesis is unmatched,
    so that body needs an explicit `;`. SOL-TCK-0447 repeats the rule at `func(Integer): String` and at a
    `Unit` closure that discards a `String`, so neither the result type nor the `Unit` default rests on one
    shape. SOL-TCK-0448 is the omission case: no return type on a body that returns a value, so the body is
    checked as a `Unit` body and the value return is rejected. SOL-TCK-0449 is the position refusal -- a bare
    anonymous function in statement position inside a function that keeps executing afterwards, so acceptance
    would be observable rather than silent.

  * REQ-3318 -- freshness and identity. "An anonymous function creates a new function value every time
    evaluation reaches the expression, and two evaluations are distinct even when the expression captures no
    values. Re-reading a local that holds an anonymous function value preserves its identity." Those two halves
    contradict every shortcut available: hoist the value out of the expression and re-reading survives while
    freshness fails; mint a fresh wrapper per *read* and freshness survives while re-reading fails. SOL-TCK-0450
    therefore asserts both directions over a *non-capturing* closure, so nothing about capture can explain the
    answer.

  * REQ-3319 -- the capture list is part of the expression and not of the function type. SOL-TCK-0451 writes a
    capturing closure into a local initializer, a call argument, a generic type argument, and a nullable
    function type, and calls each value *at* the type that position declares. SOL-TCK-0452 and SOL-TCK-0453 are
    the two list-shape refusals: an empty list, "because a non-capturing anonymous function is written
    `func(...)`", and an item that is an expression rather than an identifier or `this`, which the revision does
    not support.

  * REQ-3320 -- what capture binds, and how far it reaches. Each listed binding's *value* is bound at the
    creation site; capturing an object copies the reference and not the object graph; storing a closure does not
    flatten the closure it captured; every intervening closure must list and forward a value itself; and a
    top-level function is globally resolved, needing no entry and usable recursively from a body. SOL-TCK-0454
    observes mutation that happened *after* creation, a closure over a closure, and two creations from one
    factory each binding the value that existed at that moment. SOL-TCK-0455 carries a receiver through two
    intervening closures. SOL-TCK-0456 is the global half, including recursion through a named top-level
    function, which no capture list could supply.

  * REQ-3321 -- the capture diagnostics. The revision fixes both codes and both primary spans for mutable
    state, names `SOLV-SEM-058` for an omitted outer local and for an uncaptured `this`, and assigns
    `SOLV-RESOL-001`, `SOLV-RESOL-005` and `SOLV-RESOL-002` to the item position. "Insufficient" has shapes the
    analyzer reaches by different routes, so each is its own program with a body written not to introduce a
    second root cause -- a passing match is then about the placement under test rather than about an unrelated
    report. SOL-TCK-0457 and SOL-TCK-0458 are the mutable pair the revision distinguishes by span, and that
    pair is the whole content of the sentence: a host that silently captured the unlisted `mutable val` fails 0458, and
    one that reported the item-side case with the unlisted code, or with none, fails 0457.

  * REQ-3322 -- the boundary. An anonymous function introduces a function boundary and a lexical scope holding
    its parameters and body locals, its parameters follow the existing immutable-parameter rule, a body
    declaration may shadow an outer binding under the ordinary rules, and naming the binding whose initializer
    is evaluating this very expression is an ordinary read-before-initialization error. SOL-TCK-0464 is
    positive and reads the outer value *afterwards*, which is what shows shadowing captured nothing; SOL-TCK-0465
    rejects assignment to a closure's own parameter; SOL-TCK-0466 rejects `[selfRef]`, with the body
    deliberately never mentioning the name so the single report is about the capture item alone.

On diagnostic codes. The codes pinned here are only those the section or its required-diagnostic table names for
the exact placement under test: `SOLV-SEM-057`, `SOLV-SEM-058`, `SOLV-RESOL-001`, `SOLV-RESOL-002`,
`SOLV-RESOL-005` from section 6, and `SOLV-TYPE-008` from the self-recursion sentence, which writes that code
itself. What is deliberately *not* pinned is any code the section describes in prose without naming: the
ordinary return diagnostics that "apply inside an anonymous function exactly as they do in a declaration", the
standalone-statement rejection, the assignment-to-parameter case, and an empty or malformed capture list, whose
revision sentence says `is a parse error` and no table row covers. Those assert `{
    "family": ...
}
` alone -- the
division gen38 draws for SOL-TCK-0439 and SOL-TCK-0441, and for the same reason: pinning a code the document
never states for that placement makes this implementation's choice look specification-mandated.

One sentence with no portable witness is named here rather than quoted by a requirement claimed `tested`:
"After an invalid capture item is reported, body checking must not cascade the same root cause into an
unlisted-capture or unknown-name diagnostic." Its subject is the *absence* of a second diagnostic, and
`_diagnostic_matches` is satisfied by any single matching diagnostic among the reports, so the manifest format
cannot express it at all; it belongs to the in-process suites, which can count reports.

Idempotent like every other generator, and refuses to run if any id it claims is already claimed by another
generator in this directory.
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

# --- REQ-3317: anonymous function form and position
ANON_FORM = ("An anonymous function is an expression written with `func`, an optional capture list, a "
             "parenthesized parameter list, an optional return type, and a body:")
EXPLICIT_PARAMS = "Its parameters must have explicit types."
RETURN_RULE = ("Its return type follows the same rule as a named function: omitting it declares `Unit`; a "
               "value-returning anonymous function must write its return type and must return a compatible "
               "value on every normally completing path.")
ORDINARY_RETURN_DIAGNOSTICS = ("Function bodies never acquire an implicit tail result, and the ordinary "
                               "return diagnostics apply inside an anonymous function exactly as they do in a "
                               "declaration.")
BARE_NOT_A_STATEMENT = ("A bare anonymous function or function reference used as an expression statement "
                        "remains invalid, because creating and discarding a function value is not a call.")

# --- REQ-3318: freshness and identity
FRESH_PER_EVALUATION = ("An anonymous function creates a new function value every time evaluation reaches "
                        "the expression, and two evaluations are distinct even when the expression captures "
                        "no values.")
REREAD_PRESERVES = "Re-reading a local that holds an anonymous function value preserves its identity."

# --- REQ-3319: the capture list is part of the expression and not of the type
CAPTURE_SYNTAX = ("Every such dependency must appear in an explicit capture list between `func` and the "
                  "parameter list:")
LIST_NOT_IN_TYPE = ("The capture list is part of the anonymous-function expression but not part of its "
                    "function type: the example above has type `func(Integer): Integer`, because callers "
                    "supply `value` while the declaration visibly binds `factor` into the function value.")
EMPTY_LIST = ("An empty capture list is a parse error, because a non-capturing anonymous function is "
              "written `func(...)`.")
NO_ALIASES = "Capture aliases and arbitrary capture expressions are not supported."

# --- REQ-3320: what capture binds, and how far it reaches
ITEM_ELIGIBLE = ("A capture item is an identifier or `this`. It must resolve at the closure-creation site to "
                 "one of: a `val` local declared in an enclosing function scope; an immutable parameter of an "
                 "enclosing function; another function value held by an immutable binding; or `this` in an "
                 "enclosing instance method or constructor.")
CAPTURED_AT_CREATION = ("Each listed binding's value is captured when evaluation reaches the "
                        "anonymous-function expression.")
REFERENCE_NOT_GRAPH = ("Capturing an object copies the reference, not the reachable object graph, so later "
                       "mutation of that object's `mutable val` properties remains observable through the captured "
                       "reference.")
TRANSITIVE = "Capture is transitive only through explicit values."
NO_FLATTEN = ("A closure that captures another closure lists that function-valued binding and stores the "
              "function value; it does not duplicate or flatten the captured closure's environment.")
FORWARDING = ("In nested closures, a name used in an inner capture list counts as a use by the enclosing "
              "closure, so every intervening closure must list and forward that value explicitly.")
GLOBALS = ("Top-level and module-qualified function declarations are globally resolved declarations rather "
           "than local state and need no capture entry; there are no globals to capture.")
TOP_RECURSION = "Recursion through named top-level functions needs no capture."

# --- REQ-3321: capture diagnostics
NO_VAR_CAPTURE = ("A closure must not list or otherwise capture a `mutable val` local. Naming a `mutable val` in a capture "
                  "list is `SEM_MUTABLE_CAPTURE` (`SOLV-SEM-057`), reported on that capture item, and a read "
                  "or write of that captured name in the body is reported with the same code.")
UNLISTED_VAR = ("Referencing the same outer `mutable val` without listing it remains `SEM_UNLISTED_CAPTURE` at the "
                "body reference; the compiler never silently converts it into a capture.")
UNLISTED_BODY = ("An outer local or parameter referenced by the body but omitted from the capture list is "
                 "`SEM_UNLISTED_CAPTURE` (`SOLV-SEM-058`), reported on the body reference. This applies to "
                 "`this` as well: a closure body may use `this` only when `[this]` is written.")
ENV_ORDER = "The capture list uses source order as environment order."
DUP = ("A duplicate capture item, and a capture item with the same name as one of the anonymous function's "
       "parameters, is `SOLV-RESOL-002`.")
ITEM_CODES = ("An unknown name in a capture list remains `SOLV-RESOL-001`, and `this` where no instance "
              "receiver exists remains `SOLV-RESOL-005`.")

# --- REQ-3322: boundary, scope, and self-recursion
BOUNDARY_SCOPE = ("An anonymous function introduces a function boundary and a lexical scope containing its "
                  "parameters and body locals;")
IMMUTABLE_PARAMS = ("its parameters follow the existing immutable-parameter rule, and a declaration inside "
                    "its body may shadow an outer binding under the ordinary lexical-scope rules.")
SELF = ("Anonymous self-recursion through the binding being initialized is not supported: listing that "
        "binding in the capture list is an ordinary read-before-initialization error (`SOLV-TYPE-008`), "
        "because the value does not exist when its initializer is evaluated.")

REQS_SPEC = {
    "REQ-3317": dict(
        section="6. Functions (Anonymous functions)",
        kind="compile-time",
        summary=("An anonymous function is written with explicit parameter types and an optional return type, "
                 "omitting the return type declares `Unit`, the ordinary return diagnostics apply inside the "
                 "body, and a bare anonymous function is not a statement"),
        quotes=[ANON_FORM, EXPLICIT_PARAMS, RETURN_RULE, ORDINARY_RETURN_DIAGNOSTICS,
                BARE_NOT_A_STATEMENT],
        oracle=(
            "SOL-TCK-0446 exercises the three written forms the section permits in one program: a "
            "value-returning closure with its return type written, a `Unit` closure with it omitted -- invoked "
            "so the `Unit` result is produced and not merely typed -- and a closure written inside a call "
            "argument, which is the position where semicolon insertion is suppressed while the parenthesis is "
            "unmatched and so needs an explicit `;` in its body. The oracle is the doubled argument, the "
            "bracketed `Unit` output, and the value carried through the nested closure; a host that defaulted "
            "an omitted return type away, or that refused a closure in an argument position, produces "
            "different bytes or none. SOL-TCK-0447 is the same rule at two other types, `func(Integer): "
            "String` returning and `func(String)` discarding, so neither the result type nor the `Unit` "
            "default rests on a single shape. SOL-TCK-0448 is the omission case written as a static "
            "declaration initializer: no return type, which the section declares to be `Unit`, on a body that "
            "returns a value. SOL-TCK-0449 is the position refusal, a bare anonymous function as a statement "
            "inside a function that continues after it, followed by the sentinel. All three rejections assert "
            "a family and pin no code: the section says the return diagnostics apply exactly as in a "
            "declaration without naming one, and states the standalone-statement rule as prose with no "
            "required-diagnostic row, so a code would be this implementation's rather than the language's. "
            "The sentinel proves non-execution."),
    ),
    "REQ-3318": dict(
        section="6. Functions (Anonymous functions)",
        kind="runtime",
        summary=("Each evaluation of an anonymous-function expression creates a distinct function value, and "
                 "re-reading a local that holds one preserves its identity"),
        quotes=[FRESH_PER_EVALUATION, REREAD_PRESERVES],
        oracle=(
            "SOL-TCK-0450 asserts the two halves of one sentence in both directions over a non-capturing "
            "closure, so no capture semantics can explain the answer: two evaluations of one expression are "
            "not `===` and not `equals`, while one local read twice is `===` and is `equals`, and its value "
            "still computes. A host that hoisted each closure out of its expression passes the re-read half "
            "and fails the freshness half; a host that minted a fresh wrapper on each read passes freshness "
            "and fails re-reading. Nothing here asserts a hash relationship, because the revision's hashing "
            "invariant belongs to REQ-3313."),
    ),
    "REQ-3319": dict(
        section="6. Functions (Explicit immutable closure capture)",
        kind="compile-time",
        summary=("Capture is written between `func` and the parameter list and is not part of the function "
                 "type, so a capturing closure occupies any function-typed position; an empty list and a "
                 "non-identifier item are both refused"),
        quotes=[CAPTURE_SYNTAX, LIST_NOT_IN_TYPE, EMPTY_LIST, NO_ALIASES],
        oracle=(
            "SOL-TCK-0451 puts a capturing closure into a local initializer, a call argument, a generic type "
            "argument, and a nullable function type, and calls the value at the type each of those positions "
            "declares -- a host that folded the capture list into the function type could not type a single "
            "one of them, and could not produce the expected bytes. SOL-TCK-0452 writes `func [](...)` and "
            "SOL-TCK-0453 writes a capture item that is an arithmetic expression rather than an identifier or "
            "`this`. Both assert the `PARS` family and pin no code: the empty-list sentence states `is a parse "
            "error` with no code, the aliased/expression sentence names none, and the specific expected-token "
            "set a parser produces is not a language property. Both carry the sentinel."),
    ),
    "REQ-3320": dict(
        section="6. Functions (Explicit immutable closure capture)",
        kind="runtime",
        summary=("A capture binds the value the name held at the creation site, an object is captured by "
                 "reference so later mutation stays observable, storing a closure does not flatten the "
                 "closure it captured, every intervening closure must forward a value itself, and a top-level "
                 "function needs no capture entry and is usable recursively from a body"),
        quotes=[ITEM_ELIGIBLE, CAPTURED_AT_CREATION, REFERENCE_NOT_GRAPH, TRANSITIVE, NO_FLATTEN,
                FORWARDING, GLOBALS, TOP_RECURSION],
        oracle=(
            "SOL-TCK-0454 is the observation test: an object captured and then mutated *after* creation reads "
            "back its new state through the closure, which is what separates a copied reference from a copied "
            "object; a closure built over a closure returns the inner closure's captured value, which is what "
            "separates storing a function value from flattening its environment; and two creations from one "
            "parameterized factory each carry the value their parameter held at that moment, which a host that "
            "shared one binding per declaration could not produce. SOL-TCK-0455 carries a receiver through two "
            "intervening closures -- the path an implementation with implicit outer lookup would take without "
            "ever writing the forwarding the section requires. SOL-TCK-0456 is the global half: a body calls a "
            "named top-level function with no capture entry, and recurses through it, so the value that makes "
            "the answer right is one no capture list could have supplied."),
    ),
    "REQ-3321": dict(
        section="6. Functions (Explicit immutable closure capture; required diagnostics)",
        kind="compile-time",
        summary=("A `mutable val` named in a capture list is `SOLV-SEM-057`, an unlisted outer local or uncaptured "
                 "`this` is `SOLV-SEM-058` at the body reference, an unknown item is `SOLV-RESOL-001`, `this` "
                 "with no receiver is `SOLV-RESOL-005`, and a duplicate item or item colliding with a "
                 "parameter is `SOLV-RESOL-002`"),
        quotes=[NO_VAR_CAPTURE, UNLISTED_VAR, UNLISTED_BODY, ENV_ORDER, DUP, ITEM_CODES],
        oracle=(
            "Each report is a program of its own with the body written not to introduce a second root cause. "
            "SOL-TCK-0457 and SOL-TCK-0458 are the two mutable-state halves the revision distinguishes by span "
            "-- `SOLV-SEM-057` at the item that names a `mutable val`, plain `SOLV-SEM-058` at a body reference to the "
            "*same kind* of outer `mutable val` that was never listed. That pair is the whole content of the sentence: "
            "a host that silently captured the unlisted variable fails 0458, and one that reported the "
            "item-side case with the unlisted code, or with none, fails 0457. SOL-TCK-0459 and SOL-TCK-0460 "
            "assert the body-reference code for an omitted `val` and for `this` used without `[this]`. "
            "SOL-TCK-0461 and SOL-TCK-0462 are the two halves of the duplicate-name rule -- `[base, base]`, "
            "whose body never mentions the duplicated name, and an item colliding with a parameter. "
            "SOL-TCK-0463 and SOL-TCK-0464 pin the remaining item codes, an unknown name and `this` in a "
            "top-level function where no instance receiver exists; each body is written to reference nothing "
            "from outside the closure at all, so the capture item is the only report the program can produce "
            "and a passing match cannot be about an unrelated diagnostic. Every program carries the "
            "sentinel."),
    ),
    "REQ-3322": dict(
        section="6. Functions (Anonymous functions) / 6. Functions (Explicit immutable closure capture)",
        kind="compile-time",
        summary=("An anonymous function introduces a function boundary and a lexical scope whose parameters "
                 "are immutable and may shadow outer bindings, and naming the binding whose own initializer is "
                 "evaluating the expression is a read-before-initialization error"),
        quotes=[BOUNDARY_SCOPE, IMMUTABLE_PARAMS, SELF],
        oracle=(
            "SOL-TCK-0465 is positive and reads the outer value *afterwards*: a parameter shadows an outer "
            "local of the same spelling with no capture entry, the body declares and returns a local, and the "
            "outer value is still readable outside. The shadowing is the boundary and the scope; the readable "
            "outer value is the proof that nothing was implicitly captured across that boundary, which a "
            "shadowing test alone cannot show. SOL-TCK-0466 rejects an assignment to a closure's own "
            "parameter, the immutable-parameter rule the quoted clause imports; it asserts the `TYPE` family "
            "because the clause names no code, exactly as the return-diagnostic, standalone-statement and "
            "parse-error cases do. SOL-TCK-0467 rejects `[selfRef]` naming the binding whose initializer is "
            "evaluating this very expression, and pins the code the self-recursion sentence writes itself. Its "
            "body deliberately never mentions the name, so the single report is about the capture item and "
            "nothing else."),
    ),
}

TESTS = [
    # ------------------------------------------------- REQ-3317: form and position
    dict(
        tid="SOL-TCK-0446", req="REQ-3317", cat="types", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(b"4\n[7]\n-98").decode("ascii")},
        note="A value-returning anonymous function writes its return type, a Unit one omits it, and a "
             "closure written in a call argument keeps an explicit semicolon because insertion is suppressed "
             "while a parenthesis is unmatched",
        src="""func invoke(f: func(Integer): Integer): Integer {
    return f(2)
}

val double: func(Integer): Integer = func(value: Integer): Integer {
    return value * 2
}

val report: func(Integer): Unit = func(value: Integer) {
    print("[" .. value.toString() .. "]")
}

val nested: Integer = invoke(func(value: Integer): Integer {
    return value - 100
}
)

print(invoke(double).toString() .. "\\n")
report(7)
print("\\n")
print(nested.toString())
""",
    ),
    dict(
        tid="SOL-TCK-0447", req="REQ-3317", cat="types", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(b"v3|unit").decode("ascii")},
        note="The same form rule at two other types: a closure returning String and a Unit closure "
             "discarding its argument, so neither the result type nor the Unit default rests on one shape",
        src="""val formatter: func(Integer): String = func(value: Integer): String {
    return "v" .. value.toString()
}

val consume: func(String) = func(value: String) {
    print(value)
}

print(formatter(3) .. "|")
consume("unit")
""",
    ),
    dict(
        tid="SOL-TCK-0448", req="REQ-3317", cat="types", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE"}},
        note="Omitting the return type declares Unit, and the ordinary return diagnostics apply inside an "
             "anonymous body, so a body that returns a value there is rejected",
        src="""class Form {
    static val h: func(Integer): Integer = func(value: Integer) {
        print(value.toString())
    }
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0449", req="REQ-3317", cat="control", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "SEM"}},
        note="A bare anonymous function in statement position is rejected because creating and discarding a "
             "function value is not a call; the function continues past it, so acceptance is observable",
        src="""func use(): Unit {
    func(value: Integer): Integer {
        return value + 1
    }
    print("reached")
}

use()
print("EXECUTED-INVALID")
""",
    ),
    # ------------------------------------------------- REQ-3318: freshness and identity
    dict(
        tid="SOL-TCK-0450", req="REQ-3318", cat="equality", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(
                 b"false\nfalse\nfalse\ntrue\ntrue\n40").decode("ascii")},
        note="Two evaluations of one non-capturing anonymous function are distinct values, while one local "
             "read twice is the same value under both === and equals",
        src="""func makeHalf(): func(Integer): Integer {
    return func(value: Integer): Integer {
        return value / 2
    }
}

val first = makeHalf()
val second = makeHalf()

print((makeHalf() === makeHalf()).toString() .. "\\n")
print((first === second).toString() .. "\\n")
print(first.equals(second).toString() .. "\\n")
print((first === first).toString() .. "\\n")
print(first.equals(first).toString() .. "\\n")
print(first(81).toString())
""",
    ),
    # ------------------------------------------------- REQ-3319: the list is not part of the type
    dict(
        tid="SOL-TCK-0451", req="REQ-3319", cat="types", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(b"200|100|101|100").decode("ascii")},
        note="The capture list is not part of the function type, so a capturing closure occupies a local "
             "initializer, a call argument, a generic type argument, and a nullable function type, and is "
             "called at each of those types",
        src="""func throughParameter(f: func(Integer): Integer): Integer {
    return f(1)
}

func first(list: List<func(Integer): Integer>): Integer {
    return list.get(0)(1)
}

func run(): String {
    val offset = 100
    val scale = func [offset](value: Integer): Integer {
        return value * offset
    }
    val maybe: func(Integer): Integer? = func [offset](value: Integer): Integer {
        return value + offset
    }
    val cells: List<func(Integer): Integer> = List<func(Integer): Integer>()
    val cellClosure = func [offset](value: Integer): Integer {
        return offset
    }
    cells.add(cellClosure)

    val direct = scale(2).toString()
    val argument = throughParameter(scale).toString()
    val nullable = (maybe(1) ?? 0).toString()
    val inList = first(cells).toString()
    return direct .. "|" .. argument .. "|" .. nullable .. "|" .. inList
}

print(run())
""",
    ),
    dict(
        tid="SOL-TCK-0452", req="REQ-3319", cat="syntax", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "PARS"}},
        note="An empty capture list is a parse error, because a non-capturing anonymous function is written "
             "`func(...)` with no list at all",
        src="""func run(): func(Integer): Integer {
    return func [](value: Integer): Integer {
        return value
    }
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0453", req="REQ-3319", cat="syntax", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "PARS"}},
        note="A capture item is an identifier or `this`, so an arbitrary capture expression is refused at the "
             "list itself",
        src="""func run(base: Integer): func(Integer): Integer {
    return func [base + 1](value: Integer): Integer {
        return value
    }
}

print("EXECUTED-INVALID")
""",
    ),
    # ------------------------------------------------- REQ-3320: what capture binds
    dict(
        tid="SOL-TCK-0454", req="REQ-3320", cat="objects", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(b"2|200|301|20|10").decode("ascii")},
        note="A captured object reference shows mutation that happened after creation, a closure over a "
             "closure reaches the inner captured value, and each creation binds the value that existed at "
             "that moment",
        src="""class Cell {
    mutable val n: Integer = 0

    func bump() {
        this.n = this.n + 1
    }
}

func withFactor(factor: Integer): func(Integer): Integer {
    return func [factor](value: Integer): Integer {
        return value * factor
    }
}

func run(): String {
    val cell = Cell()
    val peek = func [cell](): Integer {
        return cell.n
    }
    cell.bump()
    cell.bump()

    val offset = 100
    val scale = func [offset](value: Integer): Integer {
        return value * offset
    }
    val composed = func [scale](value: Integer): Integer {
        return scale(value) + 1
    }

    val observed = peek().toString()
    val scaled = scale(2).toString()
    val throughStored = composed(3).toString()
    val firstCreation = withFactor(4)(5).toString()
    val secondCreation = withFactor(2)(5).toString()
    return observed .. "|" .. scaled .. "|" .. throughStored .. "|" .. firstCreation .. "|" .. secondCreation
}

print(run())
""",
    ),
    dict(
        tid="SOL-TCK-0455", req="REQ-3320", cat="objects", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(b"42|40").decode("ascii")},
        note="A receiver is captured by `[this]` and every intervening closure must list and forward it "
             "itself, so a value reaches a body only through the written path",
        src="""class Adder {
    mutable val base: Integer = 40

    func adder(): func(Integer): Integer {
        return func [this](value: Integer): Integer {
            return this.base + value
        }
    }

    func nested(): func(): func(): Integer {
        return func [this](): func(): Integer {
            return func [this](): Integer {
                return this.base
            }
        }
    }
}

func run(): String {
    val adder = Adder()
    return adder.adder()(2).toString() .. "|" .. adder.nested()()().toString()
}

print(run())
""",
    ),
    dict(
        tid="SOL-TCK-0456", req="REQ-3320", cat="evaluation", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(b"42|120").decode("ascii")},
        note="Top-level function declarations are globally resolved and need no capture entry, and a closure "
             "body may recurse through one, which no capture list could supply",
        src="""// A top-level declaration is globally resolved: not local state, so nothing to capture.
func twice(value: Integer): Integer {
    return value * 2
}

func fact(n: Integer): Integer {
    if (n <= 1) {
        return 1
    }
    return n * fact(n - 1)
}

func run(): String {
    val usesGlobal = func(value: Integer): Integer {
        return twice(value)
    }
    val recursive = func(n: Integer): Integer {
        return fact(n)
    }
    return usesGlobal(21).toString() .. "|" .. recursive(5).toString()
}

print(run())
""",
    ),
    # ------------------------------------------------- REQ-3321: capture diagnostics
    dict(
        tid="SOL-TCK-0457", req="REQ-3321", cat="objects", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"code": "SOLV-SEM-057"}},
        note="A `mutable val` named in a capture list is rejected at the capture item, which capture cannot bind",
        src="""func run(): func(Integer): Integer {
    mutable val total = 0
    return func [total](value: Integer): Integer {
        return total + value
    }
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0458", req="REQ-3321", cat="objects", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"code": "SOLV-SEM-058"}},
        note="An unlisted outer `mutable val` read by the body is an unlisted-capture diagnostic at the reference: "
             "the compiler never silently converts it into a capture",
        src="""func run(): func(Integer): Integer {
    mutable val offset = 100
    return func(value: Integer): Integer {
        return value + offset
    }
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0459", req="REQ-3321", cat="names", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"code": "SOLV-SEM-058"}},
        note="A body reference to an outer `val` the capture list omits is reported on the body reference, "
             "which is where an omitted capture becomes visible",
        src="""func run(): func(Integer): Integer {
    val offset = 100
    return func(value: Integer): Integer {
        return value + offset
    }
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0460", req="REQ-3321", cat="names", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"code": "SOLV-SEM-058"}},
        note="The unlisted-capture rule applies to the receiver too: a body may use `this` only when `[this]` "
             "is written",
        src="""class Holder {
    mutable val n: Integer = 1

    func read(): func(): Integer {
        return func(): Integer {
            return this.n
        }
    }
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0461", req="REQ-3321", cat="names", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"code": "SOLV-RESOL-002"}},
        note="A duplicate capture item is reported at the item, and the body never mentions the duplicated "
             "name so the report cannot be about anything else",
        src="""func run(base: Integer): func(): Integer {
    return func [base, base](): Integer {
        return 1
    }
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0462", req="REQ-3321", cat="names", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"code": "SOLV-RESOL-002"}},
        note="A capture item with the same name as one of the anonymous function's parameters is the other "
             "half of the duplicate-name rule",
        src="""func run(base: Integer): func(Integer): Integer {
    return func [base](base: Integer): Integer {
        return base
    }
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0463", req="REQ-3321", cat="names", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"code": "SOLV-RESOL-001"}},
        note="An unknown name written in a capture list earns the same code as a typo anywhere else, and the "
             "body is written to reference nothing from outside so the item is the only report",
        src="""func run(base: Integer): func(Integer): Integer {
    return func [bas](value: Integer): Integer {
        return value + 1
    }
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0464", req="REQ-3321", cat="names", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"code": "SOLV-RESOL-005"}},
        note="A `this` capture item where no instance receiver exists is rejected, because a closure has no "
             "receiver of its own to capture",
        src="""func run(): func(): Integer {
    return func [this](): Integer {
        return 1
    }
}

print("EXECUTED-INVALID")
""",
    ),
    # ------------------------------------------------- REQ-3322: boundary and self-recursion
    dict(
        tid="SOL-TCK-0465", req="REQ-3322", cat="names", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(b"6|7|100").decode("ascii")},
        note="A closure body is a lexical scope inside a function boundary: a parameter may shadow an outer "
             "binding with no capture entry and a body local may be declared, while the outer value stays "
             "readable outside",
        src="""func run(): String {
    val width = 100
    val inner = func(width: Integer): String {
        val doubled = width * 2
        return doubled.toString()
    }
    val shadowed = func(): Integer {
        val width = 7
        return width
    }
    return inner(3) .. "|" .. shadowed().toString() .. "|" .. width.toString()
}

print(run())
""",
    ),
    dict(
        tid="SOL-TCK-0466", req="REQ-3322", cat="types", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE"}},
        note="A closure parameter follows the existing immutable-parameter rule, so assigning to it in the "
             "body is rejected",
        src="""func run(): func(Integer): Integer {
    return func(value: Integer): Integer {
        value = value + 1
        return value
    }
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0467", req="REQ-3322", cat="names", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"code": "SOLV-TYPE-008"}},
        note="Anonymous self-recursion through the binding being initialized is a read-before-initialization "
             "error, and the body never mentions the name so the report is about the capture item alone",
        src="""func run(): func(Integer): Integer {
    val selfRef = func [selfRef](value: Integer): Integer {
        return value
    }
    return selfRef
}

print("EXECUTED-INVALID")
""",
    ),
]

# The codes the section or its required-diagnostic table names for the placements this batch tests.
NAMED_CODES = ("SOLV-SEM-057", "SOLV-SEM-058", "SOLV-RESOL-001", "SOLV-RESOL-002",
               "SOLV-RESOL-005", "SOLV-TYPE-008")
# Rejections whose placement the section describes without naming a code, so only the family is asserted.
FAMILY_ONLY = {"SOL-TCK-0448": "TYPE", "SOL-TCK-0449": "SEM", "SOL-TCK-0452": "PARS",
               "SOL-TCK-0453": "PARS", "SOL-TCK-0466": "TYPE"}


def refuse_reuse_of_claimed_ids():
    """A fresh batch must not re-allocate ids an earlier generator already owns.

    The id allocation is repository-wide, hand-rolled, and enforced only by convention, so the one thing
    standing between a new batch and silently overwriting a committed test is this check. Every other
    generator in this directory is asked what it claims, and this batch's ids must be disjoint from that.
    """
    here = os.path.dirname(os.path.abspath(__file__))
    own = os.path.basename(__file__)
    claimed = set()
    for name in sorted(os.listdir(here)):
        if not name.endswith(".py") or name == own:
            continue
        text = open(os.path.join(here, name), encoding="utf-8").read()
        claimed |= set(re.findall(r'"(SOL-TCK-\d{4,6})"', text))
        claimed |= set(re.findall(r'"(REQ-\d{4,6})"', text))
    mine = {t["tid"] for t in TESTS} | set(REQS_SPEC)
    clash = sorted(mine & claimed)
    if clash:
        sys.stderr.write("ids already claimed by another generator in this directory: %s\n"
                         % ", ".join(clash))
        return 1
    return 0


def verify():
    """Every oracle must be derivable from spec text that is actually in the document."""
    bad = []
    seen = {}
    for rid, spec in REQS_SPEC.items():
        if len(spec["quotes"]) < 2:
            bad.append((rid, "fewer than two normative quotes"))
        for q in spec["quotes"]:
            if norm(q) not in SPEC_N:
                bad.append((rid, "quote not in LANGUAGE_SPEC.md: %s" % q[:70]))
            seen.setdefault(norm(q), []).append(rid)
    for q, owners in seen.items():
        if len(owners) > 1:
            bad.append(("quotes", "quote claimed by %s: %s" % (owners, q[:60])))
    for t in TESTS:
        if "print(" not in t["src"]:
            bad.append((t["tid"], "program produces no output"))
        if t["outcome"] == "COMPILE_ERROR":
            if "EXECUTED-INVALID" not in t["src"]:
                bad.append((t["tid"], "rejected program lacks the EXECUTED-INVALID sentinel"))
            diag = t["exp"]["diagnostic"]
            if t["tid"] in FAMILY_ONLY:
                if diag.get("code") is not None or diag.get("family") != FAMILY_ONLY[t["tid"]]:
                    bad.append((t["tid"], "listed family-only but does not assert exactly %s"
                                % FAMILY_ONLY[t["tid"]]))
            elif diag.get("code") not in NAMED_CODES:
                bad.append((t["tid"], "pinned code is not one the section names for this placement"))
        if t["outcome"] == "SUCCESS" and t["exp"].get("languageExit") != 0:
            bad.append((t["tid"], "SUCCESS without languageExit 0"))
    tids = [t["tid"] for t in TESTS]
    if len(set(tids)) != len(tids):
        bad.append(("tests", "duplicate test id"))
    numbers = sorted(int(t.split("-")[-1]) for t in tids)
    if numbers != list(range(numbers[0], numbers[0] + len(numbers))):
        bad.append(("tests", "test ids are not contiguous"))
    for rid in REQS_SPEC:
        if not any(t["req"] == rid for t in TESTS):
            bad.append((rid, "requirement owns no test"))
    return bad


def main():
    if refuse_reuse_of_claimed_ids():
        return 2
    problems = verify()
    if problems:
        for tid, detail in problems:
            print("%s: %s" % (tid, detail))
        return 1

    byreq = {}
    for t in TESTS:
        byreq.setdefault(t["req"], []).append(t["tid"])

    for t in TESTS:
        d = os.path.join(CORPUS, t["tid"])
        os.makedirs(d, exist_ok=True)
        header = ("// Solvik TCK %s\n" % t["tid"]
                  + "".join("// %s\n" % ln for ln in t["note"].split("\n"))
                  + "//\n// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:\n"
                  + "".join("//   - %s\n" % q.replace("\n", " ")
                            for q in REQS_SPEC[t["req"]]["quotes"])
                  + "//\n")
        source = header + t["src"]
        with open(os.path.join(d, "main.sol"), "w", encoding="utf-8") as handle:
            handle.write(source)
        man = {"manifestSchemaVersion": 1, "specVersion": SPEC_VERSION, "testId": t["tid"],
               "category": t["cat"], "profile": "full-language", "status": "required",
               "requirements": [t["req"]], "entryPoint": "main.sol",
               "outcome": t["outcome"], "expectation": t["exp"]}
        with open(os.path.join(d, "%s.manifest.json" % t["tid"]), "w", encoding="utf-8") as handle:
            json.dump(man, handle, indent=2)
            handle.write("\n")
        print(t["tid"], t["outcome"], t["req"])

    inventory = json.load(open(REQUIREMENTS, encoding="utf-8"))
    known = {r["id"]: r for r in inventory["requirements"]}
    for rid, spec in REQS_SPEC.items():
        record = {"id": rid, "specVersion": SPEC_VERSION, "section": spec["section"],
                  "summary": spec["summary"], "kind": spec["kind"], "profile": "full-language",
                  "portable": True, "tests": byreq[rid], "status": "tested", "lifecycle": "active",
                  "oracleNotes": spec["oracle"], "normativeQuotes": spec["quotes"]}
        if rid in known:
            if known[rid].get("tests") and known[rid]["tests"] != byreq[rid]:
                sys.stderr.write("%s already owns a different test list\n" % rid)
                return 1
            known[rid].update(record)
        else:
            inventory["requirements"].append(record)
    inventory["requirements"].sort(key=lambda r: r["id"])
    with open(REQUIREMENTS, "w", encoding="utf-8") as handle:
        # ensure_ascii is left at its default, matching every other generator here. The inventory
        # is written whole by whichever generator runs last, so a single tool opting for literal
        # non-ASCII would rewrite every existing record's escaping and make the committed file depend
        # on the order the generators happened to run in.
        json.dump(inventory, handle, indent=2)
        handle.write("\n")

    profile = json.load(open(PROFILE, encoding="utf-8"))
    profile["requirements"] = sorted(set(profile["requirements"]) | set(REQS_SPEC))
    with open(PROFILE, "w", encoding="utf-8") as handle:
        json.dump(profile, handle, indent=2)
        handle.write("\n")

    print("wrote %d test directories, %d requirements" % (len(TESTS), len(REQS_SPEC)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
