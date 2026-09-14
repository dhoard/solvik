package supercon

trait Identified {

    func id(self): String
}

trait Named {

    func name(self): String
}

struct Entity implements Identified {

    idValue: String

    pub func new(id: String): Self {
        return Self { idValue: id, }
    }

    pub func id(self): String {
        return self.idValue
    }
}

struct User implements Identified, Named {

    entity: Entity
    nameValue: String
    var loginCountValue: Long

    delegate Identified to entity

    pub func new(id: String, name: String): Self {
        return Self {
            entity: Entity.new(id),
            nameValue: name,
            loginCountValue: 0,
        }
    }

    pub func name(self): String {
        return self.nameValue
    }

    pub func login(self) {
        self.loginCountValue += 1
    }

    pub func loginCount(self): Long {
        return self.loginCountValue
    }
}

struct Main {

    pub func run(args: String...): Integer {
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
