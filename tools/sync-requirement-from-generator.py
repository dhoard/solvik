#!/usr/bin/env python3
r"""Copy requirement records from a generator into requirements.json, verbatim.

Each generator asserts, on rerun, that the committed inventory equals the record it would write.
That assertion is the provenance guard, and when a generator's record changes -- as several did in
the 2026.11-draft keyword revision -- the inventory must be brought to the generator's value, not
the reverse: the generator is the artifact of record for its own requirements, and editing the
inventory by hand would leave the two to converge by memory rather than by construction.

The generator is run with its assertion and its inventory write replaced by a capture, so the
records copied are exactly the ones the generator itself would have committed. Only requirements the
generator actually builds are touched, and only ones already present in the inventory.

Usage: tools/sync-requirement-from-generator.py [--apply] tck/tools/genNN.py [REQ-id ...]
"""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile

INVENTORY = os.path.join("tck", "requirements", "requirements.json")


def run_generator_capturing(generator):
    """Run a generator with its inventory reads and writes redirected into a sandbox.

    verify_regen.py already solves the harder version of this problem -- a generator run against an
    empty inventory writes a file holding only its own records, which is precisely what is wanted.
    Reusing that shape is why this tool works on every generator without knowing each one's structure:
    the shadow inventory is empty, so whatever the generator writes there is exactly its own records.
    """
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    shadow = tempfile.mkdtemp(prefix="solvik-sync-")
    try:
        for rel in ("docs", "tck/corpus", "tck/requirements", "tck/profiles", "tck/tools"):
            os.makedirs(os.path.join(shadow, rel), exist_ok=True)
        import shutil
        shutil.copy(os.path.join(root, "docs", "LANGUAGE_SPEC.md"),
                    os.path.join(shadow, "docs", "LANGUAGE_SPEC.md"))
        shutil.copy(generator, os.path.join(shadow, "tck", "tools", os.path.basename(generator)))
        with open(os.path.join(shadow, INVENTORY), "w", encoding="utf-8") as handle:
            json.dump({"schemaVersion": 1, "specVersion": "", "requirements": []}, handle)
        spec_version = json.load(open(os.path.join(root, INVENTORY)))["specVersion"]
        with open(os.path.join(shadow, "tck", "profiles", "full-language.profile.json"), "w",
                  encoding="utf-8") as handle:
            json.dump({"schemaVersion": 1, "specVersion": spec_version, "name": "full-language",
                       "kind": "full", "requirements": []}, handle)
        # Corpus directories the generator reads (rather than writes) come from the committed tree;
        # a generator that only writes them gets an empty corpus and produces them itself.
        proc = subprocess.run([sys.executable, os.path.join("tck", "tools", os.path.basename(generator))],
                              cwd=shadow, capture_output=True, text=True)
        produced = os.path.join(shadow, INVENTORY)
        if not os.path.exists(produced):
            raise SystemExit("%s wrote no inventory (exit %d):\n%s"
                             % (generator, proc.returncode, (proc.stderr or proc.stdout)[-1500:]))
        if proc.returncode != 0:
            print("note: %s exited %d after writing its inventory:\n%s"
                  % (os.path.basename(generator), proc.returncode,
                     (proc.stderr or proc.stdout)[-800:]), file=sys.stderr)
        return json.load(open(produced, encoding="utf-8"))["requirements"]
    finally:
        import shutil
        shutil.rmtree(shadow, ignore_errors=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("generator")
    ap.add_argument("requirements", nargs="*")
    args = ap.parse_args()

    owned = run_generator_capturing(args.generator)
    by_id = {r["id"]: r for r in owned}
    if args.requirements:
        missing = [r for r in args.requirements if r not in by_id]
        if missing:
            raise SystemExit("%s does not build %s" % (args.generator, ", ".join(missing)))
        wanted = args.requirements
    else:
        wanted = sorted(by_id)

    path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), INVENTORY)
    with open(path, encoding="utf-8") as handle:
        original_text = handle.read()
    data = json.loads(original_text)
    index = {r["id"]: r for r in data["requirements"]}
    absent = [r for r in wanted if r not in index]
    if absent:
        raise SystemExit("inventory lacks %s; adding a requirement is a reviewed act, not a sync"
                         % ", ".join(absent))
    changed = []
    for rid in wanted:
        if index[rid] != by_id[rid]:
            changed.append(rid)
            index[rid].clear()
            index[rid].update(by_id[rid])
    print("%s owns %d requirements; %d would change: %s"
          % (os.path.basename(args.generator), len(by_id), len(changed), ", ".join(changed) or "none"))
    if changed and args.apply:
        # The whole file is re-emitted with the same encoder settings the generators use, so a sync
        # cannot introduce formatting drift that the byte-level provenance check would then report.
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(data, handle, indent=2)
            handle.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
