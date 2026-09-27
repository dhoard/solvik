"""Deterministic conformance reporting and runner exit-code precedence.

The JSON report binds a conformance result to the exact versioned inputs and the
IUT fingerprint (acceptance criterion 18). Rules implemented here (section 13):

* a required test that did not execute is never a pass;
* infrastructure error is not an implementation failure but prevents certification;
* an unsupported required capability prevents full-profile certification;
* a percentage is never presented as certification;
* runner exit codes: ``0`` fully-passing requested verification; ``1`` one or
  more conformance failures with no infrastructure error; ``2`` invalid input,
  protocol failure, unsupported required capability, NOT_RUN, or any
  infrastructure error. Infrastructure status dominates conformance status.
* a filtered run may return ``0`` for the requested tests but sets full-profile
  conformance to ``NOT_EVALUATED``.
"""

from __future__ import annotations

import datetime
import hashlib
import os

from . import strict_json, versions

# Exit codes.
EXIT_PASS = 0
EXIT_CONFORMANCE_FAILURE = 1
EXIT_INFRASTRUCTURE = 2

# Full-profile conformance states.
CONF_PASS = "PASS"
CONF_FAIL = "FAIL"
CONF_NOT_EVALUATED = "NOT_EVALUATED"


def utc_now() -> str:
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _counts(results):
    c = {"PASS": 0, "FAIL": 0, "NOT_RUN": 0, "INFRASTRUCTURE_ERROR": 0}
    for r in results:
        c[r["status"]] = c.get(r["status"], 0) + 1
    return c


def build_report(context, results, requested_full_profile: bool):
    """Assemble the versioned report dict from per-test results.

    ``context`` carries versions, digests, implementation identity, filters, and
    the IUT fingerprint. ``results`` must be in deterministic (sorted-by-testId)
    order; callers must not rely on completion order.
    """
    results = sorted(results, key=lambda r: r["testId"])
    counts = _counts(results)
    infra = counts.get("INFRASTRUCTURE_ERROR", 0)
    fails = counts.get("FAIL", 0)
    not_run = counts.get("NOT_RUN", 0)

    unsupported_required = context.get("unsupportedRequiredCapabilities", [])

    # A normative baseline that is a draft (or otherwise not a frozen, exhaustive
    # inventory) cannot carry a full-profile certification: TCK.md section 5 requires
    # certification against an unversioned/draft baseline to be withheld, and section
    # 5.1 requires the full-language profile to enumerate *every* portable
    # non-deferred requirement -- a seed inventory that is not a complete enumeration
    # cannot support a conformance conclusion. run_suite resolves this from the spec
    # version and the TCK's CERTIFIABLE_SPEC_VERSIONS set; it is a property of the
    # spec/TCK release, never of the implementation, so no adapter can waive it. When
    # absent from context the default is True so the aggregate PASS path remains a
    # real, unit-tested decision rather than a facade.
    baseline_certifiable = context.get("baselineCertifiable", True)

    # Full-profile conformance decision (section 13). PASS only when the exact
    # full profile was requested, the baseline is a certifiable (frozen, exhaustive)
    # normative revision, every required test executed once and passed, every
    # required capability was available, requirement coverage validation succeeded,
    # and no ambiguity or infrastructure error affects the profile.
    #
    #   * If the full profile was not requested (filtered run): NOT_EVALUATED.
    #   * If anything prevents a judgment -- a draft/non-certifiable baseline,
    #     infrastructure error, a required test that did NOT_RUN, an unsupported
    #     required capability, requirement gaps, or specification ambiguities:
    #     NOT_EVALUATED (cannot certify).
    #   * Otherwise, if any test genuinely failed: FAIL.
    #   * Otherwise: PASS.
    blocks_certification = (
        not baseline_certifiable
        or infra > 0
        or not_run > 0
        or bool(unsupported_required)
        or bool(context.get("requirementGaps"))
        or bool(context.get("ambiguities"))
    )
    if not requested_full_profile:
        full_conformance = CONF_NOT_EVALUATED
    elif blocks_certification:
        full_conformance = CONF_NOT_EVALUATED
    elif fails > 0:
        full_conformance = CONF_FAIL
    else:
        full_conformance = CONF_PASS

    exit_code = _exit_code(counts, unsupported_required, context)

    report = {
        "schemaVersion": versions.REPORT_SCHEMA_VERSION,
        "tckVersion": versions.tck_version(),
        "specVersion": context["specVersion"],
        "protocolVersion": versions.PROTOCOL_VERSION,
        "schemaIds": {
            "manifest": versions.SCHEMA_MANIFEST,
            "requirements": versions.SCHEMA_REQUIREMENTS,
            "profile": versions.SCHEMA_PROFILE,
            "protocol": versions.SCHEMA_PROTOCOL,
            "report": versions.SCHEMA_REPORT,
        },
        "implementation": context.get("implementation", {}),
        "platform": context.get("platform", {}),
        "timestamp": context.get("timestamp", utc_now()),
        "profile": context["profile"],
        "requestedFullProfile": requested_full_profile,
        "filters": context.get("filters", {}),
        "inputDigests": context.get("inputDigests", {}),
        "counts": counts,
        "unsupportedRequiredCapabilities": unsupported_required,
        "unsupportedOptionalCapabilities": context.get("unsupportedOptionalCapabilities", []),
        "requirementCoverage": context.get("requirementCoverage", {}),
        "untestedRequirements": context.get("requirementGaps", []),
        "ambiguities": context.get("ambiguities", []),
        "fullProfileConformance": full_conformance,
        "exitCode": exit_code,
        "results": results,
    }
    # Only include an IUT fingerprint when one was actually captured; a closed
    # schema forbids a null placeholder, and a conformance report must bind a
    # real fingerprint (acceptance criterion 18).
    if context.get("iutFingerprint") is not None:
        report["iutFingerprint"] = context["iutFingerprint"]
    return report


