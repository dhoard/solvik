//! Shared collection operation helpers.
//!
//! Every public collection operation — whether reached through a native call
//! (`List.add`, `Map.put`, ...) or a specialized VM opcode (`ListAdd`,
//! `MapPut`, ...) — routes through the functions in this module, so both
//! paths share one implementation of the semantics and synchronization.
//!
//! Synchronization: each operation briefly takes the heap lock to fetch the
//! collection's payload handle (and validate its kind), then performs all
//! element work under the collection's own lock. Equality, hashing, and
//! comparison of element values may take the heap lock while holding the
//! collection lock (the documented collection-outer order); no operation
//! holds the heap lock while acquiring another collection lock, and no
//! operation invokes user code, formats values for display, or recurses into
//! user objects while holding a collection lock except for the bounded
//! equality/hash/comparison work described in the heap module's
//! lock-ordering rule.

use std::cmp::Ordering;
use std::sync::{Arc, Mutex};

use crate::vm::heap::{GcRef, Heap, HeapObject, ListData, MapData, SetData, StackData};
use crate::vm::value::Value;
use crate::vm::{Vm, VmError};

fn guard<T>(m: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    m.lock().unwrap_or_else(|e| e.into_inner())
}

// ---------------------------------------------------------------------------
// Payload access
// ---------------------------------------------------------------------------

fn list_data(vm: &Vm, r: GcRef) -> Result<Arc<Mutex<ListData>>, VmError> {
    let heap = vm.heap();
    match heap.get(r) {
        Some(HeapObject::List { data }) => Ok(data.clone()),
        _ => Err(VmError::new("not a List")),
    }
}

fn map_data(vm: &Vm, r: GcRef) -> Result<Arc<Mutex<MapData>>, VmError> {
    let heap = vm.heap();
    match heap.get(r) {
        Some(HeapObject::Map { data }) => Ok(data.clone()),
        _ => Err(VmError::new("not a Map")),
    }
}

fn stack_data(vm: &Vm, r: GcRef) -> Result<Arc<Mutex<StackData>>, VmError> {
    let heap = vm.heap();
    match heap.get(r) {
        Some(HeapObject::Stack { data }) => Ok(data.clone()),
        _ => Err(VmError::new("not a Stack")),
    }
}

fn set_data(vm: &Vm, r: GcRef) -> Result<Arc<Mutex<SetData>>, VmError> {
    let heap = vm.heap();
    match heap.get(r) {
        Some(HeapObject::Set { data }) => Ok(data.clone()),
        _ => Err(VmError::new("not a Set")),
    }
}

/// Reject values whose observable equality can change after insertion.
/// Instances and collections are mutable; every other value kind is either
/// immutable or compared by identity, both of which keep hash indexing sound.
fn reject_mutable_key(vm: &Vm, v: &Value, what: &str) -> Result<(), VmError> {
    if let Value::Object(r) = v {
        let heap = vm.heap();
        if let Some(obj) = heap.get(*r) {
            if matches!(
                obj,
                HeapObject::Instance { .. }
                    | HeapObject::List { .. }
                    | HeapObject::Map { .. }
                    | HeapObject::Stack { .. }
                    | HeapObject::Set { .. }
            ) {
                return Err(vm.err_at(format!(
                    "{what}: mutable values cannot be used as Map keys or Set members"
                )));
            }
        }
    }
    Ok(())
}

// ---------------------------------------------------------------------------
// Natural ordering (shared by sort and comparisons)
// ---------------------------------------------------------------------------

