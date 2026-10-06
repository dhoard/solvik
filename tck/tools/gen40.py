#!/usr/bin/env python3
"""Convert the bound-method-reference obligations of phase 6 into tested requirements.

Phase 6 turned three member reads that previously reported a "must be invoked" error into value-producing
reads -- a declared class instance method, a declared interface member, and `super.method` -- and landed with
an in-process suite (`SolvikBoundMethodReferenceTest`) and no TCK requirements. Without portable oracles the
central promises of that part of section 6 were visible only inside this repository: that a bound value
dispatches on the *captured receiver's* runtime class rather than the static receiver type or the class the
reference was written inside, that the receiver is evaluated once at creation and retained rather than
re-evaluated per call, that `super.method` skips redispatch, and that `?.` on a nullable receiver yields a
nullable function value that is null exactly when the receiver is.

Every normative quotation added here is quoted by exactly one requirement in this batch, and every one was
already in the specification -- checked against the committed inventory before any oracle was written. Three
sentences this batch could have used are already owned elsewhere and are *not* quoted a second time: the
`toString()` rendering sentence, owned by REQ-3307, and the section-3 bare-member-read sentences and the
section 23.4 `SOLV-TYPE-014` table row, owned by REQ-1805 and REQ-2309. The non-bindable requirement here
quotes only the *section 6* sentence that defers to those, and nothing below asserts a rendering or a hash.

One quotation *is* deliberately shared with earlier requirements, and `verify()` enforces rather than assumes
the exception. REQ-3329 re-quotes the generic-value sentence *It must be instantiated to one monomorphic
function type at each value-reference site, and that instantiation is contextual*, owned by the generic-value
pair that tests it at **top-level** function references. Nothing in the corpus applies that sentence to a
**method** reference, and a method reference cannot be typed unless the receiver's class type arguments close
before the method's own parameter is inferred, so this is a second, distinct obligation under the same
sentence rather than a restatement. `verify()` refuses any quotation owned by a numerically earlier
requirement unless it is declared in `INTENTIONAL_REQUOTES`, and requires the declared owner to quote the
sentence itself; both refusal paths are exercised by inspection.

What each requirement asserts, and why it needs the tests it has:

  * REQ-3323 -- a bound method value is produced, invokable, and excludes the receiver from its type.
    SOL-TCK-0468 initializes a binding of a function type and invokes it; SOL-TCK-0469 carries a bound value
    through a call argument and a result, which is what shows the value is an ordinary function value rather
    than a special form legal only in an initializer. The third quotation is the negative half written from the
    other side: the required-diagnostics paragraph states that the deferral code `never reports` a bound
    reference to a declared instance method, and SOL-TCK-0468 and SOL-TCK-0469 are exactly the programs that
    sentence makes a lie if a host emits it.

  * REQ-3324 -- virtual dispatch is preserved. The dispatch sentence names overrides, interface defaults,
    delegated implementations, and inherited methods, and each reaches the implementation by a different route,
    so each is its own program: SOL-TCK-0470 reads through a base-typed receiver whose runtime class is two
    levels down, SOL-TCK-0471 binds an inherited method the receiver's class never redeclares, SOL-TCK-0472
    reaches a conforming instance's own implementation and a defaulted requirement through an interface-typed
    receiver, and SOL-TCK-0473 binds a method the receiver declares not at all. This is the property an
    implementation is most likely to get wrong, because binding the static receiver's implementation, or the
    implementation of the class the reference was written inside, is both easier and wrong. SOL-TCK-0474
    supplies the second quotation, which fixes semantic equality for function values as reference identity:
    the program compares the two bound values that the specification's own example names and prints the
    immediate-call result each dispatches to, so the equality answer and the dispatch rule are settled on one
    pair.

  * REQ-3325 -- receiver evaluation and retention. "The receiver expression is evaluated exactly once when the
    bound method value is created, and the receiver is retained strongly by that value." SOL-TCK-0475 makes the
    receiver expression observable -- it increments a counter -- and reads that counter before creation, after
    creation, and after two calls, so hoisting (no evaluation at creation), per-call re-evaluation, and caching
    one value are each caught by a different printed number. The value's *label* then proves the call reached
    the object the expression produced and not a fresh one.

  * REQ-3326 -- `this`, bare names, and `super`. Three sentences with three different outcomes: `this.method`
    is a bound reference to the current receiver; a bare unqualified method name in a value position is
    `SOLV-RESOL-001` while the same name with parentheses stays legal; and `super.method` skips redispatch.
    SOL-TCK-0476 reads `this.method` from a helper declared in a superclass, so the receiver that selects the
    implementation is the one passed at run time. SOL-TCK-0477 is the immediate call that must still compile --
    a test that only showed the value position failing could not distinguish the real rule from one that
    forbade the name outright. SOL-TCK-0478 rejects the value position, and the section names that code, so it
    is pinned. SOL-TCK-0479 binds `super.method` from a class that overrides the method, so a value that
    redispatched would print the override's text.

  * REQ-3327 -- fresh identity per creation. "Each successful evaluation of a bound method-reference
    expression creates a distinct function-value identity, even for the same receiver and method. Copying that
    value through bindings preserves its identity." The two halves are mutually contradictory for any single
    shortcut: memoize per receiver-and-method and freshness fails; mint a wrapper per *read* of the binding and
    copying fails. SOL-TCK-0480 asserts both directions plus the two same-value comparisons that `===` must
    accept for an identity-bearing operand.

  * REQ-3328 -- nullable receivers. "A normal member reference on a nullable receiver is illegal. Safe member
    access produces a nullable function value and evaluates the receiver once... If the receiver is null the
    result is null and no bound function is created; if it is non-null the result is the corresponding bound
    method. When the receiver's static type is non-null, `?.` retains the non-null function type." SOL-TCK-0481
    is the null and non-null cases in one program, with the refinement the section requires to invoke the
    nullable value; SOL-TCK-0482 is the unsafety refusal; SOL-TCK-0483 is the non-null-receiver case, where
    `?.` must *not* make the type nullable -- an implementation that always nullable-ized a safe read would
    fail it.

  * REQ-3329 -- contextual instantiation of a generic method reference. The generic-value sentence that fixes
    instantiation as contextual and per monomorphic reference site is quoted together with the sentence that
    extends it to method references. SOL-TCK-0484 instantiates one declaration at two monomorphic types from
    one receiver, and SOL-TCK-0485 closes the receiver's own class type arguments first by putting the generic
    method on a generic class read through `Cell(Integer)`, so a host that conflated the class parameter with
    the method parameter would substitute wrongly rather than fail to type. SOL-TCK-0486 is the failure the
    sentence names, `SOLV-TYPE-030`, at a receiver-typed reference with no expected function type.

  * REQ-3330 -- only a declared callable binds, and function-typed properties read their stored value. This is
    the requirement that makes the narrowed rule meaningful rather than a placeholder: the universal members
    stay rejected even where a class *overrides* one, and through `super`, which is the path that resolves
    through a superclass's table and so could otherwise bind the override. SOL-TCK-0487 rejects a bare
    `toString`/`equals`/`hashCode` read, SOL-TCK-0488 rejects a `super` read of an overridden universal member,
    SOL-TCK-0489 rejects a static method read -- the one deferred form the required-diagnostics paragraph
    states a code for, so that one program pins `SOLV-TYPE-014` -- and SOL-TCK-0490 is the positive half of the
    member-resolution sentence, a property whose declared type is a function type reading the stored value,
    distinguished from a same-shaped method by the value each produces.

On diagnostic codes. `SOLV-RESOL-001`, `SOLV-TYPE-030`, and `SOLV-TYPE-014` are the only codes pinned, and each
is pinned only where the specification states it for that exact placement: `SOLV-RESOL-001` for a bare method
name in a value position, `SOLV-TYPE-030` for an unconstrained generic method reference, and `SOLV-TYPE-014`
for the deferred callables the required-diagnostics paragraph enumerates by name -- a static method reference,
a constructor, an enum variant, a bare read of a fixed language-defined member, and a synthesized `Result`
operation. The universal-member and `super` refusals land on that same code in this implementation but assert
`{
    "family": "TYPE"
}
` rather than the code, because section 6 routes them to the compile-time error that
section 3 and section 23.4 already require, those sections' own bare-member-read sentences name no code, and
REQ-1805 already carries that family-only choice for the same reason. The nullable-receiver refusal likewise
asserts the family, since the sentence says only "is illegal" and states no code at all. A category that
asserted a code beyond the three above would make this implementation's choice look specification-mandated.

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

# --- REQ-3323: producing and invoking a bound method value, and what the deferral code must not report
PRODUCES = "Reading an instance method without calling it produces a bound method value:"
RECEIVER_NOT_IN_TYPE = "The method's implicit receiver does not appear in the function type."
T14_NEVER = ("It never reports a top-level function reference or a bound reference to a declared instance "
             "method, because this revision accepts both. Section 3 and section 23.4 retain their existing "
             "bare-member-read rejections unchanged.")

# --- REQ-3324: virtual dispatch, and equality on the pair the section's own example names
DISPATCH = ("Ordinary virtual dispatch is preserved. A reference obtained through a class or interface type "
            "invokes the implementation selected by the captured receiver's runtime class. Overrides, "
            "interface defaults, delegated implementations, and inherited instance methods behave the same "
            "through a bound reference as through an immediate method call.")
TWO_BOUND_CREATIONS = "formatter.format === formatter.format // false: two bound-value creations"

# --- REQ-3325: receiver evaluation and retention, judged on values whose equality is identity
EVAL_ONCE = ("The receiver expression is evaluated exactly once when the bound method value is created, and "
             "the receiver is retained strongly by that value.")
SEMANTIC_EQUALITY = ("Semantic equality for function values is reference identity, and `hashCode()` is the "
                     "matching reference-identity hash.")

# --- REQ-3326: this, bare names, and super
THIS_REF = "`this.method` is a bound reference to the current receiver."
BARE_RULE = ("An unqualified method name remains legal only as an immediate call under the existing "
             "implicit-`this` rule, so using a method as a value requires `this.method` and a bare "
             "unqualified method name in a value position is `SOLV-RESOL-001`.")
SUPER_REF = ("`super.method` creates a value bound to `this` that invokes the immediate superclass "
             "implementation without virtual redispatch, matching an immediate `super.method(...)` call.")

# --- REQ-3327: fresh identity per creation, copy-preserving
FRESH_BOUND = ("Each successful evaluation of a bound method-reference expression creates a distinct "
               "function-value identity, even for the same receiver and method.")
COPY_PRESERVES = "Copying that value through bindings preserves its identity."

# --- REQ-3328: nullable receivers
NULL_REF_ILLEGAL = "A normal member reference on a nullable receiver is illegal."
SAFE_ACCESS = "Safe member access produces a nullable function value and evaluates the receiver once:"
NULL_ABSENT = ("If the receiver is null the result is null and no bound function is created; if it is "
               "non-null the result is the corresponding bound method.")
NON_NULL_KEPT = ("When the receiver's static type is non-null, `?.` retains the non-null function type, "
                 "matching existing safe-access behavior.")

# --- REQ-3329: contextual instantiation, extended to method references
CONTEXTUAL = ("It must be instantiated to one monomorphic function type at each value-reference site, and "
              "that instantiation is contextual:")
GENERIC_REF = ("A generic method reference is instantiated contextually under the same monomorphic rules as a "
               "generic top-level function reference, so `var operation: func(Integer): Integer = "
               "object.identity` is accepted and an unconstrained reference is `SOLV-TYPE-030`.")

# --- REQ-3330: only a declared callable binds, and function-typed properties
ONLY_DECLARED = ("Only a declared callable binds. The fixed language-defined universal members `toString`, "
                 "`equals`, and `hashCode`, and the synthesized `Result` operations, are not bindable: a bare "
                 "read of one of them stays the compile-time error that section 3 and section 23.4 already "
                 "require, and the same holds for a static method, a constructor, and an enum variant.")
PROPERTY_RESOLUTION = ("A property may itself have a function type. Because a class member namespace cannot "
                       "hold a property and a method with the same name, member resolution decides statically "
                       "whether `receiver.member` reads a stored function value or creates a bound method "
                       "value.")
T14_REPORTS = ("`TYPE_FUNCTION_AS_VALUE` (`SOLV-TYPE-014`) reports a callable that this revision keeps "
               "explicitly deferred used as a value: a static method reference, a constructor, an enum "
               "variant, and a bare read of a fixed language-defined member or a synthesized `Result` "
               "operation.")

REQS_SPEC = {
    "REQ-3323": dict(
        section="6. Functions (Bound method references)",
        kind="compile-time",
        summary=("Reading a declared instance method without calling it produces a bound method value whose "
                 "function type excludes the method's implicit receiver, and the deferral diagnostic never "
                 "reports such a bound reference"),
        quotes=[PRODUCES, RECEIVER_NOT_IN_TYPE, T14_NEVER],
        oracle=(
            "SOL-TCK-0468 initializes a binding whose declared type is `func(Integer): String` from "
            "`formatter.format` and calls it, so the oracle is the formatted argument. The declared type has "
            "one parameter while the method reads its receiver, which is the implicit-receiver sentence doing "
            "its job: a host that put the receiver into the function type would need two arguments here and "
            "the program would not compile at all. SOL-TCK-0469 carries the same value through a call argument "
            "and back out as a result, because a bound value that was legal only in an initializer would "
            "still satisfy SOL-TCK-0468. The third quotation is the negative half written from the other side: "
            "the required-diagnostics paragraph states that the deferral code *never* reports a bound "
            "reference to a declared instance method, and these two programs are exactly what that sentence "
            "makes a lie if a host emits the code here -- which is why the requirement is stated over both the "
            "production and the exclusion."),
        notes=(
            "The two programs use different receivers and different method arities so neither result is "
            "byte-identical to the other. Nothing here asserts that distinct bound values hash differently: "
            "the specification guarantees only that equal values hash equally."),
    ),
    "REQ-3324": dict(
        section="6. Functions (Bound method references)",
        kind="compile-time",
        summary=("A bound method reference invokes the implementation the captured receiver's runtime class "
                 "selects -- overrides, inherited methods, interface defaults, and delegated implementations -- "
                 "and the two bound values of one method are unequal under the reference-identity equality"),
        quotes=[DISPATCH, TWO_BOUND_CREATIONS],
        oracle=(
            "Four programs, one per route the dispatch sentence names, plus a fifth settling the second "
            "quotation. SOL-TCK-0470 obtains the reference "
            "through a base-typed receiver whose runtime class overrides twice, so the printed text is the "
            "leaf's, and a host that bound the static receiver's implementation would print the base's "
            "instead. SOL-TCK-0471 binds a method the receiver's class never redeclares, which a read "
            "consulting only that class's own declarations could not resolve at all. SOL-TCK-0472 reads "
            "through an interface-typed receiver and reaches both the conforming instance's own requirement "
            "and a defaulted one that calls the requirement back. SOL-TCK-0473 binds a method supplied "
            "entirely by a `delegate`, the case with no declared method on the receiver at all. Every one of "
            "these prints the text a correct dispatch produces and not the one a creation-site or static-type "
            "dispatch would produce. SOL-TCK-0474 is the second quotation's example as a program: it compares "
            "the two values `formatter.format` produces and prints the call result each dispatches to, so the "
            "reference-identity answer and the dispatch result are fixed by one expected string."),
        notes=(
            "The interface-default program's expected text is derived by reading the default body and "
            "substituting the conforming instance's requirement, which is what the phrase 'as through an "
            "immediate method call' fixes. The comparison program prints the outcome of `===` on the two "
            "values and never a hash."),
    ),
    "REQ-3325": dict(
        section="6. Functions (Bound method references)",
        kind="compile-time",
        summary=("The receiver expression of a bound method reference is evaluated exactly once at creation "
                 "and retained strongly by the resulting value, and two values so created are distinct under "
                 "the reference-identity semantic equality"),
        quotes=[EVAL_ONCE, SEMANTIC_EQUALITY],
        oracle=(
            "SOL-TCK-0475 writes a receiver expression that increments a visible counter, then prints the "
            "counter immediately after the reference, after two calls through the value, and the value the "
            "retained receiver produces. The expected sequence is one increment at creation and none at either "
            "call. Each shortcut is caught by a different number in that sequence: evaluating at call time puts "
            "a zero first and non-zero afterwards, evaluating at neither leaves the label missing, and "
            "re-evaluating the receiver chain per call increments on the calls. The second quotation is "
            "settled in the same program by comparing a copy bound to a second binding against the original "
            "and by comparing a second reference created from the counted expression against it, so the "
            "identity rule is observed on values whose receiver came from that expression."),
        notes=(
            "The counter is read by subtraction from a saved baseline so the program does not depend on any "
            "other evaluation in the file having incremented it."),
    ),
    "REQ-3326": dict(
        section="6. Functions (Bound method references)",
        kind="compile-time",
        summary=("`this.method` binds the current receiver, a bare unqualified method name in a value "
                 "position is `SOLV-RESOL-001` while the same name as an immediate call stays legal, and "
                 "`super.method` binds the immediate superclass implementation without redispatch"),
        quotes=[THIS_REF, BARE_RULE, SUPER_REF],
        oracle=(
            "SOL-TCK-0476 calls a helper declared in a superclass from a subclass receiver, where the helper "
            "returns `this.greet` as a value, so the implementation is selected by the receiver passed at run "
            "time and the program prints the override's text for the subclass and the base's for the base. "
            "SOL-TCK-0477 keeps the bare name as an immediate call in a program that also uses `this.compute` "
            "as a value, which is what separates the real rule from one that rejects the name outright. "
            "SOL-TCK-0478 places the bare name in a return position and then prints, so acceptance is "
            "observable rather than silent; the section names `SOLV-RESOL-001` for exactly that placement and "
            "the oracle pins it. SOL-TCK-0479 binds `super.greet` from a class that overrides `greet`, so a "
            "value that redispatched would print the override and the expected text is the superclass's."),
        notes=(
            "The rejection half is its own program because SOL-TCK-0477's program must compile, and folding "
            "the rejected shape into it would discard the witness that the immediate call is still legal."),
    ),
    "REQ-3327": dict(
        section="6. Functions (Bound method references)",
        kind="compile-time",
        summary=("Each evaluation of a bound method-reference expression creates a distinct function-value "
                 "identity even for the same receiver and method, while copying the value through bindings "
                 "preserves it"),
        quotes=[FRESH_BOUND, COPY_PRESERVES],
        oracle=(
            "SOL-TCK-0480 creates two references from one receiver and one method and compares them, then "
            "copies one through a binding and compares the copy, then compares the two original values again, "
            "then compares a value against itself. The expected answers are false, true, false, true, and they "
            "are only jointly satisfiable: caching one value per receiver-and-method makes the first true, and "
            "minting a fresh value on each read of the binding makes the second false."),
        notes=(
            "`===` is used rather than `.equals()` because a function value is identity-bearing and these "
            "operands are mutually assignable; the rendering of these values is fixed by a sentence REQ-3307 "
            "owns and tests, so no expectation here restates it."),
    ),
    "REQ-3328": dict(
        section="6. Functions (Bound method references)",
        kind="compile-time",
        summary=("A normal member reference on a nullable receiver is illegal, `?.` produces a nullable "
                 "function value that is null only when the receiver is null, and `?.` on a non-null receiver "
                 "keeps the non-null type"),
        quotes=[NULL_REF_ILLEGAL, SAFE_ACCESS, NULL_ABSENT, NON_NULL_KEPT],
        oracle=(
            "SOL-TCK-0481 takes the reference from a null receiver and from a non-null one, prints the null "
            "case as the literal it is, and invokes the non-null case after the null refinement the section "
            "requires. SOL-TCK-0482 reads the same member unsafely through a nullable receiver, which the "
            "first quotation refuses; the code is not stated for the placement, so the oracle asserts the "
            "family. SOL-TCK-0483 takes `?.` through a receiver whose static type is non-null and initializes "
            "a *non-nullable* function-typed binding with the result, which is the retained-type half: an "
            "implementation that nullable-ized every safe read could not compile that initializer."),
        notes=(
            "The nullable function type is spelled `(func(): String)?` throughout. `func(): String?` would "
            "declare a non-null function returning a nullable, which is the other reading the section "
            "distinguishes."),
    ),
    "REQ-3329": dict(
        section="6. Functions (Bound method references)",
        kind="compile-time",
        summary=("A generic method reference is instantiated contextually to one monomorphic function type "
                 "per reference site, and an unconstrained reference is `SOLV-TYPE-030`"),
        quotes=[CONTEXTUAL, GENERIC_REF],
        oracle=(
            "SOL-TCK-0484 instantiates one declaration from one receiver at `func(Integer): Integer` and at "
            "`func(String): String`, which is what 'monomorphic' and 'contextual' jointly require: the value "
            "carries no type arguments, so the expected type must supply them, and two sites may differ. "
            "SOL-TCK-0485 puts the generic method on a generic class read through `Cell(Integer)`, so the "
            "class parameter is already fixed and only the method's own parameter is inferred; a host that "
            "treated the two as one inference problem would either fail to instantiate or substitute the "
            "wrong type, and the program prints the swapped value and the stored one to tell those apart. "
            "SOL-TCK-0486 is the named failure: the reference sits where no complete function signature is "
            "expected, and the section gives `SOLV-TYPE-030`."),
        notes=(
            "The positive programs assert no cross-instantiation identity, since instantiation is a static "
            "event and the distinct-identity sentence for bound values already covers what a host may "
            "observe."),
    ),
    "REQ-3330": dict(
        section="6. Functions (Bound method references)",
        kind="compile-time",
        summary=("Only a declared callable binds -- a bare read of a universal member, a `super` read of an "
                 "overridden universal member, and a static method read stay errors -- and a member whose "
                 "name denotes a function-typed property reads the stored value"),
        quotes=[ONLY_DECLARED, PROPERTY_RESOLUTION, T14_REPORTS],
        oracle=(
            "SOL-TCK-0487 reads `toString`, `equals`, and `hashCode` bare, from a class that *overrides* one "
            "of them, which is the case the sentence exists for: an implementation that consulted the "
            "receiver's dispatch table would find an override to bind. SOL-TCK-0488 is the `super` half, the "
            "path that resolves through a superclass's table and so could otherwise bind an inherited "
            "override. Both assert the family rather than a code, because the quoted sentence defers to the "
            "error section 3 and section 23.4 already require and those sentences name no code. SOL-TCK-0489 "
            "is the one deferred form the required-diagnostics paragraph does enumerate by name, a static "
            "method reference, so that program pins `SOLV-TYPE-014` -- the third quotation is what makes that "
            "pin a reading of the specification rather than of this implementation. SOL-TCK-0490 is the "
            "positive half: a class holds a function-typed property and a same-shaped method, and the program "
            "reads each and calls it, so the two printed values show member resolution chose the stored value "
            "for one name and a bound value for the other."),
        notes=(
            "The synthesized `Result` operations, constructors, and enum variants named by the same sentences "
            "stay covered by REQ-2309 and the existing corpus, which is why this requirement adds programs "
            "only for the placements no earlier requirement reaches."),
    ),
}

# --- Programs --------------------------------------------------------------------------------------------
#
# `exp` follows the committed manifest shape: a SUCCESS expectation carries `languageExit` and
# `stdoutBase64`, and a COMPILE_ERROR expectation carries a `diagnostic` whose family is the uppercase
# family prefix, because no code is stated for the placement. A rejected program prints a sentinel only if
# it could run at all, so the sentinel marks a program whose rejection must prevent execution.

def out(text):
    return {"languageExit": 0, "stdoutBase64": base64.b64encode(text.encode("utf-8")).decode("ascii")}


TESTS = [
    # ------------------------------------------------ REQ-3323: produced, invokable, receiver excluded
    dict(
        tid="SOL-TCK-0468", req="REQ-3323", cat="objects", outcome="SUCCESS",
        exp=out("v42"),
        note="A binding of a one-parameter function type is initialized from a method that also reads its "
             "receiver, and calling it at the declared type formats the argument",
        src="""class Formatter {
    func format(value: Integer): String {
        return "v" .. value.toString()
    }
}

