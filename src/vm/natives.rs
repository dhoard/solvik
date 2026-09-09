//! Native function implementations, dispatched by native id.

use std::io::Write;
use std::process::Stdio;
use std::sync::Arc;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use crate::stdlib::builtins::nat;
use crate::vm::heap::{GcRef, HeapObject};
use crate::vm::value::Value;
use crate::vm::{streams, Vm, VmError};

/// Call a native by id with already-popped arguments.
pub fn call_native(vm: &mut Vm, id: u16, args: &[Value]) -> Result<Value, VmError> {
    match id {
        nat::TO_STRING => to_string(vm, args),
        // strings
        nat::STR_LEN => str_len(vm, args),
        nat::STR_SUBSTR => str_substr(vm, args),
        nat::STR_CONTAINS => str_test(vm, args, |s, x| s.contains(x)),
        nat::STR_STARTS_WITH => str_test(vm, args, |s, x| s.starts_with(x)),
        nat::STR_ENDS_WITH => str_test(vm, args, |s, x| s.ends_with(x)),
        nat::STR_SPLIT => str_split(vm, args),
        nat::STR_REPLACE => str_replace(vm, args),
        nat::STR_TRIM => str_map(vm, args, |s| s.trim().to_string()),
        nat::STR_UPPER => str_map(vm, args, |s| s.to_uppercase()),
        nat::STR_LOWER => str_map(vm, args, |s| s.to_lowercase()),
        nat::STR_INDEX_OF => str_index_of(vm, args),
        nat::STR_CHAR_AT => str_char_at(vm, args),
        // lists
        nat::LIST_SIZE => list_len(vm, args),
        nat::LIST_ADD => list_add(vm, args),
        nat::LIST_GET => list_get(vm, args),
        nat::LIST_SET => list_set(vm, args),
        nat::LIST_REMOVE => list_remove(vm, args),
        nat::LIST_CONTAINS => list_contains(vm, args),
        nat::LIST_INDEX_OF => list_index_of(vm, args),
        nat::LIST_REVERSE => list_reverse(vm, args),
        nat::LIST_SORT => list_sort(vm, args),
        nat::LIST_JOIN => list_join(vm, args),
        nat::LIST_CLEAR => list_clear(vm, args),
        nat::LIST_IS_EMPTY => list_is_empty(vm, args),
        // maps
        nat::MAP_PUT => map_put(vm, args),
        nat::MAP_GET => map_get(vm, args),
        nat::MAP_REMOVE => map_remove(vm, args),
        nat::MAP_CONTAINS_KEY => map_contains_key(vm, args),
        nat::MAP_SIZE => map_len(vm, args),
        nat::MAP_KEYS => map_keys(vm, args, true),
        nat::MAP_VALUES => map_keys(vm, args, false),
        nat::MAP_CLEAR => map_clear(vm, args),
        nat::MAP_IS_EMPTY => map_is_empty(vm, args),
        // stacks
        nat::STACK_PUSH => stack_push(vm, args),
        nat::STACK_POP => stack_pop(vm, args),
        nat::STACK_PEEK => stack_peek(vm, args),
        nat::STACK_SIZE => stack_len(vm, args),
        nat::STACK_IS_EMPTY => stack_is_empty(vm, args),
        // sets
        nat::SET_ADD => set_add(vm, args),
        nat::SET_REMOVE => set_remove(vm, args),
        nat::SET_CONTAINS => set_contains(vm, args),
        nat::SET_SIZE => set_len(vm, args),
        nat::SET_IS_EMPTY => set_is_empty(vm, args),
        nat::SET_CLEAR => set_clear(vm, args),
        // streams
        nat::WRITE => write_stream(vm, args, false),
        nat::PRINT => write_stream(vm, args, false),
        nat::PRINTLN => write_stream(vm, args, true),
        nat::REDIRECT => redirect(vm, args),
        nat::RESET_STREAM => reset_stream(vm, args),
        nat::READLN => readln(vm, args),
        nat::READ_ALL => read_all(vm, args),
        // concurrency
        nat::LIST_NEW => {
            let r = vm.heap_mut().alloc(HeapObject::List { items: vec![] });
            Ok(Value::Object(r))
        }
        nat::MAP_NEW => {
            let r = vm.heap_mut().alloc(HeapObject::Map { entries: vec![] });
            Ok(Value::Object(r))
        }
        nat::STACK_NEW => {
            let r = vm.heap_mut().alloc(HeapObject::Stack { items: vec![] });
            Ok(Value::Object(r))
        }
        nat::SET_NEW => {
            let r = vm.heap_mut().alloc(HeapObject::Set { items: vec![] });
            Ok(Value::Object(r))
        }
        nat::THREAD_NEW => thread_new(vm, args),
        nat::THREAD_START => thread_start(vm, args),
        nat::THREAD_JOIN => thread_join(vm, args),
        nat::MUTEX_NEW => mutex_new(vm),
        nat::MUTEX_LOCK => mutex_lock(vm, args),
        nat::MUTEX_UNLOCK => mutex_unlock(vm, args),
        nat::SEM_NEW => sem_new(vm, args),
        nat::SEM_ACQUIRE => sem_acquire(vm, args),
        nat::SEM_RELEASE => sem_release(vm, args),
        // processes
        nat::PROC_NEW => proc_new(vm, args),
        nat::PROC_START => proc_start(vm, args),
        nat::PROC_WAIT => proc_wait(vm, args),
        nat::PROC_EXIT_CODE => proc_exit_code(vm, args),
        nat::PROC_STDIN => proc_stream(vm, args, 0),
        nat::PROC_STDOUT => proc_stream(vm, args, 1),
        nat::PROC_STDERR => proc_stream(vm, args, 2),
        // regex
        nat::REGEX_NEW => regex_new(vm, args),
        nat::REGEX_MATCHES => regex_matches(vm, args),
        nat::REGEX_FIND => regex_find(vm, args),
        nat::REGEX_ALL => regex_all(vm, args),
        nat::REGEX_REPLACE => regex_replace(vm, args),
        // math
        nat::MATH_SQRT => math1(args, f64::sqrt),
        nat::MATH_ABS => math_abs(args),
        nat::MATH_FLOOR => math1(args, f64::floor),
        nat::MATH_CEIL => math1(args, f64::ceil),
        nat::MATH_ROUND => math1(args, f64::round),
        nat::MATH_POW => math_pow(args),
        nat::MATH_MIN => math_min_max(args, true),
        nat::MATH_MAX => math_min_max(args, false),
        // introspection / conversion
        nat::TYPE_OF => type_of(vm, args),
        nat::TYPE_IS_TYPE => type_is_type(vm, args),
        nat::CONV_INT => conv_int(vm, args),
        nat::CONV_FLOAT => conv_float(vm, args),
        nat::CONV_BYTE => conv_byte(vm, args),
        nat::CONV_BOOL => conv_bool(args),
        nat::CONV_STRING => conv_string(vm, args),
        nat::CONV_CHAR => conv_char(args),
        // encoding / hashing
        nat::BASE64_ENCODE => base64_encode(vm, args),
        nat::BASE64_DECODE => base64_decode(vm, args),
        nat::HASH_MD5 => hash_of(vm, args, 0),
        nat::HASH_SHA1 => hash_of(vm, args, 1),
        nat::HASH_SHA256 => hash_of(vm, args, 2),
        // json
        nat::JSON_PARSE => json_parse(vm, args),
        nat::JSON_STRINGIFY => json_stringify(vm, args),
        // time / random
        nat::TIME_NOW => time_now(),
        nat::TIME_SLEEP => time_sleep(args),
        nat::RANDOM_NEXT_INT => random_next_int(vm, args),
        nat::RANDOM_NEXT_FLOAT => random_next_float(vm),
        nat::RANDOM_SEED => random_seed(vm, args),
        // files
        nat::FILE_READ => file_read(vm, args),
        nat::FILE_WRITE => file_write(vm, args),
        nat::FILE_EXISTS => file_exists(vm, args),
        nat::FILE_DELETE => file_delete(vm, args),
        nat::FILE_LIST_DIR => file_list_dir(vm, args),
        // testing
        nat::TEST_ASSERT => test_assert(vm, args),
        nat::TEST_ASSERT_EQUAL => test_assert_equal(vm, args),
        other => Err(VmError::new(format!("unknown native id {}", other))),
    }
}

