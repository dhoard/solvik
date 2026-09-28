#!/usr/bin/env python3
"""Record the normative obligations the TCK cannot exercise.

TCK.md section 5.1 requires every requirement to belong to exactly one state,
including requirements the normative specification states but no portable test
can exercise. This tool writes those records with `status` set to
`untested-ambiguous`, `untested-platform`, or `untested-portable` and an empty
`tests` list, so `validate` reports them as coverage gaps rather than silently
omitting them.

  * section 10 is a runtime-representation design statement with no language
    observable (ambiguous);
  * section 19 is a design-time feature-priority ordering (ambiguous);
  * section 20's RESOL-010 I/O failure, `~/` home expansion, and absolute-path
    resolution depend on host facilities (platform);
  * the local-variable form of definite initialization is portable in principle
    but is not expressible against the current grammar, which requires an
    initializer on a local declaration (portable-but-blocked).

These are recorded in the full-language profile with an empty `tests` list, so the full
profile names every normative obligation while `validate` reports the coverage gap: a
requirement that no test exercises must block full-profile conformance, not vanish from
the profile. The record's `status` still distinguishes whether the obstacle is ambiguity
(no observable), a platform facility, or a current implementation limitation.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPEC = open(os.path.join(ROOT, "docs/LANGUAGE_SPEC.md"), encoding="utf-8").read()
REQUIREMENTS = os.path.join(ROOT, "tck/requirements/requirements.json")
PROFILE = os.path.join(ROOT, "tck/profiles/full-language.profile.json")
SPEC_VERSION = "2026.10-draft"


def norm(t):
    t = (t.replace("\u2019", "'").replace("\u201c", '"').replace("\u201d", '"')
         .replace("\u2014", "--").replace("\u2013", "-").replace("\u00a0", " "))
    return re.sub(r"\s+", " ", t.replace("`", "").replace("*", "")).strip()


SPEC_N = norm(SPEC)

UNEXERCISED = [
    dict(
        id="REQ-2800",
        section="10. Built-in Types and Runtime Representation",
        summary="The runtime may specialize language-level primitive types to efficient primitive "
                "representations, and the object model must not force unnecessary boxing",
        kind="runtime",
        status="untested-ambiguous",
        portable=False,
        profile="full-language",
        rationale="A performance and representation choice with no language-level observable. The "
                  "specification deliberately leaves the representation to the runtime, so no "
                  "guest program can distinguish a specialized primitive from a boxed one; asserting "
                  "either would invent an observable the specification does not define.",
        quotes=["Language-level primitive types are class types conceptually, but the runtime may "
                "specialize them to efficient JVM/Truffle primitive representations.",
                "The language object model must not force unnecessary boxing."],
    ),
    dict(
        id="REQ-2801",
        section="19. Semantic Priorities",
        summary="When language features conflict, the implementation prefers compile-time "
                "correctness, deterministic syntax, explicit semantics, safe defaults, "
                "understandable diagnostics, runtime performance, then syntactic convenience",
        kind="compile-time",
        status="untested-ambiguous",
        portable=False,
        profile="full-language",
        rationale="A design-time tie-break ordering for the implementer, not a rule that constrains "
                  "guest program behavior. Where two designs both satisfy the specification there is "
                  "no observable to assert; the ordering guides choices the specification otherwise "
                  "leaves open.",
        quotes=["When language features conflict, prefer:",
                "1. compile-time correctness;"],
    ),
    dict(
        id="REQ-2802",
        section="20. File Inclusion",
        summary="Denied access and other include I/O failures are `SOLV-RESOL-010` and never escape "
                "as host errors",
        kind="compile-time",
        status="untested-platform",
        portable=False,
        profile="full-language",
        rationale="Producing an I/O failure portably requires denying filesystem access, a host "
                  "facility the portable corpus cannot assume. The rule is recorded so the gap is "
                  "visible; a platform profile that declares the access-control facility can test "
                  "it. The code is specification-named (SOLV-RESOL-010).",
        quotes=["Denied access and other I/O failures become `SOLV-RESOL-010` and never escape as "
                "host errors.",
                "| `RESOL_INCLUDE_IO` | `SOLV-RESOL-010` | include directive |"],
    ),
    dict(
        id="REQ-2803",
        section="20. File Inclusion",
        summary="An include path beginning with the exact prefix `~/` is expanded against the host "
                "user's home directory, and a later `~` or leading `~name` is ordinary path text",
        kind="module",
        status="untested-platform",
        portable=False,
        profile="full-language",
        rationale="Depends on the host user's home directory, an environment-specific value the "
                  "portable corpus cannot declare consistently. The rule is recorded as a "
                  "platform-profile obligation rather than tested with a fixture that would embed "
                  "one host's home path.",
        quotes=["If `P` begins with the exact prefix `~/`, replace that prefix with the host user's "
                "home directory and treat the result as absolute. A later `~` and a leading `~name` "
                "are ordinary path text."],
    ),
    dict(
        id="REQ-2804",
        section="20. File Inclusion",
        summary="An absolute expanded include path is used directly rather than resolved against "
                "the including file",
        kind="module",
        status="untested-platform",
        portable=False,
        profile="full-language",
        rationale="An absolute include path names a host-specific location; the portable corpus "
                  "stages into a runner-created workspace whose absolute path the program cannot "
                  "know portably. Recorded as a platform obligation.",
        quotes=["An absolute expanded path is used directly."],
    ),
    dict(
        id="REQ-2805",
        section="6. Functions",
        summary="A local variable must be definitely initialized before it is read",
        kind="compile-time",
        status="untested-portable",
        portable=True,
        profile="full-language",
        rationale="Portable in principle, but the current grammar requires an initializer on every "
                  "local declaration, so a program cannot declare an uninitialized local and the "
                  "control-flow half of the rule cannot be reached. The property form of the same "
                  "obligation is covered by REQ-2211. Recorded as a portable gap so the "
                  "implementation limitation stays visible and blocks full-profile conformance.",
        quotes=["A local variable must be definitely initialized before it is read."],
    ),
    dict(
        id="REQ-2806",
        section="1. Design Goals (Versioning)",
        summary="A new specification revision is declared only when the normative semantics change, "
                "released revisions are immutable, and pre-1.0 conformance reports must state "
                "that certification is withheld",
        kind="process",
        status="untested-ambiguous",
        portable=False,
        profile="full-language",
        rationale="A conformance-process rule, not a guest-language observable. It is enforced by "
                  "the TCK itself (the four independent version identifiers, the immutable-input "
                  "digest binding, and the empty CERTIFIABLE_SPEC_VERSIONS list that withholds "
                  "certification for a draft baseline), so no guest program can exercise it.",
        quotes=["A new specification revision is declared only when the normative semantics change; "
                "released revisions are immutable.",
                "Until this baseline is declared stable (>= 1.0), full-language conformance reports "
                "must state that certification is withheld against a pre-1.0 specification."],
    ),
    dict(
        id="REQ-2807",
        section="1. Design Goals",
        summary="Features explicitly marked deferred are not part of the language, and an "
                "implementation must not invent semantics for a deferred or unspecified feature",
        kind="process",
        status="untested-ambiguous",
        portable=False,
        profile="full-language",
        rationale="A blanket policy over many deferred features rather than one observable. Each "
                  "deferred feature with a definite spelling is tested where the specification "
                  "gives one (for example the deferred safe cast in REQ-3201 and the deferred "
                  "string interpolation in REQ-0402); the residual policy has no single program "
                  "that exercises it.",
        quotes=["Features explicitly marked deferred are not part of the language until this "
                "document defines them.",
                "An implementation must not invent semantics for a deferred or unspecified feature."],
    ),
    dict(
        id="REQ-2808",
        section="3. Equality and reference identity",
        summary="A user equals implementation must be reflexive, symmetric, transitive, consistent, "
                "and false for null, but the compiler and runtime do not prove or repair those "
                "properties",
        kind="runtime",
        status="untested-ambiguous",
        portable=False,
        profile="full-language",
        rationale="An obligation on user implementations that the specification explicitly says the "
                  "compiler and runtime do not enforce. There is no conformance verdict to assert: "
                  "a violating override must be accepted, which the equality-dispatch tests "
                  "(REQ-1706) already observe, and the contract itself is not machine-checkable "
                  "from a single program.",
        quotes=["A user `equals` implementation must be reflexive, symmetric, transitive, "
                "consistent while equality-relevant state is unchanged, and false for `null`.",
                "The compiler and runtime do not prove or repair these properties."],
    ),
    dict(
        id="REQ-2809",
        section="21.7 Result types",
        summary="A set of value-producing branches whose only shared supertypes are incomparable "
                "has no single nearest result and is a compile-time error",
        kind="compile-time",
        status="untested-ambiguous",
        portable=False,
        profile="full-language",
        rationale="The rule is unreachable in the presence of the `Any` top type. Because every "
                  "class and interface has `Any` as a declared supertype and `Any` is comparable to "
                  "itself, any branch set -- including `if (c) { A() } else { B() }` over two "
                  "unrelated interface-implementing classes -- has at least one comparable shared "
                  "supertype and joins at `Any`; the specification's own example "
                  "(`if (flag) { 1 } else { \"text\" }` typed `Any`) confirms the accept side. No "
                  "program reaches the stated error, so the failure clause cannot be asserted, and "
                  "recording a guess at what would make supertypes 'incomparable' under a top type "
                  "would fabricate a rule the document does not define.",
        quotes=["A set of branches whose only shared supertypes are incomparable has no single "
                "nearest result and is a compile-time error."],
    ),
    dict(
        id="REQ-2810",
        section="15. Strings (Rust-style raw strings)",
        summary="An unterminated raw string is a lexical error at its opening delimiter, and its "
                "diagnostic must show the exact closing delimiter that was expected",
        kind="lexical",
        status="untested-ambiguous",
        portable=False,
        profile="full-language",
        rationale="The unterminated-raw-string rejection is covered (REQ-0404 asserts the lexical "
                  "rejection), but the residual clause -- that the diagnostic *text* must show the "
                  "exact expected closing delimiter -- is not assertable by a portable oracle. The "
                  "oracle contract compares exit status and `stdout` only, and `stderr` diagnostic "
                  "wording is deliberately unconstrained (the differential runner reports it as an "
                  "unconstrained observable, never a disagreement), so no portable expectation can "
                  "pin diagnostic prose without adopting implementation-specific text.",
        quotes=["An unterminated raw string is a lexical error at its opening delimiter.",
                "The diagnostic must show the exact closing delimiter that was expected."],
    ),
]


def main():
    data = json.load(open(REQUIREMENTS, encoding="utf-8"))
    have = {r["id"]: r for r in data["requirements"]}
    for spec in UNEXERCISED:
        for q in spec["quotes"]:
            if norm(q) not in SPEC_N:
                print("REQ quote not verbatim: %r" % q[:90])
                return 1
        record = {
            "id": spec["id"], "specVersion": SPEC_VERSION, "section": spec["section"],
            "summary": spec["summary"], "kind": spec["kind"], "profile": spec["profile"],
            "portable": spec["portable"], "tests": [], "status": spec["status"],
            "lifecycle": "active", "rationale": spec["rationale"],
            "oracleNotes": spec["rationale"], "normativeQuotes": spec["quotes"]}
        if spec["id"] in have:
            if have[spec["id"]] != record:
                print("committed %s differs from this tool's record" % spec["id"])
                return 1
            continue
        data["requirements"].append(record)
    data["requirements"].sort(key=lambda r: r["id"])
    with open(REQUIREMENTS, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2)
        handle.write("\n")

    profile = json.load(open(PROFILE, encoding="utf-8"))
    profile["requirements"] = sorted(set(profile["requirements"]) |
                                     {s["id"] for s in UNEXERCISED})
    with open(PROFILE, "w", encoding="utf-8") as handle:
        json.dump(profile, handle, indent=2)
        handle.write("\n")

    print("recorded %d unexercised requirement(s): %s"
          % (len(UNEXERCISED), ", ".join(s["id"] for s in UNEXERCISED)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
