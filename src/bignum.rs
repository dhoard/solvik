//! Arbitrary-precision numeric support.
//!
//! `BigInteger` is backed directly by `num_bigint::BigInt`. `Dec` is
//! Solvik's `BigDecimal`: an arbitrary-precision unscaled integer plus a
//! base-10 scale, with a deterministic built-in decimal context for
//! division (34 significant digits, rounding half-even — equivalent to
//! IEEE DECIMAL128 precision).

use num_integer::Integer;
use num_traits::{One, Signed, ToPrimitive, Zero};
use std::cmp::Ordering;
use std::hash::{Hash, Hasher};

pub use num_bigint::BigInt;

/// Built-in decimal context precision (significant digits).
pub const DECIMAL_PRECISION: u32 = 34;

/// Arbitrary-precision decimal: value = `unscaled * 10^-scale`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Dec {
    pub unscaled: BigInt,
    pub scale: i32,
}

impl std::fmt::Display for Dec {
    /// Canonical text: no trailing fractional zeros, no exponent.
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let s = self.decimal_text();
        f.write_str(&s)
    }
}

impl Dec {
    pub fn new(unscaled: BigInt, scale: i32) -> Self {
        Dec { unscaled, scale }
    }

    pub fn zero() -> Self {
        Dec {
            unscaled: BigInt::zero(),
            scale: 0,
        }
    }

    /// Parse exact decimal text: optional sign, digits, optional fraction,
    /// optional decimal exponent (`1.5`, `-3`, `2.25e-3`, `1E+2`).
    pub fn parse(text: &str) -> Result<Self, String> {
        let t = text.trim();
        if t.is_empty() {
            return Err("empty decimal literal".into());
        }
        let (sign, rest) = match t.as_bytes()[0] {
            b'+' => (BigInt::one(), &t[1..]),
            b'-' => (-BigInt::one(), &t[1..]),
            _ => (BigInt::one(), t),
        };
        if rest.is_empty() {
            return Err("missing digits in decimal literal".into());
        }
        // Split exponent.
        let (mantissa_text, exp) = match rest.find(|c| ['e', 'E'].contains(&c)) {
            Some(i) => {
                let exp_text = &rest[i + 1..];
                if exp_text.is_empty() {
                    return Err("missing exponent digits".into());
                }
                let (esign, edigits) = match exp_text.as_bytes()[0] {
                    b'+' => (1i64, &exp_text[1..]),
                    b'-' => (-1i64, &exp_text[1..]),
                    _ => (1i64, exp_text),
                };
                if edigits.is_empty() || !edigits.bytes().all(|b| b.is_ascii_digit()) {
                    return Err("invalid exponent in decimal literal".into());
                }
                let magnitude: i64 = edigits
                    .parse()
                    .map_err(|_| "exponent out of range".to_string())?;
                (rest[..i].to_string(), esign * magnitude)
            }
            None => (rest.to_string(), 0),
        };
        // Split fraction.
        let (int_part, frac_part) = match mantissa_text.find('.') {
            Some(i) => (&mantissa_text[..i], &mantissa_text[i + 1..]),
            None => (mantissa_text.as_str(), ""),
        };
        if int_part.is_empty() && frac_part.is_empty() {
            return Err("missing digits in decimal literal".into());
        }
        if !int_part.bytes().all(|b| b.is_ascii_digit())
            || !frac_part.bytes().all(|b| b.is_ascii_digit())
        {
            return Err("invalid digits in decimal literal".into());
        }
        let digits = format!("{int_part}{frac_part}");
        let unscaled = sign
            * BigInt::parse_bytes(digits.as_bytes(), 10)
                .ok_or_else(|| "decimal literal out of range".to_string())?;
        let scale = frac_part.len() as i32 - exp as i32;
        // Absorb a negative scale into the unscaled value so the stored
        // form is canonical (scale >= 0).
        if scale < 0 {
            let factor = BigInt::from(10i64).pow((-scale) as u32);
            Ok(Dec {
                unscaled: unscaled * factor,
                scale: 0,
            })
        } else {
            Ok(Dec { unscaled, scale })
        }
    }

    pub fn from_i64(v: i64) -> Self {
        Dec {
            unscaled: BigInt::from(v),
            scale: 0,
        }
    }

    pub fn from_bigint(v: BigInt) -> Self {
        Dec {
            unscaled: v,
            scale: 0,
        }
    }

