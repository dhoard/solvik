#!/usr/bin/env python3
# Copyright (c) 2026-present Douglas Hoard
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Solvik launcher adapter: bridges the portable TCK runner to a Solvik launcher.

This is a thin, stdlib-only process that speaks the newline-delimited adapter
protocol (``tck/protocol/protocol.md``) on its own stdin/stdout and drives a
Solvik *launcher* (the JVM distribution ``standalone/target/solvik`` or the native
image ``standalone/target/solvik-native``) as a child process. It imports no
Solvik, GraalVM, Truffle, or runner code, so the JVM and native adapters are just
this one script pointed at two different launcher executables -- distinct IUT
identities that share a faithful translation layer.

Design: the runner is the only protocol endpoint; this adapter is a pure
translator. It relies on the launcher's *structured* channels, never on parsing
human-readable text:

  * compile:   ``<launcher> --compile-only --diagnostics-json=<file> <entry>``
               writes a one-line JSON ``COMPILE_ERROR`` object with structured
               ``SOLV-*`` codes and source locations only on a genuine front-end
               rejection; exit 0 means the program is statically valid.
  * execute:   ``<launcher> --run-json=<file> <entry>`` writes a one-line JSON
               ``NORMAL_EXIT`` / ``RUNTIME_FAILURE`` / ``IMPLEMENTATION_FAILURE``
               outcome on every exit branch, distinguishing an ``exit(n)`` from a
               runtime failure from a crash without process-exit-code guessing.

The launcher reports compile diagnostics and runtime locations in UTF-16 code-unit
character offsets; this adapter -- which owns the exact staged file bytes --
converts them to the protocol's half-open UTF-8 byte-offset convention (protocol
section 3). Guest stdout/stderr are returned as base64 response fields, never on
the adapter's own stdout (which carries only protocol lines). An internal-error
crash (a nonzero launcher exit with no structured record) is surfaced by exiting
nonzero so the runner records an infrastructure error, never a language result.

