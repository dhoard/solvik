module classes

// A class has private state and exposes behavior through methods. An
// interface default method dispatches back through the receiver, so each
// concrete class supplies its own `speak()`.

interface Animal {

    name(): String
    speak(): String

    describe(): String {
        return name() .. " says " .. speak()
    }
}

class Dog implements Animal {

    nameValue: String

    public static new(name: String): Self {
        return Self { nameValue: name, }
    }

    public name(): String {
        return self.nameValue
    }

    public speak(): String {
        return "woof"
    }
}

class Cat implements Animal {

    nameValue: String

    public static new(name: String): Self {
        return Self { nameValue: name, }
    }

    public name(): String {
        return self.nameValue
    }

    public speak(): String {
        return "meow"
    }
}

class Main {

    public static run(args: String...): Long {
        let a: Animal = Dog.new("rex")
        stdout.println(a.describe())
        let d: Dog = Dog.new("fido")
        stdout.println(d.speak())
        stdout.println(d.name())
        let c: Animal = Cat.new("tom")
        stdout.println(c.describe())
        return 0
    }
}
