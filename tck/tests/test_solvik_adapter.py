#!/usr/bin/env python3
"""Solvik launcher adapter self-tests (Python only; no Solvik/Java/GraalVM).

The launcher adapter is the bridge between the portable runner and a Solvik
distribution. These self-tests verify its protocol-translation logic -- which the
corpus runs cannot fully exercise on a machine without a built distribution --
entirely with the Python standard library:

  * the pure translation helpers (UTF-16 char -> UTF-8 byte offsets, family/code
    validation, exit clamping, base64, artifact-handle binding, path containment);
  * fingerprint stability and distinctness across two distributions;
  * the full protocol state machine driven through a *fake launcher* executable
    that mimics the real launcher's structured ``--compile-only`` / ``--run-json``
    contract, so compile-accept, compile-reject, normal exit, ``exit(n)``, runtime
    failure, crash-without-a-record, and execute-before-compile are all proven
    without GraalVM.

The fake launcher is a small Python program (not a shell string); commands are
argument arrays, matching the runner's own no-shell discipline.
"""

import base64
import hashlib
import importlib.util
import json
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ADAPTER = os.path.abspath(os.path.join(HERE, "..", "adapters", "solvik_launcher_adapter.py"))

# Load the adapter module directly (it is stdlib-only and import-safe: no side
# effects at import beyond constants).
_spec = importlib.util.spec_from_file_location("solvik_launcher_adapter", ADAPTER)
AD = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(AD)

RESULTS = []


def check(label, cond):
    RESULTS.append((label, bool(cond)))
    if not cond:
        print("FAIL:", label)


# --------------------------------------------------------------------------
# pure translation helpers
# --------------------------------------------------------------------------

def test_offsets():
    ws = tempfile.mkdtemp()
    # "🎈" is one UTF-16 code unit but four UTF-8 bytes; put three before a marker.
    path = os.path.join(ws, "main.sol")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write('val s = "🎈🎈🎈"\nMARKER\n')
    with open(path, "rb") as fh:
        text = fh.read().decode("utf-8")
    table = AD.char_to_byte_offsets(path)
    # Independent expectation via str.encode length.
    expected = sum(len(ch.encode("utf-8")) for ch in text)
    check("byte table final matches utf-8 length", table[len(text)] == expected)
    # The three 4-byte emoji add 3*(4-1) = 9 extra bytes versus char count.
    check("byte length exceeds char length by 9", table[len(text)] - len(text) == 9)
    char_start = text.index("MARKER")
    conv = AD.convert_location(table, char_start, char_start + len("MARKER"))
    byte_start = len(text[:char_start].encode("utf-8"))
    check("char->byte start correct", conv["startByteOffset"] == byte_start)
    check("char->byte end correct", conv["endByteOffset"] == byte_start + len("MARKER"))
    # End before start is clamped to a non-decreasing interval.
    clamped = AD.convert_location(table, 10, 3)
    check("inverted interval clamped", clamped["endByteOffset"] >= clamped["startByteOffset"])
    # Unreadable file yields None (caller omits location, never fabricates).
    check("missing file offsets None", AD.char_to_byte_offsets(os.path.join(ws, "no.sol")) is None)
    check("convert with None table is None", AD.convert_location(None, 0, 5) is None)


def test_family_and_code():
    check("family parsed from code", AD._family_from_code("SOLV-SEM-045") == "SEM")
    check("family empty on short code", AD._family_from_code("SOLV-SEM") == "")
    check("code valid SEM", AD._code_matches("SOLV-SEM-045"))
    check("code valid TYPE", AD._code_matches("SOLV-TYPE-001"))
    check("code valid 4 digits", AD._code_matches("SOLV-RESOL-0008"))
    check("code rejects lowercase family", not AD._code_matches("SOLV-sem-045"))
    check("code rejects 5 digits", not AD._code_matches("SOLV-SEM-00045"))
    check("code rejects 1 digit", not AD._code_matches("SOLV-SEM-4"))
    check("code rejects no SOLV prefix", not AD._code_matches("XXX-SEM-045"))
    check("code rejects empty", not AD._code_matches(""))


