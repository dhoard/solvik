"""Differential comparison of two adapters' raw observations (TCK.md section 15).

Differential mode runs identical portable programs through two or more adapters and
reports disagreements in compilation acceptance, diagnostics, stdout, stderr, exit
behavior, and runtime failures.

Two rules dominate this module, both stated by TCK.md section 15 and section 6.1:

* **Agreement is evidence, never a verdict.** Differential results "must never update
  expected results automatically or override the normative oracle", and agreement
  "may supplement direct oracles; none may replace one when the specification defines
  an exact result." Two implementations that share a bug agree. This module therefore
  reports disagreements and nothing else, and never reports a PASS.
* **Only manifest-authorized normalization is applied.** The single authorized
  transform is the protocol's `platform-line-separator` mapping, applied to a field
  only when the manifest declares it for that field. Nothing else -- no trimming,
  reordering, timing, or path elision -- is representable, because silent
  normalization is precisely how a real difference comes to be reported as agreement.

A test on which either adapter failed to produce a language result is reported
`inconclusive` rather than as agreement or disagreement: an infrastructure error,
crash, or refusal to run is the *absence* of an observation, and comparing absences
manufactures agreement where nothing was observed.

Whether a side produced a language result is decided from the **raw protocol status**, never
from the runner's judged verdict. The judged verdict answers "did this implementation
satisfy this oracle", so an honest refusal to run an unsupported program is judged `FAIL` --
the same value a genuine non-conformance produces. Classifying on it would turn every test an
incomplete adapter declines into a reported disagreement between the two implementations,
which is the opposite of what an absence of observation means.

A field is compared only when the manifest declares an expectation for it, because that
declaration is what makes the field's bytes normative. An undeclared field can still differ
between two implementations that both satisfy the oracle, and the specification does not make
such a difference non-conformant; it is reported separately as `unconstrained` information and
never counted as a disagreement. Diagnostic wording is likewise never normative, so it is not
compared at all -- only the structured family/code/byte-span projection.
"""

import base64
import hashlib
import os

# The only transform the protocol permits.
PLATFORM_LINE_SEPARATOR = "platform-line-separator"


def normalize_output(raw, field, manifest):
    """Apply only the manifest-declared transforms to observed bytes for `field`.

    Bytes in, bytes out: a comparison must not depend on an encoding decision made
    here. `field` is "stdout" or "stderr" -- the names the manifest uses.
    """
    for entry in manifest.get("normalization", []):
        if entry["field"] != field:
            continue
        if entry["transform"] != PLATFORM_LINE_SEPARATOR:
            # A manifest that validated against the manifest schema cannot reach here,
            # because that schema enumerates the transform. The branch exists so that
            # widening the schema cannot silently leave a transform unimplemented and
            # thereby turn a difference into agreement.
            raise ValueError("unsupported normalization transform: %r" % entry["transform"])
        sep = os.linesep.encode("utf-8")
        if sep and sep != b"\n":
            raw = raw.replace(sep, b"\n")
    return raw


def _decode(observation, key):
    value = observation.get(key) or ""
    if not value:
        return b""
    return base64.b64decode(value, validate=True)


def _diag_set(observation):
    out = []
    for d in observation.get("diagnostics", []):
        out.append((d.get("family"), d.get("code"),
                    d.get("startByteOffset"), d.get("endByteOffset")))
    return sorted(out)


# Statuses that constitute a verdict about the *program* rather than about the
# adapter. `IMPLEMENTATION_FAILURE` is deliberately absent: it is the adapter
# reporting its own inability, which carries no information about the program.
LANGUAGE_EXECUTE_STATUSES = ("NORMAL_EXIT", "RUNTIME_FAILURE")

# Protocol field name per normative field name, for deciding what an oracle declares.
_EXPECTED_FIELDS = {
    "stdout": ("stdoutBase64", "stdoutText"),
    "stderr": ("stderrBase64", "stderrText"),
    "languageExit": ("languageExit",),
    "runtimeCategory": ("runtimeCategory",),
    "diagnostics": ("diagnostic",),
}

