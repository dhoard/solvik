# Solvik

Solvik is a strongly and statically typed object-oriented language for GraalVM/Truffle. This repository converts GraalVM SimpleLanguage in place so that proven Truffle infrastructure can be reused without preserving SimpleLanguage syntax, dynamic semantics, or a legacy compatibility mode.

## Status

The implementation is in Phase 0. The checked-in language code is still inherited migration input and is not a Solvik release. See `docs/STATUS.md` for the only phase that may be implemented next.

## Implementation

For an implementation run, use:

> Follow `prompts/IMPLEMENT_SOLVIK.md` exactly. Execute only the phase marked `NEXT` in `docs/STATUS.md`.

For unattended phase-by-phase execution with a fresh Qwen context and independent builds after every phase, use:

```bash
./workflow.sh
```

The workflow hardcodes `yolo-auto/qwen3.8-flash`, verifies both `./build.sh` and `./build-native.sh`, and creates a local git checkpoint after each successful phase. It never pushes. Run `./workflow.sh --help` for dirty-worktree recovery options.

The authoritative documents are:

- `AGENTS.md`
- `docs/LANGUAGE_SPEC.md`
- `docs/ARCHITECTURE.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/TEST_PLAN.md`
- `docs/STATUS.md`

The canonical build commands are:

```bash
./build.sh
./build-native.sh
```

Both wrappers use `JAVA_HOME=/opt/graalvm` and run `./mvnw clean package`.

Do not add a SimpleLanguage compatibility parser, option, launcher, or execution mode.

## License

The repository retains the upstream Universal Permissive License and required attribution. See `LICENSE.md`.
