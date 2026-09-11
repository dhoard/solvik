//! Solvik compiler and bytecode VM entry point.

use std::path::{Path, PathBuf};
use std::process::exit;

use solvik_rs::{package, vm, CompileError};

const VERSION_LINE: &str = "solvik 0.1.0";

const USAGE: &str = "\
usage: solvik <file.sol> [args...]
       solvik --check <file.sol>
       solvik --format <file.sol>
       solvik --package <file.sol> [-o <output>]
       solvik --version";

#[derive(Debug)]
enum Command {
    Version,
    Run {
        file: String,
        program_args: Vec<String>,
    },
    Check {
        file: String,
    },
    Format {
        file: String,
    },
    Package {
        file: String,
        output: Option<String>,
    },
}

/// Parse CLI arguments into an explicit command representation.
///
/// Before the source file, options are recognized; after it, every value is
/// a program argument (run mode only). `--package` never treats trailing
/// values as runtime arguments: those are supplied later to the generated
/// executable.
fn parse_cli(args: &[String]) -> Result<Command, String> {
    let mut mode: Option<&str> = None;
    let mut output: Option<String> = None;
    let mut file: Option<String> = None;
    let mut program_args: Vec<String> = Vec::new();

    let mut i = 0;
    while i < args.len() {
        let arg = args[i].as_str();
        // Options are recognized before the source file; in --package mode
        // `-o` is also accepted after it (trailing values are otherwise
        // rejected for --package, never treated as program arguments).
        if file.is_none() || (mode == Some("--package") && arg == "-o") {
            match arg {
                "--version" | "-version" => return Ok(Command::Version),
                "--check" | "--format" | "--package" => {
                    if mode.is_some() {
                        return Err(
                            "only one of --check, --format, or --package may be given".into()
                        );
                    }
                    mode = Some(arg);
                    i += 1;
                    continue;
                }
                "-o" => {
                    if output.is_some() {
                        return Err("-o given more than once".into());
                    }
                    i += 1;
                    let value = args
                        .get(i)
                        .ok_or_else(|| "-o requires a value".to_string())?;
                    output = Some(value.clone());
                    i += 1;
                    continue;
                }
                s if s.starts_with('-') && s.len() > 1 => {
                    return Err(format!("unknown option: {s}\n{USAGE}"));
                }
                _ => {}
            }
        }
        if file.is_none() {
            file = Some(args[i].clone());
        } else {
            program_args.push(args[i].clone());
        }
        i += 1;
    }

    let file = file.ok_or_else(|| format!("a source file is required\n{USAGE}"))?;
    if output.is_some() && mode != Some("--package") {
        return Err("-o is only valid with --package".into());
    }
    match mode {
        None => Ok(Command::Run { file, program_args }),
        Some("--check") => {
            if !program_args.is_empty() {
                return Err("--check accepts exactly one filename".into());
            }
            Ok(Command::Check { file })
        }
        Some("--format") => {
            if !program_args.is_empty() {
                return Err("--format accepts exactly one filename".into());
            }
            Ok(Command::Format { file })
        }
        Some("--package") => {
            if !program_args.is_empty() {
                return Err(
                    "--package accepts exactly one filename; arguments belong to the \
                     packaged program and are passed to the generated executable"
                        .into(),
                );
            }
            Ok(Command::Package { file, output })
        }
        _ => unreachable!("mode validated above"),
    }
}

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let command = match parse_cli(&args) {
        Ok(c) => c,
        Err(message) => {
            eprintln!("error: {message}");
            exit(3);
        }
    };

    match command {
        Command::Version => println!("{VERSION_LINE}"),
        Command::Format { file } => {
            let text = read_source(&file);
            print!("{}", solvik_rs::formatter::format_source(&text));
        }
        Command::Check { file } => {
            let text = read_source(&file);
            match solvik_rs::check_source(&file, &text) {
                Ok(warnings) => print_warnings(&warnings),
                Err(e) => report_compile_error(&e),
            }
        }
        Command::Run { file, program_args } => {
            let text = read_source(&file);
            let compiled = match solvik_rs::compile_report(&file, &text, optimization_enabled()) {
                Ok(c) => c,
                Err(e) => report_compile_error(&e),
            };
            print_warnings(&compiled.warnings);
            run_module(compiled.module, program_args);
        }
        Command::Package { file, output } => {
            let text = read_source(&file);
            // One shared pipeline for normal execution and packaging: the
            // packaged bytes are the canonical encoding of the verified
            // module that `solvik <file>` would execute.
            let compiled = match solvik_rs::compile_report(&file, &text, optimization_enabled()) {
                Ok(c) => c,
                Err(e) => report_compile_error(&e),
            };
            print_warnings(&compiled.warnings);
            package_command(&file, output, &compiled.bytes);
        }
    }
}

