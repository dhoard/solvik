"""The end-to-end conformance runner driver.

Implements the outcome state machine's *process* side (TCK.md sections 8, 8.1,
12): a describe-only preflight selects compatible tests; then, per test, one
isolated adapter session runs describe -> stage -> compile -> (execute). The
runner -- never the adapter -- applies the manifest oracle via
``outcome.judge`` and assembles per-test results for ``report.py``.

The driver depends only on the adapter command line plus the portable corpus; it
imports no Solvik/GraalVM/Truffle code (acceptance criterion 1).
"""

from __future__ import annotations

import base64
import os
import platform
import time

from . import (
    adapter_transport,
    digest,
    events,
    isolation,
    manifests as MF,
    outcome as OC,
    protocol,
    report as RP,
    schema as S,
    strict_json,
    versions,
)

DEFAULT_COMPILE_TIMEOUT_MS = 30000
DEFAULT_EXECUTE_TIMEOUT_MS = 30000


class PreflightError(Exception):
    pass


class AdapterConfig:
    """A named adapter configuration bound to a canonical executable path.

    The JVM and native adapters are distinct IUT identities even when built from
    one source revision; each carries its own fingerprint and argv.
    """

    def __init__(self, name, argv, config_digest, fingerprint, env_overrides=None):
        self.name = name
        self.argv = list(argv)
        self.config_digest = config_digest
        self.fingerprint = fingerprint
        self.env_overrides = env_overrides or {}


def _b64(data: bytes) -> str:
    return base64.b64encode(data).decode("ascii")


