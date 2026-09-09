//! Standard streams with redirect support.

use std::io::Write;
use std::sync::Mutex;

/// Shared stream state (redirect flags).
#[derive(Default)]
pub struct Streams {
    /// When true, `stderr` writes go to `stdout`.
    pub err_redirected_to_out: bool,
}

static STDOUT_LOCK: Mutex<()> = Mutex::new(());
static STDERR_LOCK: Mutex<()> = Mutex::new(());

/// Write a line to stdout.
pub fn println_out(s: &str) {
    let _g = STDOUT_LOCK.lock().unwrap_or_else(|e| e.into_inner());
    let mut out = std::io::stdout();
    let _ = writeln!(out, "{}", s);
    let _ = out.flush();
}

/// Write text to stdout (no newline).
pub fn print_out(s: &str) {
    let _g = STDOUT_LOCK.lock().unwrap_or_else(|e| e.into_inner());
    let mut out = std::io::stdout();
    let _ = write!(out, "{}", s);
    let _ = out.flush();
}

/// Write a line to stderr (or stdout when redirected).
pub fn write_err(streams: &Streams, s: &str, newline: bool) {
    if streams.err_redirected_to_out {
        if newline {
            println_out(s);
        } else {
            print_out(s);
        }
        return;
    }
    let _g = STDERR_LOCK.lock().unwrap_or_else(|e| e.into_inner());
    let mut err = std::io::stderr();
    let _ = write!(err, "{}{}", s, if newline { "\n" } else { "" });
    let _ = err.flush();
}

/// Read one line from stdin; None on EOF.
pub fn readln_in() -> Option<String> {
    use std::io::BufRead;
    let mut line = String::new();
    match std::io::stdin().lock().read_line(&mut line) {
        Ok(0) => None,
        Ok(_) => {
            if line.ends_with('\n') {
                line.pop();
                if line.ends_with('\r') {
                    line.pop();
                }
            }
            Some(line)
        }
        Err(_) => None,
    }
}

/// Read all of stdin.
pub fn read_all_in() -> String {
    use std::io::Read;
    let mut buf = String::new();
    match std::io::stdin().read_to_string(&mut buf) {
        Ok(_) => buf,
        Err(_) => String::new(),
    }
}