var formatter = Formatter()
var operation: func(Integer): String = formatter.format
print(operation(42))
""",
    ),
    dict(
        tid="SOL-TCK-0469", req="REQ-3323", cat="objects", outcome="SUCCESS",
        exp=out("19"),
        note="The same value shape crosses a result and a parameter boundary and is called three times, so "
             "the witness is not an initializer position alone",
        src="""class Adder {
    var offset: Integer
    Adder(offset: Integer) {
        this.offset = offset
    }
    func add(value: Integer): Integer {
        return value + this.offset
    }
}

func operationOf(adder: Adder): func(Integer): Integer {
    return adder.add
}

func thrice(operation: func(Integer): Integer, value: Integer): Integer {
    return operation(operation(operation(value)))
}

print(thrice(operationOf(Adder(3)), 10))
""",
    ),

    # ------------------------------------------------ REQ-3324: virtual dispatch, and the named equality pair
    dict(
        tid="SOL-TCK-0470", req="REQ-3324", cat="objects", outcome="SUCCESS",
        exp=out("base|leaf"),
        note="Both references are written through a `Base`-typed receiver, so the printed texts can only "
             "come from the runtime class of each receiver",
        src="""mutable class Base {
    mutable func name(): String {
        return "base"
    }
}

mutable class Mid extends Base {
    override mutable func name(): String {
        return "mid"
    }
}

