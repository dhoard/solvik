"""Deterministic, safe execution environment for each test (TCK.md section 12).

Responsibilities:

* Build the adapter environment from an explicit *allowlist*, not the runner's
  inherited environment. Only values needed to locate/execute the adapter and
  manifest-declared deterministic values are passed.
* Create a fresh temporary workspace per test and, separately, immutable
  "compile" and mutable "run" subtrees, so a materializing AOT adapter can keep
  compiled artifacts apart from execution scratch (section 8).
* Stage the fixture tree with full path validation: resolve and validate every
  component before copying; reject device files, FIFOs, sockets, symlinks that
  escape the fixture root, absolute or parent-escaping relative paths, and
  Unicode/case-folding name collisions; re-verify containment after creation.

A temporary directory provides reproducibility, not a security sandbox; callers
are documented to trust or externally sandbox third-party adapters.
"""

from __future__ import annotations

import os
import shutil
import stat
import tempfile
import unicodedata

from . import digest, events

# Environment variables safe to forward, solely to locate/execute the adapter
# binary. Locale, timezone, HOME, TMPDIR, classpath/module path, language option
# and cache variables are DELIBERATELY NOT inherited (section 12): they are set
# to deterministic defaults below and may only be overridden by manifest-declared
# values. Inheriting host TZ/LANG/HOME would make exact-byte orphans depend on
# the machine, which the TCK forbids.
ENV_ALLOWLIST = ("PATH", "JAVA_HOME", "GRAALVM_HOME")

# Deterministic defaults applied unless a manifest declares an override.
_DEFAULT_ENV = {"LANG": "C.UTF-8", "LC_ALL": "C.UTF-8", "TZ": "UTC"}


def build_env(declared: dict, base_env: dict, adapter_bin_dir: str = None) -> dict:
    """Compose the adapter environment from the allowlist + manifest values.

    ``declared`` are manifest environment entries (already validated to be
    ``[A-Z][A-Z0-9_]*`` keys with string values). ``base_env`` is the runner's
    environment, from which only allowlisted names are copied.
    """
    env = {}
    for name in ENV_ALLOWLIST:
        if name in base_env:
            env[name] = base_env[name]
    # Controlled locale/timezone defaults (never the host's).
    env.update(_DEFAULT_ENV)
    env["SOLVIK_TCK_WORKSPACE"] = "1"  # a fixed, non-secret marker, not a per-test path
    for name, value in (declared or {}).items():
        env[name] = value
    return env


# Names whose values are safe to record verbatim in a report (deterministic,
# non-secret). Everything else is redacted to "***" so a report never leaks a
# token, path, or credential (TCK.md section 13).
_PUBLIC_ENV_NAMES = ("LANG", "LC_ALL", "LC_CTYPE", "TZ", "SOLVIK_TCK_WORKSPACE")


def redact_env(env: dict) -> dict:
    """Return {name: value-or-redacted} for recording in a report.

    Only the deterministic, non-secret defaults keep their values; all other
    variable values are replaced with ``***``. Names are always preserved so a
    reproduction command records which variables the adapter actually received.
    """
    out = {}
    for name, value in env.items():
        out[name] = value if name in _PUBLIC_ENV_NAMES else "***"
    return out


class Workspace:
    """A runner-created temporary workspace with compile/run subtrees."""

    def __init__(self, root: str):
        self.root = os.path.realpath(root)
        self.compile_dir = os.path.join(self.root, "compile")
        self.run_dir = os.path.join(self.root, "run")
        os.makedirs(self.compile_dir)
        os.makedirs(self.run_dir)

    @classmethod
    def create(cls, template="solvik-tck-"):
        root = tempfile.mkdtemp(prefix=template)
        return cls(root)

    def remove(self):
        shutil.rmtree(self.root, ignore_errors=True)


class FixtureError(Exception):
    def __init__(self, reason, **extra):
        super().__init__(reason)
        self.reason = reason
        self.extra = extra


def validate_relative_component(rel: str) -> str:
    """Validate a single manifest-declared relative path; reject traversal."""
    if rel.startswith("/") or rel.startswith("\\"):
        raise FixtureError("absolute fixture path is forbidden: %r" % rel)
    if "\\" in rel:
        raise FixtureError("backslash path separator is forbidden: %r" % rel)
    # "." is the schema-legal sentinel for "the whole selected fixture root".
    # Anywhere else a "." component is meaningless and ".." is traversal; both
    # are rejected so isolation validation matches the manifest schema exactly.
    if rel == ".":
        return "."
    parts = rel.split("/")
    if any(part in ("", ".", "..") for part in parts):
        raise FixtureError("path traversal or empty/dot component forbidden: %r" % rel)
    return os.path.join(*parts)


