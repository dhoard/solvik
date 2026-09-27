#!/usr/bin/env python3
"""Verify every quoted normative passage actually occurs in LANGUAGE_SPEC.md.

Motivation (TCK.md section 6.1): an oracle must be derived from the normative text.
While authoring the nullability/numerics batch, a draft justified `SOLV-TYPE-001` by
citing a "section 10.6" diagnostic table that does not exist in the specification. The
citation was plausible, fluent, and invented -- pattern-completed from neighbouring
sections rather than read out of the document. That is the single most dangerous failure
mode for a conformance oracle, because a fabricated quote makes an implementation-chosen
result look specification-mandated.

This module makes the property machine-checked rather than a matter of diligence:
`requirements.json` records, for each requirement, the exact specification passages the
oracle was derived from, and this test greps every one of them out of the specification.
Any passage that does not occur verbatim fails the suite.

Runs with only the Python standard library and the checked-in specification.
"""

import base64
import glob
import json
import os
import re
import sys

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
TCK_ROOT = os.path.dirname(TESTS_DIR)
REPO_ROOT = os.path.dirname(TCK_ROOT)

SPEC_PATH = os.path.join(REPO_ROOT, "docs", "LANGUAGE_SPEC.md")
REQ_PATH = os.path.join(TCK_ROOT, "requirements", "requirements.json")
CORPUS_DIR = os.path.join(TCK_ROOT, "corpus")


def b64decode(text):
    return base64.b64decode(text.encode("ascii")).decode("utf-8", "replace")

_failures = []
_checks = 0


def check(condition, label, detail=""):
    global _checks
    _checks += 1
    if not condition:
        _failures.append("%s%s" % (label, (" -- " + detail) if detail else ""))


def normalize(text):
    """Canonicalize so that quotation-style differences cannot mask a real mismatch.

    Deliberately conservative: it unifies typography, markdown code-span backticks and
    emphasis markers, and whitespace. It does NOT fuzzy-match words, so an altered or
    invented passage still fails.
    """
    text = text.replace("\u2019", "'").replace("\u2018", "'")
    text = text.replace("\u201c", '"').replace("\u201d", '"')
    text = text.replace("\u2014", "--").replace("\u2013", "-")
    text = text.replace("\u00a0", " ")
    text = text.replace("`", "").replace("*", "")
    return re.sub(r"\s+", " ", text).strip()


def load_spec():
    with open(SPEC_PATH, encoding="utf-8") as handle:
        return normalize(handle.read())


def load_headings():
    """Collect the specification heading structure.

    Returns (tops, subs, unnumbered) where
      tops:       {N: title}                     for `## N. Title`
      subs:       {(N, M): title}                for `### N.M Title`
      unnumbered: {N: [title, ...]}              for bare `### Title` inside section N

    Solvik cites subsections by prose title (e.g. "3. Equality and reference identity"
    names an unnumbered `###` inside `## 3. Static and Strong Typing`), so a citation is
    only correct when BOTH the number and the title resolve.
    """
    tops, subs, unnumbered = {}, {}, {}
    cur = None
    for line in open(SPEC_PATH, encoding="utf-8").read().splitlines():
        m = re.match(r"^##\s+(\d+)\.\s+(.*)$", line)
        if m:
            cur = int(m.group(1))
            tops[cur] = m.group(2).strip()
            unnumbered.setdefault(cur, [])
            continue
        m = re.match(r"^###\s+(\d+)\.(\d+)\.?\s+(.*)$", line)
        if m and int(m.group(1)) in tops:
            subs[(int(m.group(1)), int(m.group(2)))] = m.group(3).strip()
            continue
        m = re.match(r"^###\s+(?!\d)(.*)$", line)
        if m and cur is not None:
            unnumbered[cur].append(m.group(1).strip())
    return tops, subs, unnumbered


