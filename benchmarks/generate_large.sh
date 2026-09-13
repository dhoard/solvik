#!/usr/bin/env bash
# Generate large synthetic Solvik sources used to check compiler scaling.
#
# Usage: benchmarks/generate_large.sh <count> <output.sol> [structs|interfaces|locals]
#
# The generated program is valid Solvik that declares <count> structs (or
# interface/implementation pairs, or <count> locals plus <count> if statements
# in one method) plus a Main entry point. It exists to exercise the lexer,
# parser, semantic analysis, and Java emitter with many declarations without
# relying on a real application.
set -euo pipefail

count="${1:?usage: generate_large.sh <count> <output.sol> [structs|interfaces]}"
output="${2:?usage: generate_large.sh <count> <output.sol> [structs|interfaces]}"
kind="${3:-structs}"

{
    echo "package large"
    echo
    if [ "$kind" = "locals" ]; then
        cat <<'EOF'
struct Main {

    public func run(args: String...): Long {

EOF
        i=0
        while [ "$i" -lt "$count" ]; do
            echo "        let mutable v$i: Long = $i"
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
    elif [ "$kind" = "interfaces" ]; then
        i=0
        while [ "$i" -lt "$count" ]; do
            cat <<EOF
interface I$i {

    func m$i(self, x: Long): Long
}

struct T$i implements I$i {

    public func m$i(self, x: Long): Long {
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

    public func new(value: Long): Self {
        return Self {
            value: value,
        }
    }

    public func compute(self, x: Long): Long {
        let mutable a: Long = self.value
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

    public func run(args: String...): Long {
        let mutable s: Long = 0
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
