module t

class Main {

    public static run(args: String...): Long {
        Test.assert(true)
        Test.assert(1 == 1, "math broke")
        Test.assertEqual(1, 1)
        Test.assertEqual("a", "a", "strings")
        try {
            Test.assert(false, "boom")
            stdout.println("unreachable")
        } catch (e) {
            stdout.println(e)
        }
        try {
            Test.assertEqual(1, 2)
            stdout.println("unreachable")
        } catch (e) {
            stdout.println(e)
        }
        stdout.println("ok")
        return 0
    }
}
