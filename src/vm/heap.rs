//! Managed heap: atomic reference counting with bounded cycle collection.
//!
//! Collections (`List`, `Map`, `Stack`, `Set`) store their payload in a
//! per-collection `Arc<Mutex<_>>`. The heap lock protects object slots and
//! metadata; each collection's own mutex protects its elements. This lets
//! unrelated collections progress concurrently while every operation on one
//! collection stays linearizable under its own lock.
//!
//! Lock-ordering rule (single, normative):
//! - The heap lock is the outermost lock. Code that holds it may briefly take
//!   collection locks (GC marking, structural equality/hashing of collection
//!   values, JSON serialization).
//! - Collection operations may take the heap lock *while holding their
//!   collection lock*, solely to dereference/compare element values
//!   (equality, hashing, comparison). They never hold the heap lock while
//!   acquiring another collection lock.
//! - Cycle-collector graph snapshots run only when the program has a single
//!   active thread, so the heap-outer and collection-outer orders never
//!   interleave on live threads. Atomic retain/release remains thread-safe.

use std::collections::{HashMap, HashSet, VecDeque};
use std::io::Read;
use std::process::{Child, ChildStdin};
use std::sync::atomic::{AtomicU8, AtomicUsize, Ordering};
use std::sync::{Arc, Condvar, Mutex};
use std::thread::JoinHandle;

use super::value::Value;

/// A heap handle.
pub type GcRef = u32;

const LIVE: u8 = 0;
const RECLAIMING: u8 = 1;

/// Runtime counters used by tests, diagnostics, and the benchmark harness.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct HeapStats {
    pub allocations: usize,
    pub frees: usize,
    pub arc_frees: usize,
    pub cycle_frees: usize,
    pub cycle_candidates: usize,
    pub cycle_runs: usize,
    pub live_objects: usize,
    pub shard_count: usize,
}

#[derive(Debug, Default)]
struct Shard {
    free: Vec<GcRef>,
    allocations: usize,
    frees: usize,
}

/// A slot is stable while the handle is live. The atomic count is deliberately
/// independent of the heap mutex: the last-release decision remains safe if a
/// future allocator lets release operations arrive from different threads.
struct HeapSlot {
    object: HeapObject,
    strong: AtomicUsize,
    state: AtomicU8,
    shard: usize,
}

// ---------------------------------------------------------------------------
// Per-collection state (each guarded by its own mutex)
// ---------------------------------------------------------------------------

/// Contiguous vector storage: amortized O(1) append, O(1) indexed access.
#[derive(Debug, Default)]
pub struct ListData {
    pub items: Vec<Value>,
}

/// Hash-indexed entries: `buckets` maps a value hash to the entries whose
/// key hashes to it; equality is verified at lookup time so hash collisions
/// stay correct. Expected O(1) get/put/remove/contains. Retrieval order is
/// unspecified (hash layout); programs must not depend on it.
#[derive(Debug)]
pub struct MapData {
    pub buckets: HashMap<i64, Vec<(Value, Value)>, FnvBuildHasher>,
    pub len: usize,
}

impl Default for MapData {
    fn default() -> Self {
        MapData {
            buckets: HashMap::with_hasher(FnvBuildHasher),
            len: 0,
        }
    }
}

impl MapData {
    pub fn with_capacity(capacity: usize) -> Self {
        let mut d = Self::default();
        d.buckets.reserve(capacity);
        d
    }

    /// Snapshot of all entries (handle copies; retrieval order unspecified).
    pub fn entries(&self) -> Vec<(Value, Value)> {
        let mut out = Vec::with_capacity(self.len);
        for bucket in self.buckets.values() {
            out.extend(bucket.iter().copied());
        }
        out
    }
}

/// Contiguous deque storage: O(1) push/pop/peek at both ends.
#[derive(Debug, Default)]
pub struct StackData {
    pub items: VecDeque<Value>,
}

impl StackData {
    pub fn with_capacity(capacity: usize) -> Self {
        StackData {
            items: VecDeque::with_capacity(capacity),
        }
    }
}

