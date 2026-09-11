package supercon

interface Identified {

    id(): String
}

interface Named {

    name(): String
}

class Entity implements Identified {

    idValue: String

    public static new(id: String): Self {
        return Self { idValue: id, }
    }

    public id(): String {
        return self.idValue
    }
}

class User implements Identified, Named {

    entity: Entity
    nameValue: String
    mutable loginCountValue: Long

    delegate Identified to entity

    public static new(id: String, name: String): Self {
        return Self {
            entity: Entity.new(id),
            nameValue: name,
            loginCountValue: 0,
        }
    }

    public name(): String {
        return self.nameValue
    }

    public login(): Void {
        self.loginCountValue += 1
    }

    public loginCount(): Long {
        return self.loginCountValue
    }
}

class Main {

    public static run(args: String...): Long {
        let u: User = User.new("u1", "alice")
        let n: Named = u
        let e: Identified = u
        if n.name() != "alice" { return 1 }
        if e.id() != "u1" { return 2 }
        u.login()
        u.login()
        if u.loginCount() != 2 { return 3 }
        System.out().println("ok")
        return 0
    }
}