fn read_source(file: &str) -> String {
    match std::fs::read_to_string(file) {
        Ok(t) => t,
        Err(e) => {
            eprintln!("error: cannot read {}: {}", file, e);
            exit(3);
        }
    }
}

fn optimization_enabled() -> bool {
    std::env::var_os("SOLVIK_NO_OPT").is_none()
}

/// Print rendered non-fatal warnings to stderr (they never fail the build).
fn print_warnings(warnings: &[String]) {
    for w in warnings {
        eprintln!("{w}");
    }
}

/// Report a compile failure with the repository's exit-code conventions:
/// 1 for diagnostics, 3 for internal errors.
fn report_compile_error(e: &CompileError) -> ! {
    match e {
        CompileError::Diagnostics(message) => {
            eprintln!("{message}");
            eprintln!("error: compilation failed");
            exit(1);
        }
        CompileError::Internal(message) => {
            eprintln!("error: internal: {message}");
            exit(3);
        }
    }
}

/// Execute a verified module, preserving Solvik runtime error formatting and
/// exit-code semantics (0 = program result, 2 = runtime error).
fn run_module(module: solvik_rs::bytecode::CodeModule, program_args: Vec<String>) -> ! {
    match vm::Vm::run_main(module, program_args) {
        Ok(code) => exit(code as i32),
        Err(e) => {
            if let Some((file, line)) = e.location {
                eprintln!("runtime error at {}:{}: {}", file, line, e.message);
            } else {
                eprintln!("runtime error: {}", e.message);
            }
            exit(2);
        }
    }
}

fn package_command(source: &str, output: Option<String>, bytes: &[u8]) -> ! {
    let output_path = match resolve_output_path(source, output) {
        Ok(p) => p,
        Err(message) => {
            eprintln!("error: {message}");
            exit(3);
        }
    };
    let runtime = match package::find_runtime() {
        Ok(p) => p,
        Err(e) => {
            eprintln!("error: {e}");
            exit(3);
        }
    };
    if let Err(e) = package::create_package(&runtime, &output_path, bytes) {
        eprintln!("error: {e}");
        exit(3);
    }
    exit(0)
}