def ref_exists(ref, tops, subs):
    """True if bare `N` names a top-level heading, or `N.M` names a numbered subsection."""
    parts = [p for p in re.split(r"[.]", ref.strip()) if p.isdigit()]
    if not parts:
        return False
    top = int(parts[0])
    if top not in tops:
        return False
    if len(parts) == 1:
        return True
    return (top, int(parts[1])) in subs


def split_citations(field):
    """Split a `section` field into [(numericRef, title), ...].

    Entries are separated by ' / ' (spaces around the slash) so that a title containing a
    slash, such as `the equals/hashCode pairing rule`, is not split. A segment of the form
    `N. Title (clarifier)` cites section N; a bare `(clarifier)` adds no new citation.
    """
    out = []
    for segment in re.split(r"\s+/\s+", field):
        segment = segment.strip()
        if not segment:
            continue
        m = re.match(r"^(\d+(?:\.\d+)?)(?:\.[\s]*)?(.*)$", segment)
        if m:
            out.append((m.group(1), m.group(2).strip()))
        else:
            check(False, "section field segment is not a citation: %r" % segment,
                  "every segment of a requirement's `section` must begin with a section number")
    return out


def corpus_tests():
    """Every (testId, manifest, comment-stripped source) in the corpus."""
    out = []
    # corpus/<spec-version>/<testId>/<testId>.manifest.json
    for path in sorted(glob.glob(os.path.join(CORPUS_DIR, "*", "*", "*.manifest.json"))):
        with open(path, encoding="utf-8") as handle:
            man = json.load(handle)
        src_path = os.path.join(os.path.dirname(path), man["entryPoint"])
        with open(src_path, encoding="utf-8") as handle:
            raw = handle.read()
        # Oracle prose lives in comments; only executable text may justify an oracle.
        code = "\n".join(l for l in raw.split("\n") if not l.strip().startswith("//"))
        out.append((man["testId"], man, code))
    return out


def check_oracle_independence(ck):
    """Two tests must not share a byte-exact stdout oracle over different programs.

    A SUCCESS oracle is expected output derived from the specification. Two different
    programs deriving to the same exact bytes is the signature of a copy-pasted
    expectation rather than an independently derived one -- a defect that occurred twice
    while authoring this corpus and was caught only by hand-running the programs. A
    rejection test legitimately shares a family-only expectation with its siblings, so
    this applies only to SUCCESS tests asserting a non-empty stdout stream.
    """
    groups = {}
    for tid, man, code in corpus_tests():
        if man["outcome"] != "SUCCESS":
            continue
        exp = man.get("expectation", {})
        payload = b64decode(exp.get("stdoutBase64", ""))
        if not payload:
            continue
        groups.setdefault((exp.get("languageExit"), payload), []).append((tid, code))
    shared = [(payload, [t for t, _ in v])
              for (_, payload), v in groups.items() if len({c for _, c in v}) > 1]
    ck(not shared,
       "no two SUCCESS tests with different programs share an exact stdout oracle",
       "duplicated exact oracle: %s" % shared)


RESERVED_CATEGORIES = ("NULL_DEREFERENCE", "REGEX_FAILURE")


