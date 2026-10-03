#!/usr/bin/env python3
"""Behavioral fake adapter for Solvik TCK runner self-tests.

This is a *fake implementation under test*, deliberately independent of the
runner: it imports only the Python standard library and never ``tck_runner``. It
speaks the newline-delimited protocol over stdin/stdout and reads a JSON behavior
script named by ``SOLVIK_TCK_FAKE_BEHAVIOR`` to decide how to behave -- either as
a conforming adapter or as a defective one, so the self-tests can prove the runner
*rejects* bad implementations (acceptance criteria 7 and 16).

Guest stdout/stderr are emitted as base64 response fields, never on the adapter's
own stdout (which carries only protocol lines). A misbehaving variant can, however,
attempt to inject protocol-looking JSON on stdout/stderr to verify the runner is
not fooled by it.

Behavior script keys (all optional, sane defaults):

  describe:  {name, version, fingerprint, specVersions, profiles, capabilities, limits}
  compile:   {result: accepted|rejected, artifactFiles: [..], diagnostics: [..]}
  execute:   {result: normal|runtime|implementation_failure, stdout, stderr,
              languageExit, runtimeCategory, location}
  faults:    {wrongProtocolVersion, wrongRequestId, duplicateKeys, extraCompileOutput,
              noArtifactHandle, nonzeroExit, crash, sleepCompileMs, sleepExecuteMs,
              oversizedResponse, garbageLine, executeAfterReject, injectOnStderr,
              artifactMismatch, mutateSource, reusedHandle, changedIdentity,
              executeBeforeCompile}
"""

import base64
import hashlib
import json
import os
import sys
import time

FAKE_LIMITS = {
    "maxRequestBytes": 1 << 20,
    "maxResponseBytes": 1 << 20,
    "maxCapturedOutputBytes": 1 << 20,
    "maxSourceTreeBytes": 1 << 24,
}


def load_behavior():
    path = os.environ.get("SOLVIK_TCK_FAKE_BEHAVIOR")
    if not path:
        return {}
    with open(path, "rb") as handle:
        return json.loads(handle.read().decode("utf-8"))


def write_raw(line: str):
    # stdout carries protocol lines ONLY.
    sys.stdout.write(line + "\n")
    sys.stdout.flush()


def write_json(obj: dict):
    # Canonical json.dumps; adversarial variants build raw strings instead.
    write_raw(json.dumps(obj, separators=(",", ":"), sort_keys=True))


def fault_response(req, behavior):
    """Return True if a fault consumed this request (no normal response sent)."""
    faults = behavior.get("faults", {})
    op = req.get("op")

    if op == "compile":
        if faults.get("extraCompileOutput"):
            # Emit application-looking stdout during compile-only validation.
            obj = accepted_compile(req, behavior)
            obj["stdoutBase64"] = base64.b64encode(b"APP-RAN").decode()
            _emit(obj, faults)
            return True
        if faults.get("noArtifactHandle"):
            obj = accepted_compile(req, behavior)
            del obj["artifactHandle"]
            _emit(obj, faults)
            return True
        if faults.get("oversizedResponse"):
            write_raw("[" + "1," * 3_000_000 + "1]")
            return True
        if faults.get("garbageLine"):
            write_raw("this is not json")
            return True
        if faults.get("duplicateKeys"):
            write_raw('{"protocolVersion":"1","protocolVersion":"1","schemaVersion":1,'
                      '"requestId":%d,"op":"compile","status":"COMPILE_ACCEPTED",'
                      '"artifactHandle":"%s","artifactManifest":[]}'
                      % (req.get("requestId", 0), "a" * 64))
            return True

    if op == "execute":
        if faults.get("executeAfterReject") or faults.get("executeBeforeCompile"):
            obj = normal_execute(req, behavior)
            _emit(obj, faults)
            return True

    return False


def _apply_id_fault(obj, faults):
    if faults.get("wrongRequestId"):
        obj["requestId"] = obj.get("requestId", 0) + 999
    if faults.get("wrongProtocolVersion"):
        obj["protocolVersion"] = "0"
    return obj


def _emit(obj, faults):
    _apply_id_fault(obj, faults)
    write_json(obj)


