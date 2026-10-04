#!/usr/bin/env python3
"""Convert the generic-function-value obligations that phase 5 made observable into tested requirements.

Phase 5 made a generic function usable as a *value*. Before it, naming a generic function produced a
value at exactly one place -- an argument whose callee's type parameters were already settled -- and
`tck/tools/gen37.py` never tested one, because SOL-TCK-0428 and SOL-TCK-0431 both write `identity` and
mean the *built-in* identity, whose parameter type is `Any` and which is therefore not generic at all.
So the corpus had no requirement about generic function values at all, and no test able to tell a
correct implementation from one that quietly generalized a reference to `Any`.

A generic declaration names type parameters that have no value until something supplies them, and the
revision says where they come from: never from the reference site itself, always from the *expected
function type* at that site. The compiler computes one complete substitution, applies it to the declared
signature, and then checks ordinary function-type assignability -- the same inference a call already
performs, finished before lowering so that a function value performs no runtime type dispatch.

What each requirement asserts, and why it needs the tests it has:

  * REQ-3312 -- contextual instantiation. The revision states the rule as a property of *positions* and
    lists the positions a function type may occupy, so the same reference has to work in each of them
    and fail where none supplies a signature. SOL-TCK-0436 enumerates the declared-type positions rather
    than sampling one: a static property initializer, an instance property initializer, four `val`
    initializers of four different shapes, a static property assignment, and a generic type argument --
    one reference to one declaration simultaneously denoting `func(Integer): Integer`,
    `func(String): String`, `func(Integer, String): String` and `func(Integer): List<Integer>` in a
    single program, and called at each, which no host without a per-site substitution can produce.
    SOL-TCK-0437 is the half the revision preserves: explicit type arguments on direct calls still work,
    and are *not* how a value gets its types, because no new `name<Type>` expression form exists.

  * REQ-3313 -- canonical identity survives instantiation. Different monomorphic instantiations of one
    declaration share one runtime identity, because instantiation changes static typing and not the
    executable value. SOL-TCK-0438 reads that through the public surface only: `===` between two bindings
    of one declaration is true, `equals` between two bindings of the same declaration at *different*
    types is true while `equals` against a *different* declaration at the *same* type is false -- the
    pair that shows the identity belongs to the declaration rather than to the type or the instantiation
    -- and `hashCode` agrees in the direction section 3 actually guarantees, whose converse it says is
    explicitly not required.

  * REQ-3314 -- an insufficient expected function type is `SOLV-TYPE-030` on the reference, with no
    second inference diagnostic. "Insufficient" has shapes the analyzer reaches by different routes, so
    each is its own program: nothing expected at all (SOL-TCK-0439), an expected `Any` (SOL-TCK-0440), an
    expected unbounded type parameter (SOL-TCK-0441), and an argument position whose callee is generic
    while its parameter is not (SOL-TCK-0442). The middle pair is the important one, because in both the
    reference *has* an expected type and is still refused: honouring a reference under an expected type
    is the easy wrong implementation, and `Any` is the type a value of every function type widens to, so
    nothing chooses the type argument yet the assignment would otherwise succeed.

  * REQ-3315 -- call positions supply the expected function type. A direct call keeps its statically
    resolved path, and a reference in an argument slot is instantiated from the callee's parameter type,
    which is the position where the expected type is least like a declared type because the callee may
    still be inferring when the argument is reached. SOL-TCK-0443 takes the three call shapes that
    matter: an argument whose callee parameter mentions a type parameter settled by a *later* argument
    (so the reference has to be examined after that parameter is known rather than bind to the first
    evidence it sees), the same callee at a different type in the same program (which is what stops a
    one-substitution-per-declaration implementation from passing by accident), and the same callee
    reached through a function *value* with a closed declared type, which is the indirect path the
    section says direct calls do not replace. SOL-TCK-0444 is the nested half: a callee whose parameter
    mentions its type parameter only *inside* a function type, so the binding happens position by
    position down two function types rather than by matching them whole -- the shape a unifier that
    treats a function type as an atom cannot express.

  * REQ-3316 -- instantiation failure is inference, arity failure is assignment. When a reference's
    parameters and its expected type agree in arity, a parameter left unresolved is `SOLV-TYPE-030`; when
    they disagree, every shared position resolves and what remains is an assignability failure.
    SOL-TCK-0445 pins the second half, written as a static declaration initializer because that is the
    placement whose code the revision names. Reporting "cannot infer type argument" for a function of the
    wrong *shape* would be both wrong and misdirecting.

An earlier draft of this batch also pinned the rejection of `apply(duplicator(identity), 10)` -- a
generic reference passed inside a generic call whose own inference is unfinished. It was removed before
committing, and the reason matters more than the test would have: that program is not higher-rank,
because every value in it ends up monomorphic (`duplicator` applied at `func(Integer): Integer` returns
`func(Integer): Integer`), so nothing in the deferred-forms sentence requires refusing it. What makes it
fail today is only the order in which the analyzer performs the two inferences. A TCK oracle may pin what
the specification forbids, not what this implementation has not yet learned to infer, and pinning it
would have hardened an inference-order limitation into a permanent prohibition on a program the language
may be required to accept. The genuine spec-required refusal in this area is SOL-TCK-0442, where the
callee's type parameter has no determining argument at all.

On diagnostic codes. `SOLV-TYPE-030` is named by the required-diagnostic table for a generic function
used as a value without a complete expected function type, and `SOLV-TYPE-001` for a static declaration
initializer that is not assignable; both are pinned wherever they are the spec-named consequence. What is
deliberately *not* pinned is the diagnostic on a *binding* expression: the table places `SOLV-TYPE-030`
on the reference, and no section names a code for an initializer position tainted by a rejected
reference, so SOL-TCK-0439 and SOL-TCK-0441 assert that one as `{
    "family": "TYPE"
}
` alone -- the same
division SOL-TCK-0433 and SOL-TCK-0412 use for immutability violations.

Idempotent like every other generator, and refuses to run if any id it claims is already claimed by
another generator in this directory.
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

# --- REQ-3312: contextual instantiation
NOT_POLYMORPHIC = "A generic function declaration does not itself produce a first-class polymorphic value."
CONTEXTUAL = ("It must be instantiated to one monomorphic function type at each value-reference site, "
              "and that instantiation is contextual:")
EXPECTED_SUPPLIES = "The expected function type supplies constraints for every declared type parameter."
SUBSTITUTE_THEN_ASSIGN = ("The compiler determines one complete substitution, applies it to the "
                          "function's declared parameter and result types, and then checks ordinary "
                          "function-type assignability.")
PARAMS_FIRST = ("Inference first unifies occurrences in the declared parameter types with the expected "
                "parameter types; result positions may confirm or complete a unique substitution but "
                "never choose arbitrarily among several valid types.")
BEFORE_LOWERING = ("All type parameters must be resolved, and the decision is made before lowering: a "
                   "function value performs no runtime type dispatch.")
EXPLICIT_ON_CALLS = ("Explicit type arguments remain available on direct calls, so "
                     "`identity<Integer>(1)` keeps working.")
NO_NAME_TYPE_FORM = ("No source syntax for a polymorphic function type is introduced, and no new "
                     "`name<Type>` expression form is introduced, because it would be ambiguous with "
                     "relational expressions.")
WHERE_FUNCTION_TYPES_APPEAR = ("A function type may appear wherever another non-deferred type may "
                               "appear, including as the type of a local, parameter, return, property, "
                               "or static property, as a generic type argument, as the inner type of a "
                               "nullable type, and in the parameter or return position of another "
                               "function type:")

# --- REQ-3313: canonical identity across instantiations
SHARED_IDENTITY = ("Contextual instantiations of one generic declaration at different function types "
                   "also share that declaration's canonical runtime identity: instantiation changes "
                   "static typing, not the underlying executable value.")
# NB: two neighbouring sentences are deliberately absent from this requirement's quote list. "Every
# reference evaluation to the same declared top-level function produces the same canonical
# function-value identity..." is per-declaration canonical identity, which is already the quoted basis
# of the requirements the earlier batches own; repeating it here would make one sentence the normative
# source of two requirements. And "Canonical identity is never shared between Solvik contexts" has no
# portable witness at all: its subject is two contexts, which a single-program corpus cannot construct,
# so quoting it inside a requirement claimed `tested` would assert an oracle the test does not have.
REFERENCE_EQ = ("Semantic equality for function values is reference identity, and `hashCode()` is the "
                "matching reference-identity hash. These operations are fixed and cannot be overridden.")
HASH_INVARIANT = ("**Invariant.** When `left == right` is `true`, `left.hashCode() == "
                  "right.hashCode()` is `true`. The converse is not required: unequal values may share "
                  "a hash.")
IDENTITY_BEARING_OPERANDS = ("A function value is identity-bearing, so a concrete function type and "
                             "its nullable form are valid operands of `===` and `!==` when the ordinary "
                             "compatibility rule also holds.")

# --- REQ-3314: insufficient expected function types
NO_EXPECTED = "A generic function reference with no expected function type is `SOLV-TYPE-030`:"
INSUFFICIENT = ("An expected `Any`, an unbounded type parameter, or any other type that does not "
                "expose a complete function signature is insufficient.")
TYPE030_TABLE = ("A generic function or generic method used as a value without a complete expected "
                 "function type is the existing `TYPE_CANNOT_INFER` (`SOLV-TYPE-030`), reported on the "
                 "function or method reference; no second inference diagnostic exists.")

# --- REQ-3315: call positions supply the expected function type
DIRECT_PATH = "A direct call whose target is statically known keeps its existing statically resolved path."
INDIRECT_PATH = ("Function values add an indirect call path; they do not replace direct calls, and a "
                 "call such as `sum(1, 2)` is never lowered into constructing a function value and "
                 "then invoking it.")
CONTRAVARIANT = "Function-type assignability is contravariant in parameters and covariant in the result."
ORDINARY_MECHANISMS = ("Function types participate in ordinary nullability, flow analysis, generic "
                       "substitution, and definite initialization.")

# --- REQ-3316: instantiation failure versus arity failure
STATIC_INIT_CODE = ("A static declaration initializer that is not assignable to the declared type is "
                    "`SOLV-TYPE-001`.")

REQS_SPEC = {
    "REQ-3312": dict(
        section="6. Functions (Generic function values)",
        kind="compile-time",
        summary=("A generic function used as a value is instantiated to one monomorphic function type "
                 "from the expected function type at the reference site, by one complete substitution "
                 "computed before lowering and then checked by ordinary function-type assignability"),
        quotes=[NOT_POLYMORPHIC, CONTEXTUAL, EXPECTED_SUPPLIES, SUBSTITUTE_THEN_ASSIGN, PARAMS_FIRST,
                BEFORE_LOWERING, WHERE_FUNCTION_TYPES_APPEAR],
        oracle=(
            "SOL-TCK-0436 puts one generic reference in every declared-type position the section "
            "enumerates -- static property initializer, instance property initializer, `val` "
            "initializers of four different shapes, a static property assignment, and a generic type "
            "argument -- and then calls the value at each, so a substitution is shown usable rather "
            "than merely typed. Those shapes are mutually incompatible monomorphic types over two "
            "declarations in one program, which a host that computed one substitution per declaration, "
            "or that generalized a reference to `Any`, cannot produce. SOL-TCK-0437 is the half the "
            "section preserves: written type arguments on direct calls still resolve, at two different "
            "arities, with and without them written, beside a value reference whose types come only "
            "from its declared type -- both mechanisms in one file so neither can be quietly doing the "
            "other's work."),
    ),
    "REQ-3313": dict(
        section="6. Functions (Generic function values) / 3. Equality and reference identity (function values; hashing)",
        kind="runtime",
        summary=("Contextual instantiations of one generic declaration at different function types "
                 "share that declaration's canonical runtime identity, while distinct declarations "
                 "never share an identity whatever their types"),
        quotes=[SHARED_IDENTITY, REFERENCE_EQ, HASH_INVARIANT, IDENTITY_BEARING_OPERANDS],
        oracle=(
            "SOL-TCK-0438 reads identity through the only operations the section fixes. Two bindings of "
            "one declaration are `===`. `equals` between two bindings of the same declaration at "
            "*different* monomorphic types is true, and `equals` between two *different* declarations "
            "at the *same* monomorphic type is false -- that pair is what shows the identity comes from "
            "the declaration rather than from the type or the instantiation, and reversing either answer "
            "distinguishes every plausible wrong implementation. `hashCode` is asserted only in the "
            "direction section 3 states, since its invariant says the converse is not required, so the "
            "oracle cannot be satisfied by a hash that ignores its value."),
    ),
    "REQ-3314": dict(
        section="6. Functions (Generic function values; required diagnostics)",
        kind="compile-time",
        summary=("A generic function used as a value with no expected function type, or with an "
                 "expected type that exposes no complete function signature such as Any or an "
                 "unbounded type parameter, is TYPE_CANNOT_INFER reported on the reference with no "
                 "second inference diagnostic"),
        quotes=[NO_EXPECTED, INSUFFICIENT, TYPE030_TABLE],
        oracle=(
            "Four programs, one per route the analyzer takes. SOL-TCK-0439 has nothing expected and is "
            "the only one whose reference span is pinned, because the table places the diagnostic on the "
            "reference and an offset is checkable there; the binding's own diagnostic is family-only, as "
            "no section names a code for an initializer position tainted by a rejected reference. "
            "SOL-TCK-0440 expects `Any` -- the type every function value widens to, so an implementation "
            "that widened instead of instantiating would accept it silently. SOL-TCK-0441 expects an "
            "unbounded type parameter, written inside a generic function because a static property may "
            "not name its class's type parameter. SOL-TCK-0442 is the route where an expected type is "
            "present in the source and still supplies no constraint: the callee is generic in `U` while "
            "its parameter is `func(U): U` with `U` undetermined, so nothing resolves it."),
    ),
    "REQ-3315": dict(
        section="6. Functions (Generic function values; function values and invocation)",
        kind="compile-time",
        summary=("Call positions supply expected function types to a generic function reference, "
                 "including a callee parameter settled by a later argument and a callee parameter that "
                 "mentions the type parameter only inside a nested function type"),
        quotes=[DIRECT_PATH, INDIRECT_PATH, CONTRAVARIANT, PARAMS_FIRST, ORDINARY_MECHANISMS],
        oracle=(
            "SOL-TCK-0443 takes the accepted side through three call shapes: an argument whose callee "
            "parameter is still being inferred and is settled by a *later* argument, so the reference "
            "has to be examined after that parameter is known instead of binding to the first evidence "
            "it sees; the same callee at a second type in the same program, which is what stops a "
            "one-substitution-per-declaration implementation from passing by accident; and the same "
            "callee reached through a function value with a closed declared type, which is the indirect "
            "path the section says direct calls do not replace. SOL-TCK-0444 is the nested half: a "
            "callee whose type parameter appears only inside `func(T): T`, so the substitution is found "
            "by descending two function types position by position, which a unifier that treats a "
            "function type as an atom cannot express; the resulting values are then called at both "
            "instantiated types, so the substitution is shown usable rather than merely computed."),
    ),
    "REQ-3316": dict(
        section="6. Functions (Generic function values; required diagnostics)",
        kind="compile-time",
        summary=("A generic reference whose arity disagrees with the expected function type resolves "
                 "every shared position and fails ordinary assignability, rather than reporting an "
                 "inference failure"),
        quotes=[SUBSTITUTE_THEN_ASSIGN, CONTEXTUAL, BEFORE_LOWERING, STATIC_INIT_CODE],
        oracle=(
            "SOL-TCK-0445 assigns a two-parameter generic to a one-parameter function type as a static "
            "declaration initializer -- the placement whose code the section names, so `SOLV-TYPE-001` "
            "is pinned rather than merely observed, and the sentinel proves non-execution. Both "
            "parameters of the generic are determined from the one shared position and the result, so "
            "nothing is left uninferred and `SOLV-TYPE-030` would be a false report; the single "
            "diagnostic must name the substituted two-parameter signature, which is what tells the "
            "reader the shape is wrong rather than the inference."),
    ),
}

TESTS = [
    dict(
        tid="SOL-TCK-0436", req="REQ-3312", cat="types", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(b"41\nab\ntwo\n1\n5\ncd\n6\n").decode("ascii")},
        note="One generic reference, instantiated at four different monomorphic function types across "
             "the static-property, instance-property, binding, assignment, and generic-argument "
             "positions, and then called at each",
        src="""func identity<T>(value: T): T {
    return value
}