/// Total ordering between two values for natural ordering. Numerics compare
/// across widths/precisions; Char by code point; String by UTF-8 text;
/// BigInteger/BigDecimal numerically. `None` when the pair cannot be ordered
/// (mixed kinds, or objects without a natural order).
pub fn value_cmp(heap: &Heap, a: &Value, b: &Value) -> Option<Ordering> {
    use Ordering::*;
    // Integral numerics compare exactly as i64.
    if let (Some(x), Some(y)) = (a.int_value(), b.int_value()) {
        return Some(x.cmp(&y));
    }
    // Floating numerics compare as f64 (NaN sorts as Equal to itself,
    // matching the language rule that NaN is unordered-but-comparable).
    if let (Some(x), Some(y)) = (a.float_value(), b.float_value()) {
        return x.partial_cmp(&y).or(Some(Equal));
    }
    // Integer vs float promotes to f64 (Java-style).
    if let (Some(x), Some(y)) = (a.num_f64(), b.num_f64()) {
        return x.partial_cmp(&y).or(Some(Equal));
    }
    match (a, b) {
        (Value::Char(x), Value::Char(y)) => Some(x.cmp(y)),
        (Value::Object(ra), Value::Object(rb)) => match (heap.get(*ra), heap.get(*rb)) {
            (Some(HeapObject::String { text: ta }), Some(HeapObject::String { text: tb })) => {
                Some(ta.cmp(tb))
            }
            (
                Some(HeapObject::BigInteger { value: va }),
                Some(HeapObject::BigInteger { value: vb }),
            ) => Some(va.cmp(vb)),
            (
                Some(HeapObject::BigDecimal { value: da }),
                Some(HeapObject::BigDecimal { value: db }),
            ) => Some(da.cmp_dec(db)),
            _ => None,
        },
        _ => None,
    }
}

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

pub fn list_alloc(vm: &mut Vm, capacity: usize) -> GcRef {
    vm.heap_mut().alloc(if capacity == 0 {
        HeapObject::list()
    } else {
        HeapObject::list_with_capacity(capacity)
    })
}

/// Allocate a List pre-populated with `items` (single lock acquisition).
pub fn list_alloc_with_items(vm: &mut Vm, items: Vec<Value>) -> GcRef {
    let r = list_alloc(vm, items.len());
    let data = list_data(vm, r).expect("freshly allocated list");
    guard(&data).items = items;
    r
}

pub fn list_push(vm: &mut Vm, r: GcRef, item: Value) -> Result<(), VmError> {
    let data = list_data(vm, r)?;
    guard(&data).items.push(item);
    Ok(())
}

pub fn list_get(vm: &mut Vm, r: GcRef, idx: i64) -> Result<Value, VmError> {
    let data = list_data(vm, r)?;
    let g = guard(&data);
    usize::try_from(idx)
        .ok()
        .and_then(|i| g.items.get(i))
        .copied()
        .ok_or_else(|| vm.err_at("list index out of range"))
}

pub fn list_set(vm: &mut Vm, r: GcRef, idx: i64, val: Value) -> Result<Value, VmError> {
    let data = list_data(vm, r)?;
    let mut g = guard(&data);
    let slot = usize::try_from(idx)
        .ok()
        .and_then(|i| g.items.get_mut(i))
        .ok_or_else(|| vm.err_at("list index out of range"))?;
    Ok(std::mem::replace(slot, val))
}

pub fn list_remove_at(vm: &mut Vm, r: GcRef, idx: i64) -> Result<Value, VmError> {
    let data = list_data(vm, r)?;
    let mut g = guard(&data);
    let i = usize::try_from(idx)
        .ok()
        .filter(|i| *i < g.items.len())
        .ok_or_else(|| vm.err_at("list index out of range"))?;
    Ok(g.items.remove(i))
}

/// Insert at index (Java List.add(int, E) shape); indices beyond the end are
/// a runtime error.
pub fn list_insert_at(vm: &mut Vm, r: GcRef, idx: i64, val: Value) -> Result<(), VmError> {
    let data = list_data(vm, r)?;
    let mut g = guard(&data);
    let i = usize::try_from(idx)
        .ok()
        .filter(|i| *i <= g.items.len())
        .ok_or_else(|| vm.err_at("list index out of range"))?;
    g.items.insert(i, val);
    Ok(())
}

pub fn list_contains(vm: &mut Vm, r: GcRef, v: Value) -> Result<bool, VmError> {
    let data = list_data(vm, r)?;
    let g = guard(&data);
    let heap = vm.heap();
    Ok(g.items.iter().any(|x| Vm::values_equal(&heap, x, &v)))
}