// ---------------------------------------------------------------------------
// Argument helpers
// ---------------------------------------------------------------------------

fn int_arg(args: &[Value], i: usize) -> Result<i64, VmError> {
    match args
        .get(i)
        .ok_or_else(|| VmError::new("missing argument"))?
    {
        Value::Int(v) => Ok(*v),
        _ => Err(VmError::new("expected Int")),
    }
}

fn float_arg(args: &[Value], i: usize) -> Result<f64, VmError> {
    match args
        .get(i)
        .ok_or_else(|| VmError::new("missing argument"))?
    {
        Value::Float(v) => Ok(*v),
        Value::Int(i) => Ok(*i as f64),
        _ => Err(VmError::new("expected Float")),
    }
}

/// Read a String argument by heap handle.
fn str_arg(vm: &Vm, args: &[Value], i: usize) -> Result<String, VmError> {
    let Value::Object(r) = args
        .get(i)
        .ok_or_else(|| VmError::new("missing argument"))?
    else {
        return Err(VmError::new("expected String"));
    };
    let heap = vm.heap();
    match heap.get(*r) {
        Some(HeapObject::String { text }) => Ok(text.clone()),
        _ => Err(VmError::new("expected String")),
    }
}

fn make_string(vm: &mut Vm, text: String) -> Value {
    Value::Object(vm.alloc_string(&text))
}

/// Run `f` on the items of a List argument (args[0]).
fn list_op(
    vm: &mut Vm,
    args: &[Value],
    f: impl FnOnce(&mut Vec<Value>) -> Result<Value, VmError>,
) -> Result<Value, VmError> {
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing List"))? else {
        return Err(VmError::new("expected List"));
    };
    let mut heap = vm.heap_mut();
    match heap.get_mut(*r) {
        Some(HeapObject::List { items }) => f(items),
        _ => Err(VmError::new("not a List")),
    }
}

/// Run `f` on the entries of a Map argument (args[0]).
fn map_op(
    vm: &mut Vm,
    args: &[Value],
    f: impl FnOnce(&mut Vec<(Value, Value)>) -> Result<Value, VmError>,
) -> Result<Value, VmError> {
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing Map"))? else {
        return Err(VmError::new("expected Map"));
    };
    let mut heap = vm.heap_mut();
    match heap.get_mut(*r) {
        Some(HeapObject::Map { entries }) => f(entries),
        _ => Err(VmError::new("not a Map")),
    }
}

/// Run `f` on the items of a Set argument (args[0]).
fn set_op(
    vm: &mut Vm,
    args: &[Value],
    f: impl FnOnce(&mut Vec<Value>) -> Result<Value, VmError>,
) -> Result<Value, VmError> {
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing Set"))? else {
        return Err(VmError::new("expected Set"));
    };
    let mut heap = vm.heap_mut();
    match heap.get_mut(*r) {
        Some(HeapObject::Set { items }) => f(items),
        _ => Err(VmError::new("not a Set")),
    }
}

/// Run `f` on the items of a Stack argument (args[0]).
fn stack_op(
    vm: &mut Vm,
    args: &[Value],
    f: impl FnOnce(&mut Vec<Value>) -> Result<Value, VmError>,
) -> Result<Value, VmError> {
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing Stack"))? else {
        return Err(VmError::new("expected Stack"));
    };
    let mut heap = vm.heap_mut();
    match heap.get_mut(*r) {
        Some(HeapObject::Stack { items }) => f(items),
        _ => Err(VmError::new("not a Stack")),
    }
}

// ---------------------------------------------------------------------------
// Strings
// ---------------------------------------------------------------------------

fn to_string(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let v = args
        .first()
        .ok_or_else(|| VmError::new("toString requires a value"))?;
    let text = {
        let heap = vm.heap();
        v.to_display(&heap)
    };
    Ok(make_string(vm, text))
}

fn str_len(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let s = str_arg(vm, args, 0)?;
    Ok(Value::Int(s.chars().count() as i64))
}

fn str_substr(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let s = str_arg(vm, args, 0)?;
    let start = int_arg(args, 1)?;
    let end = int_arg(args, 2)?;
    let chars: Vec<char> = s.chars().collect();
    let n = chars.len() as i64;
    let lo = start.clamp(0, n).max(0);
    let hi = end.clamp(0, n);
    if lo > hi {
        return Err(VmError::new("substring start is after end"));
    }
    Ok(make_string(
        vm,
        chars[lo as usize..hi as usize].iter().collect(),
    ))
}

fn str_test(vm: &mut Vm, args: &[Value], f: impl Fn(&str, &str) -> bool) -> Result<Value, VmError> {
    let s = str_arg(vm, args, 0)?;
    let x = str_arg(vm, args, 1)?;
    Ok(Value::Bool(f(&s, &x)))
}

fn str_split(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let s = str_arg(vm, args, 0)?;
    let sep = str_arg(vm, args, 1)?;
    let parts: Vec<Value> = s
        .split(sep.as_str())
        .map(|p| make_string(vm, p.to_string()))
        .collect();
    let ref_ = vm.heap_mut().alloc(HeapObject::List { items: parts });
    Ok(Value::Object(ref_))
}

fn str_replace(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let s = str_arg(vm, args, 0)?;
    let from = str_arg(vm, args, 1)?;
    let to = str_arg(vm, args, 2)?;
    Ok(make_string(vm, s.replace(from.as_str(), to.as_str())))
}

fn str_map(vm: &mut Vm, args: &[Value], f: impl Fn(&str) -> String) -> Result<Value, VmError> {
    let s = str_arg(vm, args, 0)?;
    Ok(make_string(vm, f(&s)))
}

fn str_index_of(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let s = str_arg(vm, args, 0)?;
    let x = str_arg(vm, args, 1)?;
    Ok(Value::Int(
        s.find(x.as_str())
            .map(|b| s[..b].chars().count() as i64)
            .unwrap_or(-1),
    ))
}

fn str_char_at(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let s = str_arg(vm, args, 0)?;
    let i = int_arg(args, 1)?;
    match usize::try_from(i)
        .ok()
        .and_then(|index| s.chars().nth(index))
    {
        Some(c) => Ok(Value::Char(c)),
        None => Err(VmError::new("char index out of range")),
    }
}

// ---------------------------------------------------------------------------
// Lists
// ---------------------------------------------------------------------------

fn list_len(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    list_op(vm, args, |items| Ok(Value::Int(items.len() as i64)))
}

fn list_add(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let v = *args.get(1).ok_or_else(|| VmError::new("missing element"))?;
    list_op(vm, args, move |items| {
        items.push(v);
        Ok(Value::Null)
    })
}

fn list_get(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let i = int_arg(args, 1)?;
    let loc = vm.current_location();
    list_op(vm, args, move |items| {
        usize::try_from(i)
            .ok()
            .and_then(|index| items.get(index))
            .copied()
            .ok_or_else(|| VmError::with_loc("list index out of range", loc.clone()))
    })
}

fn list_set(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let i = int_arg(args, 1)?;
    let v = *args.get(2).ok_or_else(|| VmError::new("missing value"))?;
    let loc = vm.current_location();
    list_op(vm, args, move |items| {
        match usize::try_from(i)
            .ok()
            .and_then(|index| items.get_mut(index))
        {
            Some(slot) => {
                *slot = v;
                Ok(Value::Null)
            }
            None => Err(VmError::with_loc("list index out of range", loc.clone())),
        }
    })
}

fn list_remove(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let i = int_arg(args, 1)?;
    let loc = vm.current_location();
    list_op(vm, args, move |items| {
        if i < 0 || i as usize >= items.len() {
            return Err(VmError::with_loc("list index out of range", loc.clone()));
        }
        Ok(items.remove(i as usize))
    })
}

