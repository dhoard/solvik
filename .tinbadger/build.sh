#!/usr/bin/env bash
#
# build.sh — solvik build for tinbadger.
#
# Toolchains are pinned by full URL + sha256, passed explicitly to
# install_package. No common mirror is assumed: each package's URL is
# written in the call below, so packages can live anywhere (this mirror,
# a GitHub release, etc.). The build itself is delegated to the repo's
# ./build.sh (which uses GoReleaser for the multi-arch release build).
#

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
cd "${SCRIPT_DIR}/.."

# ── Tool root ───────────────────────────────────────────────────────────────
# Where packages install inside the build VM (per-job workspace disk).
# Override for local runs.
TOOLS_DIR="${TINBADGER_TOOLS_DIR:-/work/tools}"

# install_package <name> <pkg-url> <pkg-sha256> [install-url] [install-sha256]
#   download → verify sha256 → extract → (verify + run install script) → PATH.
#   All steps run in this shell so the PATH export sticks for the build below.
#   Any failure (HTTP error, checksum mismatch, bad install script) fails the build.
install_package() {
    local name="$1" url="$2" sha="$3" install_url="${4:-}" install_sha="${5:-}"
    local file pkg stage root entries script actual

    file="${url%%\?*}"
    if [[ ! "$name" =~ ^[A-Za-z0-9._-]+$ ]] || [[ "$name" == *".."* ]]; then
        echo "ERROR: unsafe package name: $name" >&2
        exit 1
    fi
    [[ "$sha" =~ ^[0-9a-fA-F]{64}$ ]] \
        || { echo "ERROR: malformed sha256 pin for $url" >&2; exit 1; }

    stage="$(mktemp -d /tmp/tb-stage.XXXXXX)"
    pkg="$(mktemp /tmp/tb-pkg.XXXXXX)"

    echo "==> download started: $name from $url"
    curl -fsSL --retry 2 --max-time 600 --max-filesize 2147483648 -o "$pkg" "$url"
    echo "==> download completed: $name ($(wc -c < "$pkg") bytes)"

    actual="$(sha256sum "$pkg" | awk '{print $1}')"
    if [[ "${actual,,}" != "${sha,,}" ]]; then
        echo "ERROR: sha256 mismatch for $url" >&2
        echo "  expected: $sha" >&2
        echo "  actual:   $actual" >&2
        exit 1
    fi
    echo "==> checksum verified: $name (sha256 ${sha:0:12}…)"

    case "$file" in
        *.zip)          unzip -q "$pkg" -d "$stage" ;;
        *.tar.gz|*.tgz) tar  -xzf "$pkg" -C "$stage" ;;
        *.tar.bz2)      tar  -xjf "$pkg" -C "$stage" ;;
        *.tar.xz)       tar  -xJf "$pkg" -C "$stage" ;;
        *.tar)          tar  -xf  "$pkg" -C "$stage" ;;
        *) echo "ERROR: unsupported archive: $url" >&2; exit 1 ;;
    esac

    # unwrap a single top-level directory (Corretto, Node, and Go wrap)
    mapfile -t entries < <(find "$stage" -mindepth 1 -maxdepth 1)
    if (( ${#entries[@]} == 1 )) && [[ -d "${entries[0]}" ]]; then
        root="${entries[0]}"
    else
        root="$stage"
    fi

    rm -rf -- "${TOOLS_DIR:?}/$name"
    mkdir -p "$TOOLS_DIR"
    mv "$root" "$TOOLS_DIR/$name"
    rm -rf "$stage"
    rm -f "$pkg"

    if [[ -n "$install_url" ]]; then
        echo "==> install started: $name"
        script="$(mktemp /tmp/tb-install.XXXXXX)"
        curl -fsSL --retry 2 -o "$script" "$install_url"
        actual="$(sha256sum "$script" | awk '{print $1}')"
        if [[ "${actual,,}" != "${install_sha,,}" ]]; then
            echo "ERROR: sha256 mismatch for install script $install_url" >&2
            exit 1
        fi
        echo "==> install script checksum verified: $name (sha256 ${install_sha:0:12}…)"
        (cd "$TOOLS_DIR/$name" && bash "$script" "$TOOLS_DIR/$name" </dev/null)
        echo "==> install completed: $name"
        rm -f "$script"
    fi

    if [[ -d "$TOOLS_DIR/$name/bin" ]]; then
        export PATH="$TOOLS_DIR/$name/bin:$PATH"
    else
        export PATH="$TOOLS_DIR/$name:$PATH"
    fi
    echo "==> $name ready at $TOOLS_DIR/$name"
}

echo "==> Installing pinned toolchains (Go 1.25 + GoReleaser + Rust 1.97.1)..."
install_package go \
    "http://192.168.123.1/packages/go/go1.25.13.linux-amd64.tar.gz" \
    39042a078ea9ceebe3ecda4a7188f0f5b96e14a071d27923ba7f40b456e85ae3 \
    "http://192.168.123.1/packages/go/go1.25.13-linux-amd64-install.sh" \
    c82ce193108ebbf7d9756166a7ce8769f6d8c04b44375135f305b312bcef3127

export GOROOT="$TOOLS_DIR/go"
export PATH="$GOROOT/bin:$PATH"
export GOTOOLCHAIN="local"
export CGO_ENABLED=0

install_package goreleaser \
    "http://192.168.123.1/packages/goreleaser/goreleaser_Linux_x86_64.tar.gz" \
    a99bbc7ae0d8d897b07c4c497a9b62f222558804715ef219d1af05a7e417bc80 \
    "http://192.168.123.1/packages/goreleaser/goreleaser_Linux_x86_64-install.sh" \
    18108135575736b22bae07115ef310b0347ebde60d2479e34effb07b17c60eeb

install_package rust \
    "http://192.168.123.1/packages/rust/rust-stable-x86_64-unknown-linux-gnu.tar.gz" \
    1c1d617520202c1dee4d512c117f299885070fc0c5c445a5f92e737102c72e31 \
    "http://192.168.123.1/packages/rust/rust-stable-x86_64-unknown-linux-gnu-install.sh" \
    41a43dfc7cf551188f029196a2c8524125b1fa347cf7c1d16bc5c728dc7691a8

# Rust crate dependencies are vendored under ./rust/vendor (see
# rust/.cargo/config.toml); force cargo offline so it never touches crates.io.
# The vendored-source replacement is written into $CARGO_HOME/config.toml so it
# is honored no matter which directory cargo is invoked from (build scripts call
# it with --manifest-path from the repo root).
export CARGO_NET_OFFLINE=true
export CARGO_HOME="${TINBADGER_CARGO_HOME:-$TOOLS_DIR/cargo-home}"
mkdir -p "$CARGO_HOME"
cat > "$CARGO_HOME/config.toml" <<EOF
[source.crates-io]
replace-with = "vendored-sources"

[source.vendored-sources]
directory = "$(pwd)/rust/vendor"
EOF

# Isolate Go caches and module cache on the per-job workspace disk.
export GOPATH="${TINBADGER_GOPATH:-$TOOLS_DIR/gopath}"
export GOMODCACHE="$GOPATH/pkg/mod"
export GOCACHE="${TINBADGER_GOCACHE:-$TOOLS_DIR/gocache}"
mkdir -p "$GOMODCACHE" "$GOCACHE"

echo "==> go version: $(go version)"
echo "==> goreleaser version: $(goreleaser --version | head -n1)"
echo "==> cargo version: $(cargo --version)"
echo "==> rustc version: $(rustc --version)"

echo "==> Running repo build script (./build.sh all)..."
exec ./build.sh all