def check_reserved_categories_unused(ck):
    """No manifest may assert a category the specification gives no fault for.

    `NULL_DEREFERENCE` and `REGEX_FAILURE` are reserved protocol members: LANGUAGE_SPEC.md
    defines no null-dereference fault and no regex-evaluation fault, so asserting either
    would invent semantics the specification does not state (protocol.md section 4.1).
    """
    users = [tid for tid, man, _ in corpus_tests()
             if man.get("expectation", {}).get("runtimeCategory") in RESERVED_CATEGORIES]
    ck(not users,
       "no manifest asserts a reserved runtime category",
       "manifests asserting a spec-undefined category: %s" % users)
    # The reservation must be stated in the protocol spec, or it is folklore.
    proto = open(os.path.join(TCK_ROOT, "protocol", "protocol.md"), encoding="utf-8").read()
    for cat in RESERVED_CATEGORIES:
        ck(cat in proto,
           "protocol.md names reserved category %s" % cat,
           "a category with no conformance use still needs a written reservation")
    ck("NULL_DEREFERENCE" in open(os.path.join(TCK_ROOT, "schemas", "manifest-1.schema.json"),
                                  encoding="utf-8").read(),
       "manifest schema still defines the reserved categories it warns about")

    # Symmetric invariant: a category some manifest DOES assert must be defined by a
    # specification sentence in protocol.md section 4.1. The reservation above protects
    # against asserting something undocumented; this protects against the table drifting
    # out of sync with the corpus, which would leave a live oracle citing a rule the
    # protocol document no longer states.
    proto = open(os.path.join(TCK_ROOT, "protocol", "protocol.md"), encoding="utf-8").read()
    i = proto.find("### 4.1")
    j = proto.find("### 4.2")
    table = proto[i:j] if i != -1 and j > i else ""
    used = {man.get("expectation", {}).get("runtimeCategory")
            for _tid, man, _code in corpus_tests()
            if man.get("expectation", {}).get("runtimeCategory")}
    for cat in sorted(used):
        ck(cat in table,
           "asserted runtime category %s is documented in protocol.md section 4.1" % cat,
           "a live oracle depends on this category's specification basis")
    # The table must be non-trivial, or the loop above is vacuous.
    ck(len(used) > 0, "the corpus asserts at least one runtime category")
    ck(table.count("| `") >= len(used),
       "protocol.md section 4.1 tabulates at least as many categories as the corpus uses")


def check_empty_stdout_is_intentional(ck):
    """A SUCCESS test expecting empty stdout must not contain a printing call."""
    offenders = []
    for tid, man, code in corpus_tests():
        if man["outcome"] != "SUCCESS":
            continue
        exp = man.get("expectation", {})
        if "stdoutBase64" not in exp or b64decode(exp["stdoutBase64"]):
            continue
        if re.search(r"\b(print|println)\s*\(", code):
            offenders.append(tid)
    ck(not offenders,
       "every SUCCESS test with an empty stdout oracle prints nothing",
       "programs that print but expect no output: %s" % offenders)


def corpus_dirs():
    """Every <spec-version>/<testId> directory in the corpus, manifest or not."""
    return sorted(d for d in glob.glob(os.path.join(CORPUS_DIR, "*", "*"))
                  if os.path.isdir(d))


def check_oracle_prose_quotes_are_verbatim(ck):
    """Every spec quotation in an oracle comment must actually be in the spec.

    `normativeQuotes` in the requirement inventory is schema-verified, but an oracle
    comment in a `.sol` file is prose that no schema sees. Two corpus tests were committed
    citing sentences that do not exist in LANGUAGE_SPEC.md, and a third batch of drafts was
    discarded for the same reason, so the rule that applies to the inventory must apply to
    the corpus too. A double-quoted passage of six or more words in a comment is read as a
    claim of verbatim specification text; shorter quotes are terminology, and backticked
    spans are Solvik code, which legitimately never appears in prose. A `...` inside a
    quotation is an editorial elision, so each fragment must appear verbatim.
    """
    spec_text = load_spec()
    offenders = []
    for path in sorted(glob.glob(os.path.join(CORPUS_DIR, "*", "*", "*.sol"))):
        for line in open(path, encoding="utf-8").read().split("\n"):
            if not line.strip().startswith("//"):
                continue
            for quote in re.findall(r'"([A-Za-z`][^"]{25,})"', line):
                if len(quote.split()) < 6:
                    continue
                for frag in quote.split("..."):
                    f = normalize(frag)
                    if len(f.split()) < 4:
                        continue
                    if f not in spec_text:
                        offenders.append("%s: %s" % (os.path.basename(os.path.dirname(path)),
                                                     f[:55]))
    ck(not offenders,
       "every multi-word spec quotation in an oracle comment is verbatim",
       "; ".join(offenders[:6]))