def test_bounded_exit():
    check("exit 0", AD._bounded_exit(0) == 0)
    check("exit 7", AD._bounded_exit(7) == 7)
    check("exit 255", AD._bounded_exit(255) == 255)
    check("exit 300 wraps", AD._bounded_exit(300) == 44)
    check("exit negative clamps", AD._bounded_exit(-1) == 0)
    check("exit non-int clamps", AD._bounded_exit("x") == 0)


def test_b64_roundtrip():
    check("encode/decode empty", AD._decode_b64(AD._encode_b64(b"")) == b"")
    data = bytes(range(256))
    check("encode/decode all bytes", AD._decode_b64(AD._encode_b64(data)) == data)


def test_containment():
    ws = tempfile.mkdtemp()
    check("plain relative contained", AD._is_contained(ws, "main.sol"))
    check("nested contained", AD._is_contained(ws, "a/b.sol"))
    check("absolute rejected", not AD._is_contained(ws, "/etc/passwd"))
    check("dotdot rejected", not AD._is_contained(ws, "../secret.sol"))
    check("backslash rejected", not AD._is_contained(ws, "a\\b.sol"))


def test_is_compile_error():
    check("valid compile error", AD._is_compile_error(
        {"phase": "compile", "status": "COMPILE_ERROR", "diagnostics": []}))
    check("wrong phase rejected", not AD._is_compile_error(
        {"phase": "execute", "status": "COMPILE_ERROR", "diagnostics": []}))
    check("non-list diagnostics rejected", not AD._is_compile_error(
        {"phase": "compile", "status": "COMPILE_ERROR", "diagnostics": {}}))
    check("None rejected", not AD._is_compile_error(None))


def test_handle_binding():
    td = hashlib.sha256(b"tree").hexdigest()
    handle = hashlib.sha256((td + "\0main.sol").encode()).hexdigest()
    check("handle is 64 hex", len(handle) == 64 and all(c in "0123456789abcdef" for c in handle))
    # A different entry or digest must bind a different handle (execute validation).
    other = hashlib.sha256((td + "\0other.sol").encode()).hexdigest()
    check("handle binds entry", handle != other)


def test_translate_diagnostics_full():
    """A realistic launcher COMPILE_ERROR translated with a non-ASCII source."""
    ws = tempfile.mkdtemp()
    src = 'val s = "\U0001f388\U0001f388\U0001f388"\nclass P { override func equals(o: Any?): Boolean { return false } }\n'
    with open(os.path.join(ws, "main.sol"), "w", encoding="utf-8") as fh:
        fh.write(src)
    with open(os.path.join(ws, "main.sol"), "rb") as fh:
        text = fh.read().decode("utf-8")
    char_start = text.index("override")
    char_end = char_start + len("override")
    structured = {"phase": "compile", "status": "COMPILE_ERROR", "entryFile": "main.sol",
                  "diagnostics": [{"family": "SEM", "code": "SOLV-SEM-045", "text": "x",
                                   "file": "main.sol", "startCharOffset": char_start,
                                   "endCharOffset": char_end}]}
    out = AD.translate_diagnostics(structured, ws)
    check("one diagnostic translated", len(out) == 1)
    expected_byte = len(text[:char_start].encode("utf-8"))
    check("translated byte start", out[0]["location"]["startByteOffset"] == expected_byte)
    check("translated family+code", out[0]["family"] == "SEM" and out[0]["code"] == "SOLV-SEM-045")
    # A diagnostic whose family is not protocol-legal is dropped (never emitted).
    structured2 = {"phase": "compile", "status": "COMPILE_ERROR", "entryFile": "main.sol",
                   "diagnostics": [{"family": "LOWER", "code": "SOLV-LOWER-001", "text": "t",
                                    "file": "main.sol", "startCharOffset": 0, "endCharOffset": 1}]}
    # LOWER is not a protocol family and its code is unknown -> dropped, so the
    # adapter must treat the whole rejection as unclassifiable (infrastructure)
    # by exiting nonzero, not fabricate a diagnostic.
    r = subprocess.run([sys.executable, "-c",
                        "import importlib.util,sys,os,json;"
                        "spec=importlib.util.spec_from_file_location('ad',%r);"
                        "m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);"
                        "m.translate_diagnostics(json.loads(%r),%r)" % (
                            ADAPTER, json.dumps(structured2), ws)],
                       capture_output=True, text=True)
    check("all-diags-dropped exits nonzero", r.returncode != 0)


