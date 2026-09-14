package nullnarrowing

struct Box {
    value: String

    pub func new(value: String): Self {
        return Self { value: value }
    }

    pub func size(self): Long {
        return self.value.length()
    }
}

struct Main {
    pub func run(args: String...): Integer {
        let present: Box? = Box.new("ok")
        if present != null {
            System.getOut().println(present.size())
        }
        if present == null {
            System.getOut().println("missing")
        } else {
            System.getOut().println(present.size())
        }

        let negativeByte: Byte = -1
        let negativeShort: Short = -1
        System.getOut().println(negativeByte)
        System.getOut().println(negativeShort)

        let absent: Box? = null
        try {
            absent.size()
        } catch (e: Exception) {
            System.getOut().println(e)
        }
        return 0
    }
}
