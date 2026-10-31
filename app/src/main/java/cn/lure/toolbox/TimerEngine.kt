package cn.lure.toolbox

/** Pure monotonic-time state machine. Missed cycles produce at most one alert. */
class TimerEngine {
    enum class Status { IDLE, RUNNING, PAUSED }
    var status = Status.IDLE; private set
    var periodMs = 5_000L; private set
    var count = 0L; private set
    private var deadline = 0L
    private var pausedRemaining = periodMs
    fun start(seconds: Int, now: Long) {
        require(seconds in 1..300)
        periodMs = seconds * 1000L; count = 0; deadline = now + periodMs
        pausedRemaining = periodMs; status = Status.RUNNING
    }
    fun remaining(now: Long): Long = when(status) {
        Status.IDLE -> periodMs
        Status.PAUSED -> pausedRemaining
        Status.RUNNING -> (deadline - now).coerceAtLeast(0)
    }
    fun tick(now: Long): Boolean {
        if (status != Status.RUNNING || now < deadline) return false
        count++
        deadline += ((now - deadline) / periodMs + 1) * periodMs
        return true
    }
    fun pause(now: Long) { if(status == Status.RUNNING) { pausedRemaining = remaining(now); status = Status.PAUSED } }
    fun resume(now: Long) { if(status == Status.PAUSED) { deadline = now + pausedRemaining; status = Status.RUNNING } }
    fun stop() { status = Status.IDLE; count = 0; pausedRemaining = periodMs }
}