pub fn list_index_of(vm: &mut Vm, r: GcRef, v: Value) -> Result<i64, VmError> {
    let data = list_data(vm, r)?;
    let g = guard(&data);
    let heap = vm.heap();
    Ok(g.items
        .iter()
        .position(|x| Vm::values_equal(&heap, x, &v))
        .map(|p| p as i64)
        .unwrap_or(-1))
}

pub fn list_reverse(vm: &mut Vm, r: GcRef) -> Result<(), VmError> {
    let data = list_data(vm, r)?;
    guard(&data).items.reverse();
    Ok(())
}

/// In-place natural-order sort. Pairs that cannot be naturally ordered are a
/// runtime error rather than a silent fallback order.
pub fn list_sort(vm: &mut Vm, r: GcRef) -> Result<(), VmError> {
    let data = list_data(vm, r)?;
    let mut g = guard(&data);
    let mut incomparable = false;
    let heap = vm.heap();
    g.items.sort_by(|x, y| match value_cmp(&heap, x, y) {
        Some(ord) => ord,
        None => {
            incomparable = true;
            Ordering::Equal
        }
    });
    // A sorted sequence is only meaningful when adjacent elements compare;
    // catch pairs the comparator never met.
    if !incomparable {
        for pair in g.items.iter().zip(g.items.iter().skip(1)) {
            if value_cmp(&heap, pair.0, pair.1).is_none() {
                incomparable = true;
                break;
            }
        }
    }
    if incomparable {
        return Err(vm.err_at("list contains incomparable elements"));
    }
    Ok(())
}

pub fn list_join(vm: &mut Vm, r: GcRef, sep: &str) -> Result<String, VmError> {
    let data = list_data(vm, r)?;
    let g = guard(&data);
    let heap = vm.heap();
    Ok(g.items
        .iter()
        .map(|v| v.to_display(&heap))
        .collect::<Vec<_>>()
        .join(sep))
}

pub fn list_clear(vm: &mut Vm, r: GcRef) -> Result<(), VmError> {
    let data = list_data(vm, r)?;
    guard(&data).items.clear();
    Ok(())
}

pub fn list_len(vm: &mut Vm, r: GcRef) -> Result<usize, VmError> {
    let data = list_data(vm, r)?;
    let g = guard(&data);
    Ok(g.items.len())
}

/// Append every element of `src` to `dst` (both Lists).
pub fn list_extend(vm: &mut Vm, dst: GcRef, src: GcRef) -> Result<(), VmError> {
    let items = list_snapshot(vm, src)?;
    let data = list_data(vm, dst)?;
    guard(&data).items.extend(items);
    Ok(())
}

/// `addAll`: append all elements of `other`; returns true when the list
/// changed (i.e. `other` was non-empty).
pub fn list_add_all(vm: &mut Vm, r: GcRef, other: GcRef) -> Result<bool, VmError> {
    let items = list_snapshot(vm, other)?;
    let changed = !items.is_empty();
    let data = list_data(vm, r)?;
    guard(&data).items.extend(items);
    Ok(changed)
}

/// Thread-safe reversed snapshot (an independent copy, not a live view).
pub fn list_reversed(vm: &mut Vm, r: GcRef) -> Result<GcRef, VmError> {
    let items = list_snapshot(vm, r)?;
    let out = list_alloc(vm, items.len());
    let data = list_data(vm, out).expect("freshly allocated list");
    guard(&data).items.extend(items.into_iter().rev());
    Ok(out)
}

/// Snapshot of a List's elements (independent copy of the handles).
pub fn list_snapshot(vm: &mut Vm, r: GcRef) -> Result<Vec<Value>, VmError> {
    let data = list_data(vm, r)?;
    let g = guard(&data);
    Ok(g.items.clone())
}

// ---------------------------------------------------------------------------
// Map
// ---------------------------------------------------------------------------