class Runner:
    def __init__(self, protocol_schema, manifest_schema, adapter: AdapterConfig,
                 compile_timeout_ms=DEFAULT_COMPILE_TIMEOUT_MS,
                 execute_timeout_ms=DEFAULT_EXECUTE_TIMEOUT_MS,
                 observation_sink=None):
        self._pschema = protocol_schema
        self._mschema = manifest_schema
        self._adapter = adapter
        self._compile_timeout_ms = compile_timeout_ms
        self._execute_timeout_ms = execute_timeout_ms
        # Differential mode (TCK.md section 15) needs the raw observations, not just
        # the verdict, so it can tell a disagreement apart from a shared mistake.
        # The report schema is closed (`additionalProperties: false`) and is validated
        # in the self-tests, so observations are deliberately kept OUT of the report
        # and handed to an optional caller-supplied dict instead. Left None, nothing
        # is recorded and a conformance run is byte-identical to one before this hook
        # existed -- differential agreement can therefore never influence a verdict.
        self._observation_sink = observation_sink

    # -- preflight ---------------------------------------------------------
    def preflight(self, profile: str, spec_version: str):
        """Run a describe-only session to capture the frozen IUT identity.

        Returns ``(implementation, capabilities)`` or raises PreflightError. A
        describe whose advertised profiles/spec versions do not include the
        requested ones is a hard preflight failure (section 5.1: capabilities do
        not downgrade requirements, and an adapter that cannot target the
        requested spec/profile cannot run a conformance suite).
        """
        ws = isolation.Workspace.create()
        transport = None
        try:
            env = isolation.build_env(self._adapter.env_overrides, dict(os.environ))
            transport = adapter_transport.Transport(
                self._adapter.argv, ws.compile_dir, env)
            transport.start()
            session = protocol.Session(self._pschema)
            req = session.make_request("describe")
            transport.send(protocol.encode_request(req))
            raw = transport.read_line(self._compile_timeout_ms)
            resp = session.expect_response(raw, req["requestId"], "describe")
            impl = resp.get("implementation")
            if impl is None:
                raise PreflightError("describe returned no implementation identity")
            if spec_version not in impl.get("specVersions", []):
                raise PreflightError("adapter does not support spec version %r" % spec_version)
            if profile not in impl.get("profiles", []):
                raise PreflightError("adapter does not support profile %r" % profile)
            return impl
        finally:
            if transport:
                transport.close()
            ws.remove()

    # -- per-test ----------------------------------------------------------
    def run_test(self, manifest, preflight_impl):
        result = {
            "testId": manifest["testId"],
            "requirements": manifest["requirements"],
            "phases": {},
            "status": "NOT_RUN",
            "reason": "",
        }
        test_dir = os.path.dirname(manifest["__path__"])
        infra = []
        # Raw phase responses for the differential sink, reset per test. The runner
        # processes tests sequentially in one process, so a per-instance slot scoped
        # to one run_test call cannot leak between tests.
        self._phase_obs = {"compile": None, "execute": None}

        ws = isolation.Workspace.create()
        transport = None
        try:
            env = isolation.build_env({**manifest.get("environment", {}),
                                       **self._adapter.env_overrides}, dict(os.environ))
            try:
                transport = adapter_transport.Transport(
                    self._adapter.argv, ws.compile_dir, env)
                transport.start()
            except adapter_transport._LaunchFailure as exc:
                infra.append(events.event(events.ADAPTER_LAUNCH_FAILED, str(exc)))
                return self._finish(result, infra, ws)

            session = protocol.Session(self._pschema)

            # 1. per-test describe must match the frozen preflight identity.
            req = session.make_request("describe")
            transport.send(protocol.encode_request(req))
            raw = transport.read_line(self._compile_timeout_ms)
            resp = session.expect_response(raw, req["requestId"], "describe")
            if not OC.describe_matches(preflight_impl, resp.get("implementation", {})):
                infra.append(events.event(events.PROTOCOL_VIOLATION,
                                          "per-test describe does not match preflight identity"))
                return self._finish(result, infra, ws)

            # Record a reproduction invocation (argv + redacted env); the report
            # binds this so a failure can be re-run without leaking secrets.
            result["reproduction"] = {
                "argv": self._adapter.argv,
                "env": isolation.redact_env(
                    isolation.build_env({**manifest.get("environment", {}),
                                         **self._adapter.env_overrides}, dict(os.environ))),
            }

            # 2. stage + hash the fixture tree.
            tree_digest = None
            staged_files = {}
            try:
                tree_digest, staged_files = isolation.stage_fixtures(test_dir, manifest, ws)
                result["inputTreeDigest"] = tree_digest
            except isolation.FixtureError as exc:
                infra.append(events.event(events.MANIFEST_DEFECT, str(exc)))
                return self._finish(result, infra, ws)

            # 3. compile under the compilation timeout.
            compile_entry = base64.b64encode(manifest["entryPoint"].encode()).decode()
            compile_req = session.make_request(
                "compile",
                workspace=ws.compile_dir,
                inputTreeDigest=tree_digest,
                entryPoint=manifest["entryPoint"],
                compileTimeoutMs=manifest.get("compileTimeoutMs", self._compile_timeout_ms),
            )
            compile_result, executed = self._phase(
                transport, session, compile_req,
                manifest.get("compileTimeoutMs", self._compile_timeout_ms),
                infra, events.COMPILE_TIMEOUT)
            result["phases"]["compile"] = (compile_result or {}).get("status", "NONE")
            self._phase_obs["compile"] = compile_result

            if infra:
                return self._finish(result, infra, ws)

            # 4/5. COMPILE_ERROR / COMPILE_SUCCESS stop without execute.
            outcome = manifest["outcome"]
            execute_result = None
            if outcome in ("SUCCESS", "RUNTIME_ERROR"):
                if compile_result and compile_result.get("status") == "COMPILE_ACCEPTED":
                    handle = compile_result.get("artifactHandle")
                    manifest_files = compile_result.get("artifactManifest", [])
                    # Section 8: re-verify the staged INPUT files are unchanged and
                    # that any materialized artifacts are present with the declared
                    # digests *before* execution.
                    for ev in self._verify_inputs_and_artifacts(ws, staged_files, manifest_files):
                        infra.append(ev)
                    if infra:
                        return self._finish(result, infra, ws)
                    # Verify the artifact manifest is well formed before execute.
                    if handle is None:
                        infra.append(events.event(events.PROTOCOL_VIOLATION,
                                                  "successful compile returned no artifact handle"))
                        return self._finish(result, infra, ws)
                    exec_req = session.make_request(
                        "execute",
                        workspace=ws.run_dir,
                        artifactHandle=handle,
                        inputTreeDigest=tree_digest,
                        executeTimeoutMs=manifest.get("executeTimeoutMs", self._execute_timeout_ms),
                        stdinBase64=self._stdin_b64(manifest),
                    )
                    execute_result, executed2 = self._phase(
                        transport, session, exec_req,
                        manifest.get("executeTimeoutMs", self._execute_timeout_ms),
                        infra, events.EXECUTE_TIMEOUT)
                    executed = executed or executed2
                    result["phases"]["execute"] = (execute_result or {}).get("status", "NONE")
                    self._phase_obs["execute"] = execute_result
                    # Section 8: after execution, artifacts must not have changed.
                    if not infra:
                        for ev in self._verify_artifacts(ws.compile_dir, manifest_files):
                            infra.append(ev)

            # 6. apply the oracle.
            decision = OC.judge(manifest, compile_result, execute_result,
                                infra_events=infra, executed=executed)
            result["status"] = decision.status
            result["reason"] = decision.reason
            if compile_result and compile_result.get("diagnostics"):
                # Project protocol diagnostics into the report's flat, closed shape.
                diags = []
                for d in compile_result["diagnostics"]:
                    entry = {"family": d["family"], "code": d["code"]}
                    if "location" in d:
                        entry["startByteOffset"] = d["location"]["startByteOffset"]
                        entry["endByteOffset"] = d["location"]["endByteOffset"]
                    diags.append(entry)
                result["diagnostics"] = diags
            return self._finish(result, infra, ws)

        except protocol.ProtocolError as exc:
            infra.append(events.event(events.PROTOCOL_VIOLATION, exc.reason))
            return self._finish(result, infra, ws)
        except adapter_transport._ReadTimeout as exc:
            kind = events.COMPILE_TIMEOUT if result["phases"].get("execute") in (None, "NONE") \
                else events.EXECUTE_TIMEOUT
            infra.append(events.event(kind, str(exc)))
            self._force_kill(transport)
            return self._finish(result, infra, ws)
        except isolation.FixtureError as exc:
            infra.append(events.event(events.WORKSPACE_ESCAPED, str(exc)))
            return self._finish(result, infra, ws)
        finally:
            if transport:
                transport.close()
                if transport.infra_events:
                    for ev in transport.infra_events:
                        infra.append(ev)
                    # Section 8: any nonzero adapter exit or loss of the protocol
                    # channel is an infrastructure error EVEN IF an earlier
                    # response claimed a language result. It overrides a PASS or
                    # FAIL produced during the session.
                    result["status"] = "INFRASTRUCTURE_ERROR"
                    result["reason"] = "adapter process error: %s" % \
                        ",".join(sorted({e["kind"] for e in transport.infra_events}))
                    result["infrastructureEvents"] = infra
            # Single recording point: reached by every exit path of run_test, after
            # any status override above has been applied.
            self._record_observation(result)
            ws.remove()

    # -- helpers -----------------------------------------------------------
    def _phase(self, transport, session, req, timeout_ms, infra, timeout_kind):
        """Send a request and read one response, converting timeouts to events.

        Returns ``(response, executed)``. ``executed`` is True when the response
        carries observable application output (used to reject compile-only tests
        that ran application code).
        """
        try:
            transport.send(protocol.encode_request(req))
            raw = transport.read_line(timeout_ms)
            resp = session.expect_response(raw, req["requestId"], req["op"])
            executed = bool(resp.get("stdoutBase64") or resp.get("stderrBase64"))
            return resp, executed
        except adapter_transport._ReadTimeout as exc:
            infra.append(events.event(timeout_kind, str(exc)))
            self._force_kill(transport)
            return None, False
        except protocol.ProtocolError as exc:
            infra.append(events.event(events.PROTOCOL_VIOLATION, exc.reason))
            return None, False

    def _force_kill(self, transport):
        try:
            transport._terminate_tree()
            transport._kill_tree()
        except Exception:
            pass

    def _verify_inputs_and_artifacts(self, ws, staged_files, artifact_manifest):
        """Re-hash staged INPUT files and verify materialized artifacts (section 8).

        Only the staged input files are checked for mutation; artifacts are *new*
        files (materialized by an AOT adapter) verified separately by their declared
        digests, so adding artifacts is not mistaken for source mutation. Source
        mutation, a missing artifact, or a digest mismatch is an infrastructure /
        protocol error -- never a pass.
        """
        events_list = []
        for rel, want in staged_files.items():
            full = os.path.join(ws.compile_dir, rel)
            if not os.path.isfile(full) or digest.sha256_file(full) != want:
                events_list.append(events.event(events.SOURCE_MUTATED,
                                                "input file changed after staging: %r" % rel))
        events_list.extend(self._verify_artifacts(ws.compile_dir, artifact_manifest))
        return events_list

    def _verify_artifacts(self, compile_dir, artifact_manifest):
        out = []
        for entry in (artifact_manifest or []):
            full = os.path.join(compile_dir, entry["path"])
            if not os.path.isfile(full):
                out.append(events.event(events.ARTIFACT_MISMATCH,
                                        "declared artifact missing: %r" % entry["path"]))
                continue
            if digest.sha256_file(full) != entry["digest"]:
                out.append(events.event(events.ARTIFACT_MISMATCH,
                                        "artifact digest mismatch: %r" % entry["path"]))
        return out

    def _stdin_b64(self, manifest):
        stdin = manifest.get("stdin")
        if not stdin:
            return ""
        if "base64" in stdin:
            return stdin["base64"]
        return _b64(stdin["text"].encode("utf-8"))

    def _finish(self, result, infra, ws):
        if infra:
            result["status"] = "INFRASTRUCTURE_ERROR"
            result["reason"] = "infrastructure error"
            result["infrastructureEvents"] = infra
        return result

    def _record_observation(self, result):
        """Copy the raw, unjudged observations to the differential sink.

        Called from `run_test`'s `finally`, not from `_finish`, because the `finally`
        can still overwrite `status` with an infrastructure verdict after `_finish`
        has returned. Recording at a single point after every mutation is what keeps
        the recorded verdict equal to the reported one; recording earlier would let
        differential mode compare a status the report never contained.

        Recorded even when the verdict is an infrastructure error: differential mode
        must be able to see that one implementation crashed where another answered,
        and must never let agreement here be read as conformance.
        """
        if self._observation_sink is None:
            return
        obs = getattr(self, "_phase_obs", None) or {}
        comp = obs.get("compile") or {}
        execd = obs.get("execute") or {}
        self._observation_sink[result["testId"]] = {
            "stdoutBase64": execd.get("stdoutBase64", ""),
            "stderrBase64": execd.get("stderrBase64", ""),
            "languageExit": execd.get("languageExit"),
            "runtimeCategory": execd.get("runtimeCategory"),
            "compileStatus": comp.get("status"),
            "executeStatus": execd.get("status"),
            "status": result.get("status"),
            "phases": dict(result.get("phases") or {}),
            "diagnostics": [dict(d) for d in result.get("diagnostics", [])],
            "inputTreeDigest": result.get("inputTreeDigest"),
            "infrastructureEvents": sorted(
                {e.get("kind") for e in result.get("infrastructureEvents", [])}),
        }
        return result


