package t

class Main {

    public static run(args: String...): Long {
        Test.assert(true)
        Test.assert(1 == 1, "math broke")
        Test.assertEqual(1, 1)
        Test.assertEqual("a", "a", "strings")
        try {
            Test.assert(false, "boom")
            System.getOut().println("unreachable")
        } catch (e: Exception) {
            System.getOut().println(e)
        }
        try {
            Test.assertEqual(1, 2)
            System.getOut().println("unreachable")
        } catch (e: Exception) {
            System.getOut().println(e)
        }
        System.getOut().println("ok")
        return 0
    }
}
