//! Launch-option parsing shared by the direct CLI (`solvik -Dk=v prog.sol`)
//! and the packaged runtime image (`./prog -Dk=v [args...]`).
//!
//! `-Dkey=value` tokens initialize the program property store observed by
//! `System.getProperty` and friends. They are launch-time options: they are
//! never embedded in package payloads, never appear in `Main.run(args)`, and
//! never alter the host environment.

/// One ordered `(key, value)` property assignment.
pub type PropertyAssignment = (String, String);

/// Parse one `-Dkey=value` token (the token must start with `-D`).
///
/// Splits at the *first* `=` so values may contain further `=` characters.
/// The key must be non-empty; the value may be empty (`-Dkey=`). Returns a
/// user-facing message on malformed input.
pub fn parse_property_token(token: &str) -> Result<PropertyAssignment, String> {
    let rest = token
        .strip_prefix("-D")
        .ok_or_else(|| format!("malformed launch option: {token}"))?;
    let Some(eq) = rest.find('=') else {
        return Err(format!(
            "launch option {token} is missing its '=' separator (expected -Dkey=value)"
        ));
    };
    let key = &rest[..eq];
    if key.is_empty() {
        return Err(format!(
            "launch option {token} has an empty property key (expected -Dkey=value)"
        ));
    }
    Ok((key.to_string(), rest[eq + 1..].to_string()))
}

/// Consume leading `-Dkey=value` options from a packaged-runtime argv.
///
/// Rules:
/// - leading `-Dkey=value` tokens become ordered property assignments;
/// - `--` ends option consumption explicitly (and is not passed through), so
///   a program whose first argument itself begins with `-D` can be launched;
/// - after the first ordinary argument, every remaining value—including
///   strings beginning with `-D`—is a program argument.
///
/// Returns `(properties, program_args)`.
pub fn split_launch_options(
    tokens: &[String],
) -> Result<(Vec<PropertyAssignment>, Vec<String>), String> {
    let mut properties: Vec<PropertyAssignment> = Vec::new();
    let mut args: Vec<String> = Vec::new();
    let mut i = 0;
    while i < tokens.len() {
        let token = &tokens[i];
        if token == "--" {
            i += 1;
            break;
        }
        if let Some(stripped) = token.strip_prefix("-D") {
            if stripped.is_empty() {
                return Err("launch option -D has no property key (expected -Dkey=value)".into());
            }
            properties.push(parse_property_token(token)?);
            i += 1;
            continue;
        }
        break;
    }
    args.extend(tokens[i..].iter().cloned());
    Ok((properties, args))
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
            parse_property_token("-Dmode=test").unwrap(),
            ("mode".to_string(), "test".to_string())
        );
        // Values may contain further '=' characters.
        assert_eq!(
            parse_property_token("-Durl=http://x?a=b=c").unwrap(),
            ("url".to_string(), "http://x?a=b=c".to_string())
        );
        // Empty values are allowed.
        assert_eq!(
            parse_property_token("-Dempty=").unwrap(),
            ("empty".to_string(), String::new())
        );
    }

    #[test]
    fn property_token_rejects_malformed_input() {
        assert!(parse_property_token("-Dnoequals").is_err());
        assert!(parse_property_token("-D=value").is_err());
        assert!(parse_property_token("-D").is_err());
        assert!(parse_property_token("Dkey=value").is_err());
    }

    #[test]
    fn split_consumes_leading_properties_only() {
        let (props, args) =
            split_launch_options(&toks(&["-Da=1", "-Db=2", "first", "-Dlate=3"])).unwrap();
        assert_eq!(
            props,
            vec![
                ("a".to_string(), "1".to_string()),
                ("b".to_string(), "2".to_string())
            ]
        );
        // A -D-looking value after the first ordinary argument stays a
        // program argument.
        assert_eq!(args, vec!["first".to_string(), "-Dlate=3".to_string()]);
    }

    #[test]
    fn split_supports_double_dash_terminator() {
        let (props, args) = split_launch_options(&toks(&["-Da=1", "--", "-Db=2", "x"])).unwrap();
        assert_eq!(props, vec![("a".to_string(), "1".to_string())]);
        assert_eq!(args, vec!["-Db=2".to_string(), "x".to_string()]);
    }

    #[test]
    fn split_accepts_only_arguments() {
        let (props, args) = split_launch_options(&toks(&["one", "two"])).unwrap();
        assert!(props.is_empty());
        assert_eq!(args, vec!["one".to_string(), "two".to_string()]);
    }

    #[test]
    fn split_rejects_malformed_leading_property() {
        assert!(split_launch_options(&toks(&["-Dbad", "one"])).is_err());
        assert!(split_launch_options(&toks(&["-D=bad"])).is_err());
    }
}