/// Default output name: the source path with its final extension removed, in
/// the same directory (`src/foo.sol` -> `src/foo`; on Windows, `.exe` is
/// appended when no extension results). An explicit `-o` value is used as-is.
fn resolve_output_path(source: &str, output: Option<String>) -> Result<PathBuf, String> {
    let src = Path::new(source);
    let path = match output {
        Some(p) => PathBuf::from(p),
        None => {
            let stem = src
                .file_stem()
                .filter(|s| !s.is_empty())
                .ok_or_else(|| format!("cannot derive an output name from {source}"))?;
            let out = match src.parent() {
                Some(p) if !p.as_os_str().is_empty() => {
                    let mut p = p.to_path_buf();
                    p.push(stem);
                    p
                }
                _ => Path::new(stem).to_path_buf(),
            };
            #[cfg(windows)]
            let out = {
                let mut p = out;
                if p.extension().is_none() {
                    p.set_extension("exe");
                }
                p
            };
            out
        }
    };
    if path == src {
        return Err(format!(
            "output {} would overwrite the source file",
            path.display()
        ));
    }
    Ok(path)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn parse(args: &[&str]) -> Result<Command, String> {
        parse_cli(&args.iter().map(|s| s.to_string()).collect::<Vec<_>>())
    }

    #[test]
    fn version_flag() {
        assert!(matches!(parse(&["--version"]), Ok(Command::Version)));
        assert!(matches!(parse(&["-version"]), Ok(Command::Version)));
    }

    #[test]
    fn run_mode_with_program_args() {
        match parse(&["hello.sol", "one", "two"]) {
            Ok(Command::Run { file, program_args }) => {
                assert_eq!(file, "hello.sol");
                assert_eq!(program_args, vec!["one", "two"]);
            }
            other => panic!("unexpected: {other:?}"),
        }
    }

    #[test]
    fn check_and_format_modes() {
        assert!(matches!(
            parse(&["--check", "a.sol"]),
            Ok(Command::Check { .. })
        ));
        assert!(matches!(
            parse(&["--format", "a.sol"]),
            Ok(Command::Format { .. })
        ));
    }

    #[test]
    fn package_mode_default_and_custom_output() {
        match parse(&["--package", "a.sol"]) {
            Ok(Command::Package { file, output }) => {
                assert_eq!(file, "a.sol");
                assert!(output.is_none());
            }
            other => panic!("unexpected: {other:?}"),
        }
        match parse(&["--package", "a.sol", "-o", "out"]) {
            Ok(Command::Package { file, output }) => {
                assert_eq!(file, "a.sol");
                assert_eq!(output.as_deref(), Some("out"));
            }
            other => panic!("unexpected: {other:?}"),
        }
        // Equivalent ordering: -o before the source file.
        match parse(&["-o", "out", "--package", "a.sol"]) {
            Ok(Command::Package { file, output }) => {
                assert_eq!(file, "a.sol");
                assert_eq!(output.as_deref(), Some("out"));
            }
            other => panic!("unexpected: {other:?}"),
        }
    }

    #[test]
    fn package_rejects_trailing_values_as_program_args() {
        assert!(parse(&["--package", "a.sol", "one"]).is_err());
    }

    #[test]
    fn missing_source_is_an_error() {
        assert!(parse(&[]).is_err());
        assert!(parse(&["--package"]).is_err());
        assert!(parse(&["--check"]).is_err());
    }

    #[test]
    fn missing_o_value_is_an_error() {
        assert!(parse(&["--package", "a.sol", "-o"]).is_err());
    }

    #[test]
    fn unknown_options_are_errors() {
        assert!(parse(&["--frobnicate", "a.sol"]).is_err());
        assert!(parse(&["-o", "x", "a.sol"]).is_err());
        assert!(parse(&["--check", "--format", "a.sol"]).is_err());
        assert!(parse(&["--check", "a.sol", "extra"]).is_err());
    }

    #[test]
    fn flags_after_the_file_are_program_args() {
        match parse(&["a.sol", "--version", "-o"]) {
            Ok(Command::Run { program_args, .. }) => {
                assert_eq!(program_args, vec!["--version", "-o"]);
            }
            other => panic!("unexpected: {other:?}"),
        }
    }

    #[test]
    fn default_output_name_derivation() {
        assert_eq!(
            resolve_output_path("hello.sol", None).unwrap(),
            PathBuf::from("hello")
        );
        assert_eq!(
            resolve_output_path("src/foo.sol", None).unwrap(),
            PathBuf::from("src/foo")
        );
        assert_eq!(
            resolve_output_path("foo.test.sol", None).unwrap(),
            PathBuf::from("foo.test")
        );
        assert_eq!(
            resolve_output_path("hello.sol", Some("myapp".into())).unwrap(),
            PathBuf::from("myapp")
        );
        // No sensible stem: rejected.
        assert!(resolve_output_path(".sol", None).is_err());
        // Never overwrite the input source.
        assert!(resolve_output_path("hello.sol", Some("hello.sol".into())).is_err());
    }
}
