package structs

// A struct has private state and exposes behavior through methods. An
// trait default method dispatches back through the receiver, so each
// concrete struct supplies its own `speak()`.

trait Animal {

    func name(self): String
    func speak(self): String

    func describe(self): String {
        return name() .. " says " .. speak()
    }
}

struct Dog implements Animal {

    nameValue: String

    pub func new(name: String): Self {
        return Self { nameValue: name, }
    }

    pub func name(self): String {
        return self.nameValue
    }

    pub func speak(self): String {
        return "woof"
    }
}

struct Cat implements Animal {

    nameValue: String

    pub func new(name: String): Self {
        return Self { nameValue: name, }
    }

    pub func name(self): String {
        return self.nameValue
    }

    pub func speak(self): String {
        return "meow"
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let a: Animal = Dog.new("rex")
        System.getOut().println(a.describe())
        let d: Dog = Dog.new("fido")
        System.getOut().println(d.speak())
        System.getOut().println(d.name())
        let c: Animal = Cat.new("tom")
        System.getOut().println(c.describe())
        return 0
    }
}
