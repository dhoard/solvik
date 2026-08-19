//! Go-runtime compatibility helpers: float formatting, PCG RNG, simple case
//! mapping, and Go-compatible integer parsing.

/// Formats an f64 exactly like Go's `strconv.FormatFloat(f, 'g', -1, 64)`
/// (which is what `%g`/`%v` use for float64 in fmt).
///
/// Shortest round-trip digits are obtained via Rust's LowerExp formatting.
/// Go uses %e when exp < -4 || exp >= 6 (for shortest representation).
pub fn go_format_float(v: f64) -> String {
    if v.is_nan() {
        return "NaN".to_string();
    }
    if v.is_infinite() {
        return if v > 0.0 { "+Inf" } else { "-Inf" }.to_string();
    }
    if v == 0.0 {
        return if v.is_sign_negative() { "-0" } else { "0" }.to_string();
    }

    // Shortest scientific form, e.g. "1.2345e6", "-3.14e-2".
    let sci = format!("{:e}", v);
    let (mantissa, exp_str) = sci.split_once('e').expect("LowerExp always has 'e'");
    let exp10: i32 = exp_str.parse().expect("valid exponent");

    let neg = mantissa.starts_with('-');
    let digits: String = mantissa.chars().filter(|c| c.is_ascii_digit()).collect();
    let digits = digits.trim_end_matches('0');
    let digits = if digits.is_empty() { "0" } else { digits };
    let nd = digits.len() as i32;
    // Decimal point position: for "d.ddd e EXP", dp = exp10 + 1.
    let dp = exp10 + 1;
    let exp = dp - 1;

    let mut out = String::new();
    if neg {
        out.push('-');
    }

    if exp < -4 || exp >= 6 {
        // %e form: d.ddde±XX (minimum two exponent digits, no trailing zeros).
        out.push_str(&digits[0..1]);
        if nd > 1 {
            out.push('.');
            out.push_str(&digits[1..]);
        }
        out.push('e');
        if exp < 0 {
            out.push('-');
        } else {
            out.push('+');
        }
        let abs_exp = exp.unsigned_abs();
        if abs_exp < 10 {
            out.push('0');
        }
        out.push_str(&abs_exp.to_string());
    } else {
        // %f form.
        if dp <= 0 {
            out.push_str("0.");
            for _ in 0..(-dp) {
                out.push('0');
            }
            out.push_str(digits);
        } else if dp >= nd {
            out.push_str(digits);
            for _ in 0..(dp - nd) {
                out.push('0');
            }
        } else {
            out.push_str(&digits[0..dp as usize]);
            out.push('.');
            out.push_str(&digits[dp as usize..]);
        }
    }
    out
}

/// Go-compatible PCG (math/rand/v2 NewPCG) generator.
pub struct Pcg {
    hi: u64,
    lo: u64,
}

impl Pcg {
    pub fn new(seed1: u64, seed2: u64) -> Pcg {
        Pcg { hi: seed1, lo: seed2 }
    }

    fn next(&mut self) -> (u64, u64) {
        const MUL_HI: u64 = 2549297995355413924;
        const MUL_LO: u64 = 4865540595714422341;
        const INC_HI: u64 = 6364136223846793005;
        const INC_LO: u64 = 1442695040888963407;

        // state = state * mul + inc
        let wide = (self.lo as u128) * (MUL_LO as u128);
        let mut hi = (wide >> 64) as u64;
        let mut lo = wide as u64;
        hi = hi
            .wrapping_add(
                self.hi
                    .wrapping_mul(MUL_LO)
                    .wrapping_add(self.lo.wrapping_mul(MUL_HI)),
            );
        // lo += INC_LO; hi += INC_HI + carry
        let (lo2, c) = lo.overflowing_add(INC_LO);
        lo = lo2;
        hi = hi.wrapping_add(INC_HI).wrapping_add(c as u64);
        self.lo = lo;
        self.hi = hi;
        (hi, lo)
    }

    pub fn uint64(&mut self) -> u64 {
        let (mut hi, lo) = self.next();
        const CHEAP_MUL: u64 = 0xda942042e4dd58b5;
        hi ^= hi >> 32;
        hi = hi.wrapping_mul(CHEAP_MUL);
        hi ^= hi >> 48;
        hi = hi.wrapping_mul(lo | 1);
        hi
    }