    /// Exact binary-to-decimal conversion (no rounding): the precise value
    /// of the floating-point number is represented. A binary64 value is
    /// `m * 2^e`; for `e < 0` this becomes `m * 5^-e / 10^-e`.
    pub fn from_f64(f: f64) -> Result<Self, String> {
        if !f.is_finite() {
            return Err("cannot convert non-finite float to BigDecimal".into());
        }
        let bits = f.to_bits();
        let negative = bits >> 63 == 1;
        let exp_field = ((bits >> 52) & 0x7ff) as i64;
        let frac = bits & 0xf_ffff_ffff_ffff;
        let (m, e) = if exp_field == 0 {
            if frac == 0 {
                return Ok(Dec::zero());
            }
            // Subnormal: frac * 2^(1 - 1023 - 52).
            (BigInt::from(frac), 1 - 1023 - 52)
        } else {
            (BigInt::from((1u64 << 52) | frac), exp_field - 1023 - 52)
        };
        let (unscaled_mag, scale) = if e >= 0 {
            (m << e, 0)
        } else {
            (m * BigInt::from(5i64).pow((-e) as u32), -e as i32)
        };
        let unscaled = if negative && !unscaled_mag.is_zero() {
            -unscaled_mag
        } else {
            unscaled_mag
        };
        Ok(Dec { unscaled, scale })
    }

    /// Exact binary-to-decimal conversion for binary32.
    pub fn from_f32(f: f32) -> Result<Self, String> {
        if !f.is_finite() {
            return Err("cannot convert non-finite float to BigDecimal".into());
        }
        let bits = f.to_bits();
        let negative = bits >> 31 == 1;
        let exp_field = ((bits >> 23) & 0xff) as i64;
        let frac = bits & 0x7_fffff;
        let (m, e) = if exp_field == 0 {
            if frac == 0 {
                return Ok(Dec::zero());
            }
            (BigInt::from(frac), 1 - 127 - 23)
        } else {
            (BigInt::from((1u32 << 23) | frac), exp_field - 127 - 23)
        };
        let (unscaled_mag, scale) = if e >= 0 {
            (m << e, 0)
        } else {
            (m * BigInt::from(5i64).pow((-e) as u32), -e as i32)
        };
        let unscaled = if negative && !unscaled_mag.is_zero() {
            -unscaled_mag
        } else {
            unscaled_mag
        };
        Ok(Dec { unscaled, scale })
    }

    fn pow10(exp: i64) -> BigInt {
        debug_assert!(exp >= 0);
        BigInt::from(10i64).pow(exp as u32)
    }

    fn align(a: &Dec, b: &Dec) -> (BigInt, BigInt, i32) {
        let s = a.scale.max(b.scale);
        let av = a.unscaled.clone() * Self::pow10((s - a.scale) as i64);
        let bv = b.unscaled.clone() * Self::pow10((s - b.scale) as i64);
        (av, bv, s)
    }

    pub fn add(&self, other: &Dec) -> Dec {
        let (av, bv, s) = Self::align(self, other);
        Dec {
            unscaled: av + bv,
            scale: s,
        }
    }

    pub fn sub(&self, other: &Dec) -> Dec {
        let (av, bv, s) = Self::align(self, other);
        Dec {
            unscaled: av - bv,
            scale: s,
        }
    }

    pub fn mul(&self, other: &Dec) -> Dec {
        Dec {
            unscaled: &self.unscaled * &other.unscaled,
            scale: self.scale + other.scale,
        }
    }

    /// Division under the built-in decimal context (34 significant digits,
    /// rounding half-even). Deterministic; never unbounded.
    pub fn div(&self, other: &Dec) -> Result<Dec, String> {
        if other.unscaled.is_zero() {
            return Err("division by zero".into());
        }
        if self.unscaled.is_zero() {
            return Ok(Dec::zero());
        }
        let preferred = self.scale - other.scale;
        let div_digits = other.unscaled.abs().to_string().len() as i64;
        let guard = (DECIMAL_PRECISION as i64 + 2 - div_digits).max(0);
        let work_scale = (preferred as i64).max(self.scale as i64 + guard) as i32;
        // numerator / denominator with the working scale applied.
        let shift = work_scale as i64 - self.scale as i64 + other.scale as i64;
        let (num, den) = if shift >= 0 {
            (
                self.unscaled.clone() * Self::pow10(shift),
                other.unscaled.clone(),
            )
        } else {
            (
                self.unscaled.clone(),
                other.unscaled.clone() * Self::pow10(-shift),
            )
        };
        let q = round_half_even_div(&num, &den);
        let (q, dropped) = round_to_precision(q, DECIMAL_PRECISION);
        Ok(Dec {
            unscaled: q,
            scale: work_scale - dropped,
        })
    }