func second<A, B>(first: A, other: B): B {
    return other
}

func wrap<T>(value: T): List<T> {
    return List<T>(value)
}

class Holder {
    val member: func(Integer): Integer = identity
    static mutable val shared: func(String): String = identity
}

val integerIdentity: func(Integer): Integer = identity
val stringIdentity: func(String): String = identity
val pick: func(Integer, String): String = second
val boxed: func(Integer): List<Integer> = wrap
val callbacks: List<func(Integer): Integer> = List<func(Integer): Integer>(identity)

val holder = Holder()
Holder.shared = identity
val taken: func(String): String = Holder.shared

print(integerIdentity(41).toString() .. "\\n")
print(stringIdentity("ab") .. "\\n")
print(pick(1, "two") .. "\\n")
print(boxed(3).size.toString() .. "\\n")
print(holder.member(5).toString() .. "\\n")
print(taken("cd") .. "\\n")
print(callbacks.get(0)(6).toString() .. "\\n")
""",
    ),
    dict(
        tid="SOL-TCK-0437", req="REQ-3312", cat="generics", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(b"7\ntext\n1\n2\nfrom-value").decode("ascii")},
        note="Written type arguments on direct calls keep working beside a value reference whose "
             "instantiation comes only from its declared type, so neither mechanism is doing the "
             "other's work",
        src="""func identity<T>(value: T): T {
    return value
}

