module supercon

class Entity {

    id: String

    public static new(id: String): Self {
        return Self { id: id, }
    }

    public id(): String {
        return self.id
    }
}

interface Named {

    name(): String
}

class User extends Entity implements Named {

    name: String

    mutable loginCount: Long

    public static new(id: String, name: String): Self {
        return Self {
            super: Entity.new(id: id),
            name: name,
            loginCount: 0,
        }
    }

    public name(): String {
        return self.name
    }

    public login(): Void {
        self.loginCount += 1
    }

    public loginCount(): Long {
        return self.loginCount
    }
}

class Main {

    public static run(args: String...): Long {
        let u: User = User.new("u1", "alice")
        let n: Named = u
        let e: Entity = u
        if n.name() != "alice" { return 1 }
        if e.id() != "u1" { return 2 }
        u.login()
        u.login()
        if u.loginCount() != 2 { return 3 }
        stdout.println("ok")
        return 0
    }
}
