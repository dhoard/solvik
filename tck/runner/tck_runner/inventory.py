"""Normative requirement inventory and compliance profile validation.

This module performs the cross-file validation required by TCK.md section 6 and
section 5.1. Schema validation proves each document is well formed; this module
proves the *relationships* between documents that a per-document schema cannot:

* requirement identifiers are unique;
* every test/requirement reference resolves;
* the spec version is one the runner understands;
* a requirement belongs to exactly one profile state;
* profiles are non-empty, reference real requirements, and have no cyclic
  inclusion;
* the full-language profile is present and includes every required portable
  requirement; and
* a manifest may not relabel a required requirement as optional or move it into
  a weaker profile than its inventory entry.

Any violation here is an infrastructure error: it prevents a conformance
judgment and is reported distinctly from an implementation failure.
"""

from __future__ import annotations

from . import digest, schema as S, strict_json, versions


class InventoryError(Exception):
    """An infrastructure error in the requirement inventory or profiles."""


def load_json(path: str):
    try:
        with open(path, "rb") as handle:
            return strict_json.loadb(handle.read())
    except (OSError, strict_json.StrictJSONError) as exc:
        raise InventoryError("%s: %s" % (path, exc)) from exc


def load_inventory(path: str, req_schema: dict):
    doc = load_json(path)
    try:
        S.validate(req_schema, doc)
    except S.ValidationError as exc:
        raise InventoryError("%s: schema invalid: %s" % (path, exc)) from exc
    if doc["specVersion"] not in versions.SUPPORTED_SPEC_VERSIONS:
        raise InventoryError("%s: unsupported spec version %r" % (path, doc["specVersion"]))
    by_id = {}
    for req in doc["requirements"]:
        rid = req["id"]
        if rid in by_id:
            raise InventoryError("duplicate requirement identifier: %s" % rid)
        by_id[rid] = req
    return {"doc": doc, "by_id": by_id, "path": path, "digest": digest.sha256_file(path)}


def load_profile(path: str, prof_schema: dict):
    doc = load_json(path)
    try:
        S.validate(prof_schema, doc)
    except S.ValidationError as exc:
        raise InventoryError("%s: profile schema invalid: %s" % (path, exc)) from exc
    return doc


def _resolve_profile_closure(name, profiles, visiting):
    """Return the requirement set for a profile, expanding ``extends``.

    Detects cycles explicitly (section 5.1): cyclic inclusion is an
    infrastructure error rather than an infinite recursion.
    """
    if name in visiting:
        raise InventoryError("cyclic profile inclusion involving %r" % name)
    if name not in profiles:
        raise InventoryError("unknown profile reference: %r" % name)
    prof = profiles[name]
    closure = set(prof["requirements"])
    for parent in prof.get("extends", []):
        closure |= _resolve_profile_closure(
            parent, profiles, visiting | {name}
        )
    return closure


def validate(inventory, profiles, spec_version):
    """Cross-validate the inventory and profiles; return a resolved model.

    ``profiles`` maps profile name -> loaded profile document.
    """
    if spec_version not in versions.SUPPORTED_SPEC_VERSIONS:
        raise InventoryError("unsupported requested spec version: %r" % spec_version)
    by_id = inventory["by_id"]

    # Every profile must reference only known requirements.
    all_reqs_in_profiles = set()
    closures = {}
    for name in sorted(profiles):
        prof = profiles[name]
        if prof["specVersion"] != spec_version:
            raise InventoryError(
                "profile %r declares spec version %r, not %r"
                % (name, prof["specVersion"], spec_version)
            )
        for rid in prof["requirements"]:
            if rid not in by_id:
                raise InventoryError("profile %r references unknown requirement %r" % (name, rid))
        closures[name] = _resolve_profile_closure(name, profiles, set())
        all_reqs_in_profiles |= closures[name]

    # Every active requirement must belong to at least one profile.
    for rid, req in sorted(by_id.items()):
        if req["lifecycle"] != "active":
            continue
        if rid not in all_reqs_in_profiles:
            raise InventoryError("requirement %r belongs to no profile" % rid)
        # A requirement's declared profile must include it.
        own = req["profile"]
        if own in closures and rid not in closures[own]:
            raise InventoryError(
                "requirement %r declares profile %r but is not in that profile's closure"
                % (rid, own)
            )

    # The mandatory full-language profile must exist and be non-empty.
    if versions.FULL_PROFILE not in profiles:
        raise InventoryError("mandatory profile %r is missing" % versions.FULL_PROFILE)
    full = profiles[versions.FULL_PROFILE]
    if full["kind"] != "full":
        raise InventoryError("profile %r must have kind 'full'" % versions.FULL_PROFILE)
    full_closure = closures[versions.FULL_PROFILE]
    if not full_closure:
        raise InventoryError("full-language profile is empty")

    # Every required *portable* active requirement must be in the full profile
    # (or a platform profile) -- a required requirement silently absent from all
    # profiles would go untested.
    for rid, req in sorted(by_id.items()):
        if req["lifecycle"] != "active":
            continue
        if req["portable"] and rid not in all_reqs_in_profiles:
            raise InventoryError("portable requirement %r is not covered by any profile" % rid)

    return {
        "by_id": by_id,
        "profiles": profiles,
        "closures": closures,
        "full_profile": versions.FULL_PROFILE,
        "spec_version": spec_version,
    }


