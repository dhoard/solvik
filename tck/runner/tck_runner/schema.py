"""Dependency-free JSON Schema (Draft 2020-12 subset) validator.

The TCK publishes its schemas as JSON Schema Draft 2020-12 documents, but the
runner must enforce the same constraints even when no third-party schema library
is installed. This module implements the closed subset of keywords that the TCK
schemas are allowed to use; ``SUPPORTED_KEYWORDS`` is the authoritative list and
a schema that uses any other keyword is rejected as an infrastructure error
rather than silently ignored (ignoring an unknown keyword would weaken
validation).

Design contract enforced by the TCK schemas and checked here:

* objects are closed with ``additionalProperties: false``;
* integers are bounded (``minimum``/``maximum``) and ``type`` is enforced;
* string lengths and patterns are bounded;
* no schema relies on a default the runner could reinterpret.

The subset implemented is exactly the keywords needed to express the manifest,
requirement, profile, protocol, and report schemas. Extending the subset is a
deliberate, reviewed change because it expands what a released schema may say.
"""

from __future__ import annotations

import re

DIALECT = "https://json-schema.org/draft/2020-12/schema"

# The closed keyword set. A schema keyword outside this set is rejected.
SUPPORTED_KEYWORDS = frozenset(
    {
        "$schema",
        "$id",
        "$ref",
        "$defs",
        "title",
        "description",
        "type",
        "enum",
        "const",
        "properties",
        "patternProperties",
        "additionalProperties",
        "propertyNames",
        "required",
        "minProperties",
        "maxProperties",
        "items",
        "prefixItems",
        "minItems",
        "maxItems",
        "uniqueItems",
        "contains",
        "minContains",
        "maxContains",
        "minimum",
        "maximum",
        "exclusiveMinimum",
        "exclusiveMaximum",
        "multipleOf",
        "format",
        "minLength",
        "maxLength",
        "pattern",
        "allOf",
        "anyOf",
        "oneOf",
        "not",
        "if",
        "then",
        "else",
        "$comment",
    }
)

# ``format`` is asserted (not merely annotated) for a small set of formats the
# TCK depends on for determinism. Unknown formats are allowed per the dialect.
_FORMATS = {
    "int32": lambda v: isinstance(v, int) and -2147483648 <= v <= 2147483647,
    "uint32": lambda v: isinstance(v, int) and 0 <= v <= 4294967295,
    "date-time": None,  # filled below (regex-checked)
    "sha256": lambda v: isinstance(v, str) and re.fullmatch(r"[0-9a-f]{64}", v) is not None,
}
_DATE_TIME_RE = re.compile(
    r"^\d{4}-\d{2}-\d{2}[Tt ]\d{2}:\d{2}:\d{2}(\.\d+)?([Zz]|[+-]\d{2}:\d{2})$"
)
_FORMATS["date-time"] = lambda v: isinstance(v, str) and _DATE_TIME_RE.match(v) is not None


class SchemaError(Exception):
    """A schema document is itself invalid (infrastructure error)."""


class ValidationError(Exception):
    """An instance fails schema validation, carrying a source-located path."""

    def __init__(self, message, path, instance_location=""):
        super().__init__(message)
        self.message = message
        self.path = path
        self.instance_location = instance_location

    def __str__(self):
        return "%s: %s" % (self.instance_location or "$", self.message)


_TYPES = {
    "object": dict,
    "array": list,
    "string": str,
    "boolean": bool,
    "null": type(None),
}


def _is_type(value, tname):
    if tname == "integer":
        return isinstance(value, int) and not isinstance(value, bool)
    if tname == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    if tname == "boolean":
        return isinstance(value, bool)
    return isinstance(value, _TYPES[tname])


def _resolve_ref(root, ref):
    if not isinstance(ref, str) or not ref.startswith("#"):
        raise SchemaError("only local $ref supported, got %r" % (ref,))
    frag = ref[1:]
    if frag.startswith("/"):
        node = root
        for raw in frag[1:].split("/"):
            token = raw.replace("~1", "/").replace("~0", "~")
            if isinstance(node, dict) and token in node:
                node = node[token]
            elif isinstance(node, list) and token.isdigit() and int(token) < len(node):
                node = node[int(token)]
            else:
                raise SchemaError("unresolvable $ref: %r" % (ref,))
        return node
    if frag == "":
        return root
    raise SchemaError("unsupported $ref form: %r" % (ref,))


def _check_unknown_keywords(schema, path):
    for key in schema:
        if key not in SUPPORTED_KEYWORDS:
            raise SchemaError("%s: unsupported schema keyword %r" % (path, key))