# The execute status each outcome obliges the implementation to produce. `executeStatus`
# is normative but is never an `expectation` key, because the manifest schema encodes it in
# `outcome` instead: SUCCESS requires NORMAL_EXIT, RUNTIME_ERROR requires RUNTIME_FAILURE,
# and the two compile-only outcomes forbid execution altogether -- where a difference in
# execute status is a difference in how two implementations were *wrong*, not a
# specification-level divergence, and is reported as unconstrained rather than suppressed.
_OUTCOME_EXECUTE_STATUS = {"SUCCESS": "NORMAL_EXIT", "RUNTIME_ERROR": "RUNTIME_FAILURE"}


def _is_declared(manifest, field):
    """True when the oracle constrains `field`, making its value normative.

    Mirrors `outcome._output_matches`, which asserts an output field "only if declared":
    the spec leaves undeclared observables (notably diagnostic and diagnostic-adjacent
    wording such as launcher warnings) unconstrained, so two conforming implementations
    may differ there. Treating such a difference as a disagreement would bury real
    divergences under harmless host noise.

    A test with no manifest available is treated as declaring *everything*. A missing
    manifest is an input the caller could not interpret, and silently classifying every
    observable as unconstrained would turn an absent oracle into reported agreement.
    """
    if not manifest:
        return True
    expectation = manifest.get("expectation", {})
    if field == "diagnostics":
        # A COMPILE_ERROR oracle always declares its expected diagnostic, and only that
        # outcome declares one.
        return "diagnostic" in expectation
    if field == "executeStatus":
        return manifest.get("outcome") in _OUTCOME_EXECUTE_STATUS
    return any(key in expectation for key in _EXPECTED_FIELDS[field])


def exit_code(counts):
    """Exit status for a differential run: 0 no disagreement, 1 disagreement, 2 vacuous.

    `2` when nothing at all could be compared. "No disagreements found" over zero compared
    tests is a vacuous result, and returning success for it is exactly how an empty or
    wholly-refusing pair of adapters would come to look like a clean differential run.
    """
    if counts["disagreements"]:
        return 1
    if counts["compared"] == 0:
        return 2
    return 0


def _digest(observation, key, field, manifest):
    """Fixed-size digest of a normalized output field, or None when the phase did not run.

    Digests rather than bytes: a report must stay bounded and must not launder captured
    guest output into a published artifact, but a reader still needs to tell "these two
    differed" from "these two were equal" without rerunning anything.

    No execute status means the phase never ran, and the runner records empty strings for
    its fields in that case. Those must not digest to the same value as a program that ran
    and printed nothing, which would turn "did not execute" into observed agreement on an
    empty output.
    """
    if observation.get("executeStatus") is None:
        return None
    return hashlib.sha256(normalize_output(_decode(observation, key), field, manifest)
                          ).hexdigest()


def _summary(name, observation, manifest):
    return {"adapter": name,
            "compileStatus": observation.get("compileStatus"),
            "executeStatus": observation.get("executeStatus"),
            "languageExit": observation.get("languageExit"),
            "runtimeCategory": observation.get("runtimeCategory"),
            "diagnostics": _diag_set(observation),
            "stdoutSha256": _digest(observation, "stdoutBase64", "stdout", manifest),
            "stderrSha256": _digest(observation, "stderrBase64", "stderr", manifest),
            "infrastructureEvents": list(observation.get("infrastructureEvents") or [])}