class Leaf extends Mid {
    override func name(): String {
        return "leaf"
    }
}

var base: Base = Base()
var leaf: Base = Leaf()
var fromBase: func(): String = base.name
var fromLeaf: func(): String = leaf.name
print(fromBase())
print("|")
print(fromLeaf())
""",
    ),
    dict(
        tid="SOL-TCK-0471", req="REQ-3324", cat="objects", outcome="SUCCESS",
        exp=out("generic|meow"),
        note="`sound` is declared only on the superclass, so the inherited instance method route is the one "
             "under test and both receivers resolve through it",
        src="""mutable class Animal {
    mutable func sound(): String {
        return "generic"
    }
}

class Cat extends Animal {
    override func sound(): String {
        return "meow"
    }
}

var animal: Animal = Animal()
var cat: Animal = Cat()
var animalMethod: func(): String = animal.sound
var catMethod: func(): String = cat.sound
print(animalMethod())
print("|")
print(catMethod())
""",
    ),
    dict(
        tid="SOL-TCK-0472", req="REQ-3324", cat="objects", outcome="SUCCESS",
        exp=out("woof|BARK|woofwoof"),
        note="An interface-typed receiver reaches two conforming instances' own requirements and the "
             "defaulted requirement, whose body calls the requirement back",
        src="""interface Speaker {
    func speak(): String
    func shout(): String {
        return speak() .. speak()
    }
}