def run_suite(runner: Runner, corpus_root, inventory_model, profile, spec_version,
              requested_full_profile, filters=None, adapter_config=None):
    """Full pipeline: validate corpus, preflight, run tests, build report."""
    context = {
        "specVersion": spec_version,
        "profile": profile,
        "platform": {"os": platform.system(), "arch": platform.architecture()[0]},
        "timestamp": RP.utc_now(),
        "filters": filters or {},
        "inputDigests": {},
        "iutFingerprint": adapter_config.fingerprint if adapter_config else None,
    }
    # Whether the requested spec revision is a frozen, exhaustive normative baseline
    # that may carry a full-profile certification (TCK.md sections 5 / 5.1). A draft
    # revision -- or one absent from CERTIFIABLE_SPEC_VERSIONS -- withholds aggregate
    # certification regardless of how many seeded tests pass; per-test PASS/FAIL and
    # the build-gate exit code are unaffected.
    context["baselineCertifiable"] = spec_version in versions.CERTIFIABLE_SPEC_VERSIONS
    context["inputDigests"]["requirements"] = inventory_model["inventoryDigest"]
    if adapter_config:
        context["inputDigests"]["adapterConfig"] = adapter_config.config_digest

    # Validate the whole corpus BEFORE invoking an adapter.
    manifest_list = MF.load_and_validate(corpus_root, runner._mschema, inventory_model, spec_version)
    context["inputDigests"]["manifests"] = MF.corpus_digest([m["__path__"] for m in manifest_list])

    selected = _filter(manifest_list, filters)

    try:
        impl = runner.preflight(profile, spec_version)
    except (PreflightError, protocol.ProtocolError) as exc:
        context["inputInvalid"] = True
        context.setdefault("implementation", {})
        report = RP.build_report(context, [
            {**_notrun(m), "reason": "preflight failed: %s" % exc, "status": "NOT_RUN"}
            for m in selected], requested_full_profile)
        return report

    context["implementation"] = {
        "name": impl["name"], "version": impl["version"],
        "fingerprint": impl.get("fingerprint"), "capabilities": impl.get("capabilities", []),
    }
    # Unsupported required capabilities: the mandatory full-language profile
    # requires 'compile-only'; if the adapter does not declare it, that required
    # capability is unsupported and full conformance is blocked.
    declared_caps = set(impl.get("capabilities", []))
    required_caps = set(inventory_model["requiredCapabilities"])
    unsupported_required = sorted(required_caps - declared_caps)
    context["unsupportedRequiredCapabilities"] = unsupported_required
    context["unsupportedOptionalCapabilities"] = []

    # Requirement coverage / gaps from the inventory.
    gaps = inventory_model["requirementGaps"]
    context["requirementGaps"] = gaps
    context["ambiguities"] = inventory_model.get("ambiguities", [])
    context["requirementCoverage"] = inventory_model.get("coverage", {})

    results = []
    for manifest in selected:
        results.append(runner.run_test(manifest, impl))

    return RP.build_report(context, results, requested_full_profile)


def _notrun(m):
    return {"testId": m["testId"], "requirements": m["requirements"], "phases": {}, "status": "NOT_RUN"}


def _filter(manifests, filters):
    if not filters:
        return manifests
    tests = set(filters.get("tests", []))
    cats = set(filters.get("categories", []))
    out = []
    for m in manifests:
        if tests and m["testId"] not in tests:
            continue
        if cats and m["category"] not in cats:
            continue
        out.append(m)
    return sorted(out, key=lambda m: m["testId"])
