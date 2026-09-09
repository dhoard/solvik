package classes

class Animal {
    pub name: String

    pub static new(name: String): Self {
        return Self { name }
    }

    pub speak(): String {
        return "..."
    }

    pub describe(): String {
        return name .. " says " .. speak()
    }
}

class Dog extends Animal {
    override pub speak(): String {
        return "woof"
    }
}

class Main {
    pub static run(args: String...): Int {
        a: Animal = Dog::new("rex")
        stdout.println(a.describe())
        d: Dog = Dog::new("fido")
        stdout.println(d.speak())
        stdout.println(d.name)
        return 0
    }
}