fn list_contains(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let v = *args.get(1).ok_or_else(|| VmError::new("missing value"))?;
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing List"))? else {
        return Err(VmError::new("expected List"));
    };
    let items = {
        let heap = vm.heap();
        match heap.get(*r) {
            Some(HeapObject::List { items }) => items.clone(),
            _ => return Err(VmError::new("not a List")),
        }
    };
    let heap = vm.heap();
    Ok(Value::Bool(
        items
            .iter()
            .any(|x| crate::vm::Vm::values_equal(&heap, x, &v)),
    ))
}

fn list_index_of(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let v = *args.get(1).ok_or_else(|| VmError::new("missing value"))?;
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing List"))? else {
        return Err(VmError::new("expected List"));
    };
    let items = {
        let heap = vm.heap();
        match heap.get(*r) {
            Some(HeapObject::List { items }) => items.clone(),
            _ => return Err(VmError::new("not a List")),
        }
    };
    let heap = vm.heap();
    Ok(Value::Int(
        items
            .iter()
            .position(|x| crate::vm::Vm::values_equal(&heap, x, &v))
            .map(|p| p as i64)
            .unwrap_or(-1),
    ))
}

fn list_reverse(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    list_op(vm, args, |items| {
        items.reverse();
        Ok(Value::Null)
    })
}

fn list_sort(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    // Sort by display form (total order over mixed values).
    let keys: Vec<String> = {
        let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing List"))? else {
            return Err(VmError::new("expected List"));
        };
        let heap = vm.heap();
        match heap.get(*r) {
            Some(HeapObject::List { items }) => items.iter().map(|v| v.to_display(&heap)).collect(),
            _ => return Err(VmError::new("not a List")),
        }
    };
    list_op(vm, args, move |items| {
        let mut order: Vec<usize> = (0..items.len()).collect();
        order.sort_by(|&a, &b| keys[a].cmp(&keys[b]));
        *items = order.into_iter().map(|i| items[i]).collect();
        Ok(Value::Null)
    })
}

fn list_join(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let sep = str_arg(vm, args, 1)?;
    let text = {
        let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing List"))? else {
            return Err(VmError::new("expected List"));
        };
        let heap = vm.heap();
        match heap.get(*r) {
            Some(HeapObject::List { items }) => items
                .iter()
                .map(|v| v.to_display(&heap))
                .collect::<Vec<_>>()
                .join(sep.as_str()),
            _ => return Err(VmError::new("not a List")),
        }
    };
    Ok(make_string(vm, text))
}

fn list_clear(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    list_op(vm, args, |items| {
        items.clear();
        Ok(Value::Null)
    })
}

fn list_is_empty(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    list_op(vm, args, |items| Ok(Value::Bool(items.is_empty())))
}

// ---------------------------------------------------------------------------
// Maps
// ---------------------------------------------------------------------------

fn map_ref(args: &[Value]) -> Result<GcRef, VmError> {
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing Map"))? else {
        return Err(VmError::new("expected Map"));
    };
    Ok(*r)
}

fn map_put(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let k = *args.get(1).ok_or_else(|| VmError::new("missing key"))?;
    let v = *args.get(2).ok_or_else(|| VmError::new("missing value"))?;
    let pos = {
        let r = map_ref(args)?;
        let heap = vm.heap();
        match heap.get(r) {
            Some(HeapObject::Map { entries }) => entries
                .iter()
                .position(|(ek, _)| crate::vm::Vm::values_equal(&heap, ek, &k)),
            _ => return Err(VmError::new("not a Map")),
        }
    };
    map_op(vm, args, move |entries| {
        match pos {
            Some(p) => entries[p].1 = v,
            None => entries.push((k, v)),
        }
        Ok(Value::Null)
    })
}

fn map_get(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let k = *args.get(1).ok_or_else(|| VmError::new("missing key"))?;
    let r = map_ref(args)?;
    let entries = {
        let heap = vm.heap();
        match heap.get(r) {
            Some(HeapObject::Map { entries }) => entries.clone(),
            _ => return Err(VmError::new("not a Map")),
        }
    };
    let heap = vm.heap();
    Ok(entries
        .iter()
        .find(|(ek, _)| crate::vm::Vm::values_equal(&heap, ek, &k))
        .map(|(_, v)| *v)
        .unwrap_or(Value::Null))
}

fn map_remove(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let k = *args.get(1).ok_or_else(|| VmError::new("missing key"))?;
    let drop_idx: Vec<usize> = {
        let r = map_ref(args)?;
        let heap = vm.heap();
        match heap.get(r) {
            Some(HeapObject::Map { entries }) => entries
                .iter()
                .enumerate()
                .filter(|(_, (ek, _))| crate::vm::Vm::values_equal(&heap, ek, &k))
                .map(|(i, _)| i)
                .collect(),
            _ => return Err(VmError::new("not a Map")),
        }
    };
    map_op(vm, args, move |entries| {
        for i in drop_idx.into_iter().rev() {
            entries.remove(i);
        }
        Ok(Value::Null)
    })
}

fn map_contains_key(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let k = *args.get(1).ok_or_else(|| VmError::new("missing key"))?;
    let r = map_ref(args)?;
    let entries = {
        let heap = vm.heap();
        match heap.get(r) {
            Some(HeapObject::Map { entries }) => entries.clone(),
            _ => return Err(VmError::new("not a Map")),
        }
    };
    let heap = vm.heap();
    Ok(Value::Bool(
        entries
            .iter()
            .any(|(ek, _)| crate::vm::Vm::values_equal(&heap, ek, &k)),
    ))
}

fn map_len(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    map_op(vm, args, |entries| Ok(Value::Int(entries.len() as i64)))
}

fn map_keys(vm: &mut Vm, args: &[Value], keys: bool) -> Result<Value, VmError> {
    let items: Vec<Value> = {
        let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing Map"))? else {
            return Err(VmError::new("expected Map"));
        };
        let heap = vm.heap();
        match heap.get(*r) {
            Some(HeapObject::Map { entries }) => entries
                .iter()
                .map(|e| if keys { e.0 } else { e.1 })
                .collect(),
            _ => return Err(VmError::new("not a Map")),
        }
    };
    let ref_ = vm.heap_mut().alloc(HeapObject::List { items });
    Ok(Value::Object(ref_))
}

fn map_clear(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    map_op(vm, args, |entries| {
        entries.clear();
        Ok(Value::Null)
    })
}

fn map_is_empty(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    map_op(vm, args, |entries| Ok(Value::Bool(entries.is_empty())))
}

// ---------------------------------------------------------------------------
// Stacks
// ---------------------------------------------------------------------------

fn stack_push(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let v = *args.get(1).ok_or_else(|| VmError::new("missing value"))?;
    stack_op(vm, args, move |items| {
        items.push(v);
        Ok(Value::Null)
    })
}

fn stack_pop(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    stack_op(vm, args, |items| Ok(items.pop().unwrap_or(Value::Null)))
}

fn stack_peek(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    stack_op(vm, args, |items| {
        Ok(items.last().copied().unwrap_or(Value::Null))
    })
}

fn stack_len(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    stack_op(vm, args, |items| Ok(Value::Int(items.len() as i64)))
}

fn stack_is_empty(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    stack_op(vm, args, |items| Ok(Value::Bool(items.is_empty())))
}

// ---------------------------------------------------------------------------
// Sets
// ---------------------------------------------------------------------------

fn set_ref(args: &[Value]) -> Result<GcRef, VmError> {
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing Set"))? else {
        return Err(VmError::new("expected Set"));
    };
    Ok(*r)
}

fn set_add(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let v = *args.get(1).ok_or_else(|| VmError::new("missing value"))?;
    let exists = {
        let r = set_ref(args)?;
        let heap = vm.heap();
        match heap.get(r) {
            Some(HeapObject::Set { items }) => items
                .iter()
                .any(|x| crate::vm::Vm::values_equal(&heap, x, &v)),
            _ => return Err(VmError::new("not a Set")),
        }
    };
    if !exists {
        set_op(vm, args, move |items| {
            items.push(v);
            Ok(Value::Null)
        })?;
    }
    Ok(Value::Null)
}