def _exit_code(counts, unsupported_required, context):
    # Infrastructure precedence dominates everything.
    if counts.get("INFRASTRUCTURE_ERROR", 0) > 0:
        return EXIT_INFRASTRUCTURE
    if unsupported_required:
        return EXIT_INFRASTRUCTURE
    if counts.get("NOT_RUN", 0) > 0:
        return EXIT_INFRASTRUCTURE
    if context.get("inputInvalid"):
        return EXIT_INFRASTRUCTURE
    # Conformance failures next.
    if counts.get("FAIL", 0) > 0:
        return EXIT_CONFORMANCE_FAILURE
    return EXIT_PASS


def write_report(report: dict, path: str):
    """Write the report as strict, deterministically ordered JSON."""
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(strict_json.dumps_canonical(report))
        handle.write("\n")


def render_terminal(report: dict) -> str:
    """Deterministic human-readable summary (order is fixed by sorted results)."""
    lines = []
    lines.append("Solvik TCK report (tck=%s spec=%s protocol=%s)" % (
        report["tckVersion"], report["specVersion"], report["protocolVersion"]))
    impl = report["implementation"]
    lines.append("implementation: %s %s" % (impl.get("name", "?"), impl.get("version", "?")))
    lines.append("profile: %s  full-conformance: %s" % (
        report["profile"], report["fullProfileConformance"]))
    c = report["counts"]
    lines.append("PASS=%(PASS)s FAIL=%(FAIL)s NOT_RUN=%(NOT_RUN)s INFRA=%(INFRASTRUCTURE_ERROR)s" % c)
    if report["unsupportedRequiredCapabilities"]:
        lines.append("UNSUPPORTED REQUIRED CAPABILITIES: %s" %
                     ", ".join(report["unsupportedRequiredCapabilities"]))
    for r in report["results"]:
        lines.append("  [%s] %s: %s" % (r["status"], r["testId"], r.get("reason", "")))
    lines.append("exit=%s" % report["exitCode"])
    return "\n".join(lines)