    /// Rand.Int64() == int64(src.Uint64() &^ (1 << 63))
    pub fn int64(&mut self) -> i64 {
        (self.uint64() & !(1u64 << 63)) as i64
    }

    /// Rand.Uint64N via Lemire's method (math/rand/v2 Rand.uint64n on 64-bit).
    pub fn uint64n(&mut self, n: u64) -> u64 {
        if n == 0 {
            return 0;
        }
        if n & (n - 1) == 0 {
            return self.uint64() & (n - 1);
        }
        let mut wide = (self.uint64() as u128) * (n as u128);
        let mut hi = (wide >> 64) as u64;
        let mut lo = wide as u64;
        if lo < n {
            let thresh = n.wrapping_neg() % n;
            while lo < thresh {
                wide = (self.uint64() as u128) * (n as u128);
                hi = (wide >> 64) as u64;
                lo = wide as u64;
            }
        }
        hi
    }

    /// Rand.Int64N
    pub fn int64n(&mut self, n: i64) -> i64 {
        self.uint64n(n as u64) as i64
    }

    /// Rand.Float64()
    pub fn float64(&mut self) -> f64 {
        (self.uint64() << 11 >> 11) as f64 / (1u64 << 53) as f64
    }
}

/// Go-compatible ParseInt with base 0 (handles 0x/0X, 0o/0O, 0b/0B, leading-0
/// octal, sign, and — irrelevant post-lex — underscores). Returns None on error.
pub fn go_parse_int_base0(s: &str) -> Option<i64> {
    if s.is_empty() {
        return None;
    }
    let (neg, rest) = match s.as_bytes()[0] {
        b'+' => (false, &s[1..]),
        b'-' => (true, &s[1..]),
        _ => (false, &s[..]),
    };
    if rest.is_empty() {
        return None;
    }
    let (base, digits) = if rest.len() >= 2 && rest.as_bytes()[0] == b'0' {
        match rest.as_bytes()[1] {
            b'x' | b'X' => (16u32, &rest[2..]),
            b'o' | b'O' => (8u32, &rest[2..]),
            b'b' | b'B' => (2u32, &rest[2..]),
            _ => (8u32, &rest[1..]),
        }
    } else {
        (10u32, rest)
    };
    if digits.is_empty() {
        return None;
    }
    let mag = u64::from_str_radix(digits, base).ok()?;
    if neg {
        if mag > (1u64 << 63) {
            return None;
        }
        Some((mag as i64).wrapping_neg())
    } else {
        if mag > i64::MAX as u64 {
            return None;
        }
        Some(mag as i64)
    }
}

/// Simple (1:1) Unicode uppercase mapping, matching Go's unicode.ToUpper for
/// characters whose mapping is single-character. Multi-character full
/// mappings (e.g. ß → SS) keep the original character, matching Go's simple
/// table behavior of leaving such characters unchanged.
pub fn go_to_upper(s: &str) -> String {
    s.chars()
        .map(|c| {
            let mut it = c.to_uppercase();
            let first = it.next().unwrap_or(c);
            if it.next().is_none() {
                first
            } else {
                c
            }
        })
        .collect()
}

/// Simple (1:1) Unicode lowercase mapping, matching Go's unicode.ToLower.
pub fn go_to_lower(s: &str) -> String {
    s.chars()
        .map(|c| {
            let mut it = c.to_lowercase();
            let first = it.next().unwrap_or(c);
            if it.next().is_none() {
                first
            } else {
                c
            }
        })
        .collect()
}

/// Go-compatible strconv.ParseFloat for the subset of syntax the language
/// accepts (decimal/scientific floats). Returns None on error.
pub fn go_parse_float(s: &str) -> Option<f64> {
    if s.is_empty() {
        return None;
    }
    // Rust accepts "inf"/"nan" spellings that Go also accepts ("Inf",
    // "Infinity", "NaN" case-insensitive); close enough for parity.
    let lower = s.to_ascii_lowercase();
    if lower == "inf" || lower == "+inf" || lower == "infinity" || lower == "+infinity" {
        return Some(f64::INFINITY);
    }
    if lower == "-inf" || lower == "-infinity" {
        return Some(f64::NEG_INFINITY);
    }
    if lower == "nan" || lower == "+nan" || lower == "-nan" {
        return Some(f64::NAN);
    }
    // Reject hex floats like Go's ParseFloat? Go accepts "0x1p3"; Rust's
    // parse() does not, so this diverges only for hex float literals.
    s.parse::<f64>().ok()
}

