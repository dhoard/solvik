package test

use file:use_helper

func main() -> int {
    message: string = helper.greet("Solvik")
    println(message)

    result: int = helper.add(40, 2)
    println("40 + 2 = " + string(result))

    return 0
}