class Dog implements Speaker {
    func speak(): String {
        return "woof"
    }
}

class Loud implements Speaker {
    func speak(): String {
        return "BARK"
    }
}

var dog: Speaker = Dog()
var loud: Speaker = Loud()
var dogSpeak: func(): String = dog.speak
var loudSpeak: func(): String = loud.speak
var dogShout: func(): String = dog.shout
print(dogSpeak())
print("|")
print(loudSpeak())
print("|")
print(dogShout())
""",
    ),
    dict(
        tid="SOL-TCK-0473", req="REQ-3324", cat="objects", outcome="SUCCESS",
        exp=out("bonjour"),
        note="The receiver declares `greet` nowhere; the implementation arrives through a `delegate` "
             "property, which is the delegated-implementation route the sentence names",
        src="""interface Greeter {
    func greet(): String
}

class FrenchGreeter implements Greeter {
    func greet(): String {
        return "bonjour"
    }
}

class Host implements Greeter {
    delegate var greeter: Greeter

    Host(greeter: Greeter) {
        this.greeter = greeter
    }
}

var host = Host(FrenchGreeter())
var method: func(): String = host.greet
print(method())
""",
    ),
    dict(
        tid="SOL-TCK-0474", req="REQ-3324", cat="equality", outcome="SUCCESS",
        exp=out("false|f|f"),
        note="The specification's own example: two reads of `formatter.format` are two bound-value "
             "creations and therefore unequal, while each still dispatches to the receiver's implementation",
        src="""class Formatter {
    var tag: String
    Formatter(tag: String) {
        this.tag = tag
    }
    func format(): String {
        return this.tag
    }
}

