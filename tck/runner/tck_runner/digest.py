"""Canonical content digests used to bind reports to exact TCK inputs.

Section 5 requires reports to record content digests for the requirements
inventory, selected manifests, and adapter configuration; section 8 requires
SHA-256 over a canonical tree serialization for staged inputs and artifacts. All
digests here are SHA-256 (lowercase hex) over deterministic byte sequences so a
result can be tied to the exact bytes that produced it.
"""

from __future__ import annotations

import hashlib
import os

from . import strict_json

SHA256_LEN = 64


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_text(text: str) -> str:
    return sha256_hex(text.encode("utf-8"))


def sha256_json(obj) -> str:
    """Digest of a logical JSON document, independent of key order/whitespace."""
    return sha256_text(strict_json.dumps_canonical(obj))


def sha256_file(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def canonical_tree_digest(root: str) -> str:
    """SHA-256 over a canonical serialization of a regular-file tree.

    Entries are sorted by workspace-relative POSIX path and each contributes its
    path and content digest, so the result depends only on which files exist and
    their bytes -- not on filesystem iteration order, inode numbers, or mtimes.
    Symlinks and non-regular files are excluded here; the caller is responsible
    for rejecting unsafe fixtures before staging (section 12).
    """
    entries = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames.sort()
        for name in sorted(filenames):
            full = os.path.join(dirpath, name)
            if os.path.islink(full) or not os.path.isfile(full):
                continue
            rel = os.path.relpath(full, root).replace(os.sep, "/")
            entries.append((rel, sha256_file(full)))
    payload = strict_json.dumps_canonical(entries).encode("utf-8")
    return sha256_hex(payload)
