package example

// Struct with mix of pub and private members
struct Account {
    pub name: string,
    pub mut balance: int,
    secret: string,         // private field

    pub func deposit(amount: int) -> void {
        balance = balance + amount
    }

    pub func getBalance() -> int {
        return balance
    }

    // Private method — internal use only
    func validate() -> bool {
        return balance >= 0
    }

    pub func withdraw(amount: int) -> bool {
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
    data: int,

    func process() -> int {
        return data * 2
    }
}

func main() -> int {
    // Public fields and methods work from outside
    mut acct: Account = Account("Alice", 1000, "s3cret")
    println("Name: " .. acct.name)
    println("Balance: " .. acct.getBalance())

    acct.deposit(500)
    println("After deposit: " .. acct.getBalance())

    ok: bool = acct.withdraw(200)
    println("Withdraw ok: " .. ok)
    println("After withdraw: " .. acct.getBalance())

    // Private field/method access would fail at compile time:
    // acct.secret        // error: field 'secret' is private
    // acct.validate()    // error: method 'validate' is private

    println("visibility test passed")
    return 0
}