func pair<A, B>(a: A, b: B): List<A> {
    return List<A>(a)
}

val made: func(String): String = identity

print(identity<Integer>(7).toString() .. "\\n")
print(identity("text") .. "\\n")
print(pair("k", 1).size.toString() .. "\\n")
print(pair<Integer, String>(2, "x").get(0).toString() .. "\\n")
print(made("from-value"))
""",
    ),
    dict(
        tid="SOL-TCK-0438", req="REQ-3313", cat="equality", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(b"true\ntrue\ntrue\nfalse\n1two\n").decode("ascii")},
        note="Instantiations of one declaration at different types share its identity, and distinct "
             "declarations at the same type do not -- the identity belongs to the declaration",
        src="""func identity<T>(value: T): T {
    return value
}

func echo<T>(value: T): T {
    return value
}

val asInteger: func(Integer): Integer = identity
val asString: func(String): String = identity
val sameShape: func(Integer): Integer = identity
val other: func(Integer): Integer = echo

print((asInteger === sameShape).toString() .. "\\n")
print(asInteger.equals(asString).toString() .. "\\n")
print((asInteger.hashCode() == asString.hashCode()).toString() .. "\\n")
print(asInteger.equals(other).toString() .. "\\n")
print(asInteger(1).toString() .. asString("two") .. "\\n")
""",
    ),
    dict(
        tid="SOL-TCK-0439", req="REQ-3314", cat="generics", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-030",
                            "location": {"startByteOffset": 0, "endByteOffset": 0}}},
        note="A generic function reference with no expected function type at all; the pinned location "
             "is the reference itself, which is where the required-diagnostic table places it",
        token="= identity", pin=1,
        src="""func identity<T>(value: T): T {
    return value
}

