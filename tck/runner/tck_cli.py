#!/usr/bin/env python3
"""Solvik TCK command-line entry point (stdlib only; needs no Solvik/Java).

Subcommands:

  validate   Validate schemas, the requirements inventory, profiles, and the
             whole selected corpus *before* any adapter runs, and report
             requirement coverage gaps. Exit 2 on any infrastructure error.

  selftests  Run the pure-Python runner self-tests (no Solvik/Java/GraalVM).

  run        Execute a conformance run against a configured adapter. (Requires a
             launcher/adapter to be present; the portable runner itself imports
             no Solvik code.)

The runner never accesses the network: every versioned input is local and digest
verified (TCK.md section 16).
"""

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from tck_runner import (  # noqa: E402
    inventory,
    manifests,
    report,
    schema,
    strict_json,
    versions,
)

TCK_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def _load_schema(name):
    path = os.path.join(TCK_ROOT, "schemas", name)
    with open(path, "rb") as handle:
        doc = strict_json.loadb(handle.read())
    schema.check_schema(doc)
    return doc


def cmd_validate(argv):
    ap = argparse.ArgumentParser(prog="tck_cli validate")
    ap.add_argument("--corpus", default=os.path.join(TCK_ROOT, "corpus"))
    ap.add_argument("--requirements", default=os.path.join(TCK_ROOT, "requirements", "requirements.json"))
    ap.add_argument("--profiles", default=os.path.join(TCK_ROOT, "profiles"))
    ap.add_argument("--spec", default=versions.SPEC_VERSION)
    ap.add_argument("--profile", default=versions.FULL_PROFILE)
    args = ap.parse_args(argv)

    req_schema = _load_schema("requirements-1.schema.json")
    prof_schema = _load_schema("profile-1.schema.json")
    man_schema = _load_schema("manifest-1.schema.json")
    # check_schema the remaining published schemas too.
    for name in ("protocol-1.schema.json", "report-1.schema.json"):
        _load_schema(name)

    inv = inventory.load_inventory(args.requirements, req_schema)
    profiles = {}
    if os.path.isdir(args.profiles):
        for root, _dirs, files in os.walk(args.profiles):
            for fn in sorted(files):
                if fn.endswith(".profile.json"):
                    doc = inventory.load_profile(os.path.join(root, fn), prof_schema)
                    profiles[doc["name"]] = doc
    model = inventory.validate(inv, profiles, args.spec)
    # Attach the resolved fields the runner/report expect.
    model["inventoryDigest"] = inv["digest"]
    model["requiredCapabilities"] = _required_capabilities(profiles)

    selected = [p for p in manifests.discover(args.corpus)]
    loaded = manifests.load_and_validate(args.corpus, man_schema, model, args.spec)

    # Coverage is only meaningful once the corpus is known: a requirement may list a
    # test that no longer exists, and counting it as tested would fabricate coverage in
    # the line printed below and in the aggregate report. Checked before the coverage
    # numbers are derived, so a broken link is a hard error rather than a statistic.
    inventory.validate_test_linkage(
        model, {doc["testId"] for doc in loaded})

    gaps = inventory.untested_requirements(model)
    model["requirementGaps"] = gaps
    model["coverage"] = _coverage(model)
    model["ambiguities"] = _ambiguous(model)

    print("validate: %d requirements, %d profiles, %d manifests (%s)" % (
        len(inv["by_id"]), len(profiles), len(loaded), args.spec))
    print("requirement coverage: %d tested / %d active" % (
        _tested_count(model), len([r for r in model["by_id"].values() if r["lifecycle"] == "active"])))
    if gaps:
        print("UNTESTED REQUIREMENTS (block full-profile certification):")
        for g in gaps:
            print("  - %s" % g)
    amb = model["ambiguities"]
    if amb:
        print("SPEC AMBIGUITIES (block full-profile certification):")
        for a in amb:
            print("  - %s" % a)
    if gaps or amb:
        print("validate: full-profile certification withheld (gaps/ambiguities present)")
    # Certification also depends on the normative baseline itself being a frozen,
    # exhaustive inventory (TCK.md sections 5 / 5.1). A `-draft`/pre-1.0 revision is
    # not, so aggregate conformance certification is withheld for it no matter how
    # many seeded requirements are covered; this is stated explicitly so a 4/4
    # coverage line is never misread as "ready to certify".
    if args.spec not in versions.CERTIFIABLE_SPEC_VERSIONS:
        print("validate: spec %s is a draft/non-certifiable baseline -> full-profile "
              "conformance certification withheld (TCK.md sections 5 / 5.1)" % args.spec)
    print("validate: OK")
    return report.EXIT_PASS


def _required_capabilities(profiles):
    prof = profiles.get(versions.FULL_PROFILE)
    return list(prof.get("capabilities", [])) if prof else []