/// Hash-indexed unique elements. Membership is unordered; expected O(1)
/// add/remove/contains.
#[derive(Debug)]
pub struct SetData {
    pub buckets: HashMap<i64, Vec<Value>, FnvBuildHasher>,
    pub len: usize,
}

impl Default for SetData {
    fn default() -> Self {
        SetData {
            buckets: HashMap::with_hasher(FnvBuildHasher),
            len: 0,
        }
    }
}

impl SetData {
    pub fn with_capacity(capacity: usize) -> Self {
        let mut d = Self::default();
        d.buckets.reserve(capacity);
        d
    }

    /// Snapshot of all elements (handle copies; order unspecified).
    pub fn items(&self) -> Vec<Value> {
        let mut out = Vec::with_capacity(self.len);
        for bucket in self.buckets.values() {
            out.extend(bucket.iter().copied());
        }
        out
    }
}

/// Deterministic FNV-1a hasher so bucket layout (and therefore retrieval
/// order) is stable across runs.
pub struct FnvBuildHasher;

impl std::hash::BuildHasher for FnvBuildHasher {
    type Hasher = FnvHasher;
    fn build_hasher(&self) -> FnvHasher {
        FnvHasher(0xcbf2_9ce4_8422_2325u64)
    }
}

#[derive(Default)]
pub struct FnvHasher(u64);

impl std::hash::Hasher for FnvHasher {
    fn finish(&self) -> u64 {
        self.0
    }
    fn write(&mut self, bytes: &[u8]) {
        for b in bytes {
            self.0 ^= u64::from(*b);
            self.0 = self.0.wrapping_mul(0x0000_0100_0000_01b3);
        }
    }
}

#[derive(Debug, Default)]
struct ProcessOutputState {
    bytes: VecDeque<u8>,
    eof: bool,
}

/// Buffered output from a child process. A pump thread fills the buffer so a
/// child cannot block on a full OS pipe while the Solvik program is waiting.
#[derive(Debug, Default)]
pub struct ProcessOutput {
    state: Mutex<ProcessOutputState>,
    ready: Condvar,
}

impl ProcessOutput {
    pub fn pump(self: Arc<Self>, mut reader: impl Read) {
        let mut buf = [0u8; 8192];
        loop {
            match reader.read(&mut buf) {
                Ok(0) | Err(_) => break,
                Ok(n) => {
                    let mut state = self.state.lock().unwrap_or_else(|e| e.into_inner());
                    state.bytes.extend(&buf[..n]);
                    self.ready.notify_all();
                }
            }
        }
        let mut state = self.state.lock().unwrap_or_else(|e| e.into_inner());
        state.eof = true;
        self.ready.notify_all();
    }

    pub fn read_line(&self) -> Option<String> {
        let mut state = self.state.lock().unwrap_or_else(|e| e.into_inner());
        loop {
            if let Some(pos) = state.bytes.iter().position(|b| *b == b'\n') {
                let mut line: Vec<u8> = state.bytes.drain(..=pos).collect();
                line.pop();
                if line.last() == Some(&b'\r') {
                    line.pop();
                }
                return Some(String::from_utf8_lossy(&line).into_owned());
            }
            if state.eof {
                if state.bytes.is_empty() {
                    return None;
                }
                let line: Vec<u8> = state.bytes.drain(..).collect();
                return Some(String::from_utf8_lossy(&line).into_owned());
            }
            state = self.ready.wait(state).unwrap_or_else(|e| e.into_inner());
        }
    }

    pub fn read_all(&self) -> String {
        let mut state = self.state.lock().unwrap_or_else(|e| e.into_inner());
        while !state.eof {
            state = self.ready.wait(state).unwrap_or_else(|e| e.into_inner());
        }
        let bytes: Vec<u8> = state.bytes.drain(..).collect();
        String::from_utf8_lossy(&bytes).into_owned()
    }
}

