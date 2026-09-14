#!/usr/bin/env bash
# Generate large synthetic Solvik sources used to check compiler scaling.
#
# Usage: benchmarks/generate_large.sh <count> <output.sol> [structs|traits|locals]
#
# The generated program is valid Solvik that declares <count> structs (or
# trait/implementation pairs, or <count> locals plus <count> if statements
# in one method) plus a Main entry point. It exists to exercise the lexer,
# parser, semantic analysis, and Java emitter with many declarations without
# relying on a real application.
set -euo pipefail

count="${1:?usage: generate_large.sh <count> <output.sol> [structs|traits]}"
output="${2:?usage: generate_large.sh <count> <output.sol> [structs|traits]}"
kind="${3:-structs}"

{
    echo "package large"
    echo
    if [ "$kind" = "locals" ]; then
        cat <<'EOF'
struct Main {

    pub func run(args: String...): Integer {

EOF
        i=0
        while [ "$i" -lt "$count" ]; do
            echo "        var v$i: Long = $i"
            i=$((i + 1))
        done
        i=0
        while [ "$i" -lt "$count" ]; do
            cat <<EOF
        if v$i > 0 {
            v$i = v$i + 1
        }

EOF
            i=$((i + 1))
        done
        cat <<'EOF'
        return 0
    }
}
EOF
    elif [ "$kind" = "traits" ]; then
        i=0
        while [ "$i" -lt "$count" ]; do
            cat <<EOF
trait I$i {

    func m$i(self, x: Long): Long
}

struct T$i implements I$i {

    pub func m$i(self, x: Long): Long {
        return x + $i
    }
}

EOF
            i=$((i + 1))
        done
    else
        i=0
        while [ "$i" -lt "$count" ]; do
            cat <<EOF
struct S$i {

    value: Long

    pub func new(value: Long): Self {
        return Self {
            value: value,
        }
    }

    pub func compute(self, x: Long): Long {
        var a: Long = self.value
        for j in 0..100 {
            a += x
        }
        return a
    }
}

EOF
            i=$((i + 1))
        done
    fi
    if [ "$kind" != "locals" ]; then
        cat <<EOF
struct Main {

    pub func run(args: String...): Integer {
        var s: Integer = 0
        for i in 0..$count {
            s += 1
        }
        return s
    }
}
EOF
    fi
} > "$output"

echo "wrote $output ($(wc -l < "$output") lines, kind=$kind, count=$count)"