Configuration is passed by the runner through the ``SOLVIK_TCK_LAUNCHER_CONFIG``
environment variable (a JSON object with ``argv``, ``name``, ``version``); the
runner controls the adapter's whole environment and runs it with a runner-created
workspace as its working directory.
"""

import base64
import hashlib
import json
import os
import subprocess
import sys
import tempfile

PROTOCOL_VERSION = "1"
SCHEMA_VERSION = 1
SPEC_VERSIONS = ["2026.09-draft"]
PROFILES = ["full-language"]
CAPABILITIES = ["compile-only"]

# Limits (all hard; the runner treats exceeding one as an infrastructure result).
# They bound this adapter's own translation, and are advertised so the runner can
# reject oversized inputs before invoking the (slower) launcher.
LIMITS = {
    "maxRequestBytes": 8 * 1024 * 1024,
    "maxResponseBytes": 8 * 1024 * 1024,
    "maxCapturedOutputBytes": 16 * 1024 * 1024,
    "maxSourceTreeBytes": 16 * 1024 * 1024,
    "maxDiagnostics": 512,
    "maxArtifacts": 4096,
    "cancelGraceMs": 5000,
}

# The protocol diagnostic-family enumeration. The launcher derives a family from
# a ``SOLV-<FAMILY>-NNN`` code; a code outside this closed set (e.g. a LOWER-family
# internal code) cannot be emitted as a protocol diagnostic, so its diagnostic is
# dropped rather than producing a protocol violation on a legitimate program.
DIAGNOSTIC_FAMILIES = ("LEX", "PARS", "RESOL", "TYPE", "SEM")

# Runtime categories the protocol accepts; the launcher exposes the same names.
RUNTIME_CATEGORIES = (
    "ARITHMETIC_ERROR", "CAST_FAILURE", "NULL_DEREFERENCE", "COLLECTION_FAILURE",
    "INDEX_OUT_OF_BOUNDS", "UNCAUGHT_EXCEPTION", "REGEX_FAILURE",
    "RESULT_WRONG_VARIANT", "OTHER_RUNTIME_ERROR",
)

# Adapter-side exit codes for infrastructure conditions (the runner only needs
# "nonzero" to classify them, but distinct values aid log reading). None is ever
# reported to the runner as a language result.
EXIT_CRASH = 200       # IUT crashed / no structured record
EXIT_TIMEOUT = 201     # IUT exceeded the operation timeout
EXIT_CONFIG = 202      # malformed adapter configuration


def emit(obj):
    """Write exactly one compact protocol line to stdout and flush it."""
    sys.stdout.write(json.dumps(obj, separators=(",", ":"), sort_keys=True) + "\n")
    sys.stdout.flush()


def envelope(rid, op):
    return {"protocolVersion": PROTOCOL_VERSION, "schemaVersion": SCHEMA_VERSION,
            "requestId": rid, "op": op}


# -- configuration -----------------------------------------------------------

def load_config():
    raw = os.environ.get("SOLVIK_TCK_LAUNCHER_CONFIG")
    if not raw:
        sys.stderr.write("missing SOLVIK_TCK_LAUNCHER_CONFIG\n")
        sys.exit(EXIT_CONFIG)
    try:
        cfg = json.loads(raw)
    except ValueError:
        sys.stderr.write("malformed SOLVIK_TCK_LAUNCHER_CONFIG\n")
        sys.exit(EXIT_CONFIG)
    argv = cfg.get("argv")
    if not isinstance(argv, list) or not argv or not all(isinstance(a, str) for a in argv):
        sys.stderr.write("adapter config argv must be a non-empty array of strings\n")
        sys.exit(EXIT_CONFIG)
    name = cfg.get("name", "solvik")
    version = cfg.get("version", "unknown")
    return {"argv": [str(a) for a in argv], "name": str(name), "version": str(version)}


def _file_digest(path):
    digest = hashlib.sha256()
    try:
        with open(path, "rb") as handle:
            for chunk in iter(lambda: handle.read(1 << 20), b""):
                digest.update(chunk)
    except OSError:
        return None
    return digest.hexdigest()


def fingerprint(argv):
    """A deterministic, content-bound fingerprint of the launcher IUT.

    The JVM launcher is a thin wrapper script whose language implementation lives
    in a sibling ``modules/`` directory; the native image is a self-contained
    binary. The fingerprint therefore hashes the launcher file together with every
    module file (sorted by relative path, each as ``path\\0sha256\\n``), so two
    distributions built from different source revisions -- or the JVM distribution
    versus the native one -- always receive distinct, stable identifiers, while
    every describe within a single conformance run of one build receives the same
    value (the property the runner's frozen-identity check relies on). For the
    native binary there is no sibling modules dir, so it hashes just the binary.
    When nothing is readable the digest falls back to argv so describe stays well
    formed; the adapter's ``--fingerprint`` mode lets a configuration record the
    exact same value.
    """
    target = argv[0]
    digest = hashlib.sha256()
    target_digest = _file_digest(target)
    if target_digest is None:
        return hashlib.sha256(("\0".join(argv) + "\0").encode("utf-8")).hexdigest()
    digest.update(("launcher:" + target_digest + "\n").encode("utf-8"))
    modules = os.path.join(os.path.dirname(os.path.abspath(target)), "modules")
    if os.path.isdir(modules):
        entries = []
        for dirpath, _dirs, files in os.walk(modules):
            for name in files:
                full = os.path.join(dirpath, name)
                rel = os.path.relpath(full, modules).replace(os.sep, "/")
                entries.append(rel)
        for rel in sorted(entries):
            digest.update(("%s\0%s\n" % (rel, _file_digest(os.path.join(modules, rel)))).encode("utf-8"))
    return digest.hexdigest()


# -- source location translation (protocol section 3) ------------------------

def char_to_byte_offsets(path):
    """Return a char-index -> UTF-8 byte-index map for the file at *path*.

    The launcher reports UTF-16 code-unit character offsets; the protocol demands
    half-open UTF-8 byte offsets. This adapter owns the staged file bytes, so it
    performs the single authoritative conversion. Returns ``None`` when the file
    cannot be read or is not valid UTF-8, in which case callers omit location
    rather than fabricate a wrong interval.
    """
    try:
        with open(path, "rb") as handle:
            data = handle.read()
        text = data.decode("utf-8")
    except (OSError, UnicodeDecodeError):
        return None
    table = [0] * (len(text) + 1)
    byte_pos = 0
    for i, ch in enumerate(text):
        table[i] = byte_pos
        byte_pos += len(ch.encode("utf-8"))
    table[len(text)] = byte_pos
    return table


def convert_location(table, start_char, end_char):
    if table is None:
        return None
    n = len(table) - 1
    start = table[min(max(int(start_char), 0), n)]
    end = table[min(max(int(end_char), 0), n)]
    if end < start:
        end = start
    return {"startByteOffset": start, "endByteOffset": end}


# -- describe ----------------------------------------------------------------

def describe(rid, cfg):
    impl = {
        "name": cfg["name"],
        "version": cfg["version"],
        "fingerprint": fingerprint(cfg["argv"]),
        "specVersions": SPEC_VERSIONS,
        "profiles": PROFILES,
        "capabilities": CAPABILITIES,
        "limits": LIMITS,
    }
    out = envelope(rid, "describe")
    out["implementation"] = impl
    emit(out)


# -- compile -----------------------------------------------------------------

def run_launcher(argv, args, cwd, stdin_bytes, timeout_ms):
    """Run the launcher, returning (returncode, stdout_bytes, stderr_bytes).

    Raises TimeoutError when the launcher exceeds ``timeout_ms``; the process tree
    is terminated in that case. ``stdin_bytes`` is fed to the guest's stdin.
    """
    cmd = list(argv) + list(args)
    try:
        proc = subprocess.Popen(
            cmd, cwd=cwd,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            start_new_session=True,
        )
    except OSError as exc:
        sys.stderr.write("failed to launch %r: %s\n" % (cmd, exc))
        sys.exit(EXIT_CRASH)
    stdin = stdin_bytes if stdin_bytes is not None else b""
    try:
        out, err = proc.communicate(input=stdin, timeout=timeout_ms / 1000.0)
        return proc.returncode, out, err
    except subprocess.TimeoutExpired:
        try:
            os.killpg(os.getpgid(proc.pid), 15)  # SIGTERM the whole tree
            out, err = proc.communicate(timeout=2)
        except Exception:
            try:
                os.killpg(os.getpgid(proc.pid), 9)  # SIGKILL
            except Exception:
                pass
            proc.kill()
        raise TimeoutError("launcher exceeded %d ms" % timeout_ms)


def read_structured(path):
    """Read a one-line structured JSON record written by the launcher, or None."""
    try:
        with open(path, "rb") as handle:
            raw = handle.read()
    except OSError:
        return None
    if os.path.exists(path):
        try:
            os.remove(path)
        except OSError:
            pass
    raw = raw.strip()
    if not raw:
        return None
    try:
        obj = json.loads(raw.decode("utf-8"))
    except (ValueError, UnicodeDecodeError):
        return None
    return obj if isinstance(obj, dict) else None


def compile_op(rid, req, cfg):
    """Compile-only validation. Never runs application code (Context.parse only)."""
    workspace = req.get("workspace")
    entry = req.get("entryPoint", "main.sol")
    tree_digest = req.get("inputTreeDigest", "")
    timeout_ms = req.get("compileTimeoutMs", 30000)
    if not workspace:
        sys.stderr.write("compile request missing workspace\n")
        sys.exit(EXIT_CONFIG)

    fd, diag_path = tempfile.mkstemp(prefix="solvik-diag-", suffix=".json")
    os.close(fd)
    try:
        args = ["--compile-only", "--diagnostics-json=" + diag_path, entry]
        rc, _out, _err = run_launcher(cfg["argv"], args, workspace, b"", timeout_ms)
    except TimeoutError:
        _unlink(diag_path)
        sys.stderr.write("compile timed out\n")
        sys.exit(EXIT_TIMEOUT)

    if rc == 0:
        # Statically valid: accept with an in-memory artifact handle bound to this
        # input tree + entry. There is no materialized artifact (the JVM/native
        # launcher re-parses and executes in one step), so the manifest is empty.
        _unlink(diag_path)
        out = envelope(rid, "compile")
        out["status"] = "COMPILE_ACCEPTED"
        out["artifactHandle"] = hashlib.sha256(
            (tree_digest + "\0" + entry).encode("utf-8")).hexdigest()
        out["artifactManifest"] = []
        emit(out)
        return

    structured = read_structured(diag_path)
    _unlink(diag_path)
    if not _is_compile_error(structured):
        # A nonzero compile with no structured COMPILE_ERROR record is an IUT
        # crash / internal error. Surface it as an infrastructure failure (nonzero
        # adapter exit), never as a language COMPILE_REJECTED a test could match.
        sys.stderr.write("compile failed without structured diagnostics (rc=%r)\n" % rc)
        sys.exit(EXIT_CRASH)

    diagnostics = translate_diagnostics(structured, workspace)
    out = envelope(rid, "compile")
    out["status"] = "COMPILE_REJECTED"
    out["diagnostics"] = diagnostics
    emit(out)


def _is_compile_error(obj):
    return (isinstance(obj, dict) and obj.get("phase") == "compile"
            and obj.get("status") == "COMPILE_ERROR"
            and isinstance(obj.get("diagnostics"), list))


def _family_from_code(code):
    # ``SOLV-<FAMILY>-NNN``; the family is the second hyphen-delimited segment.
    parts = code.split("-") if isinstance(code, str) else []
    return parts[1] if len(parts) >= 3 else ""


def translate_diagnostics(structured, workspace):
    """Translate launcher diagnostics to protocol diagnostics (byte offsets)."""
    entry_file = structured.get("entryFile", "")
    result = []
    for d in structured.get("diagnostics", []):
        if not isinstance(d, dict):
            continue
        code = d.get("code", "")
        family = d.get("family", "") or _family_from_code(code)
        # Only specification-shaped diagnostics are protocol-legal; drop the rest
        # (e.g. internal LOWER-family notes) rather than emit an invalid message.
        if family not in DIAGNOSTIC_FAMILIES:
            continue
        if not _code_matches(code):
            continue
        diag = {"family": family, "code": code}
        text = d.get("text")
        if isinstance(text, str) and text:
            diag["text"] = text[:4096]
        file_name = d.get("file") or entry_file
        if isinstance(file_name, str) and file_name and _is_contained(workspace, file_name):
            diag["file"] = file_name
            table = char_to_byte_offsets(os.path.join(workspace, file_name))
            loc = convert_location(table, d.get("startCharOffset", 0), d.get("endCharOffset", 0))
            if loc is not None:
                diag["location"] = loc
        result.append(diag)
        if len(result) >= LIMITS["maxDiagnostics"]:
            break
    # Every specification-shaped compile diagnostic has a family in the closed
    # protocol set (no SOLV-LOWER-* codes exist), so a genuine rejection always
    # yields at least one translated diagnostic. An empty result means the launcher
    # produced a malformed/unclassifiable rejection; inventing a code would let a
    # bogus value silently satisfy a COMPILE_ERROR oracle, so it is surfaced as an
    # infrastructure error (nonzero adapter exit) instead -- never a conformance
    # match. (An empty translated list could also only arise from an IUT that
    # reported a COMPILE_ERROR carrying no diagnostics at all.)
    if not result:
        sys.stderr.write("COMPILE_ERROR carried no protocol-legal diagnostics\n")
        sys.exit(EXIT_CRASH)
    return result


def _code_matches(code):
    if not isinstance(code, str) or not code.startswith("SOLV-"):
        return False
    parts = code.split("-")
    if len(parts) != 3:
        return False
    seg, num = parts[1], parts[2]
    if not seg or not all("A" <= c <= "Z" for c in seg):
        return False
    if not (2 <= len(num) <= 4 and all(c.isdigit() for c in num)):
        return False
    return True


def _is_contained(workspace, rel):
    if rel.startswith("/") or ".." in rel.split("/") or "\\" in rel:
        return False
    real_ws = os.path.realpath(workspace)
    real = os.path.realpath(os.path.join(workspace, rel))
    return real == real_ws or real.startswith(real_ws + os.sep)


# -- execute -----------------------------------------------------------------

def execute_op(rid, req, cfg, session):
    """Execute the previously accepted entry point in the run workspace."""
    expected_handle = hashlib.sha256(
        (req.get("inputTreeDigest", "") + "\0" + session["entry"]).encode("utf-8")).hexdigest()
    if req.get("artifactHandle") != expected_handle:
        # An unknown/reused/expired handle: a protocol violation the runner must
        # see as an infrastructure error, not a language result.
        sys.stderr.write("unknown artifact handle on execute\n")
        sys.exit(EXIT_CRASH)

    run_ws = req.get("workspace")
    entry = session["entry"]
    timeout_ms = req.get("executeTimeoutMs", 30000)
    stdin_bytes = _decode_b64(req.get("stdinBase64", ""))
    if not run_ws:
        sys.stderr.write("execute request missing workspace\n")
        sys.exit(EXIT_CONFIG)

    _stage_into_run(session, run_ws)

    fd, run_path = tempfile.mkstemp(prefix="solvik-run-", suffix=".json")
    os.close(fd)
    try:
        args = ["--run-json=" + run_path, entry]
        rc, out, err = run_launcher(cfg["argv"], args, run_ws, stdin_bytes, timeout_ms)
    except TimeoutError:
        _unlink(run_path)
        sys.stderr.write("execute timed out\n")
        sys.exit(EXIT_TIMEOUT)

    structured = read_structured(run_path)
    _unlink(run_path)
    emit(_execute_response(rid, structured, out, err, rc, run_ws, entry))


def _execute_response(rid, structured, out, err, rc, run_ws, entry):
    response = envelope(rid, "execute")
    if not isinstance(structured, dict) or "status" not in structured:
        # No structured outcome (a hard crash that never wrote the record): an
        # infrastructure error, surfaced by nonzero exit rather than a guess.
        sys.stderr.write("execute produced no structured outcome (rc=%r)\n" % rc)
        sys.exit(EXIT_CRASH)

    status = structured.get("status")
    if status == "NORMAL_EXIT":
        response["status"] = "NORMAL_EXIT"
        response["languageExit"] = _bounded_exit(structured.get("languageExit", 0))
        response["stdoutBase64"] = _encode_b64(out)
        response["stderrBase64"] = _encode_b64(err)
        return response
    if status == "RUNTIME_FAILURE":
        category = structured.get("runtimeCategory", "OTHER_RUNTIME_ERROR")
        if category not in RUNTIME_CATEGORIES:
            category = "OTHER_RUNTIME_ERROR"
        response["status"] = "RUNTIME_FAILURE"
        response["runtimeCategory"] = category
        # The launcher's structured runtime record carries the failure site as
        # top-level ``file``/``startCharOffset``/``endCharOffset`` (UTF-16 code-unit
        # char offsets). Convert them to the protocol's UTF-8 byte-offset interval.
        file_name = structured.get("file") or entry
        if isinstance(file_name, str) and _is_contained(run_ws, file_name):
            table = char_to_byte_offsets(os.path.join(run_ws, file_name))
            converted = convert_location(
                table, structured.get("startCharOffset", 0), structured.get("endCharOffset", 0))
            if converted is not None:
                response["location"] = converted
        # Any partial guest output produced before the failure is preserved.
        response["stdoutBase64"] = _encode_b64(out)
        response["stderrBase64"] = _encode_b64(err)
        return response
    if status == "IMPLEMENTATION_FAILURE":
        response["status"] = "IMPLEMENTATION_FAILURE"
        response["message"] = "caught implementation failure"[:8192]
        return response
    # Unknown structured status: infrastructure error (never a language result).
    sys.stderr.write("unexpected execute status %r\n" % status)
    sys.exit(EXIT_CRASH)


def _stage_into_run(session, run_ws):
    """Copy the entire staged tree from the compile workspace into the run dir.

    The launcher re-parses and executes from the run dir, so every staged file must
    be present there (the runner stages into the compile dir only). Copying the
    whole tree verbatim -- not just the entry ``.sol`` -- makes multi-file
    ``include`` fixtures and any sibling data files behave identically in the run
    workspace, preserving relative paths and skipping the adapter's own scratch
    files (which never live in the staged tree, but the guard is harmless).
    """
    compile_ws = session["workspace"]
    if not compile_ws or not os.path.isdir(compile_ws):
        return
    for dirpath, _dirs, files in os.walk(compile_ws):
        for name in files:
            src = os.path.join(dirpath, name)
            rel = os.path.relpath(src, compile_ws)
            dst = os.path.join(run_ws, rel)
            parent = os.path.dirname(dst)
            if parent and not os.path.isdir(parent):
                os.makedirs(parent, exist_ok=True)
            with open(src, "rb") as rf, open(dst, "wb") as wf:
                wf.write(rf.read())


# -- small helpers -----------------------------------------------------------

def _bounded_exit(value):
    try:
        n = int(value)
    except (TypeError, ValueError):
        return 0
    if n < 0:
        return 0
    return n & 0xFF


def _encode_b64(data):
    return base64.b64encode(data or b"").decode("ascii")


def _decode_b64(field):
    if not field:
        return b""
    try:
        return base64.b64decode(field, validate=True)
    except Exception:
        sys.stderr.write("invalid stdinBase64\n")
        sys.exit(EXIT_CONFIG)


def _unlink(path):
    try:
        os.remove(path)
    except OSError:
        pass


def main():
    # A configuration-generation helper: print the launcher IUT fingerprint for a
    # given executable and exit. Used to record the matching value in an adapter
    # config so the report binds the exact distribution it verified. Not part of
    # a protocol session; the runner never passes this flag.
    if len(sys.argv) == 3 and sys.argv[1] == "--fingerprint":
        sys.stdout.write(fingerprint([sys.argv[2]]) + "\n")
        sys.exit(0)
    cfg = load_config()
    session = None  # {workspace, entry, sources, handle} from the accepted compile
    try:
        for line in sys.stdin:
            line = line.strip("\r\n")
            if not line:
                continue
            try:
                req = json.loads(line)
            except ValueError:
                sys.stderr.write("adapter received a non-JSON request line\n")
                sys.exit(3)
            rid, op = req.get("requestId"), req.get("op")
            if op == "describe":
                describe(rid, cfg)
            elif op == "compile":
                session = {"workspace": req.get("workspace"),
                           "entry": req.get("entryPoint", "main.sol")}
                compile_op(rid, req, cfg)
            elif op == "execute":
                if session is None:
                    # Execution without a prior accepted compile in this session.
                    sys.stderr.write("execute before compile\n")
                    sys.exit(4)
                execute_op(rid, req, cfg, session)
            else:
                sys.stderr.write("unknown op %r\n" % op)
                sys.exit(5)
    except BrokenPipeError:
        sys.exit(0)
    sys.exit(0)


if __name__ == "__main__":
    main()