/// Everything that lives on the managed heap.
#[derive(Debug)]
pub enum HeapObject {
    Instance {
        class: u16,
        fields: Vec<Value>,
    },
    String {
        text: String,
    },
    List {
        data: Arc<Mutex<ListData>>,
    },
    Map {
        data: Arc<Mutex<MapData>>,
    },
    Stack {
        data: Arc<Mutex<StackData>>,
    },
    Set {
        data: Arc<Mutex<SetData>>,
    },
    Enum {
        enum_id: u16,
        index: u8,
        payload: Option<Value>,
    },
    Exception {
        /// Native kind tag (`native_kind::EXCEPTION`).
        kind: u8,
        message: String,
    },
    /// Arbitrary-precision integer.
    BigInteger {
        value: crate::bignum::BigInt,
    },
    /// Arbitrary-precision decimal.
    BigDecimal {
        value: crate::bignum::Dec,
    },
    Thread {
        /// The Runnable object to execute.
        runnable: Option<GcRef>,
        handle: Option<JoinHandle<()>>,
        done: bool,
    },
    Mutex {
        inner: Arc<(Mutex<bool>, Condvar)>,
    },
    Semaphore {
        inner: Arc<(Mutex<i32>, Condvar)>,
        max: i32,
    },
    Process {
        child: Option<Child>,
        exit_code: Option<i32>,
        /// Command line (used by start()).
        cmd: Option<String>,
        argv: Vec<String>,
        stdin: Option<ChildStdin>,
        stdout: Option<Arc<ProcessOutput>>,
        stderr: Option<Arc<ProcessOutput>>,
    },
    ProcessStream {
        process: GcRef,
        kind: u8,
    },
    Regex {
        re: regex::Regex,
    },
    /// Standard stream handle (kind: 0=stdin 1=stdout 2=stderr).
    Stream {
        kind: u8,
    },
}

impl HeapObject {
    /// Mutable access to an instance's fields (None for non-instances).
    #[cfg(test)]
    fn fields_mut(&mut self) -> Option<&mut Vec<Value>> {
        match self {
            HeapObject::Instance { fields, .. } => Some(fields),
            _ => None,
        }
    }

    /// Fresh empty collections with fresh per-collection locks.
    pub fn list() -> Self {
        HeapObject::List {
            data: Arc::new(Mutex::new(ListData::default())),
        }
    }

    pub fn list_with_capacity(capacity: usize) -> Self {
        HeapObject::List {
            data: Arc::new(Mutex::new(ListData {
                items: Vec::with_capacity(capacity),
            })),
        }
    }

    pub fn map() -> Self {
        HeapObject::Map {
            data: Arc::new(Mutex::new(MapData::default())),
        }
    }

    pub fn map_with_capacity(capacity: usize) -> Self {
        HeapObject::Map {
            data: Arc::new(Mutex::new(MapData::with_capacity(capacity))),
        }
    }

    pub fn stack() -> Self {
        HeapObject::Stack {
            data: Arc::new(Mutex::new(StackData::default())),
        }
    }

    pub fn stack_with_capacity(capacity: usize) -> Self {
        HeapObject::Stack {
            data: Arc::new(Mutex::new(StackData::with_capacity(capacity))),
        }
    }

    pub fn set() -> Self {
        HeapObject::Set {
            data: Arc::new(Mutex::new(SetData::default())),
        }
    }

    pub fn set_with_capacity(capacity: usize) -> Self {
        HeapObject::Set {
            data: Arc::new(Mutex::new(SetData::with_capacity(capacity))),
        }
    }

    /// Snapshot of all strong references stored by this object. Collection
    /// payloads are independently locked, so the snapshot is deliberately
    /// short-lived and never exposes a collection guard to the caller.
    fn references(&self) -> Vec<Value> {
        match self {
            HeapObject::Instance { fields, .. } => fields.clone(),
            HeapObject::List { data } => {
                data.lock().unwrap_or_else(|e| e.into_inner()).items.clone()
            }
            HeapObject::Stack { data } => data
                .lock()
                .unwrap_or_else(|e| e.into_inner())
                .items
                .iter()
                .copied()
                .collect(),
            HeapObject::Set { data } => data.lock().unwrap_or_else(|e| e.into_inner()).items(),
            HeapObject::Map { data } => data
                .lock()
                .unwrap_or_else(|e| e.into_inner())
                .entries()
                .into_iter()
                .flat_map(|(k, v)| [k, v])
                .collect(),
            HeapObject::Enum {
                payload: Some(p), ..
            } => vec![*p],
            HeapObject::Thread {
                runnable: Some(r), ..
            } => vec![Value::Object(*r)],
            HeapObject::ProcessStream { process, .. } => vec![Value::Object(*process)],
            _ => Vec::new(),
        }
    }