def _tested_count(model):
    return sum(1 for r in model["by_id"].values()
               if r["lifecycle"] == "active" and r["tests"])


def _coverage(model):
    active = [r for r in model["by_id"].values() if r["lifecycle"] == "active"]
    tested = [r for r in active if r["tests"]]
    return {"active": len(active), "tested": len(tested),
            "ratio": (len(tested) / len(active)) if active else 0.0}


def _ambiguous(model):
    return [rid for rid, r in model["by_id"].items()
            if r["lifecycle"] == "active" and r.get("status") == "untested-ambiguous"]


def cmd_selftests(argv):
    import subprocess

    path = os.path.join(TCK_ROOT, "tests", "run_selftests.py")
    return subprocess.call([sys.executable, path])


def _load_suite_inputs(corpus, requirements, profiles_dir, profile, spec):
    """Load and validate the requirements inventory, profiles, and the corpus.

    Shared by `run` and `differential` so the two commands cannot drift apart in what
    they validate. Everything here is offline and local: no adapter is invoked, and per
    TCK.md section 16 nothing is fetched.

    `run_suite` re-validates the corpus internally because the report must bind the
    digests of the exact manifests it executed; the load here is what coverage and
    linkage are computed from, and it happens before any adapter is contacted.
    """
    req_schema = _load_schema("requirements-1.schema.json")
    prof_schema = _load_schema("profile-1.schema.json")
    man_schema = _load_schema("manifest-1.schema.json")
    pschema = _load_schema("protocol-1.schema.json")

    inv = inventory.load_inventory(requirements, req_schema)
    profiles = {}
    for root, _d, files in os.walk(profiles_dir):
        for fn in sorted(files):
            if fn.endswith(".profile.json"):
                doc = inventory.load_profile(os.path.join(root, fn), prof_schema)
                profiles[doc["name"]] = doc
    model = inventory.validate(inv, profiles, spec)
    model["inventoryDigest"] = inv["digest"]
    model["requiredCapabilities"] = _required_capabilities(profiles)
    loaded = manifests.load_and_validate(corpus, man_schema, model, spec)
    by_id = {m["testId"]: m for m in loaded}
    inventory.validate_test_linkage(model, set(by_id))
    model["requirementGaps"] = inventory.untested_requirements(model)
    model["coverage"] = _coverage(model)
    model["ambiguities"] = _ambiguous(model)
    return {"model": model, "manifests": loaded, "byId": by_id,
            "manifestSchema": man_schema, "protocolSchema": pschema}


def cmd_run(argv):
    ap = argparse.ArgumentParser(prog="tck_cli run")
    ap.add_argument("--adapter-config", required=True,
                    help="JSON file: {name, argv, fingerprint, env, capabilities}")
    ap.add_argument("--corpus", default=os.path.join(TCK_ROOT, "corpus"))
    ap.add_argument("--requirements", default=os.path.join(TCK_ROOT, "requirements", "requirements.json"))
    ap.add_argument("--profiles", default=os.path.join(TCK_ROOT, "profiles"))
    ap.add_argument("--profile", default=versions.FULL_PROFILE)
    ap.add_argument("--spec", default=versions.SPEC_VERSION)
    ap.add_argument("--report", default=os.path.join(TCK_ROOT, "reports", "report.json"))
    ap.add_argument("--test", action="append", default=[], help="filter by test id (repeatable)")
    args = ap.parse_args(argv)

    from tck_runner import digest, runner

    suite = _load_suite_inputs(args.corpus, args.requirements, args.profiles,
                               args.profile, args.spec)
    model = suite["model"]
    man_schema, pschema = suite["manifestSchema"], suite["protocolSchema"]

    with open(args.adapter_config, "rb") as handle:
        cfg_doc = strict_json.loadb(handle.read())
    cfg = runner.AdapterConfig(
        name=cfg_doc["name"], argv=cfg_doc["argv"],
        config_digest=digest.sha256_json(cfg_doc),
        fingerprint=cfg_doc["fingerprint"],
        env_overrides=cfg_doc.get("env", {}),
    )
    r = runner.Runner(pschema, man_schema, cfg)
    filters = {"tests": args.test} if args.test else None
    full = versions.FULL_PROFILE == args.profile and not args.test
    rep = runner.run_suite(r, args.corpus, model, args.profile, args.spec,
                           requested_full_profile=full, filters=filters, adapter_config=cfg)
    report.write_report(rep, args.report)
    print(report.render_terminal(rep))
    return rep["exitCode"]


