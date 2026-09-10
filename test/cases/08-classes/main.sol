module classes

class Animal {

    public name: String

    public static new(name: String): Self {
        return Self { name: name, }
    }

    public speak(): String {
        return "..."
    }

    public describe(): String {
        return self.name .. " says " .. speak()
    }
}

class Dog extends Animal {

    override public speak(): String {
        return "woof"
    }
}

class Main {

    public static run(args: String...): Long {
        let a: Animal = Dog.new("rex")
        stdout.println(a.describe())
        let d: Dog = Dog.new("fido")
        stdout.println(d.speak())
        stdout.println(d.name)
        return 0
    }
}
