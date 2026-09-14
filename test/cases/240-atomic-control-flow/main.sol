package atomiccontrol

// `atomic` is a lexical scope and a statement, not an expression. Its block may
// return from the enclosing method, break/continue an enclosing loop, and throw;
// every exit path releases all acquired monitors through the generated finally.

struct Counter {

    var value: Integer

    pub func new(): Self {
        return Self { value: 0, }
    }

    pub func bump(self) {
        self.add(1)
    }

    func add(self, amount: Integer) {
        self.value += amount
    }

    pub func get(self): Integer {
        return self.value
    }
}

struct Picker {

    static calls: Integer = 0

    pub func reset() {
        Self.calls = 0
    }

    pub func pick(counter: Counter): Counter {
        Self.calls += 1
        return counter
    }

    pub func count(): Integer {
        return Self.calls
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let counter: Counter = Counter.new()

        // Reentrant self-call and nested atomic over the same object.
        atomic(counter) {
            counter.bump()
            atomic(counter) {
                counter.add(10)
            }
        }
        System.getOut().println(counter.get())

        // return from the middle of an atomic block.
        System.getOut().println(withdraw(counter, 3))
        System.getOut().println(counter.get())
        System.getOut().println(withdraw(counter, 100))

        // continue/break target the surrounding loop, not the atomic block.
        var i: Integer = 0
        while i < 3 {
            i += 1
            atomic(counter) {
                if i == 1 {
                    continue
                }
                counter.bump()
                if i == 3 {
                    break
                }
            }
        }
        System.getOut().println(counter.get())

        // throw unwinds through the finally and releases every lock.
        try {
            atomic(counter) {
                throw Exception.new("boom")
            }
        } catch (e: Exception) {
            System.getOut().println("caught " .. e)
        }
        counter.bump()
        System.getOut().println(counter.get())

        // A nullable reference is accepted only after existing non-null
        // narrowing has run.
        let maybe: Counter? = counter
        if maybe != null {
            atomic(maybe) {
                maybe.bump()
            }
        }
        System.getOut().println(counter.get())

        // Every target expression is evaluated exactly once, left to right,
        // before any lock is acquired, and identical objects are acquired once.
        Picker.reset()
        atomic(
            Picker.pick(counter),
            Picker.pick(counter),
        ) {
            counter.bump()
        }
        System.getOut().println(Picker.count())
        System.getOut().println(counter.get())
        return 0
    }

    func withdraw(counter: Counter, amount: Integer): Boolean {
        atomic(counter) {
            if counter.get() < amount {
                return false
            }
            counter.add(0 - amount)
            return true
        }
    }
}