def test_fingerprint_stable_and_distinct():
    ws = tempfile.mkdtemp()
    a = os.path.join(ws, "solvik-a"); open(a, "w").write("A")
    b = os.path.join(ws, "solvik-b"); open(b, "w").write("B")
    fa = AD.fingerprint([a]); fb = AD.fingerprint([b])
    check("fingerprint stable", AD.fingerprint([a]) == fa)
    check("fingerprint differs across binaries", fa != fb)
    check("fingerprint is 64 hex", len(fa) == 64 and all(c in "0123456789abcdef" for c in fa))


# --------------------------------------------------------------------------
# protocol end-to-end through a fake launcher (no GraalVM)
# --------------------------------------------------------------------------

FAKE_LAUNCHER = r'''#!/usr/bin/env python3
import sys, os
args = sys.argv[1:]
compile_only = "--compile-only" in args
diag = run = None
entry = None
for a in args:
    if a.startswith("--diagnostics-json="): diag = a.split("=",1)[1]
    elif a.startswith("--run-json="): run = a.split("=",1)[1]
    elif not a.startswith("--"): entry = a
behavior = os.environ.get("FAKE_BEHAVIOR","ok")
if compile_only:
    if behavior == "reject":
        # Read the entry to compute a UTF-16 char offset the adapter must convert.
        with open(os.path.join(os.getcwd(), entry), encoding="utf-8") as fh:
            text = fh.read()
        cs = text.index("BAD")
        with open(diag,"w",encoding="utf-8") as fh:
            fh.write('{"phase":"compile","status":"COMPILE_ERROR","entryFile":"%s","diagnostics":'
                     '[{"family":"TYPE","code":"SOLV-TYPE-001","text":"bad","file":"%s",'
                     '"startCharOffset":%d,"endCharOffset":%d}]}' % (entry, entry, cs, cs+3))
        sys.exit(1)
    if behavior == "crash":
        sys.exit(9)  # nonzero with no structured record
    sys.exit(0)
else:
    if behavior == "normal":
        with open(run,"w") as fh:
            fh.write('{"phase":"execute","status":"NORMAL_EXIT","languageExit":0}')
        sys.stdout.write("hello")
        sys.exit(0)
    if behavior == "exit7":
        with open(run,"w") as fh:
            fh.write('{"phase":"execute","status":"NORMAL_EXIT","languageExit":7}')
        sys.exit(7)
    if behavior == "arith":
        with open(run,"w") as fh:
            fh.write('{"phase":"execute","status":"RUNTIME_FAILURE","runtimeCategory":"ARITHMETIC_ERROR",'
                     '"file":"main.sol","startCharOffset":8,"endCharOffset":11}')
        sys.exit(1)
    if behavior == "crash":
        sys.exit(139)  # no structured record -> adapter must treat as infra
    if behavior == "exec-crash":
        # compile succeeds (handled above via sys.exit(0)); run exits nonzero with
        # no --run-json record, i.e. a hard crash that never wrote the outcome.
        sys.exit(139)
    sys.exit(0)
'''