fn set_remove(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let v = *args.get(1).ok_or_else(|| VmError::new("missing value"))?;
    let drop_idx: Vec<usize> = {
        let r = set_ref(args)?;
        let heap = vm.heap();
        match heap.get(r) {
            Some(HeapObject::Set { items }) => items
                .iter()
                .enumerate()
                .filter(|(_, x)| crate::vm::Vm::values_equal(&heap, x, &v))
                .map(|(i, _)| i)
                .collect(),
            _ => return Err(VmError::new("not a Set")),
        }
    };
    set_op(vm, args, move |items| {
        for i in drop_idx.into_iter().rev() {
            items.remove(i);
        }
        Ok(Value::Null)
    })
}

fn set_contains(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let v = *args.get(1).ok_or_else(|| VmError::new("missing value"))?;
    let r = set_ref(args)?;
    let items = {
        let heap = vm.heap();
        match heap.get(r) {
            Some(HeapObject::Set { items }) => items.clone(),
            _ => return Err(VmError::new("not a Set")),
        }
    };
    let heap = vm.heap();
    Ok(Value::Bool(
        items
            .iter()
            .any(|x| crate::vm::Vm::values_equal(&heap, x, &v)),
    ))
}

fn set_len(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    set_op(vm, args, |items| Ok(Value::Int(items.len() as i64)))
}

fn set_is_empty(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    set_op(vm, args, |items| Ok(Value::Bool(items.is_empty())))
}

fn set_clear(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    set_op(vm, args, |items| {
        items.clear();
        Ok(Value::Null)
    })
}

// ---------------------------------------------------------------------------
// Streams
// ---------------------------------------------------------------------------

fn write_stream(vm: &mut Vm, args: &[Value], newline: bool) -> Result<Value, VmError> {
    // args[0] is the stream handle; args[1] the value to render.
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing stream"))? else {
        return Err(VmError::new("expected Writer"));
    };
    let (kind, process) = {
        let heap = vm.heap();
        match heap.get(*r) {
            Some(HeapObject::Stream { kind }) => (*kind, None),
            Some(HeapObject::ProcessStream { process, kind }) => (*kind, Some(*process)),
            _ => return Err(VmError::new("expected Writer")),
        }
    };
    // The argument may be any value; render it via its toString().
    let v = *args
        .get(1)
        .ok_or_else(|| VmError::new("missing argument"))?;
    let text = {
        let heap = vm.heap();
        v.to_display(&heap)
    };
    if let Some(process) = process {
        if kind != 0 {
            return Err(VmError::new("process output streams are Readers"));
        }
        let mut input = {
            let mut heap = vm.heap_mut();
            match heap.get_mut(process) {
                Some(HeapObject::Process { stdin, .. }) => stdin
                    .take()
                    .ok_or_else(|| VmError::new("process stdin is unavailable"))?,
                _ => return Err(VmError::new("not a Process")),
            }
        };
        let result = input
            .write_all(text.as_bytes())
            .and_then(|()| input.flush())
            .map_err(|e| VmError::new(format!("failed to write process stdin: {}", e)));
        if let Some(HeapObject::Process { stdin, .. }) = vm.heap_mut().get_mut(process) {
            *stdin = Some(input);
        }
        result?;
        return Ok(Value::Null);
    }
    match kind {
        0 => return Err(VmError::new("stdin is a Reader")),
        2 => {
            let s = vm.shared.streams.lock().unwrap_or_else(|e| e.into_inner());
            streams::write_err(&s, &text, newline);
        }
        _ => {
            if newline {
                streams::println_out(&text);
            } else {
                streams::print_out(&text);
            }
        }
    }
    Ok(Value::Null)
}

fn redirect(vm: &mut Vm, _args: &[Value]) -> Result<Value, VmError> {
    let mut s = vm.shared.streams.lock().unwrap_or_else(|e| e.into_inner());
    s.err_redirected_to_out = true;
    Ok(Value::Null)
}

fn reset_stream(vm: &mut Vm, _args: &[Value]) -> Result<Value, VmError> {
    let mut s = vm.shared.streams.lock().unwrap_or_else(|e| e.into_inner());
    s.err_redirected_to_out = false;
    Ok(Value::Null)
}

fn readln(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    if let Some(Value::Object(stream)) = args.first() {
        let Some((process, kind)) = process_stream_info(vm, *stream)? else {
            return match streams::readln_in() {
                Some(line) => Ok(make_string(vm, line)),
                None => Ok(Value::Null),
            };
        };
        if kind == 0 {
            return Err(VmError::new("process stdin is a Writer"));
        }
        let output = process_output(vm, process, kind)?;
        return Ok(output
            .read_line()
            .map(|line| make_string(vm, line))
            .unwrap_or(Value::Null));
    }
    match streams::readln_in() {
        Some(line) => Ok(make_string(vm, line)),
        None => Ok(Value::Null),
    }
}

fn read_all(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    if let Some(Value::Object(stream)) = args.first() {
        let Some((process, kind)) = process_stream_info(vm, *stream)? else {
            return Ok(make_string(vm, streams::read_all_in()));
        };
        if kind == 0 {
            return Err(VmError::new("process stdin is a Writer"));
        }
        let output = process_output(vm, process, kind)?;
        return Ok(make_string(vm, output.read_all()));
    }
    Ok(make_string(vm, streams::read_all_in()))
}

fn process_stream_info(vm: &Vm, stream: GcRef) -> Result<Option<(GcRef, u8)>, VmError> {
    let heap = vm.heap();
    match heap.get(stream) {
        Some(HeapObject::ProcessStream { process, kind }) => Ok(Some((*process, *kind))),
        Some(HeapObject::Stream { .. }) => Ok(None),
        _ => Err(VmError::new("expected process stream")),
    }
}

fn process_output(
    vm: &Vm,
    process: GcRef,
    kind: u8,
) -> Result<Arc<crate::vm::heap::ProcessOutput>, VmError> {
    let heap = vm.heap();
    match heap.get(process) {
        Some(HeapObject::Process { stdout, .. }) if kind == 1 => stdout
            .clone()
            .ok_or_else(|| VmError::new("process stdout is unavailable")),
        Some(HeapObject::Process { stderr, .. }) if kind == 2 => stderr
            .clone()
            .ok_or_else(|| VmError::new("process stderr is unavailable")),
        _ => Err(VmError::new("not a Process")),
    }
}

// ---------------------------------------------------------------------------
// Concurrency
// ---------------------------------------------------------------------------

fn thread_new(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let Value::Object(runnable) = args
        .first()
        .ok_or_else(|| VmError::new("missing Runnable"))?
    else {
        return Err(VmError::new("expected Runnable"));
    };
    let ref_ = vm.heap_mut().alloc(HeapObject::Thread {
        runnable: Some(*runnable),
        handle: None,
        done: false,
    });
    Ok(Value::Object(ref_))
}

fn thread_start(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing Thread"))? else {
        return Err(VmError::new("expected Thread"));
    };
    let runnable = {
        let mut heap = vm.heap_mut();
        match heap.get_mut(*r) {
            Some(HeapObject::Thread {
                runnable,
                handle,
                done,
            }) => {
                if handle.is_some() || *done {
                    return Err(VmError::new("thread already started"));
                }
                runnable.take()
            }
            _ => return Err(VmError::new("not a Thread")),
        }
    };
    let runnable = runnable.ok_or_else(|| VmError::new("thread has no Runnable"))?;
    let handle = crate::vm::Vm::spawn_thread(vm.shared.clone(), runnable);
    {
        let mut heap = vm.heap_mut();
        if let Some(HeapObject::Thread { handle: h, .. }) = heap.get_mut(*r) {
            *h = Some(handle);
        }
    }
    Ok(Value::Null)
}