pub fn map_alloc(vm: &mut Vm, capacity: usize) -> GcRef {
    vm.heap_mut().alloc(if capacity == 0 {
        HeapObject::map()
    } else {
        HeapObject::map_with_capacity(capacity)
    })
}

/// Position of the entry whose key equals `k` within hash bucket `h`, if any.
fn map_find(g: &mut MapData, heap: &Heap, k: &Value, h: i64) -> Option<usize> {
    let bucket = g.buckets.get_mut(&h)?;
    bucket
        .iter()
        .position(|(ek, _)| Vm::values_equal(heap, ek, k))
}

/// Insert or update; returns the previous value or null when absent.
pub fn map_put(vm: &mut Vm, r: GcRef, k: Value, v: Value) -> Result<Value, VmError> {
    reject_mutable_key(vm, &k, "Map.put")?;
    let h = {
        let heap = vm.heap();
        Vm::value_hash(&heap, &k)
    };
    let data = map_data(vm, r)?;
    let mut g = guard(&data);
    let heap = vm.heap();
    let pos = map_find(&mut g, &heap, &k, h);
    match pos {
        Some(p) => {
            let slot = &mut g.buckets.get_mut(&h).unwrap()[p];
            Ok(std::mem::replace(&mut slot.1, v))
        }
        None => {
            g.buckets.entry(h).or_default().push((k, v));
            g.len += 1;
            Ok(Value::Null)
        }
    }
}

/// Lookup; returns the stored value or null when the key is absent.
pub fn map_get(vm: &mut Vm, r: GcRef, k: Value) -> Result<Value, VmError> {
    let h = {
        let heap = vm.heap();
        Vm::value_hash(&heap, &k)
    };
    let data = map_data(vm, r)?;
    let g = guard(&data);
    let heap = vm.heap();
    Ok(g.buckets
        .get(&h)
        .and_then(|bucket| {
            bucket
                .iter()
                .find(|(ek, _)| Vm::values_equal(&heap, ek, &k))
                .map(|(_, v)| *v)
        })
        .unwrap_or(Value::Null))
}

/// Remove the entry for `k`; returns the removed value or null when absent.
pub fn map_remove(vm: &mut Vm, r: GcRef, k: Value) -> Result<Value, VmError> {
    let h = {
        let heap = vm.heap();
        Vm::value_hash(&heap, &k)
    };
    let data = map_data(vm, r)?;
    let mut g = guard(&data);
    let heap = vm.heap();
    let Some(bucket) = g.buckets.get_mut(&h) else {
        return Ok(Value::Null);
    };
    let Some(pos) = bucket
        .iter()
        .position(|(ek, _)| Vm::values_equal(&heap, ek, &k))
    else {
        return Ok(Value::Null);
    };
    let (_, old) = bucket.remove(pos);
    if bucket.is_empty() {
        g.buckets.remove(&h);
    }
    g.len -= 1;
    Ok(old)
}

/// Atomic conditional removal: removes the mapping only when it currently
/// maps `k` to an equal value.
pub fn map_remove_mapping(vm: &mut Vm, r: GcRef, k: Value, v: Value) -> Result<bool, VmError> {
    let h = {
        let heap = vm.heap();
        Vm::value_hash(&heap, &k)
    };
    let data = map_data(vm, r)?;
    let mut g = guard(&data);
    let heap = vm.heap();
    let Some(bucket) = g.buckets.get_mut(&h) else {
        return Ok(false);
    };
    let Some(pos) = bucket
        .iter()
        .position(|(ek, ev)| Vm::values_equal(&heap, ek, &k) && Vm::values_equal(&heap, ev, &v))
    else {
        return Ok(false);
    };
    bucket.remove(pos);
    if bucket.is_empty() {
        g.buckets.remove(&h);
    }
    g.len -= 1;
    Ok(true)
}

