// solvik::util::UUID — a version 7 identifier (RFC 9562) written in Solvik, not a Java binding.
// Packaged with the compiler and part of every program: no import names it. Its module blocks
// declare `solvik::util`; the file path decides nothing about that namespace.
//
// A value is a 48-bit big-endian millisecond timestamp followed by 74 random bits, rendered as
// canonical lowercase text. Values come from now() and parse(); the constructor is private, so
// every UUID is well formed by construction. compareTo() orders chronologically: timestamp first,
// then the random fields, so ids sort by creation time. Creation order within a single millisecond
// is not preserved (rand_a is random, not a counter), and this module keeps no cross-thread state.

module solvik {

    module util {

        // --------------------------------------------------------- JDK bindings
        // Module-private: they implement this module, they are not its surface.

        type Hex native "java.lang.Long" {
            static pub func toHexString(value: Long) -> String
            static pub func parseUnsignedLong(text: String, radix: Integer) -> Long
            static pub func compareUnsigned(a: Long, b: Long) -> Integer
        }

        type Clock native "java.lang.System" {
            static pub func currentTimeMillis() -> Long
        }

        type Random native "java.security.SecureRandom" {
            pub func Random()
            pub func nextLong() -> Long
        }

        // ---------------------------------------------------------------- UUID

        pub type UUID {
            // Canonical 36-character lowercase text; the value's single source of truth.
            text := ""
            // The 48-bit timestamp, kept so timestamp() and ordering never re-parse it.
            millis := Long(0)

            // One seeded generator per program; SecureRandom is thread-safe.
            static generator := Random()

            func UUID(text: String, millis: Long) {
                self.text = text
                self.millis = millis
            }

            static pub func now() -> UUID {
                stampMillis := timestampNow()
                stamp := pad(Hex.toHexString(stampMillis), 12)
                first := pad(Hex.toHexString(generator.nextLong()), 16)
                second := pad(Hex.toHexString(generator.nextLong()), 16)
                // Canonical text is 8-4-4-4-12: timestamp, version plus rand_a,
                // variant plus rand_b, and the rest of rand_b.
                timeHigh := stamp.substring(0, 8)
                timeLow := stamp.substring(8, 12)
                versionField := "7" .. first.substring(0, 3)
                variantField := variantDigit(first.substring(3, 4)) .. second.substring(0, 3)
                randomTail := second.substring(3, 15)
                canonical := timeHigh .. "-" .. timeLow .. "-" .. versionField .. "-" .. variantField .. "-" .. randomTail
                return UUID(canonical, stampMillis)
            }

            // Accepts canonical text of either case; Null when it is not a version 7 UUID.
            static pub func parse(source: String) -> UUID? {
                if source == Null {
                    return Null
                }
                if source.size() != 36 {
                    return Null
                }
                canonical := source.toLowerCase()
                if !valid(canonical) {
                    return Null
                }
                return UUID(canonical, millisOf(canonical))
            }

            pub func timestamp() -> Long {
                return millis
            }

            pub func compareTo(other: UUID) -> Integer {
                if millis < other.timestamp() { return -1 }
                if millis > other.timestamp() { return 1 }
                versionBits := Hex.compareUnsigned(versionFieldOf(text), versionFieldOf(other.toString()))
                if versionBits != 0 { return versionBits }
                return Hex.compareUnsigned(tailOf(text), tailOf(other.toString()))
            }

            pub func toString() -> String {
                return text
            }

            // Equality is value equality. The equals contract checks and narrows other to UUID
            // before this body, so private peer state is available for direct comparison.
            pub func equals(other: Any?) -> Boolean {
                return self.text == other.text
            }

            pub func hashCode() -> Integer {
                return text.hashCode()
            }

            // ---------------------------------------------------- implementation

            static func timestampNow() -> Long {
                current := Clock.currentTimeMillis()
                if current < Long(0) { return Long(0) }
                if current > Long("281474976710655") { return Long("281474976710655") }
                return current
            }

            static func pad(value: String, width: Integer) -> String {
                mut padded := value
                while padded.size() < width {
                    padded = "0" .. padded
                }
                return padded
            }

            static func hexValue(digit: String) -> Integer {
                return "0123456789abcdef".indexOf(digit)
            }

            static func hexDigit(value: Integer) -> String {
                return "0123456789abcdef".substring(value, value + 1)
            }

            // Characters 14-17 of the canonical text: the version nibble plus rand_a.
            static func versionFieldOf(canonical: String) -> Long {
                return Hex.parseUnsignedLong(canonical.substring(14, 18), 16)
            }

            // Characters 19-35 without the separator: the variant nibble plus rand_b.
            static func tailOf(canonical: String) -> Long {
                return Hex.parseUnsignedLong(canonical.substring(19, 23) .. canonical.substring(24, 36), 16)
            }

            static func millisOf(canonical: String) -> Long {
                return Hex.parseUnsignedLong(canonical.substring(0, 8) .. canonical.substring(9, 13), 16)
            }

            // The variant nibble is 10xx: two fixed bits plus two random ones.
            static func variantDigit(digit: String) -> String {
                return hexDigit(8 + hexValue(digit) % 4)
            }

            static func valid(canonical: String) -> Boolean {
                mut index := 0
                while index < 36 {
                    character := canonical.substring(index, index + 1)
                    if index == 8 || index == 13 || index == 18 || index == 23 {
                        if character != "-" { return false }
                    } else if hexValue(character) < 0 {
                        return false
                    }
                    index = index + 1
                }
                if canonical.substring(14, 15) != "7" { return false }
                return "89ab".indexOf(canonical.substring(19, 20)) >= 0
            }
        }
    }
}
