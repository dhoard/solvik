class Plain {
}
func guard() {
    try {
        print("trying")
    } catch (e: Plain) {
        print("caught")
    }
}
guard()

print("EXECUTED-INVALID")