val ambiguous = identity
print(ambiguous)
print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0440", req="REQ-3314", cat="generics", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-030"}},
        note="An expected `Any` accepts every function value yet exposes no complete function "
             "signature, so widening instead of instantiating is refused",
        src="""func identity<T>(value: T): T {
    return value
}

val boxed: Any = identity
print(boxed)
print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0441", req="REQ-3314", cat="generics", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-030"}},
        note="An expected unbounded type parameter is a type that is present and still exposes no "
             "complete function signature",
        src="""func identity<T>(value: T): T {
    return value
}

func use<R>() {
    val slot: R = identity
    print(slot)
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0442", req="REQ-3314", cat="generics", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-030"}},
        note="A generic callee whose parameter type is not generic supplies no constraint on the "
             "reference's own type parameter",
        src="""func identity<T>(value: T): T {
    return value
}

func takesOnly<U>(f: func(U): U): Integer {
    return 0
}

func use(): Unit {
    takesOnly(identity)
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0443", req="REQ-3315", cat="generics", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(b"42\nxy\n1\n3\n").decode("ascii")},
        note="Argument positions instantiate a generic reference from the callee's parameter, "
             "including a parameter settled by a later argument and the same callee as a function value",
        src="""func identity<T>(value: T): T {
    return value
}

