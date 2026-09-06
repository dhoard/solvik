package reference_phase12_contract

// Phase 12 contract-hardening coverage. String positions and padding widths
// use Unicode characters, while byteLength remains a UTF-8 byte count.
func main() -> Int {
    text: String = "aéx"

    if text.len() != 3 {
        return 1
    }
    if text.byteLength() != 4 {
        return 2
    }
    if text.indexOf("é") != 1 {
        return 3
    }
    if text.indexOf("x") != 2 {
        return 4
    }
    if text.indexOf("missing") != -1 {
        return 5
    }

    if string.padStart("é", 3, "0") != "00é" {
        return 6
    }
    if string.padEnd("é", 3, "0") != "é00" {
        return 7
    }
    if string.padStart("éx", 2, "0") != "éx" {
        return 8
    }
    if string.padEnd("éx", 2, "0") != "éx" {
        return 9
    }

    return 0
}