def _describe_count():
    """Cross-process describe counter via a file (preflight then per-test run).

    Preflight and each per-test session are separate adapter processes, so a
    per-process counter cannot model mid-run identity change. A counter file
    makes the change deterministic: run_suite runs preflight before any test.
    """
    path = os.environ.get("SOLVIK_TCK_FAKE_COUNTER")
    if not path:
        return 1
    n = 0
    try:
        with open(path, "r") as fh:
            n = int(fh.read() or "0")
    except (OSError, ValueError):
        n = 0
    n += 1
    with open(path, "w") as fh:
        fh.write(str(n))
    return n


def describe_impl(behavior):
    count = _describe_count()
    d = behavior.get("describe", {})
    impl = {
        "name": d.get("name", "fake-iut"),
        "version": d.get("version", "0.0.0"),
        "fingerprint": d.get("fingerprint", "a" * 64),
        "specVersions": d.get("specVersions", ["2026.11-draft"]),
        "profiles": d.get("profiles", ["full-language"]),
        "capabilities": d.get("capabilities", ["compile-only"]),
        "limits": d.get("limits", FAKE_LIMITS),
    }
    faults = behavior.get("faults", {})
    # changedIdentity models an adapter that mutates its identity *during* a run:
    # the preflight describe (first) is clean, the per-test describe (second) is
    # mutated, so the runner's describe-match check must reject it.
    if faults.get("changedIdentity") and count > 1:
        impl["version"] = impl["version"] + "-MUTATED"
    return impl


def accepted_compile(req, behavior):
    """Accept compilation and materialize artifacts in the compile workspace.

    A conforming ahead-of-time adapter writes real artifact files whose digests
    match the declared manifest. The fake writes each artifact's content as its
    own path string and declares ``sha256(content)`` so the runner's artifact
    verification passes for a conforming adapter. Fault hooks then simulate
    missing artifacts, mismatched digests, and source mutation.
    """
    compile_spec = behavior.get("compile", {})
    faults = behavior.get("faults", {})
    files = compile_spec.get("artifactFiles", [])
    manifest = []
    for rel in files:
        content = rel  # canonical content for a conforming artifact
        if not faults.get("artifactMissing"):
            _write_artifact(rel, content, mismatch=faults.get("artifactMismatch"))
        manifest.append({"path": rel, "digest": hashlib.sha256(content.encode()).hexdigest()})
    manifest.sort(key=lambda e: e["path"])
    # Explicit artifact overrides (path -> content) for mismatch scenarios.
    for rel, content in compile_spec.get("writeFiles", {}).items():
        _write_artifact(rel, content)
        manifest = [e for e in manifest if e["path"] != rel]
        manifest.append({"path": rel, "digest": hashlib.sha256(content.encode()).hexdigest()})
        manifest.sort(key=lambda e: e["path"])
    # A defective adapter that edits its own inputs after staging.
    if faults.get("mutateSource"):
        try:
            with open(faults["mutateSource"] if isinstance(faults["mutateSource"], str)
                      else "main.sol", "w") as fh:
                fh.write("MUTATED\n")
        except OSError:
            pass
    handle = hashlib.sha256(
        (req.get("inputTreeDigest", "") + ":" + req.get("entryPoint", "")).encode()).hexdigest()
    return {
        "protocolVersion": "1", "schemaVersion": 1, "requestId": req["requestId"], "op": "compile",
        "status": "COMPILE_ACCEPTED", "artifactHandle": handle, "artifactManifest": manifest,
    }


def _write_artifact(rel, content, mismatch=False):
    # The adapter's cwd is the compile workspace; write a workspace-relative file.
    if os.path.isabs(rel) or ".." in rel.split("/"):
        return
    parent = os.path.dirname(rel)
    if parent:
        os.makedirs(parent, exist_ok=True)
    with open(rel, "w") as fh:
        fh.write("WRONG" if mismatch else content)


def rejected_compile(req, behavior):
    diags = behavior.get("compile", {}).get("diagnostics", [{"family": "TYPE", "code": "SOLV-TYPE-009"}])
    return {
        "protocolVersion": "1", "schemaVersion": 1, "requestId": req["requestId"], "op": "compile",
        "status": "COMPILE_REJECTED", "diagnostics": diags,
    }


