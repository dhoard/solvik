package bootstrap_cli

use file:native

// Source-level bootstrap driver. It deliberately reports the same compact
// result for every implementation; the Python reference remains the oracle
// while this Solvik program exercises the native frontend itself.

func main() -> int {
    args: list<string> = process.args()
    if args.len() == 0 {
        println("usage: solvik bootstrap/main.sol <source>")
        return 1
    }
    result: bootstrap_native.FrontendResult = bootstrap_native.analyzeFile(args[0])
    println("tokens=" .. result.tokenCount .. " nodes=" .. result.nodeCount .. " functions=" .. result.functionCount .. " parse_errors=" .. result.parseErrors .. " semantic_errors=" .. result.semanticErrors)
    if result.parseErrors != 0 || result.semanticErrors != 0 {
        return 2
    }
    return 0
}
