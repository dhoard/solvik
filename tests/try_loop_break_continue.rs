//! Regression tests for break/continue inside loops nested within try
//! bodies.
//!
//! The loop target (loop end for break, loop start for continue) always
//! lies *inside* every try body that lexically contains the loop, because a
//! try region closes only after its whole body. The break/continue
//! trampoline must therefore clean up only the try regions opened between
//! the loop start and the break site — never regions above the loop. The
//! old walk emitted a spurious `TryEnd`/`FinallyDivert` when the loop was
//! the last statement of a try body, producing bytecode the verifier
//! rejected (V012/V013).

fn run(src: &str) -> i64 {
    let module = solvik_rs::compile("t.sol", src).expect("compile");
    solvik_rs::vm::Vm::run_main(module, vec![]).expect("run")
}

#[test]
fn continue_in_while_in_try_with_loop_last_statement() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let mutable i: Long = 0\n\
         try {\n\
             let x: Long = 1\n\
             while i < 4 {\n\
                 i += 1\n\
                 if i == 2 { continue }\n\
             }\n\
         } catch (e: Exception) {}\n\
         return i\n\
         }\n}\n",
    );
    assert_eq!(code, 4);
}

#[test]
fn break_in_while_in_try_with_loop_last_statement() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let mutable i: Long = 0\n\
         try {\n\
             let x: Long = 1\n\
             while true {\n\
                 i += 1\n\
                 if i >= 3 { break }\n\
             }\n\
         } catch (e: Exception) {}\n\
         return i\n\
         }\n}\n",
    );
    assert_eq!(code, 3);
}

#[test]
fn break_in_for_in_in_try_with_loop_last_statement() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let mutable total: Long = 0\n\
         try {\n\
             let x: Long = 1\n\
             for v in [1, 2, 3, 4] {\n\
                 if v == 2 { break }\n\
                 total += v\n\
             }\n\
         } catch (e: Exception) {}\n\
         return total\n\
         }\n}\n",
    );
    assert_eq!(code, 1);
}

#[test]
fn continue_in_for_in_in_try_with_loop_last_statement() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let mutable total: Long = 0\n\
         try {\n\
             let x: Long = 1\n\
             for v in [1, 2, 3, 4] {\n\
                 if v == 2 { continue }\n\
                 total += v\n\
             }\n\
         } catch (e: Exception) {}\n\
         return total\n\
         }\n}\n",
    );
    assert_eq!(code, 8); // 1 + 3 + 4
}

#[test]
fn break_in_loop_in_try_finally_runs_once_on_normal_completion() {
    // Breaking out of the loop completes the try body normally, so the
    // finally runs exactly once, after the break.
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let log: List<String> = []\n\
         let mutable i: Long = 0\n\
         try {\n\
             log.add(\"loop\")\n\
             while true {\n\
                 i += 1\n\
                 if i >= 2 { break }\n\
             }\n\
         } finally {\n\
             log.add(\"fin\")\n\
         }\n\
         if log.size() != 2 || log.get(0) != \"loop\" || log.get(1) != \"fin\" || i != 2 {\n\
             throw Exception.new(\"bad sequence\")\n\
         }\n\
         return 0\n\
         }\n}\n",
    );
    assert_eq!(code, 0);
}

#[test]
fn loop_around_try_break_still_exits_region() {
    // Regression guard for the pre-existing (correct) direction: a break
    // whose target is outside the try must still run the finally.
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let mutable count: Long = 0\n\
         for i in 0..3 {\n\
             try {\n\
                 if i == 0 { continue }\n\
                 break\n\
             } finally {\n\
                 count += 1\n\
             }\n\
         }\n\
         return count\n\
         }\n}\n",
    );
    assert_eq!(code, 2); // i=0 continue, i=1 break; i=2 never runs
}

#[test]
fn continue_crossing_try_between_site_and_loop() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let mutable skipped: Long = 0\n\
         for x in 0..4 {\n\
             try {\n\
                 if x == 1 { continue }\n\
             } catch (e: Exception) {}\n\
             skipped += 1\n\
         }\n\
         return skipped\n\
         }\n}\n",
    );
    assert_eq!(code, 3);
}

#[test]
fn break_crossing_nested_tries_between_site_and_loop() {
    // The break crosses the inner try (no finally: plain region drop) and
    // the outer try (finally: diverted). The outer finally runs only on
    // the breaking iteration plus the normal-completion iteration.
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let mutable fins: Long = 0\n\
         for x in 0..2 {\n\
             try {\n\
                 try {\n\
                     if x == 1 { break }\n\
                 } catch (e: Exception) {}\n\
             } finally {\n\
                 fins += 1\n\
             }\n\
         }\n\
         return fins\n\
         }\n}\n",
    );
    assert_eq!(code, 2); // x=0 normal completion, x=1 diverted break
}

#[test]
fn inner_loop_break_inside_try_inside_outer_loop() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let mutable hits: Long = 0\n\
         for r in 0..3 {\n\
             try {\n\
                 let x: Long = 1\n\
                 for c in 0..5 {\n\
                     if c == 2 { break }\n\
                 }\n\
             } catch (e: Exception) {}\n\
             hits += 1\n\
         }\n\
         return hits\n\
         }\n}\n",
    );
    assert_eq!(code, 3);
}

#[test]
fn continue_in_while_in_catch_body_with_finally() {
    // The loop target is inside the catch-body region; the trampoline must
    // not touch it.
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let mutable i: Long = 0\n\
         try {\n\
             throw Exception.new(\"x\")\n\
         } catch (e: Exception) {\n\
             let marker: Long = 1\n\
             while i < 3 {\n\
                 i += 1\n\
                 if i == 2 { continue }\n\
             }\n\
         } finally {\n\
             System.out().println(\"fin\")\n\
         }\n\
         return i\n\
         }\n}\n",
    );
    assert_eq!(code, 3);
}

#[test]
fn break_crossing_inner_try_in_loop_in_catch_body() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let mutable i: Long = 0\n\
         try {\n\
             throw Exception.new(\"x\")\n\
         } catch (e: Exception) {\n\
             while i < 3 {\n\
                 try {\n\
                     i += 1\n\
                     if i >= 2 { break }\n\
                 } catch (e2: Exception) {}\n\
             }\n\
         } finally {\n\
             System.out().println(\"fin\")\n\
         }\n\
         return i\n\
         }\n}\n",
    );
    assert_eq!(code, 2);
}

#[test]
fn continue_in_while_in_finally_body() {
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let mutable i: Long = 0\n\
         try {\n\
             throw Exception.new(\"x\")\n\
         } catch (e: Exception) {}\n\
         finally {\n\
             while i < 2 {\n\
                 i += 1\n\
                 if i == 1 { continue }\n\
             }\n\
         }\n\
         return i\n\
         }\n}\n",
    );
    assert_eq!(code, 2);
}

#[test]
fn break_out_of_try_from_switch_case() {
    // The break crosses the try (finally: diverted) from inside a switch
    // case body.
    let code = run(
        "package m\nclass Main {\npublic static run(args: String...): Long {\n\
         let mutable r: Long = 0\n\
         for x in 0..3 {\n\
             try {\n\
                 switch x {\n\
                     case 1: { break }\n\
                 }\n\
             } finally {\n\
                 r += 1\n\
             }\n\
         }\n\
         return r\n\
         }\n}\n",
    );
    assert_eq!(code, 2);
}