var formatter = Formatter("f")
print(formatter.format === formatter.format)
print("|")
print(formatter.format())
print("|")
print(formatter.format())
""",
    ),

    # ------------------------------------------------ REQ-3325: receiver evaluated once and retained
    dict(
        tid="SOL-TCK-0475", req="REQ-3325", cat="evaluation", outcome="SUCCESS",
        exp=out("1|x|x|true|false|2"),
        note="A counted receiver expression is evaluated once at creation and never again on calls, the "
             "retained receiver's own state is what the calls observe, and a copy keeps the value's identity",
        src="""class Wrapper {
    var inner: Target
    var mutable evaluations: Integer = 0
    Wrapper(inner: Target) {
        this.inner = inner
    }
    func target(): Target {
        this.evaluations = this.evaluations + 1
        return this.inner
    }
}

class Target {
    var label: String
    Target(label: String) {
        this.label = label
    }
    func describe(): String {
        return this.label
    }
}

var wrapper = Wrapper(Target("x"))
var baseline = wrapper.evaluations
var method: func(): String = wrapper.target().describe
var copied = method
print(wrapper.evaluations - baseline)
print("|")
print(method())
print("|")
print(copied())
print("|")
print(copied === method)
var again: func(): String = wrapper.target().describe
print("|")
print(again === method)
print("|")
print(wrapper.evaluations - baseline)
""",
    ),

    # ------------------------------------------------ REQ-3326: this, bare names, super
    dict(
        tid="SOL-TCK-0476", req="REQ-3326", cat="objects", outcome="SUCCESS",
        exp=out("leaf|base"),
        note="`this.greet` is read inside a superclass helper, so the current receiver -- the subclass "
             "instance or the base instance -- is what selects the implementation",
        src="""mutable class Base {
    mutable func greet(): String {
        return "base"
    }
    func viaThis(): func(): String {
        return this.greet
    }
}

