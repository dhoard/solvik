// test/switch_test.sol — switch statement tests
//
// Tests: exact int matching, exact string matching, default clause,
//        regex matching, first-match semantics, multiple cases

package test

func classifyInt(code: int) -> string {
    switch code {
        case 200 {
            return "OK"
        }
        case 201 {
            return "Created"
        }
        case 404 {
            return "Not Found"
        }
        case 500 {
            return "Internal Error"
        }
        default {
            return "Unknown"
        }
    }
}

func classifyString(cmd: string) -> string {
    switch cmd {
        case "start" {
            return "starting"
        }
        case "stop" {
            return "stopping"
        }
        case "restart" {
            return "restarting"
        }
        default {
            return "unknown"
        }
    }
}

func classifyRegex(entry: string) -> string {
    switch entry {
        case regex(r"^ERROR\s+") {
            return "error"
        }
        case regex(r"^WARN\s+") {
            return "warning"
        }
        case regex(r"^INFO\s+") {
            return "info"
        }
        case "UNKNOWN" {
            return "unknown-tag"
        }
        default {
            return "other"
        }
    }
}

func firstMatch(x: int) -> string {
    switch x {
        case 1 {
            return "first"
        }
        case 1 {
            return "second" // should never be reached
        }
        default {
            return "default"
        }
    }
}

func main() -> int {
    // === int exact matching ===
    if classifyInt(200) != "OK" {
        println("FAIL: classifyInt(200) should be OK")
    }
    if classifyInt(404) != "Not Found" {
        println("FAIL: classifyInt(404) should be Not Found")
    }
    if classifyInt(999) != "Unknown" {
        println("FAIL: classifyInt(999) should be Unknown")
    }
    if classifyInt(500) != "Internal Error" {
        println("FAIL: classifyInt(500) should be Internal Error")
    }

    // === string exact matching ===
    if classifyString("start") != "starting" {
        println("FAIL: classifyString('start') should be starting")
    }
    if classifyString("stop") != "stopping" {
        println("FAIL: classifyString('stop') should be stopping")
    }
    if classifyString("reboot") != "unknown" {
        println("FAIL: classifyString('reboot') should be unknown")
    }

    // === string exact matching via variable ===
    cmd: string = "restart"
    if classifyString(cmd) != "restarting" {
        println("FAIL: classifyString(restart) should be restarting")
    }

    // === default clause ===
    if classifyInt(0) != "Unknown" {
        println("FAIL: classifyInt(0) should use default")
    }
    if classifyString("") != "unknown" {
        println("FAIL: classifyString('') should use default")
    }

    // === regex matching ===
    if classifyRegex("ERROR something failed") != "error" {
        println("FAIL: classifyRegex(ERROR...) should be error")
    }
    if classifyRegex("WARN  disk full") != "warning" {
        println("FAIL: classifyRegex(WARN...) should be warning")
    }
    if classifyRegex("INFO  startup") != "info" {
        println("FAIL: classifyRegex(INFO...) should be info")
    }
    if classifyRegex("plain text") != "other" {
        println("FAIL: classifyRegex(plain text) should be other")
    }

    // === regex with exact match fallback ===
    if classifyRegex("UNKNOWN") != "unknown-tag" {
        println("FAIL: classifyRegex(UNKNOWN) should be unknown-tag")
    }

    // === first-match semantics ===
    if firstMatch(1) != "first" {
        println("FAIL: firstMatch(1) should be 'first', got '" .. firstMatch(1) .. "'")
    }
    if firstMatch(99) != "default" {
        println("FAIL: firstMatch(99) should be 'default', got '" .. firstMatch(99) .. "'")
    }

    // === switch without default ===
    switch 200 {
        case 200 {
            // expected
        }
        case 404 {
            println("FAIL: should not match 404")
        }
    }

    println("switch tests passed")
    return 0
}