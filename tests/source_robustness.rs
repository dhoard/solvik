use solvik_rs::{
    diagnostic::Diagnostics,
    formatter::format_source,
    lexer::{Lexer, TokenKind},
};

#[test]
fn formatting_preserves_conformance_tokens_and_is_idempotent() {
    let root = std::path::Path::new(env!("CARGO_MANIFEST_DIR"));
    for entry in std::fs::read_dir(root.join("test/cases")).unwrap() {
        let path = entry.unwrap().path().join("main.sol");
        let source = std::fs::read_to_string(&path).unwrap();
        let mut diags = Diagnostics::default();
        let original = Lexer::new(0, &source).tokenize(&mut diags);
        if diags.has_errors() {
            continue;
        }
        let mut parser = solvik_rs::parser::Parser::new(original.clone());
        if parser.parse_program().is_none() || parser.diags.has_errors() {
            continue;
        }
        let formatted = format_source(&source);
        let tokens = Lexer::new(0, &formatted).tokenize(&mut diags);
        assert!(!diags.has_errors(), "{}", path.display());
        let significant = |tokens: Vec<solvik_rs::lexer::Token>| {
            tokens
                .into_iter()
                .filter(|t| t.kind != TokenKind::Newline)
                .map(|t| (t.kind, t.text))
                .collect::<Vec<_>>()
        };
        assert_eq!(
            significant(original),
            significant(tokens),
            "{}",
            path.display()
        );
        assert_eq!(formatted, format_source(&formatted), "{}", path.display());
        let mut parser =
            solvik_rs::parser::Parser::new(Lexer::new(0, &formatted).tokenize(&mut diags));
        assert!(
            parser.parse_program().is_some() && !parser.diags.has_errors(),
            "{}",
            path.display()
        );
    }
}

#[test]
fn damaged_source_returns_diagnostics_without_panicking() {
    let source = "module robustness\ninterface Named { name(): String }\nclass Main { public static run(args: String...): Long {\nlet values: List<Long> = [1, 2, 3]\nfor value in values { if value > 1 { continue } }\nreturn 0\n} }\n";
    assert!(solvik_rs::compile("test.sol", source).is_ok());
    for index in 0..source.len() {
        for replacement in ["", "}", "\"", "é", "/*", "<"] {
            let mutated = format!(
                "{}{}{}",
                &source[..index],
                replacement,
                &source[index + 1..]
            );
            let _ = solvik_rs::compile("test.sol", &mutated);
        }
    }
}

#[test]
fn braced_unicode_escapes_execute_in_both_pipelines() {
    let source = r#"module unicode
class Main {
    public static run(args: String...): Long {
        let text: String = "\u{41}\u{1f600}"
        if text == "A😀" && text.charAt(1) == '\u{1f600}' { return 0 }
        return 1
    }
}
"#;
    for optimize in [false, true] {
        let module = solvik_rs::compile_with_optimization("test.sol", source, optimize).unwrap();
        assert_eq!(solvik_rs::vm::Vm::run_main(module, vec![]).unwrap(), 0);
    }
}
