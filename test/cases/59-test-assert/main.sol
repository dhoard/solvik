package t

class Main {

    public static run(args: String...): Long {
        Test.assert(true)
        Test.assert(1 == 1, "math broke")
        Test.assertEqual(1, 1)
        Test.assertEqual("a", "a", "strings")
        try {
            Test.assert(false, "boom")
            System.out().println("unreachable")
        } catch (e) {
            System.out().println(e)
        }
        try {
            Test.assertEqual(1, 2)
            System.out().println("unreachable")
        } catch (e) {
            System.out().println(e)
        }
        System.out().println("ok")
        return 0
    }
}