/// Insert only when absent; returns the previous value or null when inserted.
pub fn map_put_if_absent(vm: &mut Vm, r: GcRef, k: Value, v: Value) -> Result<Value, VmError> {
    reject_mutable_key(vm, &k, "Map.putIfAbsent")?;
    let h = {
        let heap = vm.heap();
        Vm::value_hash(&heap, &k)
    };
    let data = map_data(vm, r)?;
    let mut g = guard(&data);
    let heap = vm.heap();
    if map_find(&mut g, &heap, &k, h).is_some() {
        return Ok(Value::Null);
    }
    g.buckets.entry(h).or_default().push((k, v));
    g.len += 1;
    Ok(Value::Null)
}
/// Update only when present; returns the old value or null when absent.
pub fn map_replace(vm: &mut Vm, r: GcRef, k: Value, v: Value) -> Result<Value, VmError> {
    let h = {
        let heap = vm.heap();
        Vm::value_hash(&heap, &k)
    };
    let data = map_data(vm, r)?;
    let mut g = guard(&data);
    let heap = vm.heap();
    match map_find(&mut g, &heap, &k, h) {
        Some(p) => {
            let slot = &mut g.buckets.get_mut(&h).unwrap()[p];
            Ok(std::mem::replace(&mut slot.1, v))
        }
        None => Ok(Value::Null),
    }
}

pub fn map_contains_key(vm: &mut Vm, r: GcRef, k: Value) -> Result<bool, VmError> {
    let h = {
        let heap = vm.heap();
        Vm::value_hash(&heap, &k)
    };
    let data = map_data(vm, r)?;
    let g = guard(&data);
    let heap = vm.heap();
    Ok(g.buckets
        .get(&h)
        .is_some_and(|bucket| bucket.iter().any(|(ek, _)| Vm::values_equal(&heap, ek, &k))))
}

pub fn map_contains_value(vm: &mut Vm, r: GcRef, v: Value) -> Result<bool, VmError> {
    let data = map_data(vm, r)?;
    let g = guard(&data);
    let heap = vm.heap();
    Ok(g.buckets
        .values()
        .any(|bucket| bucket.iter().any(|(_, ev)| Vm::values_equal(&heap, ev, &v))))
}

pub fn map_len(vm: &mut Vm, r: GcRef) -> Result<usize, VmError> {
    let data = map_data(vm, r)?;
    let g = guard(&data);
    Ok(g.len)
}

pub fn map_clear(vm: &mut Vm, r: GcRef) -> Result<(), VmError> {
    let data = map_data(vm, r)?;
    let mut g = guard(&data);
    g.buckets.clear();
    g.len = 0;
    Ok(())
}

/// Independent snapshot of the entries (retrieval order is unspecified).
pub fn map_entries_snapshot(vm: &mut Vm, r: GcRef) -> Result<Vec<(Value, Value)>, VmError> {
    let data = map_data(vm, r)?;
    let g = guard(&data);
    let mut out = Vec::with_capacity(g.len);
    for bucket in g.buckets.values() {
        out.extend(bucket.iter().copied());
    }
    Ok(out)
}

/// `keys()`: independent snapshot List of the keys.
pub fn map_keys(vm: &mut Vm, r: GcRef) -> Result<GcRef, VmError> {
    let entries = map_entries_snapshot(vm, r)?;
    let out = list_alloc(vm, entries.len());
    let data = list_data(vm, out).expect("freshly allocated list");
    guard(&data)
        .items
        .extend(entries.into_iter().map(|(k, _)| k));
    Ok(out)
}

/// `values()`: independent snapshot List of the values.
pub fn map_values(vm: &mut Vm, r: GcRef) -> Result<GcRef, VmError> {
    let entries = map_entries_snapshot(vm, r)?;
    let out = list_alloc(vm, entries.len());
    let data = list_data(vm, out).expect("freshly allocated list");
    guard(&data)
        .items
        .extend(entries.into_iter().map(|(_, v)| v));
    Ok(out)
}