def check_manifest_against_inventory(model, manifest):
    """Reject a manifest that mislabels requirement/profile/status (section 7).

    A manifest cannot relabel a required requirement as optional or place it in
    a weaker profile than its inventory entry declares.
    """
    by_id = model["by_id"]
    for rid in manifest["requirements"]:
        if rid not in by_id:
            raise InventoryError(
                "manifest %s references unknown requirement %r" % (manifest["testId"], rid)
            )
        req = by_id[rid]
        if req["lifecycle"] != "active":
            raise InventoryError(
                "manifest %s references non-active requirement %r" % (manifest["testId"], rid)
            )
    # Profile of manifest must be one whose closure contains every cited req.
    prof_name = manifest["profile"]
    if prof_name not in model["closures"]:
        raise InventoryError(
            "manifest %s cites unknown profile %r" % (manifest["testId"], prof_name)
        )
    closure = model["closures"][prof_name]
    for rid in manifest["requirements"]:
        if rid not in closure:
            raise InventoryError(
                "manifest %s places %r in profile %r but it is not in that closure"
                % (manifest["testId"], rid, prof_name)
            )
    # A requirement that the inventory marks required-by-full cannot be labeled
    # optional by a manifest.
    for rid in manifest["requirements"]:
        req = by_id[rid]
        in_full = rid in model["closures"].get(versions.FULL_PROFILE, set())
        if in_full and manifest["status"] == "optional" and req["profile"] == versions.FULL_PROFILE:
            # Only legal if the requirement is itself declared optional, which the
            # inventory expresses by NOT being in the full profile closure.
            raise InventoryError(
                "manifest %s marks %r optional but it is a full-profile requirement"
                % (manifest["testId"], rid)
            )


def validate_test_linkage(model, corpus_test_ids):
    """Require every test an active requirement claims to exist in the corpus.

    The forward direction -- a manifest must not cite an unknown requirement -- is checked
    per manifest by ``check_manifest_against_inventory``. Without this reverse direction the
    inventory's own ``tests`` list is trusted on assertion, so a requirement naming a test
    that was renamed, renumbered, or never created is still counted as tested, and
    ``validate`` prints ``N tested / N active`` while the coverage is fabricated. That is a
    false-certification shape: the aggregate report and the build gate both read coverage.

    ``corpus_test_ids`` is the set of ``testId`` values in the loaded corpus. Callers must
    pass it after loading manifests; it is deliberately not defaulted, because an omitted
    argument silently disabling a coverage guard is exactly the defect being fixed.
    """
    for rid, req in sorted(model["by_id"].items()):
        if req["lifecycle"] != "active":
            continue
        for tid in req["tests"]:
            if tid not in corpus_test_ids:
                raise InventoryError(
                    "active requirement %r lists test %r which is not present in the corpus"
                    % (rid, tid)
                )


def untested_requirements(model):
    """Active requirements with no automated test, for the report."""
    gaps = []
    for rid, req in sorted(model["by_id"].items()):
        if req["lifecycle"] != "active":
            continue
        if not req["tests"]:
            gaps.append(rid)
    return gaps