def _session(behavior, source='print(1 .. "x")\n', run_exec=True, exec_first=False):
    """Run a describe->compile[->execute] session against the adapter, given a
    fake launcher behavior. Returns (rc, [parsed responses], stderr)."""
    ws = tempfile.mkdtemp()
    rundir = tempfile.mkdtemp()
    with open(os.path.join(ws, "main.sol"), "w", encoding="utf-8") as fh:
        fh.write(source)
    # Materialize a fake launcher executable (Python, argument array -- not shell).
    launcher = os.path.join(ws, "fakelauncher")
    with open(launcher, "w") as fh:
        fh.write("#!/usr/bin/env python3\n")
        fh.write(FAKE_LAUNCHER)
    os.chmod(launcher, 0o755)
    env = dict(os.environ)
    env["FAKE_BEHAVIOR"] = behavior
    env["SOLVIK_TCK_LAUNCHER_CONFIG"] = json.dumps({"argv": [launcher], "name": "fake-iut", "version": "v"})
    td = hashlib.sha256(("tree-" + behavior).encode()).hexdigest()
    handle = hashlib.sha256((td + "\0main.sol").encode()).hexdigest()
    reqs = []
    rid = 0
    if not exec_first:
        rid += 1; reqs.append({"op": "describe", "protocolVersion": "1", "schemaVersion": 1, "requestId": rid})
        rid += 1; reqs.append({"op": "compile", "protocolVersion": "1", "schemaVersion": 1, "requestId": rid,
                               "workspace": ws, "inputTreeDigest": td, "entryPoint": "main.sol",
                               "compileTimeoutMs": 30000})
    if run_exec:
        rid += 1
        reqs.append({"op": "execute", "protocolVersion": "1", "schemaVersion": 1, "requestId": rid,
                     "workspace": rundir, "artifactHandle": handle, "inputTreeDigest": td,
                     "executeTimeoutMs": 30000, "stdinBase64": ""})
    inp = "\n".join(json.dumps(r) for r in reqs) + "\n"
    p = subprocess.run([sys.executable, ADAPTER], input=inp, capture_output=True, env=env, cwd=ws, text=True)
    responses = [json.loads(l) for l in p.stdout.splitlines() if l.strip()]
    return p.returncode, responses, p.stderr, ws, rundir, handle


def test_protocol_normal():
    rc, resp, err, *_ = _session("normal", run_exec=True)
    by_op = {r["op"]: r for r in resp}
    check("normal compile accepted", by_op["compile"]["status"] == "COMPILE_ACCEPTED")
    ex = by_op["execute"]
    check("normal exit 0", ex["status"] == "NORMAL_EXIT" and ex["languageExit"] == 0)
    check("normal stdout 'hello'", base64.b64decode(ex["stdoutBase64"]) == b"hello")


def test_protocol_exit7():
    rc, resp, err, *_ = _session("exit7", run_exec=True)
    ex = {r["op"]: r for r in resp}["execute"]
    check("exit7 NORMAL_EXIT", ex["status"] == "NORMAL_EXIT")
    check("exit7 languageExit 7", ex["languageExit"] == 7)


def test_protocol_runtime():
    rc, resp, err, *_ = _session("arith", source="println(1/0)\n", run_exec=True)
    ex = {r["op"]: r for r in resp}["execute"]
    check("runtime RUNTIME_FAILURE", ex["status"] == "RUNTIME_FAILURE")
    check("runtime category ARITHMETIC_ERROR", ex["runtimeCategory"] == "ARITHMETIC_ERROR")
    check("runtime carries no languageExit", "languageExit" not in ex)
    check("runtime has converted location", "location" in ex and ex["location"]["startByteOffset"] == 8)


def test_protocol_reject_byte_offsets():
    # Source has a 4-byte emoji before the BAD token, so the fake launcher's char
    # offset must be converted up by 3 by the adapter.
    src = 'val e = "\U0001f388"\nBAD\n'
    rc, resp, err, ws, rundir, handle = _session("reject", source=src, run_exec=False)
    by_op = {r["op"]: r for r in resp}
    diag = by_op["compile"]["diagnostics"][0]
    with open(os.path.join(ws, "main.sol"), "rb") as fh:
        text = fh.read().decode("utf-8")
    byte_start = len(text[:text.index("BAD")].encode("utf-8"))
    check("reject COMPILE_REJECTED", by_op["compile"]["status"] == "COMPILE_REJECTED")
    check("reject family TYPE code", diag["family"] == "TYPE" and diag["code"] == "SOLV-TYPE-001")
    check("reject byte offset converted", diag["location"]["startByteOffset"] == byte_start)


