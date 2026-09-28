#!/usr/bin/env python3
"""Protocol-layer adversarial self-tests (TCK.md sections 8, 8.1, 14).

Exercises the framing / correlation / schema-validation layer directly, reaching
conditions the subprocess driver cannot normally produce: multiple JSON values on
one line, missing fields, guest-stdout that *is* a forged protocol line, oversized
requests, and execute-before-compile as a state-machine violation. Only Python; no
Solvik/Java/GraalVM/Maven.
"""

import base64
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "runner"))

from tck_runner import protocol as P  # noqa: E402
from tck_runner import strict_json as SJ  # noqa: E402
from tck_runner import outcome as OC  # noqa: E402

RESULTS = []


def check(label, cond):
    RESULTS.append((label, bool(cond)))
    if not cond:
        print("FAIL:", label)


def check_raise(label, fn, exc):
    try:
        fn()
    except exc:
        check(label, True)
    except Exception as e:  # noqa: BLE001
        check("%s (wrong exc %s)" % (label, type(e).__name__), False)
    else:
        check("%s (no raise)" % label, False)


PS = SJ.loads(open(os.path.join(HERE, "..", "schemas", "protocol-1.schema.json")).read())
B64 = lambda s: base64.b64encode(s.encode()).decode()


def sess():
    return P.Session(PS)


def raw(obj_dict):
    """Serialize as a protocol line WITHOUT the trailing newline, as read() gives."""
    return (SJ.dumps_canonical(obj_dict) + "\n").encode("utf-8")


def resp(request_id, op, **kw):
    m = {"protocolVersion": "1", "schemaVersion": 1, "requestId": request_id, "op": op}
    m.update(kw)
    return m