def check_runtime_category_table_citations(ck):
    # Every spec quotation in protocol.md section 4.1 must be verbatim.
    #
    # Section 4.1 is the sole stated basis for each runtime category an oracle may assert,
    # and its table has a column headed 'Defining specification text'. A paraphrase sitting
    # in that column is indistinguishable from a rule for anyone auditing an oracle, which
    # is how the taxonomy came to be misattributed to TCK.md section 8 originally. Rows
    # whose Section cell is an em dash are declared editorial and must quote nothing.
    proto = open(os.path.join(TCK_ROOT, "protocol", "protocol.md"), encoding="utf-8").read()
    spec_text = load_spec()
    i, j = proto.find("### 4.1"), proto.find("### 4.2")
    ck(i != -1 and j > i, "protocol.md contains a section 4.1 to audit")
    rows = [r for r in proto[i:j].split("\n") if r.startswith("| `")]
    # The table must cover exactly the schema taxonomy minus the reserved members:
    # reserved categories must NOT gain a specification-grounded row, or the reservation
    # and the table would contradict each other.
    schema_cats = set(json.loads(open(
        os.path.join(TCK_ROOT, "schemas", "manifest-1.schema.json"),
        encoding="utf-8").read())["$defs"]["runtimeCategory"]["enum"])
    table_cats = {r.split("|")[1].strip().replace("`", "") for r in rows}
    ck(table_cats == schema_cats - set(RESERVED_CATEGORIES),
       "section 4.1 tabulates exactly the non-reserved schema categories",
       "table=%s expected=%s" % (sorted(table_cats),
                                 sorted(schema_cats - set(RESERVED_CATEGORIES))))
    ck(not (table_cats & set(RESERVED_CATEGORIES)),
       "no reserved category is given a specification-grounded table row",
       "a reserved category has no defining sentence to cite")
    for row in rows:
        cells = [c.strip() for c in row.strip().strip("|").split("|")]
        cat = cells[0].replace("`", "")
        editorial = cells[-1] == "\u2014"
        quotes = re.findall(r'"([^"]+)"', cells[1])
        if editorial:
            ck(not quotes,
               "editorial runtime category %s quotes no specification text" % cat,
               "a quotation in the defining-specification column reads as a rule")
            continue
        ck(bool(quotes),
           "runtime category %s cites a defining specification sentence" % cat,
           "a category an oracle may assert needs a spec basis, not editorial prose")
        for q in quotes:
            ck(normalize(q) in spec_text,
               "runtime category %s cites verbatim specification text" % cat,
               "not found in LANGUAGE_SPEC.md: %s" % q[:70])


def check_corpus_dirs_are_complete(ck):
    """Every corpus test directory carries both a source and a manifest.

    A directory holding a program but no manifest is invisible to every other corpus
    check and to the runner: it is never executed, never counted, and never reported,
    so its coverage silently vanishes while the inventory still claims the requirement
    is tested. A directory holding a manifest but no entry point is caught by manifest
    validation, but a missing manifest is caught only here.
    """
    incomplete = []
    for d in corpus_dirs():
        tid = os.path.basename(d)
        if not glob.glob(os.path.join(d, tid + ".manifest.json")):
            incomplete.append(tid + " (no manifest)")
        elif not glob.glob(os.path.join(d, "*.sol")):
            incomplete.append(tid + " (no Solvik source)")
    ck(not incomplete,
       "every corpus directory has a manifest and a source",
       "incomplete corpus directories: %s" % incomplete)
    # The corpus must not be empty, or every corpus-derived check above is vacuous.
    ck(len(corpus_tests()) > 0, "the corpus contains at least one complete test")


def check_success_tests_are_executable(ck):
    """A SUCCESS test must not contain a construct the spec makes invalid.

    Section 20 states that executable top-level statements form the entry point and
    that "An explicit `func main` in any participating file remains `SOLV-SEM-001`".
    A program that declares one therefore cannot compile, so a SUCCESS oracle on such
    a program can never be reached: the test would report a failure against an oracle
    that was never actually exercised. This is the shape the section-11 probe fell into,
    where `func main` silently produced empty stdout and no stderr-visible symptom was
    read back into the conclusion.
    """
    offenders = []
    for tid, man, code in corpus_tests():
        if man["outcome"] != "SUCCESS":
            continue
        # `code` from corpus_tests() is already comment-stripped: prose in an oracle
        # note must not be able to trip or silence this check.
        if re.search(r"\bfunc\s+main\b", code):
            offenders.append(tid)
    ck(not offenders,
       "no SUCCESS test declares `func main` (SOLV-SEM-001 per section 20)",
       "SUCCESS tests that cannot compile: %s" % offenders)