def compare_observations(test_id, left_name, left, right_name, right, manifest):
    """Return a record of how two adapters' observations for one test differ.

    `left` / `right` are the observation dicts the runner records per test. `axes`
    names each dimension on which they disagree and is empty exactly when the two
    were observationally identical; `inconclusive` is true when either side produced
    no language result to compare.
    """
    def usable(side):
        # A *language* verdict, decided from the raw protocol status and not from the
        # runner's judged verdict: see the module docstring. `IMPLEMENTATION_FAILURE`
        # is the adapter reporting about itself, not about the program, so it is not a
        # verdict on the program at all. A program rejected at compile time is a
        # complete verdict, because `execute` is illegal after a rejected compile.
        compile_status = side.get("compileStatus")
        if compile_status == "COMPILE_REJECTED":
            return True
        if compile_status != "COMPILE_ACCEPTED":
            return False
        return side.get("executeStatus") in LANGUAGE_EXECUTE_STATUSES

    left_ok, right_ok = usable(left), usable(right)
    inconclusive = not (left_ok and right_ok)

    def done(axes, unconstrained=()):
        return {"testId": test_id, "axes": axes, "unconstrained": list(unconstrained),
                "inconclusive": inconclusive,
                "left": _summary(left_name, left, manifest),
                "right": _summary(right_name, right, manifest)}

    # No language result on either side: there is nothing to compare, so no axis may be
    # reported even if the raw statuses differ. One side refusing and the other crashing
    # are two absences, and calling that a disagreement would report the two
    # implementations as diverging when in fact neither answered.
    if not left_ok or not right_ok:
        return done([])

    # Compilation acceptance is compared first. If the implementations disagree about
    # whether a program is legal, every later observable is meaningless, and listing
    # stdout differences on top of that would obscure the divergence that matters.
    if left.get("compileStatus") != right.get("compileStatus"):
        return done(["compileAcceptance"])

    axes = []
    unconstrained = []

    def classify(field, differs):
        if not differs:
            return
        (axes if _is_declared(manifest, field) else unconstrained).append(field)

    classify("stdout", normalize_output(_decode(left, "stdoutBase64"), "stdout", manifest) !=
             normalize_output(_decode(right, "stdoutBase64"), "stdout", manifest))
    classify("stderr", normalize_output(_decode(left, "stderrBase64"), "stderr", manifest) !=
             normalize_output(_decode(right, "stderrBase64"), "stderr", manifest))
    classify("languageExit", left.get("languageExit") != right.get("languageExit"))
    classify("executeStatus", left.get("executeStatus") != right.get("executeStatus"))
    classify("runtimeCategory", left.get("runtimeCategory") != right.get("runtimeCategory"))

    # Diagnostics are compared only when both sides rejected the program, and only by
    # the closed structured projection the protocol carries: family, code, byte span.
    # Message text is excluded because the protocol does not make wording normative, so
    # differing phrasing is not a conformance-relevant divergence. For a COMPILE_ERROR
    # oracle the expected diagnostic is declared, so a difference there is normative;
    # for any other outcome both sides rejected a program the oracle does not expect
    # either of them to reject, which is already a shared non-conformance rather than a
    # disagreement between the two implementations.
    both_rejected = left.get("compileStatus") == "COMPILE_REJECTED"
    if both_rejected and _diag_set(left) != _diag_set(right):
        classify("diagnostics", True)

    return done(axes, unconstrained)


def compare_suites(left_name, left_obs, right_name, right_obs, manifests):
    """Compare two adapters across every test either was asked to run.

    `manifests` maps testId to the loaded manifest. It is used only to apply
    manifest-authorized normalization and to decide which observables the oracle makes
    normative -- never to decide whether a test passes or fails.
    """
    ids = sorted(set(left_obs) | set(right_obs))
    records = []
    for tid in ids:
        if tid not in left_obs or tid not in right_obs:
            # One adapter never recorded this test (filtered out, or lost before a
            # result). That is an absence of observation, not a disagreement.
            records.append({"testId": tid, "axes": [], "inconclusive": True,
                            "left": {"adapter": left_name, "present": tid in left_obs},
                            "right": {"adapter": right_name, "present": tid in right_obs}})
            continue
        records.append(compare_observations(
            tid, left_name, left_obs[tid], right_name, right_obs[tid],
            manifests.get(tid, {})))
    counts = {
        "tests": len(ids),
        "compared": sum(1 for r in records if not r["inconclusive"]),
        "disagreements": sum(1 for r in records if r["axes"]),
        "inconclusive": sum(1 for r in records if r["inconclusive"]),
        # Differences on observables the oracle does not constrain. Reported so that a
        # reader can see them, and excluded from `disagreements` because the spec does not
        # make them non-conformant.
        "unconstrained": sum(1 for r in records if r.get("unconstrained")),
    }
    return {"left": left_name, "right": right_name, "counts": counts, "results": records}
