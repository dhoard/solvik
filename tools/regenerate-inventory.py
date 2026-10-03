#!/usr/bin/env python3
r"""Regenerate the requirement inventory and profile from the committed generators.

`tck/tools/verify_regen.py` already builds a shadow repository, runs every self-contained
generator into it, and requires the result to match the committed surface byte for byte. That
shadow inventory is exactly the value the committed inventory should hold, because the generators
are authoritative for the requirements they own -- a claim verify_regen.py exists to enforce.

This tool runs the same build and either reports the difference between the shadow inventory and the
committed one (--check, the default) or copies the shadow inventory and profile over the committed
ones (--apply). It exists because a semantic change to the specification moves a generator's records,
and re-editing the inventory to match by hand invites drift: the whole point of the assertion each
generator carries is that the generator's record wins.

Requirements the committed inventory holds but no generator produces are preserved, not deleted.
verify_regen.py reports those separately as unowned, and deleting them here would turn a provenance
gap into data loss.

Usage: tools/regenerate-inventory.py [--check|--apply]
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INVENTORY = os.path.join("tck", "requirements", "requirements.json")
PROFILE = os.path.join("tck", "profiles", "full-language.profile.json")

# Kept in step with tck/tools/verify_regen.py by an explicit read rather than a copy of the list:
# if the two diverged, this tool would regenerate from a different set than the gate checks, and the
# gate would then fail on the result with no obvious cause.
def generator_lists():
    text = open(os.path.join(ROOT, "tck", "tools", "verify_regen.py"), encoding="utf-8").read()
    def names(variable):
        block = text.split(variable + " = [", 1)[1].split("]", 1)[0]
        return [n.strip().strip('",') for n in block.split(",") if n.strip()]
    return names("SELF_CONTAINED"), names("MANIFEST_ONLY")


def build_shadow(spec_version):
    shadow = tempfile.mkdtemp(prefix="solvik-inventory-")
    os.makedirs(os.path.join(shadow, "docs"))
    shutil.copy(os.path.join(ROOT, "docs", "LANGUAGE_SPEC.md"),
                os.path.join(shadow, "docs", "LANGUAGE_SPEC.md"))
    for rel in ("tck/corpus", "tck/requirements", "tck/profiles", "tck/tools"):
        os.makedirs(os.path.join(shadow, rel), exist_ok=True)
    with open(os.path.join(shadow, INVENTORY), "w", encoding="utf-8") as handle:
        json.dump({"schemaVersion": 1, "specVersion": spec_version, "requirements": []}, handle, indent=2)
        handle.write("\n")
    with open(os.path.join(shadow, PROFILE), "w", encoding="utf-8") as handle:
        json.dump({"schemaVersion": 1, "specVersion": spec_version, "name": "full-language",
                   "kind": "full", "requirements": []}, handle, indent=2)
        handle.write("\n")
    self_contained, manifest_only = generator_lists()
    for gen in self_contained + manifest_only:
        shutil.copy(os.path.join(ROOT, "tck", "tools", gen),
                    os.path.join(shadow, "tck", "tools", gen))
    return shadow, self_contained, manifest_only


def seed_manifest_only(shadow, spec_version, manifest_only_reads):
    """Provide the hand-authored sources the manifest-only tool reads.

    Mirrors tck/tools/verify_regen.py: that tool's contract is to read `main.sol` rather than write
    it, so without this seeding it fails on a missing source and the produced inventory stops
    holding its requirements.
    """
    version_dir = os.path.join(shadow, "tck", "corpus", spec_version)
    self_contained_ids = {n for n in os.listdir(version_dir) if n.startswith("SOL-TCK-")} \
        if os.path.isdir(version_dir) else set()
    real = os.path.join(ROOT, "tck", "corpus", spec_version)
    dst_root = version_dir
    import re
    text = ""
    for gen in manifest_only_reads:
        text += open(os.path.join(ROOT, "tck", "tools", gen), encoding="utf-8").read()
    owned = set(re.findall(r'"(SOL-TCK-\d{4})"', text))
    for tid in sorted(owned - self_contained_ids):
        src_dir = os.path.join(real, tid)
        if not os.path.isdir(src_dir):
            continue
        dst = os.path.join(dst_root, tid)
        os.makedirs(dst, exist_ok=True)
        for name in os.listdir(src_dir):
            if name.endswith(".manifest.json"):
                continue
            shutil.copy(os.path.join(src_dir, name), os.path.join(dst, name))
    return owned


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    committed = json.load(open(os.path.join(ROOT, INVENTORY), encoding="utf-8"))
    shadow, self_contained, manifest_only = build_shadow(committed["specVersion"])
    try:
        for gen in self_contained:
            proc = subprocess.run([sys.executable, os.path.join("tck", "tools", gen)],
                                  cwd=shadow, capture_output=True, text=True)
            if proc.returncode != 0:
                raise SystemExit("%s exited %d:\n%s" % (gen, proc.returncode,
                                                        (proc.stderr or proc.stdout)[-2000:]))
        seed_manifest_only(shadow, committed["specVersion"], manifest_only)
        for gen in manifest_only:
            # The manifest-only tool reads corpus sources the self-contained run did not create;
            # verify_regen.py seeds them, and seeding them here keeps the produced inventory the same
            # set of records rather than one missing that tool's requirements.
            proc = subprocess.run([sys.executable, os.path.join("tck", "tools", gen)],
                                  cwd=shadow, capture_output=True, text=True)
            if proc.returncode != 0:
                raise SystemExit("%s exited %d:\n%s" % (gen, proc.returncode,
                                                        (proc.stderr or proc.stdout)[-2000:]))
        produced = json.load(open(os.path.join(shadow, INVENTORY), encoding="utf-8"))
        produced_profile = json.load(open(os.path.join(shadow, PROFILE), encoding="utf-8"))
    finally:
        shutil.rmtree(shadow, ignore_errors=True)

    by_id = {r["id"]: r for r in produced["requirements"]}
    committed_index = {r["id"]: r for r in committed["requirements"]}
    differing = [rid for rid in sorted(by_id)
                 if rid in committed_index and committed_index[rid] != by_id[rid]]
    missing = sorted(set(by_id) - set(committed_index))
    unowned = sorted(set(committed_index) - set(by_id))
    print("generators produce %d requirements; %d differ from the committed inventory"
          % (len(by_id), len(differing)))
    for rid in differing:
        print("  differs: %s" % rid)
    if missing:
        raise SystemExit("the committed inventory lacks %s, which a generator produces; adding a "
                         "requirement is a reviewed act, so this tool will not do it"
                         % ", ".join(missing))
    print("%d committed requirements are not produced by any generator and are preserved as-is"
          % len(unowned))

    committed_profile = json.load(open(os.path.join(ROOT, PROFILE), encoding="utf-8"))
    profile_extra = sorted(set(committed_profile["requirements"]) - set(produced_profile["requirements"]))
    merged_profile = dict(committed_profile)
    merged_profile["requirements"] = sorted(set(committed_profile["requirements"])
                                            | set(produced_profile["requirements"]))

    if not differing and merged_profile == committed_profile:
        print("committed inventory and profile already match the generators")
        return 0
    if not args.apply:
        print("rerun with --apply to sync")
        return 1

    updated = dict(committed)
    # Replace in place by position rather than through `committed_index`: those objects *are* the
    # elements of `committed["requirements"]`, so clearing one would also clear its own `id`, which is
    # the key still needed to look it up.
    for position, record in enumerate(updated["requirements"]):
        replacement = by_id.get(record["id"])
        if replacement is not None:
            updated["requirements"][position] = dict(replacement)
    updated["requirements"].sort(key=lambda r: r["id"])
    with open(os.path.join(ROOT, INVENTORY), "w", encoding="utf-8") as handle:
        json.dump(updated, handle, indent=2)
        handle.write("\n")
    if merged_profile != committed_profile:
        with open(os.path.join(ROOT, PROFILE), "w", encoding="utf-8") as handle:
            json.dump(merged_profile, handle, indent=2)
            handle.write("\n")
    print("applied: %d requirement record(s) synced from the generators" % len(differing))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