def section_of_line(lineno, tops, subs):
    """Return 'N' or 'N.M' naming the heading that contains 1-based `lineno`."""
    cur = None
    for line in open(SPEC_PATH, encoding="utf-8").read().splitlines()[:lineno]:
        m = re.match(r"^###\s+(\d+)\.(\d+)\.?\s", line)
        if m:
            cur = "%s.%s" % (m.group(1), m.group(2))
            continue
        m = re.match(r"^##\s+(\d+)\.", line)
        if m:
            cur = m.group(1)
    return cur


def code_occurrences(code):
    """Sections containing each literal mention of a diagnostic code in the spec."""
    out = []
    lines = open(SPEC_PATH, encoding="utf-8").read().split("\n")
    tops, subs, _ = load_headings()
    for i, line in enumerate(lines):
        if code in line:
            out.append(section_of_line(i + 1, tops, subs))
    return out


def _title_key(title):
    """Canonicalize a heading/citation title so markdown and qualifiers do not matter."""
    t = normalize(title).lower().replace("`", "")
    t = re.sub(r"\(.*?\)", "", t)          # drop parenthetical qualifiers
    t = re.sub(r"[^a-z0-9 ]", " ", t)      # drop punctuation such as the `..` operator
    return re.sub(r"\s+", " ", t).strip()


def title_matches(ref, title, tops, subs, unnumbered):
    """True if `title` is the real heading name for the numeric cite `ref`.

    Accepted forms:
      `N. Top Title`                       -- names the top-level heading
      `N. Unnumbered Subsection Title`     -- names a bare `###` heading inside N
      `N.M Numbered Subsection Title`      -- names that `### N.M` heading
      `N. Anything (clarifier)`            -- a clarifier naming an unnumbered heading
                                             inside N is checked, others are free text
    """
    parts = [int(p) for p in re.split(r"[.]", ref.strip()) if p.isdigit()]
    if not parts:
        return False
    top = parts[0]
    if top not in tops:
        return False
    # A trailing parenthetical is a TCK-authored clarifier ("which rule within the
    # section"), not a citation claim, so it is stripped before the title is matched.
    # The parts that DO assert a location -- the number and the heading title -- are the
    # parts held to the real heading structure.
    stem = re.sub(r"\s*\(.*?\)\s*", " ", title)
    want = _title_key(stem)
    if len(parts) > 1:
        return want == _title_key(subs.get((top, parts[1]), "\x00"))
    if not want:
        return True  # numeric-only citation
    if want == _title_key(tops[top]):
        return True
    return any(want == _title_key(t) for t in unnumbered.get(top, []))