def test_protocol_compile_crash():
    rc, resp, err, *_ = _session("crash", run_exec=False)
    check("compile crash nonzero adapter exit", rc == AD.EXIT_CRASH)
    check("compile crash emits no COMPILE_REJECTED", all("status" not in r for r in resp if r["op"] == "compile"))
    check("compile crash stdout empty", resp == [] or all(r["op"] != "compile" for r in resp))


def test_protocol_execute_crash():
    # Compile accepts, then execution crashes without a structured record: the
    # adapter must exit nonzero (infrastructure), never guess a language result.
    rc, resp, err, *_ = _session("exec-crash", run_exec=True)
    by_op = {r["op"]: r for r in resp}
    check("exec-crash compile accepted", by_op["compile"]["status"] == "COMPILE_ACCEPTED")
    check("exec-crash adapter exits infrastructure", rc == AD.EXIT_CRASH)
    check("exec-crash emits no execute response", "execute" not in by_op)


def test_protocol_execute_before_compile():
    rc, resp, err, *_ = _session("normal", run_exec=True, exec_first=True)
    check("execute-before-compile exits nonzero (rc 4)", rc == 4)
    check("execute-before-compile no response", resp == [])


def test_protocol_reused_handle():
    # Send a describe+compile then an execute with a WRONG handle.
    ws = tempfile.mkdtemp(); rundir = tempfile.mkdtemp()
    with open(os.path.join(ws, "main.sol"), "w") as fh:
        fh.write("print(1)\n")
    launcher = os.path.join(ws, "fakelauncher")
    with open(launcher, "w") as fh:
        fh.write("#!/usr/bin/env python3\n" + FAKE_LAUNCHER)
    os.chmod(launcher, 0o755)
    env = dict(os.environ); env["FAKE_BEHAVIOR"] = "normal"
    env["SOLVIK_TCK_LAUNCHER_CONFIG"] = json.dumps({"argv": [launcher], "name": "fake-iut", "version": "v"})
    td = hashlib.sha256(b"t").hexdigest()
    reqs = [
        {"op": "describe", "protocolVersion": "1", "schemaVersion": 1, "requestId": 1},
        {"op": "compile", "protocolVersion": "1", "schemaVersion": 1, "requestId": 2,
         "workspace": ws, "inputTreeDigest": td, "entryPoint": "main.sol", "compileTimeoutMs": 30000},
        {"op": "execute", "protocolVersion": "1", "schemaVersion": 1, "requestId": 3,
         "workspace": rundir, "artifactHandle": "f" * 64, "inputTreeDigest": td,
         "executeTimeoutMs": 30000, "stdinBase64": ""},
    ]
    inp = "\n".join(json.dumps(r) for r in reqs) + "\n"
    p = subprocess.run([sys.executable, ADAPTER], input=inp, capture_output=True, env=env, cwd=ws, text=True)
    check("wrong artifact handle -> infrastructure exit", p.returncode == AD.EXIT_CRASH)


def test_describe_identity_fields():
    rc, resp, err, *_ = _session("normal", run_exec=False)
    impl = {r["op"]: r for r in resp}["describe"]["implementation"]
    check("describe name", impl["name"] == "fake-iut")
    check("describe spec version", "2026.10-draft" in impl["specVersions"])
    check("describe profile", "full-language" in impl["profiles"])
    check("describe capability", "compile-only" in impl["capabilities"])
    check("describe fingerprint hex", len(impl["fingerprint"]) == 64)


def main():
    test_offsets()
    test_family_and_code()
    test_bounded_exit()
    test_b64_roundtrip()
    test_containment()
    test_is_compile_error()
    test_handle_binding()
    test_translate_diagnostics_full()
    test_fingerprint_stable_and_distinct()
    test_protocol_normal()
    test_protocol_exit7()
    test_protocol_runtime()
    test_protocol_reject_byte_offsets()
    test_protocol_compile_crash()
    test_protocol_execute_crash()
    test_protocol_execute_before_compile()
    test_protocol_reused_handle()
    test_describe_identity_fields()
    failed = [l for l, c in RESULTS if not c]
    print("solvik adapter: %d/%d passed" % (len(RESULTS) - len(failed), len(RESULTS)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
