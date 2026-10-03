#!/usr/bin/env python3
"""Synchronize requirement *prose* (summary / evidence notes) with the keyword overhaul.

Why this exists
---------------
`tck/tools/verify_regen.py` proves the committed corpus and the committed
requirement records are byte-identical to what the generators in `tck/tools`
produce -- but only for the requirements those generators actually own (167 of
304). The remaining 137 come from the earliest batches, whose per-batch steps
were never committed; they are corrected in place, exactly as the
`superseded`-field correction was, and are reported as unowned on every gate
run.

Those unowned records still *described* the removed vocabulary in their free
text: REQ-2900 opens "`var` declares a mutable binding or property", which after
this change is a sentence about syntax the language no longer has. Requirement
prose is the human-facing statement of the verdict, so leaving it stale would
make the inventory disagree with the spec it cites -- the same defect the quote
sync repaired, one level up.

This tool rewrites only `summary` and the free-text evidence strings, and only
with rules whose right-hand side contains none of the removed keywords, so it is
idempotent. The `quote` fields are deliberately *not* touched here: those must
match the spec verbatim and are synchronized by
`tools/sync-requirement-from-generator.py` / `tools/regenerate-inventory.py`.

Records owned by a committed generator are skipped: rewriting them here would
break the byte-for-byte reproduction `verify_regen.py` asserts, and their prose
is regenerated from the generator instead.

Two modes, deliberately different in cost:

  * `--check` (default) scans the committed inventory and fails while any record's
    free prose names a removed keyword. It reads one JSON file, so it is cheap
    enough to run on every gate.
  * `--write` additionally builds the shadow inventory (the same build
    `verify_regen.py` performs) to learn which records a generator owns, repairs
    the unowned ones in place, and leaves the owned ones for their generators.

A stale *owned* record is fixed in the generator literal, not here;
`tools/check-generator-keyword-prose.py` is the check that catches those, so a
clean run of that script plus a clean `--check` here means the whole inventory is
current whether or not the ownership split is known.

Usage: python3 tools/repair-keyword-summaries.py [--check|--write]
"""

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INV_PATH = os.path.join(ROOT, 'tck', 'requirements', 'requirements.json')

# Substring rules for free prose. Unlike the source rules these are unanchored,
# because a summary is one flowing sentence rather than a program; each is
# keyed on the backticked keyword or a fixed phrase so it cannot fire on
# ordinary English. Order matters: longer phrases first.
PROSE_RULES = [
    (r'\*\*`var` and `mutable val`\*\*', '**`val` and `mutable val`**'),
    (r'`var` and `mutable val`', '`val` and `mutable val`'),
    (r'`var` or `val`', '`val` or `mutable val`'),
    (r'`var` and `val`', '`mutable val` and `val`'),
    (r'`var` declares', '`mutable val` declares'),
    (r'`var` property', '`mutable val` property'),
    (r'`var` invalidates', '`mutable val` invalidates'),
    (r'`var` narrowed', '`mutable val` narrowed'),
    (r'`var` named', '`mutable val` named'),
    (r'`var` local', '`mutable val` local'),
    (r'`var` may be reassigned', '`mutable val` may be reassigned'),
    (r'`var` binding', '`mutable val` binding'),
    (r'\bdeclared `var`', 'declared `mutable val`'),
    (r'a local declared `var`', 'a local declared `mutable val`'),
    (r'overriding an `open` member', 'overriding a `mutable` member'),
    (r'`open` or `override`', '`mutable` or `override`'),
    (r'an `open` member', 'a `mutable` member'),
    (r'`open` member', '`mutable` member'),
    (r'`open` method', '`mutable` method'),
    (r'`open` class', '`mutable` class'),
    (r'declared `open`', 'declared `mutable`'),
    (r'must be `open`', 'must be `mutable`'),
    (r'static `open`', 'static `mutable`'),
    (r'`sealed` class', 'abstract class'),
    (r'sealed class', 'abstract class'),
    (r'sealed classes', 'abstract classes'),
    (r'sealed subclass', 'abstract subclass'),
    (r'sealed hierarchy', 'open class hierarchy'),
    (r'a sealed type', 'an abstract class'),
    (r'sealed type', 'abstract class'),
    (r'enum or sealed', 'enum'),
    (r'enum/sealed', 'enum'),
    (r'subtype set', 'subtype set'),          # no-op anchor, kept for clarity
    (r'`sealed`', '`abstract`'),
    (r'\bsealed\b', 'abstract'),
    (r'\bpermitted-subtype\b', 'subclass'),
    (r'permitted subtypes', 'subclasses'),
    (r'permitted-subtype set', 'subclass relation'),
]
REMOVED = re.compile(r'\b(var|sealed)\b|`open`')