/// Formats an I/O error message the way Go's `os` package does (lowercase,
/// errno text without the "os error N" suffix), so the Rust build produces
/// identical CLI error strings for file access failures.
pub fn go_os_error_message(err: &std::io::Error) -> String {
    use std::io::ErrorKind::*;
    match err.kind() {
        NotFound => "no such file or directory".to_string(),
        PermissionDenied => "permission denied".to_string(),
        _ => {
            let s = err.to_string();
            match s.find(" (os error") {
                Some(i) => s[..i].to_lowercase(),
                None => s.to_lowercase(),
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn float_formatting_matches_go() {
        // Expected values produced by Go's fmt.Sprintf("%g", v).
        let cases: Vec<(f64, &str)> = vec![
            (3.14, "3.14"),
            (1000000.0, "1e+06"),
            (100000.0, "100000"),
            (1234567.0, "1.234567e+06"),
            (0.0001, "0.0001"),
            (0.00001, "1e-05"),
            (1e21, "1e+21"),
            (1e20, "1e+20"),
            (2.5, "2.5"),
            (-0.5, "-0.5"),
            (3.0, "3"),
            (100.0, "100"),
            (1.5, "1.5"),
            (0.1, "0.1"),
            (0.30000000000000004, "0.30000000000000004"),
            (5.0 / 3.0, "1.6666666666666667"),
            (std::f64::consts::PI, "3.141592653589793"),
            (std::f64::consts::E, "2.718281828459045"),
            (123456789012345.0, "1.23456789012345e+14"),
            (1234567890123456.0, "1.234567890123456e+15"),
            (2e-3, "0.002"),
            (-1.25e-7, "-1.25e-07"),
            (0.0, "0"),
            (42.0, "42"),
            (1000001.0, "1.000001e+06"),
            (120000.0, "120000"),
            (999999.0, "999999"),
            (9999999.0, "9.999999e+06"),
            (9.999999999999999e-5, "9.999999999999999e-05"),
        ];
        for (v, want) in cases {
            assert_eq!(go_format_float(v), want, "value {}", v);
        }
        assert_eq!(go_format_float(f64::INFINITY), "+Inf");
        assert_eq!(go_format_float(f64::NEG_INFINITY), "-Inf");
        assert_eq!(go_format_float(f64::NAN), "NaN");
        assert_eq!(go_format_float(-0.0), "-0");
    }

    #[test]
    fn pcg_matches_go_seed_stream() {
        // Verified against Go: rand.New(rand.NewPCG(99, 99^0xdeadbeefcafebabe))
        // produces 1+Int64N(1000)=14, Float64()=0.44315330398939645, 1+Int64N(6)=4.
        let mut p = Pcg::new(99, 99 ^ 0xdeadbeefcafebabe);
        assert_eq!(1 + p.int64n(1000), 14);
        assert_eq!(p.float64(), 0.44315330398939645);
        assert_eq!(1 + p.int64n(6), 4);
        let mut q = Pcg::new(1, 1 ^ 0xdeadbeefcafebabe);
        assert_eq!(1 + q.int64n(1000000), 298879);
        assert_eq!(q.uint64(), 15473376590097359205);
        assert_eq!(2.0 + (3.0 - 2.0) * q.float64(), 2.801720747616985);
    }

    #[test]
    fn os_error_message() {
        let not_found = std::io::Error::from(std::io::ErrorKind::NotFound);
        assert_eq!(go_os_error_message(&not_found), "no such file or directory");
        let denied = std::io::Error::from(std::io::ErrorKind::PermissionDenied);
        assert_eq!(go_os_error_message(&denied), "permission denied");
    }

    #[test]
    fn parse_int_base0() {
        assert_eq!(go_parse_int_base0("42"), Some(42));
        assert_eq!(go_parse_int_base0("0x1F"), Some(31));
        assert_eq!(go_parse_int_base0("0X1f"), Some(31));
        assert_eq!(go_parse_int_base0("0b101"), Some(5));
        assert_eq!(go_parse_int_base0("0o755"), Some(493));
        assert_eq!(go_parse_int_base0("0755"), Some(493));
        assert_eq!(go_parse_int_base0("-16"), Some(-16));
        assert_eq!(go_parse_int_base0("-0x10"), Some(-16));
        assert_eq!(go_parse_int_base0("08"), None);
        assert_eq!(go_parse_int_base0(""), None);
    }
}