fn thread_join(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing Thread"))? else {
        return Err(VmError::new("expected Thread"));
    };
    // Take the handle out so we can block without holding the heap lock.
    let handle = {
        let mut heap = vm.heap_mut();
        match heap.get_mut(*r) {
            Some(HeapObject::Thread { handle, done, .. }) => {
                if *done {
                    None
                } else {
                    if handle.is_none() {
                        return Err(VmError::new(
                            "thread has not started or join is in progress",
                        ));
                    }
                    if handle
                        .as_ref()
                        .is_some_and(|h| h.thread().id() == std::thread::current().id())
                    {
                        return Err(VmError::new("thread cannot join itself"));
                    }
                    handle.take()
                }
            }
            _ => return Err(VmError::new("not a Thread")),
        }
    };
    if let Some(h) = handle {
        let _ = h.join();
    }
    {
        let mut heap = vm.heap_mut();
        if let Some(HeapObject::Thread { done, .. }) = heap.get_mut(*r) {
            *done = true;
        }
    }
    Ok(Value::Null)
}

fn mutex_new(vm: &mut Vm) -> Result<Value, VmError> {
    use std::sync::{Condvar, Mutex};
    let ref_ = vm.heap_mut().alloc(HeapObject::Mutex {
        inner: std::sync::Arc::new((Mutex::new(false), Condvar::new())),
    });
    Ok(Value::Object(ref_))
}

fn mutex_lock(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing Mutex"))? else {
        return Err(VmError::new("expected Mutex"));
    };
    let inner = {
        let heap = vm.heap();
        match heap.get(*r) {
            Some(HeapObject::Mutex { inner }) => inner.clone(),
            _ => return Err(VmError::new("not a Mutex")),
        }
    };
    let (lock, cv) = &*inner;
    let mut g = lock.lock().unwrap_or_else(|e| e.into_inner());
    while *g {
        g = cv.wait(g).unwrap_or_else(|e| e.into_inner());
    }
    *g = true;
    Ok(Value::Null)
}

fn mutex_unlock(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing Mutex"))? else {
        return Err(VmError::new("expected Mutex"));
    };
    let inner = {
        let heap = vm.heap();
        match heap.get(*r) {
            Some(HeapObject::Mutex { inner }) => inner.clone(),
            _ => return Err(VmError::new("not a Mutex")),
        }
    };
    let (lock, cv) = &*inner;
    let mut g = lock.lock().unwrap_or_else(|e| e.into_inner());
    *g = false;
    cv.notify_one();
    Ok(Value::Null)
}

fn sem_new(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    use std::sync::{Condvar, Mutex};
    let count = int_arg(args, 0)?;
    if count < 0 {
        return Err(VmError::new(
            "semaphore count must be between 0 and 2147483647",
        ));
    }
    let count = i32::try_from(count)
        .map_err(|_| VmError::new("semaphore count must be between 0 and 2147483647"))?;
    let ref_ = vm.heap_mut().alloc(HeapObject::Semaphore {
        inner: std::sync::Arc::new((Mutex::new(count), Condvar::new())),
        max: count,
    });
    Ok(Value::Object(ref_))
}

fn sem_acquire(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let Value::Object(r) = args
        .first()
        .ok_or_else(|| VmError::new("missing Semaphore"))?
    else {
        return Err(VmError::new("expected Semaphore"));
    };
    let inner = {
        let heap = vm.heap();
        match heap.get(*r) {
            Some(HeapObject::Semaphore { inner, .. }) => inner.clone(),
            _ => return Err(VmError::new("not a Semaphore")),
        }
    };
    let (count, cv) = &*inner;
    let mut g = count.lock().unwrap_or_else(|e| e.into_inner());
    while *g <= 0 {
        g = cv.wait(g).unwrap_or_else(|e| e.into_inner());
    }
    *g -= 1;
    Ok(Value::Null)
}

fn sem_release(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let Value::Object(r) = args
        .first()
        .ok_or_else(|| VmError::new("missing Semaphore"))?
    else {
        return Err(VmError::new("expected Semaphore"));
    };
    let (inner, max) = {
        let heap = vm.heap();
        match heap.get(*r) {
            Some(HeapObject::Semaphore { inner, max }) => (inner.clone(), *max),
            _ => return Err(VmError::new("not a Semaphore")),
        }
    };
    let (count, cv) = &*inner;
    let mut g = count.lock().unwrap_or_else(|e| e.into_inner());
    if *g < max {
        *g += 1;
    }
    cv.notify_one();
    Ok(Value::Null)
}

// ---------------------------------------------------------------------------
// Processes
// ---------------------------------------------------------------------------

fn proc_new(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let cmd = str_arg(vm, args, 0)?;
    let argv: Vec<String> = match args.get(1) {
        Some(Value::Object(r)) => {
            let heap = vm.heap();
            match heap.get(*r) {
                Some(HeapObject::List { items }) => {
                    items.iter().map(|v| v.to_display(&heap)).collect()
                }
                _ => return Err(VmError::new("expected List of args")),
            }
        }
        _ => Vec::new(),
    };
    let ref_ = vm.heap_mut().alloc(HeapObject::Process {
        child: None,
        exit_code: None,
        cmd: Some(cmd),
        argv,
        stdin: None,
        stdout: None,
        stderr: None,
    });
    Ok(Value::Object(ref_))
}

fn proc_start(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let Value::Object(r) = args
        .first()
        .ok_or_else(|| VmError::new("missing Process"))?
    else {
        return Err(VmError::new("expected Process"));
    };
    let (cmd, argv) = {
        let mut heap = vm.heap_mut();
        match heap.get_mut(*r) {
            Some(HeapObject::Process {
                child,
                exit_code,
                cmd,
                argv,
                ..
            }) => {
                if child.is_some() || exit_code.is_some() {
                    return Err(VmError::new("process already started"));
                }
                (cmd.clone(), argv.clone())
            }
            _ => return Err(VmError::new("not a Process")),
        }
    };
    let (cmd, argv) = match cmd {
        Some(c) => (c, argv),
        None => return Err(VmError::new("process has no command")),
    };
    let mut split = cmd.split_whitespace();
    let program = split.next().ok_or_else(|| VmError::new("empty command"))?;
    let mut full_args: Vec<String> = split.map(|s| s.to_string()).collect();
    full_args.extend(argv);
    let mut child = std::process::Command::new(program)
        .args(&full_args)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| VmError::new(format!("failed to start process: {}", e)))?;
    let child_stdin = child.stdin.take();
    let child_stdout = child.stdout.take().map(|reader| {
        let output = Arc::new(crate::vm::heap::ProcessOutput::default());
        let pump_output = output.clone();
        std::thread::spawn(move || pump_output.pump(reader));
        output
    });
    let child_stderr = child.stderr.take().map(|reader| {
        let output = Arc::new(crate::vm::heap::ProcessOutput::default());
        let pump_output = output.clone();
        std::thread::spawn(move || pump_output.pump(reader));
        output
    });
    {
        let mut heap = vm.heap_mut();
        if let Some(HeapObject::Process {
            child: slot,
            stdin,
            stdout,
            stderr,
            ..
        }) = heap.get_mut(*r)
        {
            *slot = Some(child);
            *stdin = child_stdin;
            *stdout = child_stdout;
            *stderr = child_stderr;
        }
    }
    Ok(Value::Null)
}

fn proc_stream(vm: &mut Vm, args: &[Value], kind: u8) -> Result<Value, VmError> {
    let Value::Object(process) = args
        .first()
        .ok_or_else(|| VmError::new("missing Process"))?
    else {
        return Err(VmError::new("expected Process"));
    };
    let started = {
        let heap = vm.heap();
        match heap.get(*process) {
            Some(HeapObject::Process {
                child, exit_code, ..
            }) => child.is_some() || exit_code.is_some(),
            _ => return Err(VmError::new("not a Process")),
        }
    };
    if !started {
        return Err(VmError::new("process has not started"));
    }
    let stream = vm.heap_mut().alloc(HeapObject::ProcessStream {
        process: *process,
        kind,
    });
    Ok(Value::Object(stream))
}