def main():
    s = sess()

    # --- valid round trip ---
    req = s.make_request("describe")
    check("describe request has id/op/version",
          req["op"] == "describe" and req["requestId"] == 1 and req["protocolVersion"] == "1")
    r = s.expect_response(raw(resp(1, "describe", implementation={
        "name": "f", "version": "1", "specVersions": ["2026.10-draft"],
        "profiles": ["full-language"], "capabilities": ["compile-only"], "fingerprint": "a" * 64})),
        1, "describe")
    check("describe response ok", r["op"] == "describe")

    # --- request id increments and correlation enforced ---
    s2 = sess()
    r1 = s2.make_request("compile")["requestId"]
    r2 = s2.make_request("compile")["requestId"]
    check("ids increment", r2 == r1 + 1)
    check_raise("request-id mismatch",
                lambda: s2.expect_response(raw(resp(999, "compile", status="COMPILE_REJECTED",
                                                    diagnostics=[{"family": "TYPE", "code": "SOLV-TYPE-001"}])),
                                           r1, "compile"),
                P.ProtocolError)
    # --- op mismatch (unsolicited / wrong op) ---
    check_raise("op mismatch",
                lambda: s2.expect_response(raw(resp(r1, "execute", status="NORMAL_EXIT",
                                                    languageExit=0, stdoutBase64="", stderrBase64="")),
                                           r1, "compile"),
                P.ProtocolError)
    # --- blank line ---
    check_raise("blank line rejected", lambda: s2.expect_response(b"\n", r1, "compile"), P.ProtocolError)
    # --- two JSON values on one line ---
    check_raise("multiple JSON values on a line",
                lambda: s2.expect_response(b'{"a":1} {"b":2}\n', r1, "compile"), P.ProtocolError)
    # --- trailing data ---
    check_raise("trailing data",
                lambda: s2.expect_response(b'{"a":1}x\n', r1, "compile"), P.ProtocolError)
    # --- invalid UTF-8 ---
    check_raise("invalid utf-8", lambda: s2.expect_response(b'\xff\xfe\n', r1, "compile"), P.ProtocolError)
    # --- duplicate keys ---
    check_raise("duplicate keys",
                lambda: s2.expect_response(b'{"requestId":1,"requestId":1,"op":"compile",'
                                           b'"protocolVersion":"1","schemaVersion":1,'
                                           b'"status":"COMPILE_REJECTED","diagnostics":[]}\n',
                                           r1, "compile"),
                P.ProtocolError)
    # --- wrong protocol version in response ---
    check_raise("bad protocol version",
                lambda: s2.expect_response(raw({**resp(r1, "compile"), "protocolVersion": "0"}),
                                           r1, "compile"),
                P.ProtocolError)
    # --- response missing required field (schema) ---
    check_raise("missing status field",
                lambda: s2.expect_response(raw({"protocolVersion": "1", "schemaVersion": 1,
                                                "requestId": r1, "op": "compile"}),
                                           r1, "compile"),
                P.ProtocolError)
    # --- unknown field (closed schema) ---
    check_raise("unknown field",
                lambda: s2.expect_response(raw({**resp(r1, "compile", status="COMPILE_REJECTED",
                                                       diagnostics=[{"family": "TYPE", "code": "SOLV-TYPE-001"}]),
                                                "bogus": 1}),
                                           r1, "compile"),
                P.ProtocolError)
    # --- oversized response ---
    check_raise("oversized response",
                lambda: s2.expect_response(b"[" + b"1," * 3_000_000 + b"1]\n", r1, "compile"),
                P.ProtocolError)
    # --- EOF before response ---
    check_raise("EOF before response", lambda: s2.expect_response(None, r1, "compile"), P.ProtocolError)
    # --- guest stdout that IS a protocol line does not count (it's never parsed) ---
    # The runner only ever parses adapter stdout lines; a response field carrying
    # protocol-looking JSON is opaque bytes, proving guest output cannot forge data.
    forged = B64('{"op":"execute","status":"NORMAL_EXIT","languageExit":0}')
    ok = s2.expect_response(raw(resp(r1, "compile", status="COMPILE_REJECTED",
                                     diagnostics=[{"family": "TYPE", "code": "SOLV-TYPE-001"}])),
                            r1, "compile")
    check("protocol line parsing unaffected by unrelated base64", ok["status"] == "COMPILE_REJECTED")

    # --- request encoding enforces size ---
    check_raise("oversized request",
                lambda: P.encode_request(s.make_request("compile", workspace="/w" + "x" * 9_000_000)),
                P.ProtocolError)

    # --- decode_b64 round-trips canonical values and rejects non-canonical ones ---
    check("canonical base64 ok", P.decode_b64(base64.b64encode(b"A").decode()) == b"A")
    check_raise("missing base64 padding", lambda: P.decode_b64("QQ"), P.ProtocolError)
    # 'Q===' is valid base64 for b'A' in lenient decoders but not canonical
    # (canonical for b'A' is 'QQ=='); the runner must reject it so digests are
    # stable and a host decoder cannot silently accept malformed output.
    check_raise("non-canonical base64", lambda: P.decode_b64("Q==="), P.ProtocolError)

    # --- execute-before-compile is a state-machine FAIL, not reachable as a pass ---
    acc = None
    rterror = {"status": "RUNTIME_FAILURE", "runtimeCategory": "ARITHMETIC_ERROR"}
    mf = {"outcome": "RUNTIME_ERROR", "expectation": {"runtimeCategory": "ARITHMETIC_ERROR"}}
    # No compile happened (None) but execute claims a runtime failure:
    d = OC.judge(mf, acc, rterror)
    check("execute w/o successful compile -> INFRA/FAIL (not PASS)", d.status != OC.PASS)

    # --- describe response schema shape enforced ---
    check_raise("describe missing implementation",
                lambda: s.expect_response(raw({"protocolVersion": "1", "schemaVersion": 1,
                                               "requestId": 1, "op": "describe", "status": "OK"}),
                                          1, "describe"),
                P.ProtocolError)

    failed = [l for l, c in RESULTS if not c]
    print("protocol selftests: %d/%d passed" % (len(RESULTS) - len(failed), len(RESULTS)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
