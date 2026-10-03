"""Version identifiers shared across the TCK.

Every machine-readable input and every protocol message carries an explicit
version so the runner can *reject* unknown versions instead of silently
reinterpreting them (see TCK.md section 5). These constants are the single
source of truth for the values published in ``tck/VERSION`` and the schemas.
"""

from __future__ import annotations

import os

# The language specification revision this TCK is written against. This is NOT
# the Maven artifact version and is deliberately pre-1.0 while the language is
# unstable. See docs/LANGUAGE_SPEC.md "Versioning".
SPEC_VERSION = "2026.11-draft"

# Specification revisions this runner release understands. A manifest or report
# naming anything else is an infrastructure error.
SUPPORTED_SPEC_VERSIONS = ("2026.11-draft",)

# Specification revisions that are frozen, exhaustive normative baselines and may
# therefore carry a full-profile conformance result. A baseline whose version string
# is suffixed `-draft`, or which is otherwise pre-1.0, is NOT in this set: TCK.md
# section 5 requires that certification against an unversioned/draft normative
# baseline be *withheld*, and section 5.1 requires the full-language profile to be
# every portable non-deferred requirement -- an inventory that is a deliberate seed
# rather than a complete enumeration cannot support a conformance conclusion. Tests
# still run and are still judged PASS/FAIL individually; only the aggregate
# certification is withheld, and the report names the reason. This is a property of
# the TCK/spec release, never of an implementation, so no adapter can waive it.
CERTIFIABLE_SPEC_VERSIONS: tuple = ()  # populated only once a frozen baseline exists

# The TCK release identifier, read from tck/VERSION at build time. A released
# value is immutable; corrections require a new release (section 5).
_VERSION_FILE = os.path.join(os.path.dirname(__file__), "..", "..", "VERSION")


def tck_version() -> str:
    try:
        with open(_VERSION_FILE, "r", encoding="utf-8") as handle:
            return handle.read().strip()
    except OSError:
        return "unknown"


# The adapter protocol version. The runner and an adapter must agree exactly; a
# mismatch is a protocol/infrastructure error, never a silent downgrade.
PROTOCOL_VERSION = "1"

# JSON Schema $id values for the published, versioned schemas. These are the
# canonical identifiers named by each schema's $id and by $schema.
SCHEMA_MANIFEST = "https://solvik.org/tck/schema/manifest-1.json"
SCHEMA_REQUIREMENTS = "https://solvik.org/tck/schema/requirements-1.json"
SCHEMA_PROFILE = "https://solvik.org/tck/schema/profile-1.json"
SCHEMA_PROTOCOL = "https://solvik.org/tck/schema/protocol-1.json"
SCHEMA_REPORT = "https://solvik.org/tck/schema/report-1.json"

# The manifest schema version value carried inside every manifest.
MANIFEST_SCHEMA_VERSION = 1
PROTOCOL_SCHEMA_VERSION = 1
REPORT_SCHEMA_VERSION = 1

# The four supported outcomes (section 7).
OUTCOMES = ("SUCCESS", "COMPILE_SUCCESS", "COMPILE_ERROR", "RUNTIME_ERROR")

# Structured runtime error categories (section 8 result taxonomy).
RUNTIME_CATEGORIES = (
    "ARITHMETIC_ERROR",
    "CAST_FAILURE",
    "NULL_DEREFERENCE",
    "COLLECTION_FAILURE",
    "INDEX_OUT_OF_BOUNDS",
    "UNCAUGHT_EXCEPTION",
    "REGEX_FAILURE",
    "RESULT_WRONG_VARIANT",
    "OTHER_RUNTIME_ERROR",
)

# Diagnostic families (section 2 audit). Only specification-required codes are
# automatically normative; the TCK records the family plus the specific code.
DIAGNOSTIC_FAMILIES = ("LEX", "PARS", "RESOL", "TYPE", "SEM")

# The mandatory full-language profile. Subprofiles may exist for development or
# constrained hosts but passing one is never reported as full conformance.
FULL_PROFILE = "full-language"
