//! Launch-option parsing shared by the direct CLI (`solvik -Pk=v prog.sol`)
//! and the packaged runtime image (`./prog -Pk=v [args...]`).
//!
//! `-Pkey=value` tokens initialize the program property store observed by
//! `System.getProperty` and friends. They are launch-time options: they are
//! never embedded in package payloads, never appear in `Main.run(args)`, and
//! never alter the host environment.

/// One `(key, value)` property assignment.
pub type PropertyAssignment = (String, String);

/// Parsed launch options: property assignments plus an optional cycle
/// collection byte budget.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct LaunchOptions {
    pub properties: Vec<PropertyAssignment>,
    pub heap_budget_bytes: Option<usize>,
}

/// Parse one `-X<size>MB` cycle-collection budget token.
///
/// Accepts `-X<size>`, `-X<size>M`, and `-X<size>MB` (unit case-insensitive),
/// where `<size>` is a positive integer number of megabytes.
pub fn parse_gc_budget_token(token: &str) -> Result<usize, String> {
    let rest = token
        .strip_prefix("-X")
        .ok_or_else(|| format!("malformed launch option: {token}"))?;
    let digits: String = rest.chars().take_while(|c| c.is_ascii_digit()).collect();
    if digits.is_empty() {
        return Err(format!(
            "launch option {token} is missing a size (expected -X<size>MB)"
        ));
    }
    let suffix = &rest[digits.len()..];
    if !suffix.is_empty() && !suffix.eq_ignore_ascii_case("m") && !suffix.eq_ignore_ascii_case("mb")
    {
        return Err(format!(
            "launch option {token} has an invalid unit '{suffix}' (expected MB)"
        ));
    }
    let megabytes: u64 = digits
        .parse()
        .map_err(|_| format!("launch option {token} has an invalid size"))?;
    if megabytes == 0 {
        return Err("launch option -X budget must be at least 1 MB".into());
    }
    megabytes
        .checked_mul(1024 * 1024)
        .and_then(|bytes| usize::try_from(bytes).ok())
        .ok_or_else(|| format!("launch option {token} is too large"))
}

/// Parse one `-Pkey=value` token (the token must start with `-P`).
///
/// Splits at the *first* `=` so values may contain further `=` characters.
/// The key must be non-empty; the value may be empty (`-Pkey=`). Returns a
/// user-facing message on malformed input.
pub fn parse_property_token(token: &str) -> Result<PropertyAssignment, String> {
    let rest = token
        .strip_prefix("-P")
        .ok_or_else(|| format!("malformed launch option: {token}"))?;
    let Some(eq) = rest.find('=') else {
        return Err(format!(
            "launch option {token} is missing its '=' separator (expected -Pkey=value)"
        ));
    };
    let key = &rest[..eq];
    if key.is_empty() {
        return Err(format!(
            "launch option {token} has an empty property key (expected -Pkey=value)"
        ));
    }
    Ok((key.to_string(), rest[eq + 1..].to_string()))
}

