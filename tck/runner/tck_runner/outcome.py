"""The outcome-matching state machine (TCK.md section 8.1).

Pure function of (manifest, phase results, infrastructure events) -> decision.
Keeping it free of process/IO concerns lets the runner self-tests drive every
path -- including the adversarial ones -- without a real adapter.

Exact phase order:

  1. describe preflight selects compatible tests (handled by the runner);
  2. per-test describe must match frozen preflight identity (``describe_matches``);
  3. stage + hash fixtures (runner);
  4. compile under the compilation timeout;
  5. COMPILE_SUCCESS / COMPILE_ERROR stop without execute;
  6. SUCCESS / RUNTIME_ERROR require success THEN execute;
  7. compare structured observables.

Any invalid transition is INFRASTRUCTURE_ERROR. A compile error may never satisfy
RUNTIME_ERROR; a crash, timeout, malformed message, or OS failure may never
satisfy a language error expectation.
"""

from __future__ import annotations

import base64

from . import protocol

INFRA = protocol.INFRA
FAIL = protocol.FAIL
PASS = protocol.PASS


class Decision:
    def __init__(self, status, reason, details=None):
        self.status = status
        self.reason = reason
        self.details = details or {}

    def __repr__(self):
        return "Decision(%s, %r)" % (self.status, self.reason)


def describe_matches(preflight, per_test) -> bool:
    """Per-test describe metadata must match the frozen preflight exactly."""
    for key in ("name", "version", "fingerprint", "specVersions", "profiles", "capabilities"):
        if preflight.get(key) != per_test.get(key):
            return False
    if preflight.get("limits") != per_test.get("limits"):
        return False
    return True


def _b64_matches(field, expected_b64):
    """Compare captured output bytes against an expected base64 field."""
    actual = protocol.decode_b64(field) if field else b""
    expected = base64.b64decode(expected_b64, validate=True) if expected_b64 else b""
    return actual == expected


def _apply_normalization(value: bytes, field, manifest) -> bytes:
    """Apply only the closed, manifest-declared transformations (section 7).

    The only supported transform is ``platform-line-separator`` applied to the
    named field: it maps the platform line separator to ``\\n`` so that an exact
    byte expectation can be written portably for ``println``. Arbitrary regex,
    trimming, path elision, or reordering are not representable here.
    """
    for entry in manifest.get("normalization", []):
        if entry["field"] != field:
            continue
        transform = entry["transform"]
        if transform == "platform-line-separator":
            import os

            sep = os.linesep.encode("utf-8")
            if sep and sep != b"\n":
                value = value.replace(sep, b"\n")
    return value