fn proc_wait(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let Value::Object(r) = args
        .first()
        .ok_or_else(|| VmError::new("missing Process"))?
    else {
        return Err(VmError::new("expected Process"));
    };
    // Take the child out so we can block without holding the heap lock.
    let child = {
        let mut heap = vm.heap_mut();
        match heap.get_mut(*r) {
            Some(HeapObject::Process {
                child, exit_code, ..
            }) => {
                if let Some(code) = exit_code {
                    return Ok(Value::Int(*code as i64));
                }
                child
                    .take()
                    .ok_or_else(|| VmError::new("process has not started or wait is in progress"))?
            }
            _ => return Err(VmError::new("not a Process")),
        }
    };
    let mut child = child;
    let code = match child.wait() {
        Ok(status) => status.code().unwrap_or(-1),
        Err(e) => {
            if let Some(HeapObject::Process { child: slot, .. }) = vm.heap_mut().get_mut(*r) {
                *slot = Some(child);
            }
            return Err(VmError::new(format!("failed to wait for process: {}", e)));
        }
    };
    {
        let mut heap = vm.heap_mut();
        if let Some(HeapObject::Process { exit_code, .. }) = heap.get_mut(*r) {
            *exit_code = Some(code);
        }
    }
    Ok(Value::Int(code as i64))
}

fn proc_exit_code(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let Value::Object(r) = args
        .first()
        .ok_or_else(|| VmError::new("missing Process"))?
    else {
        return Err(VmError::new("expected Process"));
    };
    let heap = vm.heap();
    match heap.get(*r) {
        Some(HeapObject::Process { exit_code, .. }) => match exit_code {
            Some(c) => Ok(Value::Int(*c as i64)),
            None => Err(VmError::new("process has not exited")),
        },
        _ => Err(VmError::new("not a Process")),
    }
}

// ---------------------------------------------------------------------------
// Regex
// ---------------------------------------------------------------------------

fn regex_new(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let pattern = str_arg(vm, args, 0)?;
    let re =
        regex::Regex::new(&pattern).map_err(|e| VmError::new(format!("invalid regex: {}", e)))?;
    let ref_ = vm.heap_mut().alloc(HeapObject::Regex { re });
    Ok(Value::Object(ref_))
}

fn regex_obj(vm: &Vm, args: &[Value]) -> Result<regex::Regex, VmError> {
    let Value::Object(r) = args.first().ok_or_else(|| VmError::new("missing Regex"))? else {
        return Err(VmError::new("expected Regex"));
    };
    let heap = vm.heap();
    match heap.get(*r) {
        Some(HeapObject::Regex { re }) => Ok(re.clone()),
        _ => Err(VmError::new("not a Regex")),
    }
}

fn regex_matches(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let re = regex_obj(vm, args)?;
    let text = str_arg(vm, args, 1)?;
    Ok(Value::Bool(re.is_match(&text)))
}

fn regex_find(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let re = regex_obj(vm, args)?;
    let text = str_arg(vm, args, 1)?;
    match re.find(&text) {
        Some(m) => Ok(make_string(vm, m.as_str().to_string())),
        None => Ok(Value::Null),
    }
}

fn regex_all(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let re = regex_obj(vm, args)?;
    let text = str_arg(vm, args, 1)?;
    let items: Vec<Value> = re
        .find_iter(&text)
        .map(|m| make_string(vm, m.as_str().to_string()))
        .collect();
    let ref_ = vm.heap_mut().alloc(HeapObject::List { items });
    Ok(Value::Object(ref_))
}

fn regex_replace(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let re = regex_obj(vm, args)?;
    let text = str_arg(vm, args, 1)?;
    let repl = str_arg(vm, args, 2)?;
    Ok(make_string(
        vm,
        re.replace_all(&text, repl.as_str()).into_owned(),
    ))
}

// ---------------------------------------------------------------------------
// Math
// ---------------------------------------------------------------------------

fn math1(args: &[Value], f: impl Fn(f64) -> f64) -> Result<Value, VmError> {
    let x = float_arg(args, 0)?;
    Ok(Value::Float(f(x)))
}

fn math_abs(args: &[Value]) -> Result<Value, VmError> {
    match args.first() {
        Some(Value::Int(i)) => i
            .checked_abs()
            .map(Value::Int)
            .ok_or_else(|| VmError::new("integer overflow")),
        Some(Value::Float(f)) => Ok(Value::Float(f.abs())),
        _ => Err(VmError::new("expected numeric")),
    }
}

fn math_pow(args: &[Value]) -> Result<Value, VmError> {
    let a = float_arg(args, 0)?;
    let b = float_arg(args, 1)?;
    Ok(Value::Float(a.powf(b)))
}

fn math_min_max(args: &[Value], is_min: bool) -> Result<Value, VmError> {
    let a = float_arg(args, 0)?;
    let b = float_arg(args, 1)?;
    let r = if is_min { a.min(b) } else { a.max(b) };
    // Preserve Int-ness when both inputs are ints.
    match (args.first(), args.get(1)) {
        (Some(Value::Int(x)), Some(Value::Int(y))) => {
            Ok(Value::Int(if is_min { (*x).min(*y) } else { (*x).max(*y) }))
        }
        _ => Ok(Value::Float(r)),
    }
}

// ---------------------------------------------------------------------------
// Introspection / conversion
// ---------------------------------------------------------------------------

/// Type tag for a value (backing of `Type::of`).
fn type_tag(vm: &Vm, v: &Value) -> String {
    match v {
        Value::Null => "null".to_string(),
        Value::Bool(_) => "Bool".to_string(),
        Value::Int(_) => "Int".to_string(),
        Value::Float(_) => "Float".to_string(),
        Value::Char(_) => "Char".to_string(),
        Value::Object(r) => {
            let heap = vm.heap();
            match heap.get(*r) {
                Some(HeapObject::String { .. }) => "String".to_string(),
                Some(HeapObject::List { .. }) => "List".to_string(),
                Some(HeapObject::Map { .. }) => "Map".to_string(),
                Some(HeapObject::Stack { .. }) => "Stack".to_string(),
                Some(HeapObject::Set { .. }) => "Set".to_string(),
                Some(HeapObject::Enum { .. }) => "Enum".to_string(),
                Some(HeapObject::Exception { .. }) => "Exception".to_string(),
                Some(HeapObject::Thread { .. }) => "Thread".to_string(),
                Some(HeapObject::Mutex { .. }) => "Mutex".to_string(),
                Some(HeapObject::Semaphore { .. }) => "Semaphore".to_string(),
                Some(HeapObject::Process { .. }) => "Process".to_string(),
                Some(HeapObject::ProcessStream { kind, .. }) => match kind {
                    0 => "Writer".to_string(),
                    _ => "Reader".to_string(),
                },
                Some(HeapObject::Regex { .. }) => "Regex".to_string(),
                Some(HeapObject::Stream { kind }) => match kind {
                    0 => "Reader".to_string(),
                    1 => "Writer".to_string(),
                    _ => "Writer".to_string(),
                },
                Some(HeapObject::Instance { class, .. }) => vm
                    .shared
                    .module
                    .classes
                    .get(*class as usize)
                    .map(|c| c.name.clone())
                    .unwrap_or_else(|| "Object".to_string()),
                None => "Object".to_string(),
            }
        }
    }
}

fn type_of(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let v = args.first().ok_or_else(|| VmError::new("missing value"))?;
    Ok(make_string(vm, type_tag(vm, v)))
}

fn type_is_type(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let v = args.first().ok_or_else(|| VmError::new("missing value"))?;
    let name = str_arg(vm, args, 1)?;
    Ok(Value::Bool(type_tag(vm, v) == name))
}

