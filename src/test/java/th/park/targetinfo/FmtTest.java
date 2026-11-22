package th.park.targetinfo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FmtTest {
    @Test void wholeNumber() { assertEquals("20", Fmt.hp(20.0)); }
    @Test void half() { assertEquals("15.5", Fmt.hp(15.5)); }
    @Test void roundsToOneDecimal() { assertEquals("3.3", Fmt.hp(3.3333)); }
    @Test void tinyButAliveShowsAboveZero() { assertEquals("0.1", Fmt.hp(0.01)); }
    @Test void dead() { assertEquals("0", Fmt.hp(0)); }
    @Test void bigBoss() { assertEquals("500", Fmt.hp(500)); }

    @Test void aboveHeadWithoutName() { assertEquals(2.25, Fmt.lift(1.95, false), 1e-9); }
    @Test void aboveNameTag() { assertEquals(2.55, Fmt.lift(1.95, true), 1e-9); }
}
