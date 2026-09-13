package supercon

interface Identified {

    func id(self): String
}

interface Named {

    func name(self): String
}

struct Entity implements Identified {

    idValue: String

    public func new(id: String): Self {
        return Self { idValue: id, }
    }

    public func id(self): String {
        return self.idValue
    }
}

struct User implements Identified, Named {

    entity: Entity
    nameValue: String
    mutable loginCountValue: Long

    delegate Identified to entity

    public func new(id: String, name: String): Self {
        return Self {
            entity: Entity.new(id),
            nameValue: name,
            loginCountValue: 0,
        }
    }

    public func name(self): String {
        return self.nameValue
    }

    public func login(self): Void {
        self.loginCountValue += 1
    }

    public func loginCount(self): Long {
        return self.loginCountValue
    }
}

struct Main {

    public func run(args: String...): Long {
        let u: User = User.new("u1", "alice")
        let n: Named = u
        let e: Identified = u
        if n.name() != "alice" { return 1 }
        if e.id() != "u1" { return 2 }
        u.login()
        u.login()
        if u.loginCount() != 2 { return 3 }
        System.getOut().println("ok")
        return 0
    }
}