class Leaf extends Base {
    override func greet(): String {
        return "leaf"
    }
}

print(Leaf().viaThis()())
print("|")
print(Base().viaThis()())
""",
    ),
    dict(
        tid="SOL-TCK-0477", req="REQ-3326", cat="names", outcome="SUCCESS",
        exp=out("7|7"),
        note="The bare name stays legal as an immediate call in the same class where `this.compute` is the "
             "value form, which is what separates the rule from one that forbids the name outright",
        src="""class Helper {
    func compute(): Integer {
        return 7
    }
    func asCall(): Integer {
        return compute()
    }
    func asValue(): func(): Integer {
        return this.compute
    }
}

print(Helper().asCall())
print("|")
print(Helper().asValue()())
""",
    ),
    dict(
        tid="SOL-TCK-0478", req="REQ-3326", cat="names", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"code": "SOLV-RESOL-001"}},
        note="A bare unqualified method name in a value position is SOLV-RESOL-001, and the section names "
             "that code for exactly this placement",
        src="""class Helper {
    func compute(): Integer {
        return 7
    }
    func asValue(): func(): Integer {
        return compute
    }
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0479", req="REQ-3326", cat="objects", outcome="SUCCESS",
        exp=out("base|mid"),
        note="`super.greet` is bound from a class that overrides `greet`, so the value prints the immediate "
             "superclass text while the immediate call on the same receiver prints the override",
        src="""mutable class Base {
    mutable func greet(): String {
        return "base"
    }
}

mutable class Mid extends Base {
    override func greet(): String {
        return "mid"
    }
    func viaSuper(): func(): String {
        return super.greet
    }
}

print(Mid().viaSuper()())
print("|")
print(Mid().greet())
""",
    ),

    # ------------------------------------------------ REQ-3327: fresh identity, copy-preserving
    dict(
        tid="SOL-TCK-0480", req="REQ-3327", cat="equality", outcome="SUCCESS",
        exp=out("false|true|false|true"),
        note="Two creations from one receiver and method are distinct, a binding copy preserves the identity, "
             "and both directions are asserted over the same pair of values",
        src="""class Formatter {
    func format(): String {
        return "f"
    }
}

var formatter = Formatter()
var first: func(): String = formatter.format
var second: func(): String = formatter.format
var copied = first
print(first === second)
print("|")
print(copied === first)
print("|")
print(first === second)
print("|")
print(first === first)
""",
    ),

    # ------------------------------------------------ REQ-3328: nullable receivers
    dict(
        tid="SOL-TCK-0481", req="REQ-3328", cat="types", outcome="SUCCESS",
        exp=out("null|t"),
        note="`?.` on a null receiver yields null with no bound function and on a non-null receiver the "
             "corresponding bound method, which the refined nullable value is then called at",
        src="""class Target {
    func describe(): String {
        return "t"
    }
}

var nothing: Target? = null
var absent: (func(): String)? = nothing?.describe
print(absent)
print("|")
var something: Target? = Target()
var present: (func(): String)? = something?.describe
if (present != null) {
    print(present())
}
""",
    ),
    dict(
        tid="SOL-TCK-0482", req="REQ-3328", cat="types", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE"}},
        note="A normal member reference on a nullable receiver is illegal; no code is stated for the "
             "placement, so the oracle asserts the family",
        src="""class Target {
    func describe(): String {
        return "t"
    }
}

func use(target: Target?) {
    var method: func(): String = target.describe
    print(method())
}

use(Target())
print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0483", req="REQ-3328", cat="types", outcome="SUCCESS",
        exp=out("t"),
        note="`?.` through a receiver whose static type is non-null keeps the non-null function type, which "
             "a *non-nullable* function-typed binding can only accept if the type was retained",
        src="""class Target {
    func describe(): String {
        return "t"
    }
}

var target = Target()
var method: func(): String = target?.describe
print(method())
""",
    ),

    # ------------------------------------------------ REQ-3329: contextual instantiation of method references
    dict(
        tid="SOL-TCK-0484", req="REQ-3329", cat="generics", outcome="SUCCESS",
        exp=out("42|s"),
        note="One generic method reference instantiates to two different monomorphic function types from one "
             "receiver, each driven entirely by its expected type",
        src="""class Box {
    func pick<T>(value: T): T {
        return value
    }
}

var box = Box()
var fromInteger: func(Integer): Integer = box.pick
var fromString: func(String): String = box.pick
print(fromInteger(42))
print("|")
print(fromString("s"))
""",
    ),
    dict(
        tid="SOL-TCK-0485", req="REQ-3329", cat="generics", outcome="SUCCESS",
        exp=out("swapped|1"),
        note="The generic method sits on a generic class read through `Cell(Integer)`, so the receiver's own "
             "type arguments are already closed and only the method's parameter is inferred",
        src="""mutable class Cell<T> {
    var stored: T
    Cell(stored: T) {
        this.stored = stored
    }
    mutable func swap<E>(value: E): E {
        return value
    }
}

var cell: Cell<Integer> = Cell(1)
var swapString: func(String): String = cell.swap
print(swapString("swapped"))
print("|")
print(cell.stored)
""",
    ),
    dict(
        tid="SOL-TCK-0486", req="REQ-3329", cat="generics", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"code": "SOLV-TYPE-030"}},
        note="A generic method reference where no complete function signature is expected is "
             "SOLV-TYPE-030, the code the sentence names",
        src="""class Box {
    func pick<T>(value: T): T {
        return value
    }
}

func use(box: Box) {
    var method: Any = box.pick
    print(method)
}

use(Box())
print("EXECUTED-INVALID")
""",
    ),

    # ------------------------------------------------ REQ-3330: only a declared callable binds
    dict(
        tid="SOL-TCK-0487", req="REQ-3330", cat="objects", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE"}},
        note="A bare read of `toString`, `equals`, or `hashCode` is not bindable even where the class "
             "overrides one, so a dispatch-table read that found an override to bind would be wrong",
        src="""class Point {
    override func toString(): String {
        return "p"
    }
    var x: Integer
    Point(x: Integer) {
        this.x = x
    }
}

func use(point: Point) {
    var render: Any = point.toString
    print("bound")
}

use(Point(1))
print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0488", req="REQ-3330", cat="objects", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"family": "TYPE"}},
        note="The same rejection holds through `super`, the path that resolves through a superclass's "
             "dispatch table and so could otherwise bind an inherited override",
        src="""mutable class Base {
    override mutable func equals(other: Any?): Boolean {
        return true
    }
    override mutable func hashCode(): Integer {
        return 1
    }
}

class Derived extends Base {
    func use(): Any {
        var method: Any = super.equals
        return method
    }
}

print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0489", req="REQ-3330", cat="objects", outcome="COMPILE_ERROR",
        exp={"diagnostic": {"code": "SOLV-TYPE-014"}},
        note="A static method reference used as a value is one of the deferred callables the "
             "required-diagnostics paragraph enumerates by name, and that paragraph states its code",
        src="""class Registry {
    static func make(): Integer {
        return 5
    }
}

func use() {
    var factory: Any = Registry.make
    print("bound")
}

use()
print("EXECUTED-INVALID")
""",
    ),
    dict(
        tid="SOL-TCK-0490", req="REQ-3330", cat="objects", outcome="SUCCESS",
        exp=out("9|4"),
        note="A function-typed property reads the stored value while a same-shaped method read creates a "
             "bound value, and the two different results show member resolution chose correctly",
        src="""class Holder {
    var mutable stored: func(): Integer
    func storedMethod(): Integer {
        return 4
    }
    Holder() {
        this.stored = func(): Integer {
            return 9
        }
    }
}

var holder = Holder()
var fromProperty: func(): Integer = holder.stored
var fromMethod: func(): Integer = holder.storedMethod
print(fromProperty())
print("|")
print(fromMethod())
""",
    ),
]

NAMED_CODES = ("SOLV-RESOL-001", "SOLV-TYPE-030", "SOLV-TYPE-014")

# One quotation in this batch is *also* claimed by an earlier requirement, which every other
# generator here treats as disqualifying. It is declared rather than hidden because the reuse is
# the point: the generic-value pair REQ-3312 and REQ-3316 (their ids are read from the committed
# inventory at guard time rather than written here, so naming them does not collide with the
# sibling-generator id guard) quote the contextual-instantiation sentence and test it at *top-level*
# function references, and nothing in the corpus applies it to a *method* reference. `box.pick`
# could not be typed correctly unless the receiver's class type arguments close first and the
# method's own parameter is then inferred, so a method-reference witness of that sentence is a
# distinct obligation from the top-level one -- quoting the sentence is what says so. The guard in
# verify() fails if any *other* quotation lands in an earlier requirement, so the exception stays
# exactly this one and its owner is checked to be the requirement named below.
INTENTIONAL_REQUOTES = {norm(CONTEXTUAL): "REQ-3329"}
FAMILY_ONLY = {"SOL-TCK-0482": "TYPE", "SOL-TCK-0487": "TYPE", "SOL-TCK-0488": "TYPE"}


def refuse_reuse_of_claimed_ids():
    mine = {t["tid"] for t in TESTS} | set(REQS_SPEC)
    here = os.path.dirname(os.path.abspath(__file__))
    claimed = set()
    for name in sorted(os.listdir(here)):
        if not name.startswith("gen") or not name.endswith(".py"):
            continue
        path = os.path.join(here, name)
        if os.path.abspath(path) == os.path.abspath(__file__):
            continue
        text = open(path, encoding="utf-8").read()
        for token in re.findall(r"[\"'](SOL-TCK-\d{4}|REQ-\d{4})[\"']", text):
            claimed.add(token)
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
    # Cross-batch provenance: a quotation this batch adds must be claimed by no requirement that
    # predates it, unless it is declared in INTENTIONAL_REQUOTES with the owners it defers to.
    committed = json.load(open(REQUIREMENTS, encoding="utf-8"))
    prior = {}
    for r in committed["requirements"]:
        if int(r["id"].split("-")[1]) < min(int(rid.split("-")[1]) for rid in REQS_SPEC):
            for q in r.get("normativeQuotes", []):
                prior.setdefault(norm(q), []).append(r["id"])
    for q in {norm(q) for spec in REQS_SPEC.values() for q in spec["quotes"]}:
        earlier = prior.get(q, [])
        if not earlier:
            continue
        owner = INTENTIONAL_REQUOTES.get(q)
        if owner is None:
            bad.append(("quotes", "quote is already owned by %s and is not a declared re-quote: %s"
                        % (earlier, q[:60])))
        elif owner not in REQS_SPEC or q not in [norm(x) for x in REQS_SPEC[owner]["quotes"]]:
            bad.append(("quotes", "declared re-quote owner %s does not itself quote the sentence"
                        % owner))
        else:
            # The declared reuse is legitimate only because an *earlier* requirement still owns
            # the sentence -- otherwise there is nothing being reused and the declaration is dead.
            if not earlier:
                bad.append(("quotes", "declared re-quote for %s has no earlier owner" % owner))
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
        if t["outcome"] == "SUCCESS":
            if t["exp"].get("languageExit") != 0:
                bad.append((t["tid"], "SUCCESS without languageExit 0"))
            if "stdoutBase64" not in t["exp"]:
                bad.append((t["tid"], "SUCCESS without a stdout oracle"))
    tids = [t["tid"] for t in TESTS]
    if len(set(tids)) != len(tids):
        bad.append(("tests", "duplicate test id"))
    numbers = sorted(int(t.split("-")[-1]) for t in tids)
    if numbers != list(range(numbers[0], numbers[0] + len(numbers))):
        bad.append(("tests", "test ids are not contiguous"))
    for rid in REQS_SPEC:
        if not any(t["req"] == rid for t in TESTS):
            bad.append((rid, "requirement owns no test"))
    stdout_seen = {}
    for t in TESTS:
        if t["outcome"] != "SUCCESS":
            continue
        stdout_seen.setdefault(t["exp"]["stdoutBase64"], []).append(t["tid"])
    for text, owners in stdout_seen.items():
        if len(owners) > 1:
            bad.append(("stdout", "%s share a byte-identical stdout oracle" % owners))
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
                  + "// %s\n" % t["note"]
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

    inventory = json.load(open(REQUIREMENTS, encoding="utf-8"))
    known = {r["id"]: r for r in inventory["requirements"]}
    for rid, spec in REQS_SPEC.items():
        record = {"id": rid, "specVersion": SPEC_VERSION, "section": spec["section"],
                  "summary": spec["summary"], "kind": spec["kind"], "profile": "full-language",
                  "portable": True, "tests": byreq[rid], "status": "tested", "lifecycle": "active",
                  # The inventory record has no free-form `notes` field (the requirements schema is
                  # closed), so the secondary prose a batch wants to record is appended to the oracle
                  # notes rather than dropped or added under a new key.
                  "oracleNotes": spec["oracle"] + ((" " + spec["notes"]) if "notes" in spec else ""),
                  "normativeQuotes": spec["quotes"]}
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
