package letmutableobject

// A `let` binding is immutable, but the object it refers to is not deeply
// immutable. Mutating the referenced List is allowed.

struct Main {

    pub func run(args: String...): Integer {
        let values: List<Integer> = [1, 2]
        values.add(3)
        System.getOut().println(values.size())
        return 0
    }
}
