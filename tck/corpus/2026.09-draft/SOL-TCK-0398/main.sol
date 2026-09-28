// Solvik TCK SOL-TCK-0398
// The two includes canonicalize to the same file, so the included print runs once.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Two paths or symlinks that resolve to the same file are the same include.
//   - A canonical physical file is expanded at most once per evaluated root. A later include of the same canonical file is a no-op, so a diamond is deterministic and an included top-level statement never runs twice.
//
include "a.sol"
include "sub/../a.sol"
print("cz")