fn conv_int(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    match args.first().ok_or_else(|| VmError::new("missing value"))? {
        Value::Int(i) => Ok(Value::Int(*i)),
        Value::Float(f) => {
            // i64::MAX rounds up to 2^63 as f64, so the upper bound is exclusive.
            if !f.is_finite() || *f < i64::MIN as f64 || *f >= 9223372036854775808.0 {
                return Err(VmError::new("value out of Int range"));
            }
            Ok(Value::Int(*f as i64))
        }
        Value::Bool(b) => Ok(Value::Int(i64::from(*b))),
        Value::Char(c) => Ok(Value::Int(*c as i64)),
        Value::Null => Err(VmError::new("cannot convert null to Int")),
        Value::Object(_r) => {
            let s = str_arg(vm, args, 0)?;
            s.trim()
                .parse::<i64>()
                .map(Value::Int)
                .map_err(|_| VmError::new(format!("cannot parse Int from \"{}\"", s)))
        }
    }
}

fn conv_float(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    match args.first().ok_or_else(|| VmError::new("missing value"))? {
        Value::Int(i) => Ok(Value::Float(*i as f64)),
        Value::Float(f) => Ok(Value::Float(*f)),
        Value::Bool(b) => Ok(Value::Float(f64::from(*b))),
        Value::Char(_) => Err(VmError::new("cannot convert to Float")),
        Value::Null => Err(VmError::new("cannot convert null to Float")),
        Value::Object(_) => {
            let s = str_arg(vm, args, 0)?;
            s.trim()
                .parse::<f64>()
                .map(Value::Float)
                .map_err(|_| VmError::new(format!("cannot parse Float from \"{}\"", s)))
        }
    }
}

fn conv_byte(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let i = conv_int(vm, args)?;
    match i {
        Value::Int(v) if (-128i64..=127).contains(&v) => Ok(Value::Int(v)),
        _ => Err(VmError::new("value out of Byte range")),
    }
}

fn conv_bool(args: &[Value]) -> Result<Value, VmError> {
    match args.first().ok_or_else(|| VmError::new("missing value"))? {
        Value::Bool(b) => Ok(Value::Bool(*b)),
        Value::Int(i) => Ok(Value::Bool(*i != 0)),
        Value::Float(f) => Ok(Value::Bool(*f != 0.0)),
        _ => Err(VmError::new("cannot convert to Bool")),
    }
}

fn conv_char(args: &[Value]) -> Result<Value, VmError> {
    match args.first().ok_or_else(|| VmError::new("missing value"))? {
        Value::Char(c) => Ok(Value::Char(*c)),
        Value::Int(i) => u32::try_from(*i)
            .ok()
            .and_then(char::from_u32)
            .map(Value::Char)
            .ok_or_else(|| VmError::new("not a valid code point")),
        _ => Err(VmError::new("cannot convert to Char")),
    }
}

fn conv_string(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    to_string(vm, args)
}

// ---------------------------------------------------------------------------
// Encoding / hashing
// ---------------------------------------------------------------------------

fn base64_encode(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    use base64::Engine;
    let s = str_arg(vm, args, 0)?;
    let enc = base64::engine::general_purpose::STANDARD.encode(s.as_bytes());
    Ok(make_string(vm, enc))
}

fn base64_decode(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    use base64::Engine;
    let s = str_arg(vm, args, 0)?;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(s.as_bytes())
        .map_err(|e| VmError::new(format!("invalid base64: {}", e)))?;
    let text =
        String::from_utf8(bytes).map_err(|_| VmError::new("decoded data is not valid UTF-8"))?;
    Ok(make_string(vm, text))
}

fn hash_of(vm: &mut Vm, args: &[Value], which: u8) -> Result<Value, VmError> {
    use md5::Digest as _;
    let s = str_arg(vm, args, 0)?;
    let bytes = s.as_bytes();
    let hex = match which {
        0 => {
            let d = md5::Md5::digest(bytes);
            hex_of(&d)
        }
        1 => {
            let d = sha1::Sha1::digest(bytes);
            hex_of(&d)
        }
        _ => {
            let d = sha2::Sha256::digest(bytes);
            hex_of(&d)
        }
    };
    Ok(make_string(vm, hex))
}

fn hex_of(d: &[u8]) -> String {
    let mut out = String::with_capacity(d.len() * 2);
    for b in d {
        out.push_str(&format!("{:02x}", b));
    }
    out
}

// ---------------------------------------------------------------------------
// JSON
// ---------------------------------------------------------------------------

fn json_parse(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let s = str_arg(vm, args, 0)?;
    let v: serde_json::Value =
        serde_json::from_str(&s).map_err(|e| VmError::new(format!("invalid JSON: {}", e)))?;
    json_to_value(vm, &v)
}

fn json_to_value(vm: &mut Vm, v: &serde_json::Value) -> Result<Value, VmError> {
    match v {
        serde_json::Value::Null => Ok(Value::Null),
        serde_json::Value::Bool(b) => Ok(Value::Bool(*b)),
        serde_json::Value::Number(n) => {
            if let Some(i) = n.as_i64() {
                Ok(Value::Int(i))
            } else if let Some(f) = n.as_f64() {
                Ok(Value::Float(f))
            } else {
                Err(VmError::new("JSON number out of range"))
            }
        }
        serde_json::Value::String(s) => Ok(make_string(vm, s.clone())),
        serde_json::Value::Array(items) => {
            let mut out = Vec::with_capacity(items.len());
            for item in items {
                out.push(json_to_value(vm, item)?);
            }
            let ref_ = vm.heap_mut().alloc(HeapObject::List { items: out });
            Ok(Value::Object(ref_))
        }
        serde_json::Value::Object(map) => {
            let mut entries = Vec::with_capacity(map.len());
            for (k, val) in map {
                entries.push((make_string(vm, k.clone()), json_to_value(vm, val)?));
            }
            let ref_ = vm.heap_mut().alloc(HeapObject::Map { entries });
            Ok(Value::Object(ref_))
        }
    }
}

fn json_stringify(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let v = args.first().ok_or_else(|| VmError::new("missing value"))?;
    let j = {
        let module = &*vm.shared.module;
        let heap = vm.heap();
        value_to_json(module, &heap, v, &mut std::collections::HashSet::new())?
    };
    let text = serde_json::to_string(&j).map_err(|e| VmError::new(format!("json error: {}", e)))?;
    Ok(make_string(vm, text))
}

fn value_to_json(
    module: &crate::bytecode::CodeModule,
    heap: &crate::vm::heap::Heap,
    v: &Value,
    active: &mut std::collections::HashSet<crate::vm::heap::GcRef>,
) -> Result<serde_json::Value, VmError> {
    if let Value::Object(r) = v {
        if !active.insert(*r) {
            return Err(VmError::new("cyclic value is not JSON-serializable"));
        }
    }
    let result = match v {
        Value::Null => Ok(serde_json::Value::Null),
        Value::Bool(b) => Ok(serde_json::Value::Bool(*b)),
        Value::Int(i) => Ok(serde_json::Number::from(*i).into()),
        Value::Float(f) => serde_json::Number::from_f64(*f)
            .map(serde_json::Value::Number)
            .ok_or_else(|| VmError::new("float not representable in JSON")),
        Value::Char(c) => Ok(serde_json::Value::String(c.to_string())),
        Value::Object(r) => match heap.get(*r) {
            Some(HeapObject::String { text }) => Ok(serde_json::Value::String(text.clone())),
            Some(HeapObject::List { items }) => {
                let arr: Vec<serde_json::Value> = items
                    .iter()
                    .map(|x| value_to_json(module, heap, x, active))
                    .collect::<Result<_, _>>()?;
                Ok(serde_json::Value::Array(arr))
            }
            Some(HeapObject::Map { entries }) => {
                let mut map = serde_json::Map::new();
                for (k, val) in entries {
                    let key = match k {
                        Value::Object(kr) => match heap.get(*kr) {
                            Some(HeapObject::String { text }) => text.clone(),
                            _ => k.to_display(heap),
                        },
                        other => other.to_display(heap),
                    };
                    map.insert(key, value_to_json(module, heap, val, active)?);
                }
                Ok(serde_json::Value::Object(map))
            }
            Some(HeapObject::Instance { class, fields }) => {
                let name = module
                    .classes
                    .get(*class as usize)
                    .map(|c| c.name.clone())
                    .unwrap_or_default();
                let arr: Vec<serde_json::Value> = fields
                    .iter()
                    .map(|f| value_to_json(module, heap, f, active))
                    .collect::<Result<_, _>>()?;
                let mut map = serde_json::Map::new();
                map.insert("$type".to_string(), serde_json::Value::String(name));
                map.insert("fields".to_string(), serde_json::Value::Array(arr));
                Ok(serde_json::Value::Object(map))
            }
            _ => Err(VmError::new("value is not JSON-serializable")),
        },
    };
    if let Value::Object(r) = v {
        active.remove(r);
    }
    result
}

