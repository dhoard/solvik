//! Managed heap: handle-based allocation with tracing mark-and-sweep GC.

use std::collections::{HashSet, VecDeque};
use std::io::Read;
use std::process::{Child, ChildStdin};
use std::sync::{Arc, Condvar, Mutex};
use std::thread::JoinHandle;

use super::value::Value;

/// A heap handle.
pub type GcRef = u32;

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
        items: Vec<Value>,
    },
    Map {
        entries: Vec<(Value, Value)>,
    },
    Stack {
        items: Vec<Value>,
    },
    Set {
        items: Vec<Value>,
    },
    Enum {
        enum_id: u16,
        index: u8,
        payload: Option<Value>,
    },
    Exception {
        message: String,
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

    /// Values reachable from this object (for GC marking).
    fn mark_into(&self, marked: &mut HashSet<GcRef>) {
        match self {
            HeapObject::Instance { fields, .. } => {
                for v in fields {
                    mark_value(v, marked);
                }
            }
            HeapObject::List { items }
            | HeapObject::Stack { items }
            | HeapObject::Set { items } => {
                for v in items {
                    mark_value(v, marked);
                }
            }
            HeapObject::Map { entries } => {
                for (k, v) in entries {
                    mark_value(k, marked);
                    mark_value(v, marked);
                }
            }
            HeapObject::Enum {
                payload: Some(p), ..
            } => mark_value(p, marked),
            HeapObject::Thread {
                runnable: Some(r), ..
            } => {
                mark_value(&Value::Object(*r), marked);
            }
            HeapObject::ProcessStream { process, .. } => {
                mark_value(&Value::Object(*process), marked);
            }
            _ => {}
        }
    }

    /// Display form used by `toString` and diagnostics.
    pub fn string(&self) -> String {
        match self {
            HeapObject::String { text } => text.clone(),
            HeapObject::List { items } => format!(
                "[{}]",
                items.iter().map(value_repr).collect::<Vec<_>>().join(", ")
            ),
            HeapObject::Map { entries } => format!(
                "{{ {} }}",
                entries
                    .iter()
                    .map(|(k, v)| format!("{}: {}", value_repr(k), value_repr(v)))
                    .collect::<Vec<_>>()
                    .join(", ")
            ),
            HeapObject::Stack { items } => format!(
                "Stack[{}]",
                items.iter().map(value_repr).collect::<Vec<_>>().join(", ")
            ),
            HeapObject::Set { items } => format!(
                "Set[{}]",
                items.iter().map(value_repr).collect::<Vec<_>>().join(", ")
            ),
            HeapObject::Enum { index, .. } => format!("Enum#{}", index),
            HeapObject::Exception { message } => message.clone(),
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
        Value::Bool(b) => b.to_string(),
        Value::Long(i) => i.to_string(),
        Value::Double(f) => f.to_string(),
        Value::Char(c) => format!("'{}'", c),
        Value::Object(_) => "<object>".to_string(),
    }
}

fn mark_value(v: &Value, marked: &mut HashSet<GcRef>) {
    if let Value::Object(r) = v {
        marked.insert(*r);
    }
}

/// The managed heap.
pub struct Heap {
    objects: Vec<Option<HeapObject>>,
    free: Vec<u32>,
    /// Allocation counter used to trigger GC.
    allocs_since_gc: usize,
}

impl Default for Heap {
    fn default() -> Self {
        Self::new()
    }
}

impl Heap {
    pub fn new() -> Self {
        Heap {
            objects: vec![None],
            free: vec![],
            allocs_since_gc: 0,
        }
    }

    /// Allocate a new object; returns its handle.
    pub fn alloc(&mut self, obj: HeapObject) -> GcRef {
        self.allocs_since_gc += 1;
        if let Some(ref_) = self.free.pop() {
            self.objects[ref_ as usize] = Some(obj);
            return ref_;
        }
        let ref_ = self.objects.len() as GcRef;
        self.objects.push(Some(obj));
        ref_
    }

    pub fn get(&self, ref_: GcRef) -> Option<&HeapObject> {
        self.objects.get(ref_ as usize)?.as_ref()
    }

    pub fn get_mut(&mut self, ref_: GcRef) -> Option<&mut HeapObject> {
        self.objects.get_mut(ref_ as usize)?.as_mut()
    }

    /// Free an object (used by GC and explicit deallocation).
    pub fn free(&mut self, ref_: GcRef) {
        if ref_ == 0 {
            return;
        }
        if self.objects.get(ref_ as usize).is_some_and(|o| o.is_some()) {
            self.objects[ref_ as usize] = None;
            self.free.push(ref_);
        }
    }

    /// Tracing mark-and-sweep. `roots` are the live values.
    pub fn collect(&mut self, roots: &[Value]) {
        // Fixed-point marking over the object graph.
        let mut marked2: HashSet<GcRef> = HashSet::new();
        for r in roots {
            mark_value(r, &mut marked2);
        }
        let mut changed = true;
        while changed {
            changed = false;
            for r in marked2.iter().copied().collect::<Vec<_>>() {
                if let Some(obj) = self.get(r) {
                    let mut local = HashSet::new();
                    obj.mark_into(&mut local);
                    for m in local {
                        if marked2.insert(m) {
                            changed = true;
                        }
                    }
                }
            }
        }
        // Sweep.
        let mut dead = Vec::new();
        for (i, slot) in self.objects.iter().enumerate() {
            if slot.is_some() && !marked2.contains(&(i as GcRef)) {
                dead.push(i as GcRef);
            }
        }
        for r in dead {
            self.free(r);
        }
        self.allocs_since_gc = 0;
    }

    /// True when a collection is due.
    pub fn gc_due(&self) -> bool {
        self.allocs_since_gc > 4096
    }

    /// Number of live objects.
    #[allow(dead_code)]
    pub fn live_count(&self) -> usize {
        self.objects.iter().filter(|s| s.is_some()).count()
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
}
