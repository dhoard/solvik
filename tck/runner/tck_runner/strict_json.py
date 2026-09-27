"""Strict JSON decoding used by the Solvik TCK runner.

The TCK forbids silently reinterpreting data. Every JSON document that crosses a
trust boundary (manifests, requirement inventory, profiles, adapter protocol
messages, reports) is parsed with these helpers, which reject the permissive
behaviours that the built-in ``json`` module would otherwise accept:

* duplicate object keys (last-wins in the stdlib; here it is a hard error),
* the non-standard literals ``NaN``, ``Infinity``, ``-Infinity``,
* leading/trailing data around a single top-level value,
* invalid UTF-8 bytes, over-long escapes, and lone surrogates,
* control characters inside string literals.

Only the Python standard library is used so the runner self-tests require no
third-party packages and no network access.
"""

from __future__ import annotations

import json
import math
import re
from json import decoder as _py_decoder


class StrictJSONError(ValueError):
    """Raised when a document is not strict, canonical JSON."""


# The stdlib decoder routes these three non-standard tokens through these
# constants. We override them so their mere presence raises.
def _reject(name):
    def _fail(*_args, **_kwargs):
        raise StrictJSONError("non-standard JSON literal: %s" % name)

    return _fail


_STRICT_CONSTANTS = {
    "NaN": _reject("NaN"),
    "Infinity": _reject("Infinity"),
    "-Infinity": _reject("-Infinity"),
}

# A canonical JSON number per the TCK: optional minus, an integer part with no
# leading zeros (except a lone zero), an optional fraction, an optional
# exponent. Hex, +NaN, 1e999 (which parses to inf), and bare infinities are all
# rejected elsewhere.
_CANONICAL_NUMBER = re.compile(
    r"-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][-+]?[0-9]+)?"
)


def _object_pairs_hook(pairs):
    """Build a dict but reject any repeated key.

    ``json`` hands us the raw key/value pairs in source order, so a duplicate is
    detectable before last-wins would silently discard the earlier value.
    """
    seen = {}
    for key, value in pairs:
        if key in seen:
            raise StrictJSONError("duplicate object key: %r" % (key,))
        seen[key] = value
    return seen


def _reject_constant(token):
    raise StrictJSONError("non-standard JSON literal: %s" % token)


def loads(text: str):
    """Decode *text* (a ``str``) as strict JSON, returning the decoded value.

    Raises :class:`StrictJSONError` (a ``ValueError``) on any non-strict input.
    """
    if not isinstance(text, str):
        raise StrictJSONError("strict JSON input must be str, got %s" % type(text).__name__)

    parser = _py_decoder.JSONDecoder(
        object_pairs_hook=_object_pairs_hook,
        parse_float=_parse_float,
        parse_int=_parse_int,
        parse_constant=_reject_constant,
    )
    try:
        value, end = parser.raw_decode(text)
    except StrictJSONError:
        raise
    except json.JSONDecodeError as exc:
        raise StrictJSONError(str(exc)) from exc
    # Reject trailing data: only ASCII whitespace may follow the single value.
    if text[end:].strip(" \t\r\n") != "":
        raise StrictJSONError("trailing data after top-level JSON value")
    _reject_surrogates(value)
    return value


def _reject_surrogates(value):
    """Recursively reject decoded strings containing lone UTF-16 surrogates.

    A well-formed ``\\uD800\\uDC00`` pair decodes into a single astral scalar and
    leaves no surrogate code point in the resulting ``str``; a *lone* surrogate
    escape remains as a surrogate code point and cannot be re-encoded as UTF-8.
    The TCK requires lossless UTF-8 for reports and protocol lines, so a lone
    surrogate is rejected rather than allowed to corrupt a later encode.
    """
    stack = [value]
    while stack:
        node = stack.pop()
        if isinstance(node, str):
            for ch in node:
                if 0xD800 <= ord(ch) <= 0xDFFF:
                    raise StrictJSONError("lone surrogate in decoded string")
        elif isinstance(node, dict):
            stack.extend(node.keys())
            stack.extend(node.values())
        elif isinstance(node, list):
            stack.extend(node)


def _parse_int(raw):
    # int() already rejects things like "+5" is allowed by int but not by the
    # JSON grammar; the decoder only feeds grammar-valid integers here, so the
    # only additional guard is an explicit leading-zero form, which the
    # tokenizer never produces. Return the int unchanged.
    return int(raw)


def _parse_float(raw):
    # Guard against values that overflow to infinity (e.g. 1e999) or are NaN;
    # the JSON grammar permits such literals but they are not finite.
    value = float(raw)
    if math.isnan(value) or math.isinf(value):
        raise StrictJSONError("non-finite number: %s" % raw)
    return value


def loadb(data: bytes):
    """Decode strict JSON from *bytes*, rejecting invalid UTF-8 and BOMs."""
    if not isinstance(data, (bytes, bytearray)):
        raise StrictJSONError("loadb requires bytes")
    if data.startswith(b"\xef\xbb\xbf"):
        raise StrictJSONError("UTF-8 BOM is not permitted in strict JSON")
    try:
        text = bytes(data).decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise StrictJSONError("invalid UTF-8 in JSON input: %s" % exc) from exc
    # A decoded lone surrogate cannot survive a re-encode; reject it up front so
    # downstream UTF-8 encoding of reports and protocol lines is lossless.
    try:
        text.encode("utf-8", errors="strict")
    except UnicodeEncodeError as exc:
        raise StrictJSONError("lone surrogate in JSON input: %s" % exc) from exc
    return loads(text)


def dumps_canonical(obj) -> str:
    """Deterministic, strict JSON encoding used for digests and reports.

    Sorted keys, no insignificant whitespace, ASCII-escaped, and no non-finite
    floats. Two equal logical documents always produce identical bytes, which is
    what content digests rely on.
    """
    return json.dumps(
        obj,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=True,
        allow_nan=False,
    )
