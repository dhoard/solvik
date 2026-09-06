package test

use file:use_helper

func main() -> Int {
    message: String = helper.greet("Solvik")
    println(message)

    result: Int = helper.add(40, 2)
    println("40 + 2 = " .. result)

    return 0
}