def _validate(schema, instance, path, root, seen):
    if isinstance(schema, bool):
        if schema is False:
            raise ValidationError("schema is false at %s" % path, path)
        return
    if not isinstance(schema, dict):
        raise SchemaError("%s: schema must be object or boolean" % path)
    _check_unknown_keywords(schema, path)

    if "$ref" in schema:
        resolved = _resolve_ref(root, schema["$ref"])
        if id(resolved) in seen:
            raise SchemaError("%s: cyclic $ref" % path)
        _validate(resolved, instance, path, root, seen | {id(resolved)})
        # When a schema has only $ref (plus $comment/$id), nothing else applies.
        if set(schema) <= {"$ref", "$comment", "$id", "title", "description"}:
            return

    if "type" in schema:
        types = schema["type"]
        type_list = types if isinstance(types, list) else [types]
        if not any(_is_type(instance, t) for t in type_list):
            raise ValidationError(
                "expected type %s, got %s" % (type_list, type(instance).__name__),
                path,
            )

    if "enum" in schema:
        if not any(instance == opt for opt in schema["enum"]):
            raise ValidationError("value not in enum %s" % (schema["enum"],), path)

    if "const" in schema:
        if instance != schema["const"]:
            raise ValidationError("value != const %r" % (schema["const"],), path)

    if isinstance(instance, dict):
        _validate_object(schema, instance, path, root, seen)
    elif isinstance(instance, list):
        _validate_array(schema, instance, path, root, seen)
    elif isinstance(instance, str):
        _validate_string(schema, instance, path)
    elif isinstance(instance, (int, float)) and not isinstance(instance, bool):
        _validate_number(schema, instance, path)

    if "format" in schema:
        checker = _FORMATS.get(schema["format"])
        if checker is not None and not checker(instance):
            raise ValidationError("value fails format %r" % schema["format"], path)

    for combinator in ("allOf", "anyOf", "oneOf"):
        if combinator in schema:
            _validate_combinator(combinator, schema[combinator], instance, path, root, seen)
    if "not" in schema:
        try:
            _validate(schema["not"], instance, path + "/not", root, seen)
        except ValidationError:
            pass
        else:
            raise ValidationError("value must not match 'not' schema", path)
    if "if" in schema:
        matches = True
        try:
            _validate(schema["if"], instance, path + "/if", root, seen)
        except ValidationError:
            matches = False
        if matches and "then" in schema:
            _validate(schema["then"], instance, path + "/then", root, seen)
        if not matches and "else" in schema:
            _validate(schema["else"], instance, path + "/else", root, seen)


def _validate_object(schema, instance, path, root, seen):
    if "required" in schema:
        for req in schema["required"]:
            if req not in instance:
                raise ValidationError("missing required property %r" % req, path)
    props = schema.get("properties", {})
    pprops = schema.get("patternProperties", {})
    addl = schema.get("additionalProperties", True)
    for key, value in instance.items():
        matched = False
        if key in props:
            matched = True
            _validate(props[key], value, path + "/properties/" + key, root, seen)
        for pat in pprops:
            if re.search(pat, key):
                matched = True
                _validate(pprops[pat], value, path + "/patternProperties/" + pat, root, seen)
        if not matched:
            if addl is False:
                raise ValidationError("additional property %r is not permitted" % key, path)
            if isinstance(addl, dict):
                _validate(addl, value, path + "/additionalProperties", root, seen)
    if "propertyNames" in schema:
        for key in instance:
            _validate(schema["propertyNames"], key, path + "/propertyNames", root, seen)
    size = len(instance)
    if "minProperties" in schema and size < schema["minProperties"]:
        raise ValidationError("object has %d props, min %d" % (size, schema["minProperties"]), path)
    if "maxProperties" in schema and size > schema["maxProperties"]:
        raise ValidationError("object has %d props, max %d" % (size, schema["maxProperties"]), path)


