#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATCH_ZIP="${SCRIPT_DIR}/solvik-language-evolution-patch.zip"

fail() {
    echo "patch.sh: $*" >&2
    exit 1
}

[[ -f "go.mod" && -f "solvik.py" && -f "LANGUAGE.md" ]] || \
    fail "run this script from the root of the dhoard/solvik checkout"
[[ -f "$PATCH_ZIP" ]] || fail "missing $PATCH_ZIP"
command -v python3 >/dev/null || fail "python3 is required"
command -v unzip >/dev/null || fail "unzip is required"

check_blob() {
    local path="$1" expected="$2"
    if [[ "${SOLVIK_PATCH_FORCE:-0}" == "1" ]]; then
        return 0
    fi
    command -v git >/dev/null || fail "git is required for safe base verification (or set SOLVIK_PATCH_FORCE=1)"
    local actual
    actual="$(git hash-object "$path")"
    [[ "$actual" == "$expected" ]] || fail "$path is not the expected base (expected $expected, got $actual); update your checkout or set SOLVIK_PATCH_FORCE=1 after reviewing"
}

check_blob solvik.py 557fb9b84c13a943a1cc0103d36b36bcf19eac07
check_blob LANGUAGE.md d67ec63ffff2b6f63c89d93fc6062354e9cda52a
check_blob README.md 785a4b02dc5906480c487520749e5900be84d81b
check_blob build.sh 4551939b46545d08e315b980c27220e90359738e

# Remove paths owned by this patch before extracting so stale code cannot
# survive a re-application. Existing primary source files are transformed only
# after their exact base hashes have been verified above.
rm -rf .solvik-patch test/reference
rm -f tools/parity.py PARITY.md PATCH_MANIFEST.md

unzip -q "$PATCH_ZIP" -d .
python3 .solvik-patch/apply_language_evolution.py
rm -rf .solvik-patch
chmod +x tools/parity.py build.sh solvik.py 2>/dev/null || true

python3 -m py_compile solvik.py tools/parity.py

echo ""
echo "Solvik Python-first language-evolution patch applied."
echo "Recommended validation:"
echo "  python3 tools/parity.py --reference-only"
echo "  ./build.sh"
