package privatefield

struct Account {

    secret: Long

    public func new(): Self {
        return Self { secret: 5, }
    }

    public func reveal(self): Long {
        return self.secret
    }
}

struct Main {

    public func run(args: String...): Integer {
        let a: Account = Account.new()
        // ERROR: fields are private to their declaring struct; use a method.
        System.getOut().println(a.secret)
        // This would be valid inside 'Account' only:
        let _ok: Long = a.reveal()
        return 0
    }
}