func apply<T>(f: func(T): T, v: T): T {
    return f(v)
}

func outer<U>(f: func(U): U, v: U): U {
    return f(v)
}

val composed: func(func(Integer): Integer, Integer): Integer = apply

print(apply(identity, 42).toString() .. "\\n")
print(apply(identity, "xy") .. "\\n")
print(outer(identity, 1).toString() .. "\\n")
print(composed(identity, 3).toString() .. "\\n")
""",
    ),
    dict(
        tid="SOL-TCK-0444", req="REQ-3315", cat="generics", outcome="SUCCESS",
        exp={"languageExit": 0,
             "stdoutBase64": base64.b64encode(b"ab\n20\n").decode("ascii")},
        note="A callee whose type parameter appears only inside a nested function type binds it by "
             "descending two function types position by position",
        src="""func identity<T>(value: T): T {
    return value
}

func duplicator<T>(f: func(T): T): func(T): T {
    return f
}

val viaString: func(String): String = duplicator(identity)
val viaInteger: func(Integer): Integer = duplicator(identity)

print(viaString("ab") .. "\\n")
print(viaInteger(20).toString() .. "\\n")
""",
    ),
    dict(
        tid="SOL-TCK-0445", req="REQ-3316", cat="types", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE", "code": "SOLV-TYPE-001"}},
        note="A two-parameter generic against a one-parameter function type resolves every shared "
             "position, so the remaining mismatch is ordinary assignability and not an inference failure",
        src="""func second<A, B>(first: A, other: B): B {
    return other
}