def _validate_array(schema, instance, path, root, seen):
    # Draft 2020-12 array semantics: ``prefixItems`` applies positionally to the
    # leading items, and ``items`` (if present) applies to every item AFTER the
    # prefix. There is no ``additionalItems`` in this dialect -- a schema that
    # uses it is rejected by ``check_schema`` as an unsupported keyword rather
    # than silently given draft-07 meaning, which would differ from the
    # published 2020-12 schema's behavior.
    prefix = schema.get("prefixItems", [])
    for idx, subschema in enumerate(prefix):
        if idx < len(instance):
            _validate(subschema, instance[idx], path + "/prefixItems/%d" % idx, root, seen)
    if "items" in schema:
        start = len(prefix)
        for idx in range(start, len(instance)):
            _validate(schema["items"], instance[idx], path + "/items/%d" % idx, root, seen)
    elif "prefixItems" in schema:
        # No 'items' and no 'additionalItems' in 2020-12: trailing items are
        # simply unconstrained by this schema. Nothing more to check.
        pass
    if "contains" in schema:
        count = 0
        for item in instance:
            try:
                _validate(schema["contains"], item, path + "/contains", root, seen)
                count += 1
            except ValidationError:
                pass
        if "minContains" in schema and count < schema["minContains"]:
            raise ValidationError("contains matched %d, min %d" % (count, schema["minContains"]), path)
        if count == 0 and schema.get("minContains", 1) > 0:
            raise ValidationError("no array item matched 'contains'", path)
        if "maxContains" in schema and count > schema["maxContains"]:
            raise ValidationError("contains matched %d, max %d" % (count, schema["maxContains"]), path)
    if "uniqueItems" in schema and schema["uniqueItems"]:
        seen_keys = []
        for item in instance:
            key = _hashable(item)
            if key in seen_keys:
                raise ValidationError("array items must be unique", path)
            seen_keys.append(key)
    size = len(instance)
    if "minItems" in schema and size < schema["minItems"]:
        raise ValidationError("array length %d < minItems %d" % (size, schema["minItems"]), path)
    if "maxItems" in schema and size > schema["maxItems"]:
        raise ValidationError("array length %d > maxItems %d" % (size, schema["maxItems"]), path)


def _hashable(item):
    import json

    return json.dumps(item, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def _validate_string(schema, instance, path):
    if "minLength" in schema and len(instance) < schema["minLength"]:
        raise ValidationError("string length < minLength", path)
    if "maxLength" in schema and len(instance) > schema["maxLength"]:
        raise ValidationError("string length > maxLength", path)
    if "pattern" in schema and re.search(schema["pattern"], instance) is None:
        raise ValidationError("string does not match pattern %r" % schema["pattern"], path)


def _validate_number(schema, instance, path):
    if "minimum" in schema and instance < schema["minimum"]:
        raise ValidationError("value < minimum", path)
    if "maximum" in schema and instance > schema["maximum"]:
        raise ValidationError("value > maximum", path)
    if "exclusiveMinimum" in schema and instance <= schema["exclusiveMinimum"]:
        raise ValidationError("value <= exclusiveMinimum", path)
    if "exclusiveMaximum" in schema and instance >= schema["exclusiveMaximum"]:
        raise ValidationError("value >= exclusiveMaximum", path)
    if "multipleOf" in schema:
        step = schema["multipleOf"]
        if step == 0:
            raise SchemaError("multipleOf must be nonzero")
        q = instance / step
        if abs(q - round(q)) > 1e-9 * max(1.0, abs(q)):
            raise ValidationError("value not a multiple of %r" % step, path)


def _validate_combinator(name, subschemas, instance, path, root, seen):
    passed = 0
    errors = []
    for i, subschema in enumerate(subschemas):
        try:
            _validate(subschema, instance, "%s/%s/%d" % (path, name, i), root, seen)
            passed += 1
        except ValidationError as exc:
            errors.append(exc)
    if name == "allOf" and passed != len(subschemas):
        raise errors[0] if errors else ValidationError("allOf failed", path)
    if name == "anyOf" and passed == 0:
        raise errors[0] if errors else ValidationError("anyOf failed", path)
    if name == "oneOf" and passed != 1:
        raise ValidationError(
            "oneOf expected exactly 1 match, got %d" % passed, path
        )


def validate(schema, instance, root=None):
    """Validate *instance* against *schema*; raise :class:`ValidationError`.

    :class:`SchemaError` is raised if the schema itself misuses a keyword.
    """
    if root is None:
        root = schema
    _validate(schema, instance, "", root, {id(schema)})


def check_schema(schema):
    """Recursively verify a schema document uses only supported keywords.

    Run against every TCK schema at load time so an unsupported keyword is an
    infrastructure error at startup, never a silently ignored constraint.
    """

    def walk(node, path):
        if isinstance(node, bool):
            return
        if not isinstance(node, dict):
            raise SchemaError("%s: schema node is neither object nor boolean" % path)
        _check_unknown_keywords(node, path)
        for key, value in node.items():
            sub = "%s/%s" % (path, key)
            if key in ("properties", "patternProperties", "$defs"):
                for k, v in value.items():
                    walk(v, "%s/%s" % (sub, k))
            elif key in ("items", "contains", "if", "then", "else",
                         "additionalProperties", "propertyNames", "not"):
                if isinstance(value, (dict, bool)):
                    walk(value, sub)
            elif key in ("allOf", "anyOf", "oneOf", "prefixItems"):
                if not isinstance(value, list):
                    raise SchemaError("%s: keyword %s must be an array" % (path, key))
                for i, v in enumerate(value):
                    walk(v, "%s/%d" % (sub, i))
        if "$ref" in node:
            try:
                _resolve_ref(schema, node["$ref"])
            except SchemaError as exc:
                raise SchemaError("%s: %s" % (path, exc))

    walk(schema, "")