    /// Values reachable from this object (for the compatibility tracing
    /// collector). Newly marked handles are appended to `queue` for worklist
    /// traversal.
    fn mark_into(&self, marked: &mut HashSet<GcRef>, queue: &mut Vec<GcRef>) {
        for value in self.references() {
            mark_value(&value, marked, queue);
        }
    }

    /// Display form used by `toString` and diagnostics.
    pub fn string(&self) -> String {
        match self {
            HeapObject::String { text } => text.clone(),
            HeapObject::List { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                format!(
                    "[{}]",
                    g.items
                        .iter()
                        .map(value_repr)
                        .collect::<Vec<_>>()
                        .join(", ")
                )
            }
            HeapObject::Map { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                let mut pairs: Vec<String> = Vec::with_capacity(g.len);
                for bucket in g.buckets.values() {
                    for (k, v) in bucket {
                        pairs.push(format!("{}: {}", value_repr(k), value_repr(v)));
                    }
                }
                format!("{{ {} }}", pairs.join(", "))
            }
            HeapObject::Stack { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                format!(
                    "Stack[{}]",
                    g.items
                        .iter()
                        .map(value_repr)
                        .collect::<Vec<_>>()
                        .join(", ")
                )
            }
            HeapObject::Set { data } => {
                let g = data.lock().unwrap_or_else(|e| e.into_inner());
                let mut reprs: Vec<String> = Vec::with_capacity(g.len);
                for bucket in g.buckets.values() {
                    for v in bucket {
                        reprs.push(value_repr(v));
                    }
                }
                format!("Set[{}]", reprs.join(", "))
            }
            HeapObject::Enum { index, .. } => format!("Enum#{}", index),
            HeapObject::Exception { message, .. } => message.clone(),
            HeapObject::BigInteger { value } => value.to_string(),
            HeapObject::BigDecimal { value } => value.to_string(),
            HeapObject::Instance { class, .. } => format!("<instance #{}>", class),
            HeapObject::Thread { done, .. } => {
                if *done {
                    "Thread(done)".to_string()
                } else {
                    "Thread(running)".to_string()
                }
            }
            HeapObject::Mutex { .. } => "Mutex".to_string(),
            HeapObject::Semaphore { .. } => "Semaphore".to_string(),
            HeapObject::Process { exit_code, cmd, .. } => match exit_code {
                Some(c) => format!("Process({} exit={})", cmd.as_deref().unwrap_or("?"), c),
                None => format!("Process({} running)", cmd.as_deref().unwrap_or("?")),
            },
            HeapObject::ProcessStream { kind, .. } => match kind {
                0 => "process.stdin".to_string(),
                1 => "process.stdout".to_string(),
                _ => "process.stderr".to_string(),
            },
            HeapObject::Regex { re } => format!("Regex({})", re.as_str()),
            HeapObject::Stream { kind } => match kind {
                0 => "stdin".to_string(),
                1 => "stdout".to_string(),
                _ => "stderr".to_string(),
            },
        }
    }
}

fn value_repr(v: &Value) -> String {
    match v {
        Value::Null => "null".to_string(),
        Value::Boolean(b) => b.to_string(),
        Value::Byte(i) => i.to_string(),
        Value::Short(i) => i.to_string(),
        Value::Integer(i) => i.to_string(),
        Value::Long(i) => i.to_string(),
        Value::Float(f) => f.to_string(),
        Value::Double(f) => f.to_string(),
        Value::Char(c) => format!("'{}'", c),
        Value::Object(_) => "<object>".to_string(),
    }
}

fn mark_value(v: &Value, marked: &mut HashSet<GcRef>, queue: &mut Vec<GcRef>) {
    if let Value::Object(r) = v {
        if marked.insert(*r) {
            queue.push(*r);
        }
    }
}

