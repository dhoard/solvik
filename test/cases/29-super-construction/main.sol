package supercon

class Entity {
    id: String

    pub static new(id: String): Self {
        return Self { id }
    }

    pub id(): String {
        return id
    }
}

interface Named {
    name(): String
}

class User extends Entity implements Named {
    name: String

    mut {
        loginCount: Int
    }

    pub static new(id: String, name: String): Self {
        return Self {
            super: Entity::new(id: id)
            name
            loginCount: 0
        }
    }

    pub name(): String {
        return name
    }

    pub login(): Void {
        loginCount += 1
    }

    pub loginCount(): Int {
        return loginCount
    }
}

class Main {
    pub static run(args: String...): Int {
        u: User = User::new("u1", "alice")
        n: Named = u
        e: Entity = u
        if n.name() != "alice" { return 1 }
        if e.id() != "u1" { return 2 }
        u.login()
        u.login()
        if u.loginCount() != 2 { return 3 }
        stdout.println("ok")
        return 0
    }
}