class Boundary {
    static val mismatched: func(Integer): Integer = second
}

print("EXECUTED-INVALID")
""",
    ),
]

# The only diagnostic codes the revision names for these placements. Anything else pinned here would be
# a code borrowed from the implementation rather than one the specification requires.
NAMED_CODES = ("SOLV-TYPE-030", "SOLV-TYPE-001")


def refuse_reuse_of_claimed_ids():
    """A fresh batch must not re-allocate ids an earlier generator already owns.

    The id allocation is repository-wide, hand-rolled, and enforced only by convention, so the one thing
    standing between a new batch and silently overwriting a committed test is this check. Every other
    generator in this directory that mentions this batch's spec version is asked what it claims, and this
    batch's ids must be disjoint from that. Generators pinned to other versions are included too rather
    than skipped, because an id string is unique across the corpus regardless of which version directory
    it sits in, and being wrong in the conservative direction costs nothing here.
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
    for rid, spec in REQS_SPEC.items():
        if len(spec["quotes"]) < 2:
            bad.append((rid, "fewer than two normative quotes"))
        for q in spec["quotes"]:
            if norm(q) not in SPEC_N:
                bad.append((rid, "quote not in LANGUAGE_SPEC.md: %s" % q[:70]))
    for t in TESTS:
        if "print(" not in t["src"]:
            bad.append((t["tid"], "program produces no output"))
        if t["outcome"] == "COMPILE_ERROR":
            if "EXECUTED-INVALID" not in t["src"]:
                bad.append((t["tid"], "rejected program lacks the EXECUTED-INVALID sentinel"))
            code = t["exp"]["diagnostic"].get("code")
            if code not in NAMED_CODES:
                bad.append((t["tid"], "pinned code is not one the section names for this placement"))
        if t["outcome"] == "SUCCESS" and t["exp"].get("languageExit") != 0:
            bad.append((t["tid"], "SUCCESS without languageExit 0"))
        if "pin" in t and t["src"].count(t["token"]) < t["pin"]:
            bad.append((t["tid"], "pin token occurs fewer times than the pin index"))
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
        exp = json.loads(json.dumps(t["exp"]))
        if "pin" in t:
            # The header shifts every offset, so a pinned span is measured in the file as written rather
            # than assumed. `token` is text *around* the reference and `pin` selects which occurrence, so
            # the recorded span is the reference word itself.
            at = -1
            for _ in range(t["pin"]):
                at = source.index(t["token"], at + 1)
            word = t["token"].lstrip("= ").strip()
            start = source.index(word, at)
            exp["diagnostic"]["location"] = {
                "startByteOffset": len(source[:start].encode("utf-8")),
                "endByteOffset": len(source[:start + len(word)].encode("utf-8"))}
        with open(os.path.join(d, "main.sol"), "w", encoding="utf-8") as handle:
            handle.write(source)
        man = {"manifestSchemaVersion": 1, "specVersion": SPEC_VERSION, "testId": t["tid"],
               "category": t["cat"], "profile": "full-language", "status": "required",
               "requirements": [t["req"]], "entryPoint": "main.sol",
               "outcome": t["outcome"], "expectation": exp}
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