def generator_owned_requirement_ids():
    """Requirement ids written by a committed generator, determined empirically.

    Reading them out of the generator sources would miss the ones declared with
    a helper's default id, so the generators are run against an empty shadow
    inventory and the resulting ids are taken as the owned set. That is the same
    definition `verify_regen.py` uses, so the two tools cannot disagree about
    which records are safe to edit here.
    """
    import shutil
    sys.path.insert(0, os.path.join(ROOT, 'tck', 'tools'))
    import verify_regen as V
    shadow = V.build_shadow()
    try:
        V.run(shadow, V.SELF_CONTAINED)
        sc = V.test_ids(os.path.join(shadow, 'tck/corpus'))
        V.seed_manifest_only_inputs(shadow, sc)
        V.run(shadow, V.MANIFEST_ONLY)
        with open(os.path.join(shadow, 'tck/requirements/requirements.json'),
                  encoding='utf-8') as fh:
            owned = {r['id'] for r in json.load(fh)['requirements']}
    finally:
        shutil.rmtree(shadow, ignore_errors=True)
    return owned


def repair_text(text):
    out = text
    for pat, rep in PROSE_RULES:
        out = re.sub(pat, rep, out)
    if REMOVED.search(out):
        # A rule failed to cover some phrasing. Reporting it is more useful than
        # guessing, and leaving the text untouched keeps the gate honest.
        return None
    return out


def repair_string_field(value, log, where):
    if not isinstance(value, str) or not REMOVED.search(value):
        return value
    fixed = repair_text(value)
    if fixed is None:
        print(f"  UNHANDLED at {where}: {value[:130]}")
        return value
    log.append(where)
    return fixed


def stale_records(requirements):
    """(id, offending fragments) for every record whose free prose names a removed word.

    Only `summary` and the free-text evidence fields are inspected. `quote`
    entries are excluded on purpose: they must match the specification verbatim,
    and the specification no longer contains the removed vocabulary, so a quote
    that mentioned it would be caught by the oracle-quote check rather than here.
    """
    out = []
    for rec in requirements:
        rid = rec['id']
        fields = [rec.get('summary', '')]
        ev = rec.get('evidence', {})
        if isinstance(ev, dict):
            for key in ('oracle', 'notes', 'failureModes', 'supersededReason'):
                val = ev.get(key)
                if isinstance(val, str):
                    fields.append(val)
                elif isinstance(val, list):
                    fields.extend(x for x in val if isinstance(x, str))
        hits = sorted({m.group(0) for text in fields for m in REMOVED.finditer(text)})
        if hits:
            out.append((rid, hits))
    return out


def check():
    """Scan the committed inventory; the cheap mode, run by the TCK gate."""
    inv = json.load(open(INV_PATH, encoding='utf-8'))
    stale = stale_records(inv['requirements'])
    if stale:
        for rid, hits in stale:
            print(f"requirement-prose: FAIL: {rid} still names "
                  f"{', '.join(hits)} in its summary or evidence prose", file=sys.stderr)
        print("requirement-prose: %d requirement record(s) describe the removed "
              "vocabulary; repair an unowned record with "
              "tools/repair-keyword-summaries.py --write, and an owned one in the "
              "generator literal that produces it" % len(stale), file=sys.stderr)
        return 1
    print("requirement-prose: OK -- no requirement summary or evidence note still "
          "describes `var`, `open`, or `sealed` as Solvik syntax")
    return 0


def main():
    if '--write' not in sys.argv:
        return check()
    owned = generator_owned_requirement_ids()
    inv = json.load(open(INV_PATH, encoding='utf-8'))
    log, touched, skipped_owned, unhandled = [], 0, 0, []
    for rec in inv['requirements']:
        rid = rec['id']
        if rid in owned:
            # Owned records are regenerated, not edited, here; rewriting one would
            # break the byte-for-byte claim verify_regen.py asserts of it.
            if REMOVED.search(rec.get('summary', '') +
                              json.dumps(rec.get('evidence', ''))):
                skipped_owned += 1
            continue
        dirty = REMOVED.search(rec.get('summary', '')) or \
            REMOVED.search(json.dumps(rec.get('evidence', '')))
        if not dirty:
            continue
        unhandled_before = len(log)
        s = repair_string_field(rec.get('summary', ''), log, f"{rid}.summary")
        rec['summary'] = s
        ev = rec.get('evidence', {})
        if isinstance(ev, dict):
            for key in ('oracle', 'notes', 'failureModes', 'supersededReason'):
                val = ev.get(key)
                if isinstance(val, str):
                    ev[key] = repair_string_field(val, log, f"{rid}.evidence.{key}")
                elif isinstance(val, list):
                    ev[key] = [repair_string_field(x, log, f"{rid}.evidence.{key}[{i}]")
                               if isinstance(x, str) else x
                               for i, x in enumerate(val)]
        touched += 1
        if len(log) - unhandled_before == 0:
            unhandled.append(rid)
    print(f"{len(log)} field(s) repaired across {touched} unowned requirement(s)")
    for w in log[:40]:
        print(f"   {w}")
    if skipped_owned:
        print(f"{skipped_owned} generator-owned record(s) still mention removed "
              f"vocabulary and were left for the generators")
    if unhandled:
        print(f"records with unhandled phrasing: {', '.join(unhandled)}")
    if log:
        with open(INV_PATH, 'w', encoding='utf-8') as fh:
            json.dump(inv, fh, indent=2)
            fh.write('\n')
        print(f"wrote {INV_PATH}")
    return 1 if unhandled or skipped_owned else 0


if __name__ == '__main__':
    sys.exit(main())