/// The managed heap.
pub struct Heap {
    objects: Vec<Option<HeapSlot>>,
    shards: Vec<Shard>,
    allocs_since_maintenance: usize,
    candidates: VecDeque<GcRef>,
    stats: HeapStats,
    /// Per-class static field storage (one slot vector per class), shared
    /// by all instances and all threads. Static slots are GC roots.
    pub statics: Vec<Vec<Value>>,
}

impl Default for Heap {
    fn default() -> Self {
        Self::new()
    }
}

impl Heap {
    pub fn new() -> Self {
        let shard_count = std::env::var("SOLVIK_HEAP_SHARDS")
            .ok()
            .and_then(|s| s.parse::<usize>().ok())
            .filter(|n| *n > 0)
            .unwrap_or_else(|| {
                std::thread::available_parallelism()
                    .map(|n| n.get().min(8))
                    .unwrap_or(1)
            });
        Self::with_shards(shard_count)
    }

    /// Construct a heap with an explicit allocator-shard count. This is used
    /// by deterministic tests and benchmarks; a count of one is always valid.
    pub fn with_shards(shard_count: usize) -> Self {
        let shard_count = shard_count.max(1);
        Heap {
            objects: vec![None],
            shards: (0..shard_count).map(|_| Shard::default()).collect(),
            allocs_since_maintenance: 0,
            candidates: VecDeque::new(),
            stats: HeapStats {
                shard_count,
                ..HeapStats::default()
            },
            statics: vec![],
        }
    }

    /// Allocate a new object; the returned handle owns one strong reference.
    /// The allocation is assigned to a preferred shard selected from the
    /// current thread. Shards are allocator bookkeeping only; references can
    /// cross them freely.
    pub fn alloc(&mut self, obj: HeapObject) -> GcRef {
        let shard = self.preferred_shard();
        let ref_ = if let Some(ref_) = self.shards[shard].free.pop() {
            self.objects[ref_ as usize] = Some(HeapSlot {
                object: obj,
                strong: AtomicUsize::new(1),
                state: AtomicU8::new(LIVE),
                shard,
            });
            ref_
        } else {
            let ref_ = self.objects.len() as GcRef;
            self.objects.push(Some(HeapSlot {
                object: obj,
                strong: AtomicUsize::new(1),
                state: AtomicU8::new(LIVE),
                shard,
            }));
            ref_
        };
        // The newly returned strong reference is the caller's ownership. The
        // object's stored edges are additional ownerships.
        let children = self
            .objects
            .get(ref_ as usize)
            .and_then(|slot| slot.as_ref())
            .map(|slot| slot.object.references())
            .unwrap_or_default();
        for value in children {
            self.retain_value(value);
        }
        self.shards[shard].allocations += 1;
        self.stats.allocations += 1;
        self.stats.live_objects += 1;
        self.allocs_since_maintenance += 1;
        ref_
    }

    pub fn get(&self, ref_: GcRef) -> Option<&HeapObject> {
        self.objects
            .get(ref_ as usize)?
            .as_ref()
            .filter(|slot| slot.state.load(Ordering::Acquire) == LIVE)
            .map(|slot| &slot.object)
    }

    pub fn get_mut(&mut self, ref_: GcRef) -> Option<&mut HeapObject> {
        self.objects
            .get_mut(ref_ as usize)?
            .as_mut()
            .filter(|slot| slot.state.load(Ordering::Acquire) == LIVE)
            .map(|slot| &mut slot.object)
    }

    /// Increment a strong reference. Returns false for an invalid or already
    /// reclaiming handle; a caller must never revive such a handle.
    pub fn retain(&self, ref_: GcRef) -> bool {
        let Some(Some(slot)) = self.objects.get(ref_ as usize) else {
            return false;
        };
        if slot.state.load(Ordering::Acquire) != LIVE {
            return false;
        }
        let result = slot
            .strong
            .fetch_update(Ordering::AcqRel, Ordering::Acquire, |count| {
                count.checked_add(1)
            });
        if result.is_err() {
            panic!("Solvik reference-count overflow for handle {}", ref_);
        }
        true
    }