def stage_fixtures(source_root: str, manifest, workspace: Workspace):
    """Copy the validated fixture tree into ``workspace.compile_dir``.

    Returns the canonical input-tree digest of the staged tree. Every source is
    resolved and validated before copying; symlinks and non-regular files are
    rejected; containment is re-checked after the copy. Unicode-normalization and
    case-folding collisions are rejected so a case-insensitive or NFD/NFC host
    cannot silently merge two fixtures.
    """
    staged_root = workspace.compile_dir
    staged_files = {}
    norm_seen = {}
    case_seen = {}

    def _check_name(rel, path):
        # Unicode-normalization name collision (section 12).
        nfc = unicodedata.normalize("NFC", rel)
        if nfc in norm_seen and norm_seen[nfc] != rel:
            raise FixtureError("NFC name collision: %r vs %r" % (rel, norm_seen[nfc]))
        norm_seen[nfc] = rel
        low = rel.lower()
        if low in case_seen and case_seen[low] != rel:
            raise FixtureError("case-folding name collision: %r vs %r" % (rel, case_seen[low]))
        case_seen[low] = rel

    def _copy_tree(src_dir, rel_prefix):
        with os.scandir(src_dir) as it:
            entries = sorted(it, key=lambda e: e.name)
        for entry in entries:
            rel = f"{rel_prefix}{entry.name}" if rel_prefix else entry.name
            _check_name(rel, entry.path)
            # lstat: never follow a symlink to inspect the target's identity.
            st = os.lstat(entry.path)
            mode = st.st_mode
            if stat.S_ISLNK(mode):
                target = os.path.realpath(entry.path)
                real_src = os.path.realpath(source_root)
                if not _contained(target, real_src):
                    raise FixtureError("symlink escapes fixture root: %r -> %r" % (rel, target))
                # Contained symlinks are dereferenced into regular copies.
                if not os.path.isfile(entry.path):
                    raise FixtureError("symlink does not resolve to a regular file: %r" % rel)
                _copy_file(entry.path, rel)
            elif stat.S_ISDIR(mode):
                os.makedirs(os.path.join(staged_root, rel), exist_ok=True)
                _copy_tree(entry.path, rel + "/")
            elif stat.S_ISREG(mode):
                _copy_file(entry.path, rel)
            else:
                raise FixtureError(
                    "non-regular fixture file (device/fifo/socket) forbidden: %r" % rel
                )

    def _copy_file(src, rel):
        dst = os.path.join(staged_root, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        # Reject before copy, then re-verify containment after creation.
        if not _contained(os.path.abspath(dst), staged_root):
            raise FixtureError("staged path escapes workspace: %r" % rel)
        shutil.copyfile(src, dst)
        if not _contained(os.path.realpath(dst), staged_root):
            os.remove(dst)
            raise FixtureError("staged path escaped workspace after creation: %r" % rel)
        staged_files[rel] = digest.sha256_file(dst)

    entry = validate_relative_component(manifest["entryPoint"])
    entry_abs = os.path.join(source_root, entry)
    if not os.path.isfile(entry_abs):
        raise FixtureError("missing source entry point: %r" % manifest["entryPoint"])

    # Stage the declared fixture root if present, else just the entry file(s).
    if "fixtureRoot" in manifest:
        fr = validate_relative_component(manifest["fixtureRoot"])
        fr_abs = os.path.join(source_root, fr)
        if not os.path.isdir(fr_abs):
            raise FixtureError("fixture root is not a directory: %r" % manifest["fixtureRoot"])
        _copy_tree(fr_abs, "")
    if "fixtures" in manifest:
        for f in manifest["fixtures"]:
            rf = validate_relative_component(f)
            src = os.path.join(source_root, rf)
            if not os.path.isfile(src):
                raise FixtureError("declared fixture missing: %r" % f)
            _copy_file(src, rf)
    # Ensure the entry point itself is present (it may not be under fixtureRoot).
    if os.path.basename(manifest["entryPoint"]) not in staged_files and \
            manifest["entryPoint"] not in staged_files:
        # entry point may already be staged via fixtureRoot; verify by relpath.
        dest = os.path.join(staged_root, entry)
        if not os.path.isfile(dest):
            _copy_file(entry_abs, entry)
            staged_files[entry] = digest.sha256_file(dest)

    # Final containment sweep over the whole staged tree.
    for dirpath, dirnames, filenames in os.walk(staged_root):
        for name in filenames:
            full = os.path.join(dirpath, name)
            if not _contained(os.path.realpath(full), staged_root):
                raise FixtureError("staged tree containment violated: %r" % full)
    return digest.canonical_tree_digest(staged_root), staged_files


def _contained(child: str, parent: str) -> bool:
    child = os.path.abspath(child)
    parent = os.path.abspath(parent)
    return child == parent or child.startswith(parent + os.sep)