/// `putAll`: insert every entry of `other`, updating existing keys.
pub fn map_put_all(vm: &mut Vm, r: GcRef, other: GcRef) -> Result<(), VmError> {
    let entries = map_entries_snapshot(vm, other)?;
    for (k, v) in entries {
        map_put(vm, r, k, v)?;
    }
    Ok(())
}

// ---------------------------------------------------------------------------
// Set
// ---------------------------------------------------------------------------

pub fn set_alloc(vm: &mut Vm, capacity: usize) -> GcRef {
    vm.heap_mut().alloc(if capacity == 0 {
        HeapObject::set()
    } else {
        HeapObject::set_with_capacity(capacity)
    })
}

/// Insert; returns true when the set changed (element was absent).
pub fn set_add(vm: &mut Vm, r: GcRef, v: Value) -> Result<bool, VmError> {
    reject_mutable_key(vm, &v, "Set.add")?;
    let h = {
        let heap = vm.heap();
        Vm::value_hash(&heap, &v)
    };
    let data = set_data(vm, r)?;
    let mut g = guard(&data);
    let heap = vm.heap();
    let bucket = g.buckets.entry(h).or_default();
    if bucket.iter().any(|x| Vm::values_equal(&heap, x, &v)) {
        return Ok(false);
    }
    bucket.push(v);
    g.len += 1;
    Ok(true)
}

/// Remove; returns true when the set changed (element was present).
pub fn set_remove(vm: &mut Vm, r: GcRef, v: Value) -> Result<bool, VmError> {
    let h = {
        let heap = vm.heap();
        Vm::value_hash(&heap, &v)
    };
    let data = set_data(vm, r)?;
    let mut g = guard(&data);
    let heap = vm.heap();
    let Some(bucket) = g.buckets.get_mut(&h) else {
        return Ok(false);
    };
    let Some(pos) = bucket.iter().position(|x| Vm::values_equal(&heap, x, &v)) else {
        return Ok(false);
    };
    bucket.remove(pos);
    if bucket.is_empty() {
        g.buckets.remove(&h);
    }
    g.len -= 1;
    Ok(true)
}

pub fn set_contains(vm: &mut Vm, r: GcRef, v: Value) -> Result<bool, VmError> {
    let h = {
        let heap = vm.heap();
        Vm::value_hash(&heap, &v)
    };
    let data = set_data(vm, r)?;
    let g = guard(&data);
    let heap = vm.heap();
    Ok(g.buckets
        .get(&h)
        .is_some_and(|bucket| bucket.iter().any(|x| Vm::values_equal(&heap, x, &v))))
}

pub fn set_len(vm: &mut Vm, r: GcRef) -> Result<usize, VmError> {
    let data = set_data(vm, r)?;
    let g = guard(&data);
    Ok(g.len)
}

pub fn set_clear(vm: &mut Vm, r: GcRef) -> Result<(), VmError> {
    let data = set_data(vm, r)?;
    let mut g = guard(&data);
    g.buckets.clear();
    g.len = 0;
    Ok(())
}

/// `addAll`; returns true when the set changed.
pub fn set_add_all(vm: &mut Vm, r: GcRef, other: GcRef) -> Result<bool, VmError> {
    let items = set_snapshot(vm, other)?;
    let mut changed = false;
    for v in items {
        if set_add(vm, r, v)? {
            changed = true;
        }
    }
    Ok(changed)
}

/// `containsAll`; true when every element of `other` is a member.
pub fn set_contains_all(vm: &mut Vm, r: GcRef, other: GcRef) -> Result<bool, VmError> {
    let items = set_snapshot(vm, other)?;
    for v in items {
        if !set_contains(vm, r, v)? {
            return Ok(false);
        }
    }
    Ok(true)
}

/// `toList()`: independent snapshot List (order unspecified).
pub fn set_to_list(vm: &mut Vm, r: GcRef) -> Result<GcRef, VmError> {
    let items = set_snapshot(vm, r)?;
    let out = list_alloc(vm, items.len());
    let data = list_data(vm, out).expect("freshly allocated list");
    guard(&data).items.extend(items);
    Ok(out)
}