// ---------------------------------------------------------------------------
// Time / random
// ---------------------------------------------------------------------------

fn time_now() -> Result<Value, VmError> {
    let millis = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0);
    Ok(Value::Int(millis))
}

fn time_sleep(args: &[Value]) -> Result<Value, VmError> {
    let ms = int_arg(args, 0)?;
    std::thread::sleep(Duration::from_millis(ms.max(0) as u64));
    Ok(Value::Null)
}

fn random_bits(vm: &Vm) -> Result<u64, VmError> {
    let mut state = vm
        .shared
        .random_state
        .lock()
        .unwrap_or_else(|e| e.into_inner());
    if let Some(value) = state.as_mut() {
        // xorshift64 provides a small deterministic generator for explicitly
        // seeded programs without changing the default OS entropy behavior.
        *value ^= *value << 13;
        *value ^= *value >> 7;
        *value ^= *value << 17;
        return Ok(*value);
    }
    drop(state);
    let mut buf = [0u8; 8];
    getrandom::getrandom(&mut buf).map_err(|e| VmError::new(format!("entropy error: {}", e)))?;
    Ok(u64::from_le_bytes(buf))
}

fn random_next_int(vm: &Vm, args: &[Value]) -> Result<Value, VmError> {
    let bound = int_arg(args, 0)?;
    if bound <= 0 {
        return Err(VmError::new("random bound must be positive"));
    }
    Ok(random_int_from_bits(random_bits(vm)?, bound))
}

fn random_int_from_bits(bits: u64, bound: i64) -> Value {
    // Reduce unsigned entropy before converting to the signed result range.
    Value::Int((bits % bound as u64) as i64 + 1)
}

fn random_next_float(vm: &Vm) -> Result<Value, VmError> {
    let bits = random_bits(vm)?;
    // Map into [0, 1).
    Ok(Value::Float((bits >> 11) as f64 / (1u64 << 53) as f64))
}

fn random_seed(vm: &Vm, args: &[Value]) -> Result<Value, VmError> {
    let seed = int_arg(args, 0)? as u64;
    let mut state = vm
        .shared
        .random_state
        .lock()
        .unwrap_or_else(|e| e.into_inner());
    *state = Some(if seed == 0 {
        0x9e37_79b9_7f4a_7c15
    } else {
        seed
    });
    Ok(Value::Null)
}

// ---------------------------------------------------------------------------
// Files
// ---------------------------------------------------------------------------

fn file_read(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let path = str_arg(vm, args, 0)?;
    let data = std::fs::read_to_string(&path)
        .map_err(|e| VmError::new(format!("failed to read {}: {}", path, e)))?;
    Ok(make_string(vm, data))
}

fn file_write(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let path = str_arg(vm, args, 0)?;
    let data = str_arg(vm, args, 1)?;
    std::fs::write(&path, data)
        .map_err(|e| VmError::new(format!("failed to write {}: {}", path, e)))?;
    Ok(Value::Null)
}

fn file_exists(_vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let path = str_arg(_vm, args, 0)?;
    Ok(Value::Bool(std::path::Path::new(&path).exists()))
}

fn file_delete(_vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let path = str_arg(_vm, args, 0)?;
    match std::fs::remove_file(&path) {
        Ok(()) => Ok(Value::Null),
        Err(e) => Err(VmError::new(format!("failed to delete {}: {}", path, e))),
    }
}

fn file_list_dir(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let path = str_arg(vm, args, 0)?;
    let rd = std::fs::read_dir(&path)
        .map_err(|e| VmError::new(format!("failed to list {}: {}", path, e)))?;
    let mut items = Vec::new();
    for entry in rd.flatten() {
        if let Some(name) = entry.file_name().to_str() {
            items.push(make_string(vm, name.to_string()));
        }
    }
    let ref_ = vm.heap_mut().alloc(HeapObject::List { items });
    Ok(Value::Object(ref_))
}

// ---------------------------------------------------------------------------
// Testing
// ---------------------------------------------------------------------------

/// Optional trailing message argument rendered as ": <msg>".
fn optional_msg(vm: &Vm, args: &[Value], i: usize) -> String {
    match args.get(i) {
        Some(Value::Object(r)) => {
            let heap = vm.heap();
            match heap.get(*r) {
                Some(HeapObject::String { text }) => format!(": {}", text),
                _ => String::new(),
            }
        }
        _ => String::new(),
    }
}

fn test_assert(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let cond = match args
        .first()
        .ok_or_else(|| VmError::new("missing condition"))?
    {
        Value::Bool(b) => *b,
        _ => return Err(VmError::new("expected Bool")),
    };
    if !cond {
        let suffix = optional_msg(vm, args, 1);
        let exc = vm.heap_mut().alloc(HeapObject::Exception {
            message: format!("assertion failed{}", suffix),
        });
        vm.do_throw(Value::Object(exc))?;
    }
    Ok(Value::Null)
}

fn test_assert_equal(vm: &mut Vm, args: &[Value]) -> Result<Value, VmError> {
    let a = args.first().ok_or_else(|| VmError::new("missing value"))?;
    let b = args.get(1).ok_or_else(|| VmError::new("missing value"))?;
    let equal = {
        let heap = vm.heap();
        Vm::values_equal(&heap, a, b)
    };
    if !equal {
        let (da, db) = {
            let heap = vm.heap();
            (a.to_display(&heap), b.to_display(&heap))
        };
        let suffix = optional_msg(vm, args, 2);
        let exc = vm.heap_mut().alloc(HeapObject::Exception {
            message: format!(
                "assertion failed: expected {} equal to {}{}",
                da, db, suffix
            ),
        });
        vm.do_throw(Value::Object(exc))?;
    }
    Ok(Value::Null)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn time_now_returns_epoch_milliseconds() {
        let before = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_millis();
        let Value::Int(now) = time_now().unwrap() else {
            panic!("expected Int")
        };
        let after = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_millis();
        assert!((before..=after).contains(&(now as u128)));
    }

    #[test]
    fn random_integer_stays_in_bounds() {
        for bound in [1, 3, 10, i64::MAX] {
            for bits in [0, 1, i64::MAX as u64, 1 << 63, u64::MAX] {
                let Value::Int(value) = random_int_from_bits(bits, bound) else {
                    panic!("expected Int")
                };
                assert!(
                    (1..=bound).contains(&value),
                    "bits={bits} bound={bound} value={value}"
                );
            }
        }
    }

    #[test]
    fn numeric_boundaries() {
        assert_eq!(
            math_abs(&[Value::Int(i64::MIN)]).unwrap_err().message,
            "integer overflow"
        );
        for (input, expected) in [(0, 0), (-1, 1), (i64::MAX, i64::MAX)] {
            assert!(
                matches!(math_abs(&[Value::Int(input)]).unwrap(), Value::Int(v) if v == expected)
            );
        }
        for input in [-4294967231, -1, 0xd800, 0x110000, 4294967361, i64::MAX] {
            assert!(conv_char(&[Value::Int(input)]).is_err(), "accepted {input}");
        }
        for input in [0, 65, 0x1f600, 0x10ffff] {
            assert!(
                matches!(conv_char(&[Value::Int(input)]).unwrap(), Value::Char(c) if c as i64 == input)
            );
        }
    }
}