def judge(manifest, compile_result, execute_result, *, infra_events=None, executed=False):
    """Return a :class:`Decision` for a completed (or aborted) test attempt.

    ``compile_result`` / ``execute_result`` are validated protocol response dicts
    or ``None`` when that phase did not run. ``infra_events`` is a list of
    infrastructure events that occurred (see ``events.py``). ``executed`` records
    whether the adapter reported that application code ran.
    """
    infra_events = infra_events or []
    outcome = manifest["outcome"]

    # Infrastructure precedence: any infra event (process loss, timeout, protocol
    # violation) prevents a conformance judgment.
    if infra_events:
        kinds = sorted({e["kind"] for e in infra_events})
        return Decision(INFRA, "infrastructure events: %s" % ",".join(kinds),
                        {"events": infra_events})

    exp = manifest["expectation"]

    # --- COMPILE_ERROR: passes only on a structured source rejection from compile
    if outcome == "COMPILE_ERROR":
        if compile_result is None or compile_result.get("status") != "COMPILE_REJECTED":
            return Decision(FAIL, "COMPILE_ERROR expects a structured COMPILE_REJECTED from compile",
                            {"compile": compile_result})
        if not _diagnostic_matches(compile_result, exp["diagnostic"]):
            return Decision(FAIL, "compile diagnostic does not match expected code/family/location",
                            {"diagnostics": compile_result.get("diagnostics")})
        if executed:
            return Decision(FAIL, "COMPILE_ERROR test executed application code")
        return Decision(PASS, "compile rejected with matching diagnostic")

    # --- COMPILE_SUCCESS: success compile-only, zero application observables
    if outcome == "COMPILE_SUCCESS":
        if compile_result is None or compile_result.get("status") != "COMPILE_ACCEPTED":
            return Decision(FAIL, "COMPILE_SUCCESS expects COMPILE_ACCEPTED from compile",
                            {"compile": compile_result})
        # The compile response schema forbids stdout; if any observable appears it
        # is an infrastructure/protocol violation caught upstream. Execution is
        # forbidden for this outcome; executing is an invalid transition.
        if executed:
            return Decision(FAIL, "COMPILE_SUCCESS test executed application code")
        return Decision(PASS, "compile-only validation succeeded with no application observables")

    # --- SUCCESS and RUNTIME_ERROR require successful compile THEN execute.
    if compile_result is None or compile_result.get("status") != "COMPILE_ACCEPTED":
        # A compile error can never satisfy a runtime or success expectation.
        if outcome == "RUNTIME_ERROR":
            return Decision(FAIL, "RUNTIME_ERROR cannot be satisfied by a compile failure")
        return Decision(FAIL, "SUCCESS requires successful compilation before execution",
                        {"compile": compile_result})
    if execute_result is None:
        return Decision(INFRA, "execution phase did not run after successful compilation")

    status = execute_result.get("status")

    if outcome == "SUCCESS":
        if status != "NORMAL_EXIT":
            # A runtime failure / crash must not satisfy SUCCESS.
            if status == "IMPLEMENTATION_FAILURE":
                return Decision(FAIL, "implementation crash/internal failure cannot satisfy SUCCESS")
            return Decision(FAIL, "SUCCESS expects NORMAL_EXIT, got %r" % status)
        if not _language_exit_matches(execute_result, exp["languageExit"]):
            return Decision(FAIL, "language exit status mismatch",
                            {"expected": exp["languageExit"],
                             "actual": execute_result.get("languageExit")})
        if not _output_matches(execute_result, "stdoutBase64", "stdoutText", "stdout", exp, manifest):
            return Decision(FAIL, "stdout mismatch",
                            {"expected_base64": exp.get("stdoutBase64")})
        if not _output_matches(execute_result, "stderrBase64", "stderrText", "stderr", exp, manifest):
            return Decision(FAIL, "stderr mismatch (only if declared)")
        return Decision(PASS, "normal completion with matching observables")

    # outcome == RUNTIME_ERROR
    if status == "IMPLEMENTATION_FAILURE":
        return Decision(FAIL, "implementation crash/internal failure cannot satisfy RUNTIME_ERROR")
    if status != "RUNTIME_FAILURE":
        # A normal exit (even nonzero language exit) is not a runtime error.
        return Decision(FAIL, "RUNTIME_ERROR expects a structured RUNTIME_FAILURE from execute",
                        {"status": status})
    if "runtimeCategory" in exp and execute_result.get("runtimeCategory") != exp["runtimeCategory"]:
        return Decision(FAIL, "runtime category mismatch",
                        {"expected": exp["runtimeCategory"],
                         "actual": execute_result.get("runtimeCategory")})
    if "location" in exp:
        if not _location_matches(execute_result.get("location"), exp["location"]):
            return Decision(FAIL, "runtime error source location mismatch")
    if "stdoutBase64" in exp and not _output_matches(execute_result, "stdoutBase64", None, "stdout", exp, manifest):
        return Decision(FAIL, "stdout mismatch on runtime error path")
    return Decision(PASS, "runtime failure with matching category")


def _language_exit_matches(execute_result, expected_exit):
    # Omission of the manifest expectation means exactly 0, never "ignore".
    actual = execute_result.get("languageExit")
    return actual == expected_exit


def _output_matches(execute_result, b64_field, text_field, field, exp, manifest):
    """Compare an output field only when the manifest declares that field."""
    declared = None
    if b64_field in exp:
        declared = _b64_matches(execute_result.get(b64_field, ""), exp[b64_field])
        return declared
    if text_field and text_field in exp:
        actual = protocol.decode_b64(execute_result.get(b64_field, "")) if execute_result.get(b64_field) else b""
        actual = _apply_normalization(actual, field, manifest)
        return actual == exp[text_field].encode("utf-8")
    # No expectation on this field: the manifest does not constrain it. For
    # SUCCESS stdout the manifest must declare at least one observable; the
    # schema enforces that. Here an unconstrained field always matches.
    return True


def _diagnostic_matches(compile_result, expected):
    diags = compile_result.get("diagnostics", [])
    for diag in diags:
        if "code" in expected and diag.get("code") != expected["code"]:
            continue
        if "family" in expected and diag.get("family") != expected["family"]:
            continue
        if "location" in expected:
            if not _location_matches(diag.get("location"), expected["location"]):
                continue
        return True
    return False


def _location_matches(actual, expected):
    if actual is None:
        return False
    return (actual.get("startByteOffset") == expected.get("startByteOffset")
            and actual.get("endByteOffset") == expected.get("endByteOffset"))