    /// Remainder: `a - (a / b) * b` using the contextual division.
    pub fn rem(&self, other: &Dec) -> Result<Dec, String> {
        let q = self.div(other)?;
        Ok(self.sub(&q.mul(other)))
    }

    pub fn neg(&self) -> Dec {
        Dec {
            unscaled: -self.unscaled.clone(),
            scale: self.scale,
        }
    }

    pub fn abs(&self) -> Dec {
        Dec {
            unscaled: self.unscaled.abs(),
            scale: self.scale,
        }
    }

    pub fn is_zero(&self) -> bool {
        self.unscaled.is_zero()
    }

    /// Total ordering comparison.
    pub fn cmp_dec(&self, other: &Dec) -> Ordering {
        let (av, bv, _) = Self::align(self, other);
        av.cmp(&bv)
    }

    /// Strip trailing zeros (and absorb negative scale) without changing
    /// the numeric value.
    pub fn normalize(&self) -> Dec {
        let mut unscaled = self.unscaled.clone();
        let mut scale = self.scale;
        while scale < 0 {
            unscaled *= 10;
            scale += 1;
        }
        let ten = BigInt::from(10);
        while scale > 0 {
            let (q, r) = unscaled.div_rem(&ten);
            if r != BigInt::zero() {
                break;
            }
            unscaled = q;
            scale -= 1;
        }
        Dec { unscaled, scale }
    }

    /// Truncate toward zero to an integral value.
    pub fn to_bigint_truncating(&self) -> BigInt {
        if self.scale == 0 {
            return self.unscaled.clone();
        }
        let factor = Self::pow10(self.scale as i64);
        self.unscaled.clone() / factor
    }

    /// Convert to f64 (rounding; may lose precision for large values).
    pub fn to_f64(&self) -> f64 {
        let n = self.normalize();
        let mag = n.unscaled.abs().to_f64().unwrap_or(f64::INFINITY);
        let v = mag * 10f64.powi(-n.scale);
        if n.unscaled < BigInt::zero() {
            -v
        } else {
            v
        }
    }

    /// Canonical text: no trailing fractional zeros, no exponent.
    fn decimal_text(&self) -> String {
        let n = self.normalize();
        if n.scale == 0 {
            return n.unscaled.to_string();
        }
        let negative = n.unscaled < BigInt::zero();
        let mag = n.unscaled.abs().to_string();
        let point_at = mag.len().saturating_sub(n.scale as usize);
        let mut out = String::new();
        if negative {
            out.push('-');
        }
        if point_at == 0 {
            out.push_str("0.");
            // Zero-pad the fraction when the scale exceeds the digit count
            // (e.g. 0.000001).
            out.push_str(&"0".repeat(n.scale as usize - mag.len()));
            out.push_str(&mag);
        } else if (point_at as i64) >= mag.len() as i64 {
            out.push_str(&mag);
            out.push_str(&"0".repeat(point_at - mag.len()));
            out.push_str(".0");
        } else {
            out.push_str(&mag[..point_at]);
            out.push('.');
            out.push_str(&mag[point_at..]);
        }
        out
    }

    /// Hash consistent with numeric equality (normalized form).
    pub fn hash_value(&self) -> u64 {
        let n = self.normalize();
        let mut h = std::collections::hash_map::DefaultHasher::new();
        n.unscaled.magnitude().to_bytes_be().hash(&mut h);
        (n.unscaled < BigInt::zero()).hash(&mut h);
        n.scale.hash(&mut h);
        h.finish()
    }
}

/// Integer division rounded half-to-even (ties go to the even quotient).
fn round_half_even_div(num: &BigInt, den: &BigInt) -> BigInt {
    let (q, r) = num.div_rem(den);
    let twice = r.abs() * BigInt::from(2);
    let den_abs = den.abs();
    if twice > den_abs || (twice == den_abs && q.is_odd()) {
        // Round away from zero.
        if num * den >= BigInt::zero() {
            q + BigInt::one()
        } else {
            q - BigInt::one()
        }
    } else {
        q
    }
}

