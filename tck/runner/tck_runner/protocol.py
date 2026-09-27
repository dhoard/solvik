"""Versioned adapter protocol framing, correlation, and the outcome state machine.

The wire format (TCK.md section 8) is newline-delimited, strict UTF-8 JSON: the
runner writes one compact JSON request per line to the adapter's stdin and reads
exactly one response line from the adapter's stdout. Guest stdout and stderr are
*base64 fields inside responses*, never the adapter's own streams, so guest
output can never forge protocol data. This module implements the framing,
request/response correlation, schema validation, and the strict phase-order state
machine that decides PASS / FAIL / INFRASTRUCTURE_ERROR.

Design rule: the runner, never the adapter, applies the manifest oracle. The
state machine only accepts the transitions a conforming adapter can produce; every
invalid transition, oversized message, mismatched correlation id, malformed JSON,
or premature process exit is an infrastructure error, never a pass.
"""

from __future__ import annotations

import base64

from . import schema as S, strict_json, versions

# Protocol hard limits (section 8). Exceeding a limit is an infrastructure
# result, never output truncation followed by comparison.
MAX_MESSAGE_BYTES = 8 * 1024 * 1024  # one request or response line
MAX_CAPTURED_OUTPUT_BYTES = 16 * 1024 * 1024
DEFAULT_CANCEL_GRACE_MS = 5000

# Result of a single protocol step or an outcome decision.
INFRA = "INFRASTRUCTURE_ERROR"
FAIL = "FAIL"
PASS = "PASS"


class ProtocolError(Exception):
    """A protocol framing or correlation failure -> infrastructure error."""

    def __init__(self, reason, raw=None):
        super().__init__(reason)
        self.reason = reason
        self.raw = raw


def encode_request(request: dict) -> bytes:
    """Serialize a request to one protocol line (bytes, newline terminated)."""
    line = strict_json.dumps_canonical(request)
    data = line.encode("utf-8")
    if len(data) + 1 > MAX_MESSAGE_BYTES:
        raise ProtocolError("request exceeds max message size: %d bytes" % (len(data) + 1))
    return data + b"\n"


def _decode_response_line(raw: bytes) -> dict:
    if raw is None:
        raise ProtocolError("adapter closed stdout before a response line")
    if len(raw) > MAX_MESSAGE_BYTES:
        raise ProtocolError("response exceeds max message size: %d bytes" % len(raw))
    text = raw.decode("latin-1") if _has_bad_utf8(raw) else None
    if text is None:
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise ProtocolError("response is not valid UTF-8: %s" % exc)
    stripped = text.rstrip("\r\n")
    if stripped == "":
        raise ProtocolError("blank protocol line")
    # raw_decode would reject a second value, but we also reject the two-JSON
    # values-on-one-line case explicitly for a clear reason string.
    try:
        value = strict_json.loads(stripped)
    except strict_json.StrictJSONError as exc:
        raise ProtocolError("response is not strict JSON: %s" % exc) from exc
    if not isinstance(value, dict):
        raise ProtocolError("response is not a JSON object")
    return value


def _has_bad_utf8(raw: bytes) -> bool:
    try:
        raw.decode("utf-8")
        return False
    except UnicodeDecodeError:
        return True


class Session:
    """Tracks request/response correlation for one adapter session.

    The runner calls :meth:`expect_response` with the raw stdout line (or ``None``
    on EOF) and the expected request id/operation; it returns the validated
    response dict or raises :class:`ProtocolError`.
    """

    def __init__(self, protocol_schema, adapter_exit_code=None):
        self._schema = protocol_schema
        self._next_id = 1

    def next_request_id(self):
        rid = self._next_id
        self._next_id += 1
        return rid

    def make_request(self, op: str, **fields) -> dict:
        req = {
            "protocolVersion": versions.PROTOCOL_VERSION,
            "schemaVersion": versions.PROTOCOL_SCHEMA_VERSION,
            "requestId": self.next_request_id(),
            "op": op,
        }
        req.update(fields)
        # Validate the outgoing request against the same schema an adapter must
        # satisfy, so a runner bug cannot emit an unvalidatable request.
        try:
            S.validate(self._schema, req)
        except S.ValidationError as exc:
            raise ProtocolError("runner produced an invalid request: %s" % exc) from exc
        return req

    def expect_response(self, raw_line, expected_request_id: int, expected_op: str) -> dict:
        resp = _decode_response_line(raw_line)
        if resp.get("protocolVersion") != versions.PROTOCOL_VERSION:
            raise ProtocolError("unsupported/incorrect protocol version in response")
        if resp.get("schemaVersion") != versions.PROTOCOL_SCHEMA_VERSION:
            raise ProtocolError("unsupported/incorrect schema version in response")
        if resp.get("requestId") != expected_request_id:
            raise ProtocolError(
                "response requestId %r does not match request %r"
                % (resp.get("requestId"), expected_request_id)
            )
        if resp.get("op") != expected_op:
            raise ProtocolError(
                "response op %r does not match request op %r" % (resp.get("op"), expected_op)
            )
        try:
            S.validate(self._schema, resp)
        except S.ValidationError as exc:
            raise ProtocolError("response failed schema validation: %s" % exc) from exc
        # The shared envelope schema cannot require `status`/`implementation` (a
        # *request* carries neither). Response completeness is therefore enforced
        # here, where we know this is a response: a missing outcome field is a
        # protocol violation (infrastructure error), never a silent conformance
        # result. (section 8 result taxonomy.)
        if resp["op"] in ("compile", "execute") and "status" not in resp:
            raise ProtocolError("%s response is missing 'status'" % resp["op"])
        if resp["op"] == "describe" and "implementation" not in resp:
            raise ProtocolError("describe response is missing 'implementation'")
        return resp


def decode_b64(field: str) -> bytes:
    """Losslessly decode a base64 output field; reject non-canonical data."""
    try:
        data = base64.b64decode(field, validate=True)
    except Exception as exc:  # binascii.Error
        raise ProtocolError("captured output field is not valid base64: %s" % exc) from exc
    # Reject non-canonical padding/roundtrip so digests are stable.
    if base64.b64encode(data).decode("ascii") != field:
        raise ProtocolError("captured output field is not canonical base64")
    return data
