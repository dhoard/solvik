"""Infrastructure event vocabulary shared by the runner and the state machine.

Every event here is, by definition, an infrastructure condition: it prevents a
conformance judgment rather than indicating an implementation conformance
failure (section 13). They are kept as plain dicts so they serialize losslessly
into the JSON report.

The distinctions in section 8 that matter most:

* ``IMPLEMENTATION_FAILURE`` is a *caught* crash/internal error the adapter
  reported. That is a real IUT result and produces a conformance ``FAIL`` (the
  state machine handles it), NOT an infrastructure event.
* ``ADAPTER_LOST`` / ``ADAPTER_NONZERO_EXIT`` are loss of a trustworthy result, so
  they are infrastructure events.
"""

from __future__ import annotations

# Infrastructure event kinds (each -> INFRASTRUCTURE_ERROR).
ADAPTER_EXIT_NONZERO = "ADAPTER_EXIT_NONZERO"
ADAPTER_LOST = "ADAPTER_LOST"           # premature stdout/stdin closure
ADAPTER_LAUNCH_FAILED = "ADAPTER_LAUNCH_FAILED"
PROTOCOL_VIOLATION = "PROTOCOL_VIOLATION"
COMPILE_TIMEOUT = "COMPILE_TIMEOUT"
EXECUTE_TIMEOUT = "EXECUTE_TIMEOUT"
RESOURCE_LIMIT = "RESOURCE_LIMIT"
MANIFEST_DEFECT = "MANIFEST_DEFECT"
ARTIFACT_MISMATCH = "ARTIFACT_MISMATCH"
SOURCE_MUTATED = "SOURCE_MUTATED"
WORKSPACE_ESCAPED = "WORKSPACE_ESCAPED"


def event(kind: str, detail: str = "", **extra) -> dict:
    data = {"kind": kind, "detail": detail}
    data.update(extra)
    return data