    pub fn retain_value(&self, value: Value) {
        if let Value::Object(ref_) = value {
            if ref_ != 0 {
                assert!(self.retain(ref_), "retaining an invalid Solvik handle");
            }
        }
    }

    /// Release one strong reference. A zero transition takes the sole
    /// reclamation winner and drains child releases iteratively, so a long
    /// object chain cannot overflow the native stack.
    pub fn release(&mut self, ref_: GcRef) {
        let Some(Some(slot)) = self.objects.get(ref_ as usize) else {
            return;
        };
        let previous = slot
            .strong
            .fetch_update(Ordering::AcqRel, Ordering::Acquire, |count| {
                if count == 0 {
                    None
                } else {
                    Some(count - 1)
                }
            });
        let Ok(previous) = previous else {
            debug_assert!(
                false,
                "Solvik reference-count underflow for handle {}",
                ref_
            );
            return;
        };
        if previous == 1 {
            self.candidates.push_back(ref_);
            self.reclaim_zero_worklist(false);
        } else {
            // Any object whose count stays nonzero may be part of a cycle.
            // Candidate processing is bounded and deferred to maintenance.
            self.candidates.push_back(ref_);
        }
    }

    pub fn release_value(&mut self, value: Value) {
        if let Value::Object(ref_) = value {
            if ref_ == 0 {
                return;
            }
            self.release(ref_);
        }
    }

    /// Force-free a slot for the legacy explicit tracing test hook. Normal VM
    /// code uses `release`; this method is intentionally not an exposed
    /// language operation.
    pub fn free(&mut self, ref_: GcRef) {
        if ref_ == 0 {
            return;
        }
        if let Some(slot) = self.take_slot(ref_) {
            self.shards[slot.shard].free.push(ref_);
            self.shards[slot.shard].frees += 1;
            self.stats.frees += 1;
            self.stats.live_objects = self.stats.live_objects.saturating_sub(1);
        }
    }

    fn take_slot(&mut self, ref_: GcRef) -> Option<HeapSlot> {
        let slot = self.objects.get_mut(ref_ as usize)?.take()?;
        slot.state.store(RECLAIMING, Ordering::Release);
        Some(slot)
    }

    fn reclaim_zero_worklist(&mut self, cycle: bool) {
        let mut work = VecDeque::new();
        while let Some(ref_) = self.candidates.pop_front() {
            if self
                .objects
                .get(ref_ as usize)
                .and_then(|slot| slot.as_ref())
                .is_some_and(|slot| slot.strong.load(Ordering::Acquire) == 0)
            {
                work.push_back(ref_);
            }
        }
        while let Some(ref_) = work.pop_front() {
            let Some(slot) = self.take_slot(ref_) else {
                continue;
            };
            let children = slot.object.references();
            self.shards[slot.shard].free.push(ref_);
            self.shards[slot.shard].frees += 1;
            self.stats.frees += 1;
            self.stats.arc_frees += usize::from(!cycle);
            self.stats.cycle_frees += usize::from(cycle);
            self.stats.live_objects = self.stats.live_objects.saturating_sub(1);
            for value in children {
                let Value::Object(child) = value else {
                    continue;
                };
                let Some(Some(child_slot)) = self.objects.get(child as usize) else {
                    continue;
                };
                // Trial deletion marks every member of an unreachable cycle
                // zero before the worklist runs. Internal cycle edges are
                // already accounted for by that batch and must not underflow
                // when the individual slots are removed.
                if child_slot.strong.load(Ordering::Acquire) == 0 {
                    continue;
                }
                let Ok(previous) =
                    child_slot
                        .strong
                        .fetch_update(Ordering::AcqRel, Ordering::Acquire, |count| {
                            count.checked_sub(1)
                        })
                else {
                    debug_assert!(false, "child reference-count underflow");
                    continue;
                };
                if previous == 1 {
                    work.push_back(child);
                }
            }
        }
    }

    fn preferred_shard(&self) -> usize {
        use std::hash::{Hash, Hasher};
        let mut hasher = std::collections::hash_map::DefaultHasher::new();
        std::thread::current().id().hash(&mut hasher);
        (hasher.finish() as usize) % self.shards.len().max(1)
    }