def cmd_differential(argv):
    """Run the same portable programs through two adapters and report disagreements.

    TCK.md section 15. Agreement is evidence, never a verdict: this command never
    reports conformance, never updates an oracle, and never overrides one. Its exit
    code signals only infrastructure problems and discovered disagreements, because
    "the two implementations differ" is the outcome a caller acts on.
    """
    ap = argparse.ArgumentParser(prog="tck_cli differential")
    ap.add_argument("--left-config", required=True,
                    help="adapter config JSON for the left implementation")
    ap.add_argument("--right-config", required=True,
                    help="adapter config JSON for the right implementation")
    ap.add_argument("--corpus", default=os.path.join(TCK_ROOT, "corpus"))
    ap.add_argument("--requirements", default=os.path.join(TCK_ROOT, "requirements", "requirements.json"))
    ap.add_argument("--profiles", default=os.path.join(TCK_ROOT, "profiles"))
    ap.add_argument("--profile", default=versions.FULL_PROFILE)
    ap.add_argument("--spec", default=versions.SPEC_VERSION)
    ap.add_argument("--report", default=os.path.join(TCK_ROOT, "reports", "differential.json"))
    ap.add_argument("--test", action="append", default=[], help="filter by test id (repeatable)")
    args = ap.parse_args(argv)

    from tck_runner import digest, differential, runner

    suite = _load_suite_inputs(args.corpus, args.requirements, args.profiles,
                               args.profile, args.spec)
    model, by_id = suite["model"], suite["byId"]

    sides = []
    for label, path in (("left", args.left_config), ("right", args.right_config)):
        with open(path, "rb") as handle:
            cfg_doc = strict_json.loadb(handle.read())
        sides.append((cfg_doc["name"], runner.AdapterConfig(
            name=cfg_doc["name"], argv=cfg_doc["argv"],
            config_digest=digest.sha256_json(cfg_doc),
            fingerprint=cfg_doc["fingerprint"],
            env_overrides=cfg_doc.get("env") or {})))

    left_name, right_name = sides[0][0], sides[1][0]
    if left_name == right_name:
        # Two adapters claiming one identity cannot be distinguished in a report, so
        # comparing them would produce records whose sides are indistinguishable. Checked
        # before either suite runs: this is a configuration error, not an outcome.
        sys.stderr.write("differential: both adapters are named %r; use distinct names\n"
                         % left_name)
        return 2

    observations, impls = {}, {}
    for name, cfg in sides:
        sink = {}
        r = runner.Runner(suite["protocolSchema"], suite["manifestSchema"], cfg,
                          observation_sink=sink)
        # The conformance verdict is deliberately discarded: differential mode must
        # not inherit or echo a PASS/FAIL, only the raw observations.
        rep = runner.run_suite(r, args.corpus, model, args.profile, args.spec,
                               requested_full_profile=False,
                               filters={"tests": args.test} if args.test else None,
                               adapter_config=cfg)
        observations[name] = sink
        impls[name] = rep.get("implementation", {})

    cmp = differential.compare_suites(left_name, observations[left_name],
                                       right_name, observations[right_name], by_id)
    cmp["tckVersion"] = versions.tck_version()
    cmp["specVersion"] = args.spec
    cmp["protocolVersion"] = versions.PROTOCOL_VERSION
    cmp["implementationNames"] = {"left": left_name, "right": right_name}
    cmp["leftImplementation"] = impls.get(left_name, {})
    cmp["rightImplementation"] = impls.get(right_name, {})
    # Agreement is reported as a count of disagreements, never as a conformance claim.
    cmp["conformanceClaimed"] = False
    # A distinct schema id, so this document is never mistaken for a conformance report
    # (which is schema `report-1` with additionalProperties false and a PASS/FAIL meaning).
    cmp["schemaId"] = "https://solvik.org/tck/schema/differential-1.json"
    report.write_report(cmp, args.report)

    counts = cmp["counts"]
    print("differential: %s vs %s over %s test(s)" %
          (left_name, right_name, counts["tests"]))
    print("disagreements=%s inconclusive=%s compared=%s unconstrained=%s" %
          (counts["disagreements"], counts["inconclusive"], counts["compared"],
           counts["unconstrained"]))
    for rec in cmp["results"]:
        if rec["axes"]:
            print("  [DIFF] %s: %s" % (rec["testId"], ",".join(rec["axes"])))
    for rec in cmp["results"]:
        if rec.get("unconstrained"):
            print("  [unconstrained] %s: %s" %
                  (rec["testId"], ",".join(rec["unconstrained"])))
    return differential.exit_code(counts)


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if not argv:
        print(__doc__)
        return 2
    cmd, rest = argv[0], argv[1:]
    handlers = {"validate": cmd_validate, "selftests": cmd_selftests, "run": cmd_run, "differential": cmd_differential}
    if cmd not in handlers:
        print("unknown command: %s" % cmd, file=sys.stderr)
        return 2
    return handlers[cmd](rest)


if __name__ == "__main__":
    sys.exit(main())
