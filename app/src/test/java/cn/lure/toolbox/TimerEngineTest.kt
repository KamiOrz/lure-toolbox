package cn.lure.toolbox
import org.junit.Assert.*
import org.junit.Test
class TimerEngineTest {
 @Test fun firstAlertWaitsFullPeriod() { val e=TimerEngine();e.start(5,100);assertFalse(e.tick(5099));assertTrue(e.tick(5100));assertEquals(1L,e.count);assertEquals(5000L,e.remaining(5100)) }
 @Test fun pausePreservesRemaining() { val e=TimerEngine();e.start(5,0);e.pause(1800);assertEquals(3200L,e.remaining(99999));assertFalse(e.tick(99999));e.resume(100000);assertFalse(e.tick(103199));assertTrue(e.tick(103200)) }
 @Test fun delayedTickSkipsMissedCyclesWithoutDrift() { val e=TimerEngine();e.start(3,0);assertTrue(e.tick(10100));assertEquals(1L,e.count);assertEquals(1900L,e.remaining(10100));assertFalse(e.tick(10100));assertTrue(e.tick(12000)) }
 @Test fun stopAndRestartResetCount() { val e=TimerEngine();e.start(1,0);e.tick(1000);e.stop();assertEquals(0L,e.count);assertEquals(1000L,e.remaining(99999));assertFalse(e.tick(99999));e.start(300,99999);assertEquals(300000L,e.remaining(99999)) }
 @Test fun boundaries() { for(n in listOf(1,300)) TimerEngine().start(n,0);for(n in listOf(-1,0,301)) { try { TimerEngine().start(n,0);fail("accepted $n") } catch(_:IllegalArgumentException) {} } }
}