/// Round a significand to `prec` decimal digits, half-to-even. Returns the
/// rounded value and how many digits were dropped.
fn round_to_precision(q: BigInt, prec: u32) -> (BigInt, i32) {
    if q.is_zero() {
        return (BigInt::zero(), 0);
    }
    let digits = q.abs().to_string().len() as u32;
    if digits <= prec {
        return (q, 0);
    }
    let drop = digits - prec;
    let factor = BigInt::from(10i64).pow(drop);
    let (q2, r) = q.div_rem(&factor);
    let twice = r.abs() * BigInt::from(2);
    let q2 = if twice > factor || (twice == factor && q2.is_odd()) {
        if q >= BigInt::zero() {
            q2 + BigInt::one()
        } else {
            q2 - BigInt::one()
        }
    } else {
        q2
    };
    (q2, drop as i32)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn d(s: &str) -> Dec {
        Dec::parse(s).unwrap()
    }

    #[test]
    fn parses_exact_decimal_text() {
        assert_eq!(d("1.25").unscaled, BigInt::from(125));
        assert_eq!(d("1.25").scale, 2);
        assert_eq!(d("-3").unscaled, BigInt::from(-3));
        assert_eq!(d("2.25e-3").unscaled, BigInt::from(225));
        assert_eq!(d("2.25e-3").scale, 5);
        assert_eq!(d("1E+2").unscaled, BigInt::from(100));
        assert!(d("0.000").is_zero());
        for bad in ["", "+", ".e3", "1e", "abc", "1.2.3"] {
            assert!(Dec::parse(bad).is_err(), "accepted {bad:?}");
        }
    }

    #[test]
    fn arithmetic_is_exact() {
        assert_eq!(d("0.1").add(&d("0.2")).to_string(), "0.3");
        assert_eq!(d("1.005").mul(&d("2")).to_string(), "2.01");
        assert_eq!(d("10").sub(&d("0.1")).to_string(), "9.9");
    }

    #[test]
    fn division_is_deterministic_contextual() {
        // 1/3 rounds to 34 significant digits, half-even.
        let q = d("1").div(&d("3")).unwrap();
        let s = q.to_string();
        assert!(s.starts_with("0.33333333333333333333333333333333"), "{s}");
        // Terminating division stays exact.
        assert_eq!(d("1").div(&d("4")).unwrap().to_string(), "0.25");
        // Tie rounds to even: 0.05 / 0.01 = 5 exactly; 2/4 = 0.5.
        assert_eq!(d("2").div(&d("4")).unwrap().to_string(), "0.5");
        assert!(d("1").div(&d("0")).is_err());
    }

    #[test]
    fn equality_and_normalization_are_numeric() {
        assert_eq!(d("1.0").cmp_dec(&d("1.00")), Ordering::Equal);
        assert_eq!(d("1.0").hash_value(), d("1.00").hash_value());
        assert_ne!(d("1.0").hash_value(), d("1.01").hash_value());
        assert_eq!(d("-0.0").to_string(), "0");
        // Fraction zero-padding: scale larger than the digit count.
        assert_eq!(d("0.000001").to_string(), "0.000001");
        assert_eq!(d("-0.0012").to_string(), "-0.0012");
        assert_eq!(d("123.0000004").to_string(), "123.0000004");
    }

    #[test]
    fn truncation_toward_zero() {
        assert_eq!(d("2.9").to_bigint_truncating(), BigInt::from(2));
        assert_eq!(d("-2.9").to_bigint_truncating(), BigInt::from(-2));
        assert_eq!(d("2").rem(&d("1")).unwrap().to_string(), "0");
    }

    #[test]
    fn float_conversion_is_exact_binary_expansion() {
        // 0.1f64 is not 0.1 decimal.
        let v = Dec::from_f64(0.1).unwrap();
        assert_ne!(v.cmp_dec(&d("0.1")), Ordering::Equal);
        assert_eq!(Dec::from_f64(1.5).unwrap().to_string(), "1.5");
        assert_eq!(Dec::from_f32(1.5).unwrap().to_string(), "1.5");
        assert!(Dec::from_f64(f64::NAN).is_err());
        assert!(Dec::from_f64(f64::INFINITY).is_err());
    }
}
