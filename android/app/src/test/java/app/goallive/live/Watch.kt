package app.goallive.live

/**
 * Progress for the screenshot run: each shot prints how long it took, and when nothing has been shot for three minutes
 * the test thread's stack is printed, so a screen that never settles shows where it hangs.
 */
object Watch {
    @Volatile private var last = System.currentTimeMillis()
    @Volatile private var lastName = "start"
    private var thread: Thread? = null

    fun mark(name: String) {
        val now = System.currentTimeMillis()
        System.err.println("SHOT $name (${(now - last) / 1000}s)")
        last = now; lastName = name
        if (thread == null) {
            val test = Thread.currentThread()
            thread = Thread {
                while (true) {
                    Thread.sleep(30_000)
                    if (System.currentTimeMillis() - last > 180_000) {
                        System.err.println("SHOT stuck after $lastName:")
                        test.stackTrace.take(40).forEach { System.err.println("SHOT    at $it") }
                        last = System.currentTimeMillis()
                    }
                }
            }.apply { isDaemon = true; start() }
        }
    }
}