/// Consume leading `-Pkey=value` options from a packaged-runtime argv.
///
/// Rules:
/// - leading `-Pkey=value` tokens become ordered property assignments;
/// - leading `-X<size>MB` tokens set the cycle-collection budget (last wins);
/// - `--` ends option consumption explicitly (and is not passed through), so
///   a program whose first argument itself begins with `-P` or `-X` can be
///   launched;
/// - after the first ordinary argument, every remaining value—including
///   strings beginning with `-P` or `-X`—is a program argument.
///
/// Returns the parsed options and the program arguments.
pub fn split_launch_options(tokens: &[String]) -> Result<(LaunchOptions, Vec<String>), String> {
    let mut options = LaunchOptions::default();
    let mut args: Vec<String> = Vec::new();
    let mut i = 0;
    while i < tokens.len() {
        let token = &tokens[i];
        if token == "--" {
            i += 1;
            break;
        }
        if let Some(stripped) = token.strip_prefix("-P") {
            if stripped.is_empty() {
                return Err("launch option -P has no property key (expected -Pkey=value)".into());
            }
            options.properties.push(parse_property_token(token)?);
            i += 1;
            continue;
        }
        if let Some(stripped) = token.strip_prefix("-X") {
            if stripped.is_empty() {
                return Err("launch option -X has no size (expected -X<size>MB)".into());
            }
            options.heap_budget_bytes = Some(parse_gc_budget_token(token)?);
            i += 1;
            continue;
        }
        break;
    }
    args.extend(tokens[i..].iter().cloned());
    Ok((options, args))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn toks(items: &[&str]) -> Vec<String> {
        items.iter().map(|s| s.to_string()).collect()
    }

    #[test]
    fn property_token_splits_at_first_equals() {
        assert_eq!(
            parse_property_token("-Pmode=test").unwrap(),
            ("mode".to_string(), "test".to_string())
        );
        // Values may contain further '=' characters.
        assert_eq!(
            parse_property_token("-Purl=http://x?a=b=c").unwrap(),
            ("url".to_string(), "http://x?a=b=c".to_string())
        );
        // Empty values are allowed.
        assert_eq!(
            parse_property_token("-Pempty=").unwrap(),
            ("empty".to_string(), String::new())
        );
    }

    #[test]
    fn property_token_rejects_malformed_input() {
        assert!(parse_property_token("-Pnoequals").is_err());
        assert!(parse_property_token("-P=value").is_err());
        assert!(parse_property_token("-P").is_err());
        assert!(parse_property_token("Pkey=value").is_err());
    }

    #[test]
    fn d_prefix_is_not_a_property_prefix() {
        // The old `-D` spelling is not accepted or translated as a property.
        assert!(parse_property_token("-Dmode=test").is_err());
        let (options, args) = split_launch_options(&toks(&["-Dmode=test", "one"])).unwrap();
        assert!(options.properties.is_empty());
        assert_eq!(args, vec!["-Dmode=test".to_string(), "one".to_string()]);
    }

    #[test]
    fn gc_budget_token_accepts_units() {
        assert_eq!(parse_gc_budget_token("-X64").unwrap(), 64 * 1024 * 1024);
        assert_eq!(parse_gc_budget_token("-X64M").unwrap(), 64 * 1024 * 1024);
        assert_eq!(parse_gc_budget_token("-X64mb").unwrap(), 64 * 1024 * 1024);
        assert_eq!(parse_gc_budget_token("-X1").unwrap(), 1024 * 1024);
    }

    #[test]
    fn gc_budget_token_rejects_malformed_values() {
        assert!(parse_gc_budget_token("-X").is_err());
        assert!(parse_gc_budget_token("-X0").is_err());
        assert!(parse_gc_budget_token("-X64GB").is_err());
        assert!(parse_gc_budget_token("-Xabc").is_err());
        assert!(parse_gc_budget_token("X64").is_err());
    }

    #[test]
    fn split_consumes_budget_last_wins() {
        let (options, args) =
            split_launch_options(&toks(&["-X8M", "-Pa=1", "-X16", "run", "-X32"])).unwrap();
        assert_eq!(options.heap_budget_bytes, Some(16 * 1024 * 1024));
        assert_eq!(options.properties, vec![("a".to_string(), "1".to_string())]);
        assert_eq!(args, vec!["run".to_string(), "-X32".to_string()]);
    }

    #[test]
    fn split_consumes_leading_properties_only() {
        let (options, args) =
            split_launch_options(&toks(&["-Pa=1", "-Pb=2", "first", "-Plate=3"])).unwrap();
        assert_eq!(
            options.properties,
            vec![
                ("a".to_string(), "1".to_string()),
                ("b".to_string(), "2".to_string())
            ]
        );
        // A -P-looking value after the first ordinary argument stays a
        // program argument.
        assert_eq!(args, vec!["first".to_string(), "-Plate=3".to_string()]);
    }

    #[test]
    fn split_supports_double_dash_terminator() {
        let (options, args) = split_launch_options(&toks(&["-Pa=1", "--", "-Pb=2", "x"])).unwrap();
        assert_eq!(options.properties, vec![("a".to_string(), "1".to_string())]);
        assert_eq!(args, vec!["-Pb=2".to_string(), "x".to_string()]);
    }

    #[test]
    fn split_accepts_only_arguments() {
        let (options, args) = split_launch_options(&toks(&["one", "two"])).unwrap();
        assert!(options.properties.is_empty());
        assert_eq!(args, vec!["one".to_string(), "two".to_string()]);
    }

    #[test]
    fn split_rejects_malformed_leading_property() {
        assert!(split_launch_options(&toks(&["-Pbad", "one"])).is_err());
        assert!(split_launch_options(&toks(&["-P=bad"])).is_err());
    }
}
