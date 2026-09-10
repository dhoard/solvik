module privatefield

class Account {

    secret: Long

    public static new(): Self {
        return Self { secret: 5, }
    }

    public reveal(): Long {
        return self.secret
    }
}

class Main {

    public static run(args: String...): Long {
        let a: Account = Account.new()
        // ERROR: fields are private to their declaring class; use a method.
        stdout.println(a.secret)
        // This would be valid inside 'Account' only:
        let _ok: Long = a.reveal()
        return 0
    }
}