    /// Compatibility tracing hook used by the existing unit tests and for
    /// explicit debug diagnostics. Runtime maintenance uses ARC and the
    /// cycle collector below; ordinary VM execution does not scan the heap.
    pub fn collect(&mut self, roots: &[Value]) {
        // Worklist traversal over the object graph (linear in the number of
        // reachable objects).
        let mut marked: HashSet<GcRef> = HashSet::new();
        let mut queue: Vec<GcRef> = Vec::new();
        for r in roots {
            mark_value(r, &mut marked, &mut queue);
        }
        while let Some(r) = queue.pop() {
            if let Some(obj) = self.get(r) {
                obj.mark_into(&mut marked, &mut queue);
            }
        }
        // Sweep.
        let mut dead = Vec::new();
        for (i, slot) in self.objects.iter().enumerate() {
            if slot.is_some() && !marked.contains(&(i as GcRef)) {
                dead.push(i as GcRef);
            }
        }
        for r in dead {
            self.free(r);
        }
        self.allocs_since_maintenance = 0;
    }

    /// Run bounded trial-deletion cycle collection. The heap mutex is the
    /// coordination point for this initial implementation; VM callers invoke
    /// it only when no other Solvik thread is executing. The logical graph is
    /// not partitioned by allocator shard.
    pub fn collect_cycles(&mut self, budget: usize) -> usize {
        let mut candidates = HashSet::new();
        while candidates.len() < budget {
            let Some(r) = self.candidates.pop_front() else {
                break;
            };
            if self.get(r).is_some() {
                candidates.insert(r);
            }
        }
        if candidates.is_empty() {
            return 0;
        }
        self.stats.cycle_runs += 1;
        let mut graph = candidates.clone();
        let mut queue: VecDeque<GcRef> = candidates.iter().copied().collect();
        while let Some(r) = queue.pop_front() {
            let children = self.get(r).map(HeapObject::references).unwrap_or_default();
            for child in children.into_iter().filter_map(Value::as_object) {
                if graph.insert(child) {
                    queue.push_back(child);
                }
            }
        }
        let mut trial: HashMap<GcRef, usize> = graph
            .iter()
            .filter_map(|r| {
                self.objects
                    .get(*r as usize)
                    .and_then(|slot| slot.as_ref())
                    .map(|slot| (*r, slot.strong.load(Ordering::Acquire)))
            })
            .collect();
        for r in &graph {
            for child in self
                .get(*r)
                .map(HeapObject::references)
                .unwrap_or_default()
                .into_iter()
                .filter_map(Value::as_object)
            {
                if let Some(count) = trial.get_mut(&child) {
                    *count = count.saturating_sub(1);
                }
            }
        }
        // A candidate with an external owner keeps its entire reachable
        // subgraph alive. Trial counts identify the externally-rooted entry
        // points; they are not a per-node liveness decision by themselves.
        let mut live = HashSet::new();
        let mut live_queue: VecDeque<GcRef> = trial
            .iter()
            .filter_map(|(r, count)| (*count > 0).then_some(*r))
            .collect();
        while let Some(r) = live_queue.pop_front() {
            if !live.insert(r) {
                continue;
            }
            for child in self
                .get(r)
                .map(HeapObject::references)
                .unwrap_or_default()
                .into_iter()
                .filter_map(Value::as_object)
            {
                if graph.contains(&child) && !live.contains(&child) {
                    live_queue.push_back(child);
                }
            }
        }
        let dead: HashSet<GcRef> = graph.difference(&live).copied().collect();
        self.stats.cycle_candidates += graph.len();
        for r in &dead {
            if let Some(Some(slot)) = self.objects.get(*r as usize) {
                slot.strong.store(0, Ordering::Release);
            }
            self.candidates.push_back(*r);
        }
        self.reclaim_zero_worklist(true);
        self.allocs_since_maintenance = 0;
        dead.len()
    }

    /// True when bounded maintenance work is due.
    pub fn maintenance_due(&self) -> bool {
        self.allocs_since_maintenance > 4096 || self.candidates.len() > 256
    }

