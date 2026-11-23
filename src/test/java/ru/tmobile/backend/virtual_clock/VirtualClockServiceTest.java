package ru.tmobile.backend.virtual_clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class VirtualClockServiceTest {

	@Test
	void defaultClockRunsAtRealTimeWithMultiplierOne() {
		VirtualClockService clock = new VirtualClockService();
		long before = System.currentTimeMillis();
		long actual = clock.instant().toEpochMilli();
		long after = System.currentTimeMillis();

		assertEquals(1, clock.getMultiplier());
		assertFalse(clock.isPaused());
		assertTrue(actual >= before && actual <= after);
	}

	@Test
	void syncTimeWithMultiplierAdvancesVirtualTimeFasterThanRealTime() {
		VirtualClockService clock = new VirtualClockService();
		long virtualStart = 1_000_000L;
		long realStart = System.currentTimeMillis() - 10_000L;

		clock.syncTime(virtualStart, realStart, 2, false);
		long actual = clock.instant().toEpochMilli();

		assertEquals(2, clock.getMultiplier());
		assertFalse(clock.isPaused());
		assertTrue(actual >= virtualStart + 20_000L - 2_000L && actual <= virtualStart + 20_000L + 2_000L,
				"virtual time should be about 2x real elapsed time, actual=" + actual);
	}

	@Test
	void syncTimeKeepsRequestedVirtualTimeWhenPaused() {
		VirtualClockService clock = new VirtualClockService();
		long virtualStart = 1_700_000_000_000L;

		clock.syncTime(virtualStart, System.currentTimeMillis(), 5, true);

		assertTrue(clock.isPaused());
		assertEquals(Instant.ofEpochMilli(virtualStart), clock.instant());
	}

	@Test
	void pausedClockDoesNotAdvanceBetweenReads() {
		VirtualClockService clock = new VirtualClockService();
		clock.syncTime(1_700_000_000_000L, System.currentTimeMillis(), 5, true);

		Instant first = clock.instant();
		Instant second = clock.instant();

		assertEquals(first, second);
	}

	@Test
	void getZoneReturnsSystemDefaultZone() {
		assertEquals(ZoneId.systemDefault(), new VirtualClockService().getZone());
	}

	@Test
	void withZoneIsExplicitlyUnsupported() {
		assertThrows(UnsupportedOperationException.class,
				() -> new VirtualClockService().withZone(ZoneId.of("UTC")));
	}
}