def normal_execute(req, behavior):
    ex = behavior.get("execute", {})
    return {
        "protocolVersion": "1", "schemaVersion": 1, "requestId": req["requestId"], "op": "execute",
        "status": "NORMAL_EXIT", "languageExit": ex.get("languageExit", 0),
        "stdoutBase64": base64.b64encode(ex.get("stdout", "").encode()).decode(),
        "stderrBase64": base64.b64encode(ex.get("stderr", "").encode()).decode(),
    }


def main():
    behavior = load_behavior()
    faults = behavior.get("faults", {})
    compile_accepted = False
    try:
        for raw in sys.stdin:
            raw = raw.strip("\r\n")
            if raw == "":
                continue
            try:
                req = json.loads(raw)
            except Exception:
                # A malformed request is the adapter's problem; exit nonzero so the
                # runner observes loss of the protocol channel.
                sys.exit(3)
            op = req.get("op")
            if faults.get("sleepCompileMs") and op == "compile":
                time.sleep(faults["sleepCompileMs"] / 1000.0)
            if faults.get("sleepExecuteMs") and op == "execute":
                time.sleep(faults["sleepExecuteMs"] / 1000.0)

            if op == "describe":
                # describe is always emitted fault-free at the framing level; a
                # wrong protocol version / request id on describe would fail the
                # preflight rather than the intended compile/execute phase. Only
                # the identity content (changedIdentity) varies, per above.
                write_json({"protocolVersion": "1", "schemaVersion": 1,
                            "requestId": req["requestId"], "op": "describe",
                            "implementation": describe_impl(behavior)})
                continue

            if op == "compile":
                if fault_response(req, behavior):
                    if faults.get("crash") or faults.get("nonzeroExit"):
                        sys.exit(1 if faults.get("nonzeroExit") else 139)
                    continue
                if behavior.get("compile", {}).get("result") == "rejected":
                    _emit(rejected_compile(req, behavior), faults)
                else:
                    compile_accepted = True
                    _emit(accepted_compile(req, behavior), faults)
                if faults.get("crash") or faults.get("nonzeroExit"):
                    sys.exit(139 if faults.get("crash") else 1)
                continue

            if op == "execute":
                if faults.get("injectOnStderr"):
                    # Attempt to forge protocol data on the *adapter's own stderr*;
                    # the runner captures stderr as diagnostics only, never as the
                    # protocol channel, so this must be harmless.
                    sys.stderr.write('{"protocolVersion":"1","op":"execute",'
                                     '"requestId":99,"status":"NORMAL_EXIT",'
                                     '"languageExit":0,"stdoutBase64":"Zm9yZ2Vk"}\n')
                    sys.stderr.flush()
                # A conforming adapter refuses to execute without a prior accepted
                # compile in the same session; a defective one ignores this.
                if not compile_accepted and not (
                        faults.get("executeAfterReject") or faults.get("executeBeforeCompile")):
                    sys.exit(4)
                if fault_response(req, behavior):
                    continue
                result = behavior.get("execute", {}).get("result", "normal")
                if result == "runtime":
                    ex = behavior.get("execute", {})
                    obj = {"protocolVersion": "1", "schemaVersion": 1,
                           "requestId": req["requestId"], "op": "execute",
                           "status": "RUNTIME_FAILURE",
                           "runtimeCategory": ex.get("runtimeCategory", "ARITHMETIC_ERROR")}
                    if "location" in ex:
                        obj["location"] = ex["location"]
                    _emit(obj, faults)
                elif result == "implementation_failure":
                    _emit({"protocolVersion": "1", "schemaVersion": 1,
                           "requestId": req["requestId"], "op": "execute",
                           "status": "IMPLEMENTATION_FAILURE", "message": "caught crash"}, faults)
                else:
                    _emit(normal_execute(req, behavior), faults)
                continue

            # Unknown op: protocol violation on the adapter's part.
            sys.exit(5)
    except BrokenPipeError:
        sys.exit(0)
    # Clean end of stdin: exit 0 (protocol session closed cleanly).
    sys.exit(0)


if __name__ == "__main__":
    main()
