package example

// Struct with mix of pub and private members
struct Account {
    pub name: String,
    pub mut balance: Int,
    secret: String,         // private field

    pub mut func deposit(amount: Int) {
        balance = balance + amount
    }

    pub func getBalance() -> Int {
        return balance
    }

    // Private method — internal use only
    func validate() -> Bool {
        return balance >= 0
    }

    pub mut func withdraw(amount: Int) -> Bool {
        // Private method accessible inside the struct
        if !validate() {
            return false
        }
        if amount > balance {
            return false
        }
        balance = balance - amount
        return true
    }
}

// Struct with only private members
struct Internal {
    data: Int,

    func process() -> Int {
        return data * 2
    }
}

func main() -> Int {
    // Public fields and methods work from outside
    mut acct: Account = Account { name: "Alice", balance: 1000, secret: "s3cret" }
    println("Name: " .. acct.name)
    println("Balance: " .. acct.getBalance())

    acct.deposit(500)
    println("After deposit: " .. acct.getBalance())

    ok: Bool = acct.withdraw(200)
    println("Withdraw ok: " .. ok)
    println("After withdraw: " .. acct.getBalance())

    // Private field/method access would fail at compile time:
    // acct.secret        // error: field 'secret' is private
    // acct.validate()    // error: method 'validate' is private

    println("visibility test passed")
    return 0
}
