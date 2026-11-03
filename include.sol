// include.sol — included by example.sol. It contributes declarations only.
//
// A module groups type and function names. Inside the module the short name works; outside it a
// declaration is reachable as `showcase::Name` when it says `pub`. Otherwise it is private to
// the module, including other files that reopen `module showcase`.
//
// Aliases are file-local: the block below is visible to this file's declarations and to no other
// file, so `example.sol` writes `showcase::Ticket` (or its own alias). An alias names an existing
// canonical symbol and adds no type, no package, and no generated Java.

alias {
    showcase::Ticket LocalTicket
    showcase LocalBoard
}

module showcase {

    // A private helper type: usable inside the module, never in a pub signature.
    type Counter {
        mut steps := 0

        pub func Counter() {
            steps = 0
        }

        pub func bump() -> Integer {
            steps = steps + 1
            return steps
        }
    }

    pub type Ticket {

        pub id := 0

        pub func Ticket(id: Integer) {
            self.id = id
        }

        pub func toString() -> String {
            return "#" .. id
        }

        pub func next() -> Ticket {
            return Ticket(id + 1)
        }
    }

    pub type Board {

        pub name := "board"
        counter := Counter()

        pub func Board(name: String) {
            self.name = name
        }

        pub func first() -> String {
            counted := counter.bump()
            return name .. ":" .. counted
        }
    }

    pub func issue(id: Integer) -> Ticket {
        return Ticket(id)
    }

    pub func label(ticket: Ticket) -> String {
        return "ticket " .. ticket
    }

    // The file-local aliases in use inside the file that declares them: a type alias constructs
    // and annotates `showcase::Ticket`, and a module alias names `showcase::nextAfter`.
    pub func bump(value: LocalTicket) -> LocalTicket {
        return LocalTicket(LocalBoard::nextAfter(value.id))
    }

    pub func nextAfter(value: Integer) -> Integer {
        return value + 1
    }

    // A native type may expose a Java class whose useful API is static. Every exported function
    // says `static` so the generated adapter calls java.lang.Math directly, without an instance.
    pub type Math native "java.lang.Math" {
        static pub func abs(value: Integer) -> Integer
        static pub func max(a: Integer, b: Integer) -> Integer
        static pub func sqrt(value: Double) -> Double
        static pub func floor(value: Double) -> Double
    }

    // `native "javaName"` renames the Java member a declaration selects, which is how two Java
    // overloads get distinct Solvik names. Declarations are the whole exported API: everything
    // else java.lang.Integer offers stays unreachable.
    pub type NumberText native "java.lang.Integer" {
        static pub func text(value: Integer) -> String native "toString"
        static pub func parse(value: String) -> Integer native "parseInt"
    }

    // An instance binding owns a private delegate: each Solvik value wraps one Java object, the
    // declared methods call it directly, and nothing else on the Java class is reachable. Two
    // modules may bind the same Java class; the binding belongs to its module, not to the class.
    pub type Die native "java.util.Random" {
        pub func Die()
        pub func roll(sides: Integer) -> Integer native "nextInt"
    }

    // A binding may also route one of the contract names to a Java member. `hashCode()` here is the
    // Java list's `size()`, and an undeclared `toString()` still renders the delegate itself.
    pub type Bag native "java.util.ArrayList" {
        pub func Bag()
        pub func put(item: Any) native "add"
        pub func count() -> Integer native "size"
        pub func hashCode() -> Integer native "size"
    }

    // A declared `equals` compares the wrapped Java objects, and a declared `hashCode` is the Java
    // hash, so a value of this binding works as a key in `solvik::collection::Map`. Operator `==`
    // on these values stays reference identity.
    pub type Amount native "java.math.BigInteger" {
        pub func Amount(text: String)
        pub func equals(other: Any?) -> Boolean
        pub func hashCode() -> Integer
        pub func text() -> String native "toString"
    }

    // A binding may also declare the contract name itself. `toString()` here selects the Java
    // StringBuilder method, so the adapter carries the override instead of falling back to the
    // shared render helper. `append` has no return type, so the Java result is discarded.
    pub type Text native "java.lang.StringBuilder" {
        pub func Text()
        pub func append(value: String)
        pub func toString() -> String
    }
}
