//! Structural invariants of the compiler, bytecode format, verifier, and VM
//! value model. These guard properties that the rest of the system relies on
//! but that no single functional test pins down.

use solvik_rs::{bytecode, vm::value::Value};

const SRC: &str = r#"
module invariants

class Point {
    public x: Long
    public y: Long

    public static new(x: Long, y: Long): Self {
        return Self { x: x, y: y, }
    }

    public dist2(other: Point): Long {
        dx: Long = self.x - other.x
        dy: Long = self.y - other.y
        return dx * dx + dy * dy
    }
}

class Main {
    public static run(args: String...): Long {
        a: Point = Point.new(0, 0)
        b: Point = Point.new(3, 4)
        return a.dist2(b)
    }
}
"#;

/// `Value` is a small copy type: the VM moves it through the operand stack
/// by value on every instruction. A regression to heap-backed values (or a
/// growing enum) would silently change the cost model of the whole machine.
#[test]
fn value_is_copy_and_fixed_size() {
    // Compile-time proof that Value is Copy.
    fn assert_copy<T: Copy>() {}
    assert_copy::<Value>();
    // 16 bytes: a tag plus one pointer-width payload slot.
    assert_eq!(std::mem::size_of::<Value>(), 16);
}

/// Parameters occupy the first local slots, so every function must have at
/// least as many locals as parameters. The VM indexes arguments at
/// `base + i` without a bounds check against `params.len()`.
#[test]
fn params_never_exceed_locals() {
    let module = solvik_rs::compile("invariants.sol", SRC).unwrap();
    for f in &module.functions {
        assert!(
            f.params.len() <= f.local_count as usize,
            "function {}: {} params but only {} locals",
            f.name,
            f.params.len(),
            f.local_count
        );
    }
}

/// The binary encoding is deterministic: the same source must always produce
/// the same bytes. `.solb` files are compared and cached by content elsewhere,
/// and nondeterminism would break reproducible builds.
#[test]
fn encoding_is_deterministic() {
    let m1 = solvik_rs::compile("invariants.sol", SRC).unwrap();
    let m2 = solvik_rs::compile("invariants.sol", SRC).unwrap();
    let b1 = bytecode::encode::encode(&m1);
    let b2 = bytecode::encode::encode(&m2);
    assert_eq!(b1, b2);
}

/// max_stack survives an encode/decode round trip and is stable across
/// recompiles: it is part of the verified contract between module producer
/// and consumer.
#[test]
fn max_stack_is_stable_across_round_trips() {
    let m1 = solvik_rs::compile("invariants.sol", SRC).unwrap();
    let bytes = bytecode::encode::encode(&m1);
    let m2 = bytecode::decode::decode(&bytes).unwrap();
    for (f1, f2) in m1.functions.iter().zip(&m2.functions) {
        assert_eq!(
            f1.max_stack, f2.max_stack,
            "max_stack changed for {}",
            f1.name
        );
    }
    let m3 = solvik_rs::compile("invariants.sol", SRC).unwrap();
    for (f1, f3) in m1.functions.iter().zip(&m3.functions) {
        assert_eq!(
            f1.max_stack, f3.max_stack,
            "max_stack unstable for {}",
            f1.name
        );
    }
}

/// Corrupted or truncated bytecode must be rejected with an error, never
/// panic: modules are untrusted input when loaded from disk.
#[test]
fn malformed_bytecode_is_rejected_not_panicked() {
    // Garbage.
    assert!(bytecode::decode::decode(&[0u8; 64]).is_err());
    // Truncated at every prefix length of a valid module.
    let module = solvik_rs::compile("invariants.sol", SRC).unwrap();
    let bytes = bytecode::encode::encode(&module);
    for len in 0..bytes.len() {
        assert!(
            bytecode::decode::decode(&bytes[..len]).is_err(),
            "truncated module of {len} bytes decoded successfully"
        );
    }
    // Wrong magic.
    let mut bad = bytes.clone();
    bad[0] = b'X';
    assert!(bytecode::decode::decode(&bad).is_err());
    // Wrong version.
    let mut bad = bytes.clone();
    bad[4] = 99;
    assert!(bytecode::decode::decode(&bad).is_err());
}

/// Flipping individual bytes of a valid module must either decode to a
/// working module or fail cleanly — the decoder and verifier are total
/// functions over their inputs.
#[test]
fn single_byte_corruption_never_panics() {
    let module = solvik_rs::compile("invariants.sol", SRC).unwrap();
    let bytes = bytecode::encode::encode(&module);
    for i in 0..bytes.len() {
        let mut bad = bytes.clone();
        bad[i] ^= 0xFF;
        if let Ok(mut m) = bytecode::decode::decode(&bad) {
            // If it decodes, verification must not panic either.
            let mut diags = solvik_rs::diagnostic::Diagnostics::default();
            let _ = solvik_rs::verifier::verify_with_max_stacks(&mut m, &mut diags);
        }
    }
}

/// The entry point must exist and be a real function in the module.
#[test]
fn entry_function_exists() {
    let module = solvik_rs::compile("invariants.sol", SRC).unwrap();
    let entry = module.entry.expect("compiled module has an entry point");
    assert!((entry as usize) < module.functions.len());
    assert!(!module.functions[entry as usize].code.is_empty());
}

/// Every function's code section is non-empty and its line map is well
/// formed: sparse (offset, line) pairs with strictly increasing offsets
/// inside the code section.
#[test]
fn line_maps_match_code_length() {
    let module = solvik_rs::compile("invariants.sol", SRC).unwrap();
    for f in &module.functions {
        assert!(!f.code.is_empty(), "{} has no code", f.name);
        let mut prev = 0u32;
        for (off, _line) in &f.line_map {
            assert!(
                *off < f.code.len() as u32,
                "{} line map offset out of range",
                f.name
            );
            assert!(*off >= prev, "{} line map offsets not increasing", f.name);
            prev = *off + 1;
        }
    }
}
