# Protocol session transcript (reference)

A byte-level, single-test session against a conforming JVM-style adapter. `<` is a
runner request to adapter stdin; `>` is an adapter response on stdout. Guest output
is carried in `stdoutBase64`/`stderrBase64`, never as adapter stdout.

Program under test (staged into the workspace): a program that prints `hi` and
calls `exit(0)`.

```
< {"op":"describe","protocolVersion":"1","requestId":1,"schemaVersion":1}
> {"implementation":{"capabilities":["compile-only"],"fingerprint":"3f2b…",
>  "limits":{"maxCapturedOutputBytes":1048576,"maxDiagnostics":512,
>  "maxRequestBytes":1048576,"maxResponseBytes":1048576,"maxArtifacts":4096,
>  "maxSourceTreeBytes":16777216},"name":"solvik-jvm","profiles":["full-language"],
>  "specVersions":["2026.09-draft"],"version":"1.0.0-SNAPSHOT"},"op":"describe",
>  "protocolVersion":"1","requestId":1,"schemaVersion":1}

< {"compileTimeoutMs":30000,"entryPoint":"main.sol","inputTreeDigest":"a4f1…",
> (runner request, cont.)
< {"op":"compile","protocolVersion":"1","requestId":2,"schemaVersion":1,"workspace":"/tmp/solvik-tck-xyz/compile"}
> {"artifactHandle":"7c9d…","artifactManifest":[],"op":"compile",
>  "protocolVersion":"1","requestId":2,"schemaVersion":1,"status":"COMPILE_ACCEPTED"}

< {"artifactHandle":"7c9d…","executeTimeoutMs":30000,"inputTreeDigest":"a4f1…",
<  "op":"execute","protocolVersion":"1","requestId":3,"schemaVersion":1,
<  "stdinBase64":"","workspace":"/tmp/solvik-tck-xyz/run"}
> {"languageExit":0,"op":"execute","protocolVersion":"1","requestId":3,
>  "schemaVersion":1,"status":"NORMAL_EXIT","stderrBase64":"","stdoutBase64":"aGkK"}
```

Runner closes stdin; adapter stdout reaches EOF; adapter exits 0. The runner applies
the manifest oracle: `SUCCESS` with `languageExit=0` and stdout bytes `hi\n` -> PASS.

A rejected compile instead returns, with **no** artifact handle or stdout:

```
> {"diagnostics":[{"code":"SOLV-SEM-045","family":"SEM",
>  "location":{"endByteOffset":132,"startByteOffset":101}}],"op":"compile",
>  "protocolVersion":"1","requestId":2,"schemaVersion":1,"status":"COMPILE_REJECTED"}
```

Notes:

* The `describe` response is the only message carrying `implementation`; `compile`
  and `execute` carry a `status`. A response missing the required shape field is a
  protocol error (runner-enforced, because a *request* carries neither).
* `inputTreeDigest` is re-verified by the runner before execute; a mutated staged
  tree or a missing/mismatched materialized artifact is an infrastructure error.
