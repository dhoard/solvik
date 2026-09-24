// Solvik static members and class initialization.
//
// A static property is class-level storage reached through the class name, a static method has no
// receiver, and one class initializer block per class runs once on the class's first active use.
// Initialization is lazy and dependency-ordered: on first use a class runs its superclass chain first,
// then its own static property initializers before its block, an unused class is never initialized, and
// the result never depends on the order classes are declared (docs/LANGUAGE_SPEC.md section 7).

open class Registry {
    static var entries: Integer = 0

    static func add(): Integer {
        Registry.entries = Registry.entries + 1
        return Registry.entries
    }

    static {
        Registry.add()
    }
}

class Session extends Registry {
    static var id: Integer
    static var seen: Integer = 0

    static func label(): String {
        return "session"
    }

    static {
        Session.seen = Registry.entries
        Session.id = 100
    }
}

class Defaults {
    static var count: Integer
    static var ratio: Double
    static var enabled: Boolean
    static var name: String?
}

println(Registry.entries)
println(Session.seen)
println(Session.id)
println(Session.label())
println(Defaults.count == 0)
println(Defaults.ratio == 0.0)
println(Defaults.enabled == false)
println(Defaults.name == null)
Registry.add()
println(Registry.entries)

// Lazy initialization: a class whose static block would print, but which the program never actively
// uses, is never initialized, so nothing is printed for it. First touching Unused below would run its
// block; not touching it leaves it silent.
class Unused {
    static var marker: Integer = 0
    static {
        println("unused class initialized")
    }
}
