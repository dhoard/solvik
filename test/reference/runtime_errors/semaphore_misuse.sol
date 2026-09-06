// expected E080: a negative initial count is a catchable runtime error
package semaphore_misuse

func main() -> Int {
    try {
        s: Semaphore = semaphore(-1)
    } catch (e: Exception) {
        throw e.code .. ":" .. e.message
    }
    return 0
}
