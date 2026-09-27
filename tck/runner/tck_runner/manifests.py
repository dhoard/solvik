"""Portable test-manifest loading, discovery, and validation.

Schema validation (``schema.py``) proves a manifest is well formed. This module
adds the corpus-level checks that a per-document schema cannot express
(TCK.md section 7):

* deterministic discovery order (sorted by manifest path / test id);
* unique test identifiers across the corpus;
* source entry point and declared fixtures exist on disk;
* cross-file agreement with the requirements inventory (delegated to
  ``inventory.check_manifest_against_inventory``); and
* that the whole corpus is validated *before* any adapter is invoked.

A manifest defect is always an infrastructure error, never an implementation
failure, and it blocks validation of the selected corpus.
"""

from __future__ import annotations

import glob
import os

from . import digest, inventory, schema as S, strict_json


class ManifestError(Exception):
    def __init__(self, reason, **extra):
        super().__init__(reason)
        self.reason = reason
        self.extra = extra


def discover(corpus_root: str):
    """Return manifest file paths in a deterministic (sorted) order."""
    paths = glob.glob(os.path.join(corpus_root, "**", "*.manifest.json"), recursive=True)
    return sorted(paths)


def load_manifest(path: str, schema: dict):
    try:
        with open(path, "rb") as handle:
            doc = strict_json.loadb(handle.read())
    except (OSError, strict_json.StrictJSONError) as exc:
        raise ManifestError("%s: %s" % (path, exc)) from exc
    try:
        S.validate(schema, doc)
    except S.ValidationError as exc:
        raise ManifestError("%s: manifest schema invalid: %s" % (path, exc)) from exc
    doc["__path__"] = path
    doc["__digest__"] = digest.sha256_file(path)
    return doc


def corpus_digest(manifest_paths) -> str:
    """Deterministic digest binding a report to the exact selected manifests."""
    entries = sorted((p, digest.sha256_file(p)) for p in manifest_paths)
    return digest.sha256_json(entries)


def load_and_validate(corpus_root: str, schema: dict, inventory_model, spec_version: str):
    """Validate the entire selected corpus before any adapter is used.

    Returns a list of validated manifest dicts. Raises :class:`ManifestError`
    (an infrastructure condition) on the first defect so no partial, unvalidated
    corpus reaches an adapter.
    """
    paths = discover(corpus_root)
    manifests = [load_manifest(p, schema) for p in paths]

    seen_ids = {}
    for m in manifests:
        if m["specVersion"] != spec_version:
            raise ManifestError(
                "%s: manifest spec version %r != requested %r"
                % (m["__path__"], m["specVersion"], spec_version)
            )
        tid = m["testId"]
        if tid in seen_ids:
            raise ManifestError("duplicate test identifier %s (in %s and %s)" %
                                (tid, seen_ids[tid], m["__path__"]))
        seen_ids[tid] = m["__path__"]

        # Inventory cross-validation (profile/status/requirement agreement).
        try:
            inventory.check_manifest_against_inventory(inventory_model, m)
        except inventory.InventoryError as exc:
            raise ManifestError("%s: %s" % (m["__path__"], exc)) from exc

        # Fixture presence: the test directory is the manifest's own directory.
        test_dir = os.path.dirname(m["__path__"])
        entry = m["entryPoint"]
        if os.path.isabs(entry) or ".." in entry.split("/"):
            raise ManifestError("%s: entry point escapes test dir" % m["__path__"])
        if not os.path.isfile(os.path.join(test_dir, entry)):
            raise ManifestError("%s: missing source entry point %r" % (m["__path__"], entry))
        for f in m.get("fixtures", []):
            if not os.path.isfile(os.path.join(test_dir, f)):
                raise ManifestError("%s: missing fixture %r" % (m["__path__"], f))
    return manifests