/// Snapshot of a Set's elements (independent copy of the handles).
pub fn set_snapshot(vm: &mut Vm, r: GcRef) -> Result<Vec<Value>, VmError> {
    let data = set_data(vm, r)?;
    let g = guard(&data);
    let mut out = Vec::with_capacity(g.len);
    for bucket in g.buckets.values() {
        out.extend(bucket.iter().copied());
    }
    Ok(out)
}

// ---------------------------------------------------------------------------
// Stack
// ---------------------------------------------------------------------------

pub fn stack_alloc(vm: &mut Vm, capacity: usize) -> GcRef {
    vm.heap_mut().alloc(if capacity == 0 {
        HeapObject::stack()
    } else {
        HeapObject::stack_with_capacity(capacity)
    })
}

/// LIFO push (append at the back).
pub fn stack_push(vm: &mut Vm, r: GcRef, v: Value) -> Result<(), VmError> {
    let data = stack_data(vm, r)?;
    guard(&data).items.push_back(v);
    Ok(())
}

/// LIFO pop; runtime collection-underflow error when empty.
pub fn stack_pop(vm: &mut Vm, r: GcRef) -> Result<Value, VmError> {
    let data = stack_data(vm, r)?;
    let mut g = guard(&data);
    g.items
        .pop_back()
        .ok_or_else(|| vm.err_at("stack underflow: pop on empty Stack"))
}

/// Top element, or null when empty.
pub fn stack_peek(vm: &mut Vm, r: GcRef) -> Result<Value, VmError> {
    let data = stack_data(vm, r)?;
    let g = guard(&data);
    Ok(g.items.back().copied().unwrap_or(Value::Null))
}

/// Pop without error; null when empty.
pub fn stack_poll(vm: &mut Vm, r: GcRef) -> Result<Value, VmError> {
    let data = stack_data(vm, r)?;
    let mut g = guard(&data);
    Ok(g.items.pop_back().unwrap_or(Value::Null))
}

pub fn stack_add_first(vm: &mut Vm, r: GcRef, v: Value) -> Result<(), VmError> {
    let data = stack_data(vm, r)?;
    guard(&data).items.push_front(v);
    Ok(())
}

pub fn stack_add_last(vm: &mut Vm, r: GcRef, v: Value) -> Result<(), VmError> {
    stack_push(vm, r, v)
}

/// Remove the front element; underflow error when empty.
pub fn stack_remove_first(vm: &mut Vm, r: GcRef) -> Result<Value, VmError> {
    let data = stack_data(vm, r)?;
    let mut g = guard(&data);
    g.items
        .pop_front()
        .ok_or_else(|| vm.err_at("stack underflow: removeFirst on empty Stack"))
}

/// Remove the back element; underflow error when empty.
pub fn stack_remove_last(vm: &mut Vm, r: GcRef) -> Result<Value, VmError> {
    let data = stack_data(vm, r)?;
    let mut g = guard(&data);
    g.items
        .pop_back()
        .ok_or_else(|| vm.err_at("stack underflow: removeLast on empty Stack"))
}

pub fn stack_peek_first(vm: &mut Vm, r: GcRef) -> Result<Value, VmError> {
    let data = stack_data(vm, r)?;
    let g = guard(&data);
    Ok(g.items.front().copied().unwrap_or(Value::Null))
}

pub fn stack_peek_last(vm: &mut Vm, r: GcRef) -> Result<Value, VmError> {
    stack_peek(vm, r)
}

pub fn stack_get(vm: &mut Vm, r: GcRef, idx: i64) -> Result<Value, VmError> {
    let data = stack_data(vm, r)?;
    let g = guard(&data);
    usize::try_from(idx)
        .ok()
        .and_then(|i| g.items.get(i))
        .copied()
        .ok_or_else(|| vm.err_at("stack index out of range"))
}

pub fn stack_len(vm: &mut Vm, r: GcRef) -> Result<usize, VmError> {
    let data = stack_data(vm, r)?;
    let g = guard(&data);
    Ok(g.items.len())
}