    /// Backwards-compatible name for callers that used the tracing GC hint.
    pub fn gc_due(&self) -> bool {
        self.maintenance_due()
    }

    /// Number of live objects.
    #[allow(dead_code)]
    pub fn live_count(&self) -> usize {
        self.objects.iter().filter(|s| s.is_some()).count()
    }

    pub fn stats(&self) -> HeapStats {
        HeapStats {
            live_objects: self.live_count(),
            ..self.stats
        }
    }

    pub fn shard_count(&self) -> usize {
        self.shards.len()
    }

    /// Display string for an object handle (for `toString`).
    pub fn object_string(&self, ref_: GcRef) -> String {
        match self.get(ref_) {
            Some(o) => o.string(),
            None => "<freed>".to_string(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn gc_collects_unreachable_cycle() {
        let mut heap = Heap::new();
        // Two instances that reference each other (a cycle), plus one live
        // root that does not reference the cycle.
        let a = heap.alloc(HeapObject::Instance {
            class: 0,
            fields: vec![Value::Null],
        });
        let b = heap.alloc(HeapObject::Instance {
            class: 0,
            fields: vec![Value::Null],
        });
        heap.get_mut(a).unwrap().fields_mut().unwrap()[0] = Value::Object(b);
        heap.get_mut(b).unwrap().fields_mut().unwrap()[0] = Value::Object(a);
        let c = heap.alloc(HeapObject::Instance {
            class: 0,
            fields: vec![],
        });

        // Only C is rooted; the A<->B cycle is unreachable.
        heap.collect(&[Value::Object(c)]);

        assert!(heap.get(a).is_none(), "cycle member A should be collected");
        assert!(heap.get(b).is_none(), "cycle member B should be collected");
        assert!(heap.get(c).is_some(), "rooted object C must survive");
    }

    #[test]
    fn gc_preserves_reachable_objects() {
        let mut heap = Heap::new();
        let a = heap.alloc(HeapObject::Instance {
            class: 0,
            fields: vec![Value::Null],
        });
        let b = heap.alloc(HeapObject::Instance {
            class: 0,
            fields: vec![Value::Null],
        });
        heap.get_mut(a).unwrap().fields_mut().unwrap()[0] = Value::Object(b);

        // Root A; B is reachable through A.
        heap.collect(&[Value::Object(a)]);

        assert!(heap.get(a).is_some());
        assert!(
            heap.get(b).is_some(),
            "B is reachable via A and must survive"
        );
    }

    #[test]
    fn arc_reclaims_an_unreachable_cycle() {
        let mut heap = Heap::new();
        let a = heap.alloc(HeapObject::Instance {
            class: 0,
            fields: vec![Value::Null],
        });
        let b = heap.alloc(HeapObject::Instance {
            class: 0,
            fields: vec![Value::Null],
        });
        heap.get_mut(a).unwrap().fields_mut().unwrap()[0] = Value::Object(b);
        heap.get_mut(b).unwrap().fields_mut().unwrap()[0] = Value::Object(a);
        heap.retain_value(Value::Object(a));
        heap.retain_value(Value::Object(b));
        // The two field stores above are the graph edges; release the two
        // external construction handles and leave only the cycle.
        heap.release(a);
        heap.release(b);
        assert_eq!(heap.collect_cycles(16), 2);
        assert!(heap.get(a).is_none());
        assert!(heap.get(b).is_none());
    }

    #[test]
    fn arc_release_worklist_handles_a_deep_chain() {
        let mut heap = Heap::new();
        let mut refs = Vec::with_capacity(20_000);
        for _ in 0..20_000 {
            refs.push(heap.alloc(HeapObject::Instance {
                class: 0,
                fields: vec![Value::Null],
            }));
        }
        for pair in refs.windows(2) {
            heap.get_mut(pair[0]).unwrap().fields_mut().unwrap()[0] = Value::Object(pair[1]);
            heap.retain(pair[1]);
        }
        for r in refs.iter().copied() {
            heap.release(r);
        }
        assert_eq!(heap.live_count(), 0);
        assert!(heap.stats().arc_frees >= 20_000);
    }
}