def main():
    if not os.path.isfile(SPEC_PATH):
        print("specification not found: %s" % SPEC_PATH, file=sys.stderr)
        return 2
    spec = load_spec()

    with open(REQ_PATH, encoding="utf-8") as handle:
        inventory = json.load(handle)

    requirements = {r["id"]: r for r in inventory["requirements"]}

    # Every requirement must declare its normative source quotes.
    for rid, req in sorted(requirements.items()):
        quotes = req.get("normativeQuotes")
        check(isinstance(quotes, list) and len(quotes) >= 1,
              "%s declares at least one normativeQuotes passage" % rid,
              "oracleNotes is not sufficient; the quoted source must be machine-checkable")
        if not isinstance(quotes, list):
            continue
        for quote in quotes:
            check(isinstance(quote, str) and len(quote.strip()) >= 15,
                  "%s quote is substantive" % rid, repr(quote)[:80])
            check(normalize(quote) in spec,
                  "%s quote occurs verbatim in LANGUAGE_SPEC.md" % rid,
                  repr(quote)[:120])

    # Every `section` citation must name a heading that actually exists. A section field
    # pointing at an invented section is the same class of defect as an invented quote:
    # it lends false authority to an oracle, so it is checked mechanically.
    tops, subs, unnumbered = load_headings()
    check("specification heading set is non-trivial",
          len(tops) == 23 and 23 in tops and 24 not in tops,
          "found %d top-level sections; the document ends at 23" % len(tops))
    for rid, req in sorted(requirements.items()):
        # A section field may cite several sections separated by '/'; check each segment.
        for ref, title in split_citations(str(req.get("section", ""))):
            # NB: check(condition, label, detail) -- condition comes FIRST.
            check(ref_exists(ref, tops, subs),
                  "%s section citation '%s' names a real section" % (rid, ref),
                  req.get("section", ""))
            check(title_matches(ref, title, tops, subs, unnumbered),
                  "%s section citation '%s' title matches section %s" % (rid, ref, ref),
                  "section %s is %r; unnumbered subsections: %s" % (
                      ref, tops.get(ref), unnumbered.get(ref, [])))
    check("non-existent top-level section is rejected", not ref_exists("24", tops, subs))
    check("non-existent subsection is rejected", not ref_exists("3.77", tops, subs))
    check("real numbered subsection is accepted", ref_exists("22.6", tops, subs))
    # The title check must be able to fail: a real number carrying the wrong title.
    check("wrong title for a real section number is rejected",
          not title_matches("5", "Functions", tops, subs, unnumbered))
    check("correct title for a real section number is accepted",
          title_matches("6", "Functions", tops, subs, unnumbered))
    check("unnumbered subsection title is accepted",
          title_matches("3", "Equality and reference identity", tops, subs, unnumbered))
    check("subsection title under the wrong number is rejected",
          not title_matches("4", "Equality and reference identity", tops, subs, unnumbered))

    # A fabricated-citation canary: the exact invention that this guard was written for.
    check("section 10.6" not in spec,
          "specification has no 'section 10.6' (guards the retracted fabricated citation)")
    check("TYPE_OPERAND" not in spec,
          "specification defines no TYPE_OPERAND code (guards the retracted fabrication)")

    # SOLV-TYPE-001 is bound to three narrow assignability contexts. If the specification
    # ever names it as the *general* assignability code, REQ-0456/0457 may pin the code
    # rather than the family -- so both its count and its locations are pinned.
    occ = code_occurrences("SOLV-TYPE-001")
    check(len(occ) == 3,
          "SOLV-TYPE-001 is mentioned exactly 3 times (occurrences, not lines)",
          "found %d; a line-based count under-reports mentions sharing one line" % len(occ))
    where = sorted(set(occ))
    check(where == ["22.1", "22.6", "7"],
          "SOLV-TYPE-001 occurs in exactly sections 7, 22.1 and 22.6",
          "found %s; REQ-0456 et al. assert TYPE-family-only on the assumption that the "
          "specification names no general assignability code" % where)

    # Corpus-level oracle hygiene, independent of the requirement inventory.
    check_oracle_independence(check)
    check_empty_stdout_is_intentional(check)
    check_reserved_categories_unused(check)
    check_corpus_dirs_are_complete(check)
    check_oracle_prose_quotes_are_verbatim(check)
    check_runtime_category_table_citations(check)
    check_success_tests_are_executable(check)
    check(len(corpus_tests()) > 0,
          "corpus is discoverable by the oracle-hygiene checks",
          "if this is 0 the hygiene checks above are vacuous")

    print("=" * 70)
    if _failures:
        for f in _failures:
            print("FAIL: %s" % f)
        print("%d/%d checks passed" % (_checks - len(_failures), _checks))
        return 1
    print("oracle quotes: %d/%d passed" % (_checks, _checks))
    return 0


if __name__ == "__main__":
    sys.exit(main())
