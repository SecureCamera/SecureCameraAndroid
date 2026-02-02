package com.darkrockstudios.app.securecamera.metadata

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for the metadata file growth strategy.
 *
 * The growth strategy uses doubling with bounds to reduce information leakage
 * from file size changes. This makes it harder to correlate file size with
 * actual photo count or deletion activity.
 */
class MetadataGrowthTest {

	companion object {
		// These must match MetadataManager.Companion values
		private const val MIN_GROWTH_SLOTS = 64
		private const val MAX_GROWTH_SLOTS = 512
	}

	/**
	 * Replicates the growth calculation from MetadataManager.allocateNewSlot()
	 */
	private fun calculateGrowthAmount(currentCapacity: Int): Int {
		return when {
			currentCapacity == 0 -> MIN_GROWTH_SLOTS
			currentCapacity < MAX_GROWTH_SLOTS -> currentCapacity.coerceAtLeast(MIN_GROWTH_SLOTS)
			else -> MAX_GROWTH_SLOTS
		}
	}

	@Test
	fun `first allocation should create MIN_GROWTH_SLOTS`() {
		val growth = calculateGrowthAmount(0)
		assertEquals(MIN_GROWTH_SLOTS, growth)
	}

	@Test
	fun `growth from 64 should double to 64`() {
		val growth = calculateGrowthAmount(64)
		assertEquals(64, growth)
	}

	@Test
	fun `growth from 128 should double to 128`() {
		val growth = calculateGrowthAmount(128)
		assertEquals(128, growth)
	}

	@Test
	fun `growth from 256 should double to 256`() {
		val growth = calculateGrowthAmount(256)
		assertEquals(256, growth)
	}

	@Test
	fun `growth from 512 should cap at MAX_GROWTH_SLOTS`() {
		val growth = calculateGrowthAmount(512)
		assertEquals(MAX_GROWTH_SLOTS, growth)
	}

	@Test
	fun `growth from 1024 should cap at MAX_GROWTH_SLOTS`() {
		val growth = calculateGrowthAmount(1024)
		assertEquals(MAX_GROWTH_SLOTS, growth)
	}

	@Test
	fun `growth from small capacity should use minimum`() {
		// If capacity is less than MIN_GROWTH_SLOTS, still grow by MIN_GROWTH_SLOTS
		val growth = calculateGrowthAmount(32)
		assertEquals(MIN_GROWTH_SLOTS, growth)
	}

	@Test
	fun `capacity progression should follow expected pattern`() {
		var capacity = 0
		val expectedProgression = listOf(
			64,    // 0 -> 64
			128,   // 64 -> 128
			256,   // 128 -> 256
			512,   // 256 -> 512
			1024,  // 512 -> 1024
			1536,  // 1024 -> 1536 (capped growth)
			2048,  // 1536 -> 2048 (capped growth)
		)

		for (expected in expectedProgression) {
			val growth = calculateGrowthAmount(capacity)
			capacity += growth
			assertEquals(expected, capacity, "Capacity should be $expected after growth from ${capacity - growth}")
		}
	}

	@Test
	fun `file size should grow in predictable steps`() {
		// Each slot is ENTRY_BLOCK_SIZE (168 bytes)
		// Plus HEADER_SIZE (32 bytes) for the file
		val capacities = listOf(64, 128, 256, 512, 1024)
		val expectedFileSizes = capacities.map { capacity ->
			SecmFileFormat.HEADER_SIZE + (capacity * SecmFileFormat.ENTRY_BLOCK_SIZE)
		}

		// Verify the file sizes are what we expect
		assertEquals(32 + 64 * 168, expectedFileSizes[0])   // ~10.8 KB
		assertEquals(32 + 128 * 168, expectedFileSizes[1])  // ~21.5 KB
		assertEquals(32 + 256 * 168, expectedFileSizes[2])  // ~43 KB
		assertEquals(32 + 512 * 168, expectedFileSizes[3])  // ~86 KB
		assertEquals(32 + 1024 * 168, expectedFileSizes[4]) // ~172 KB
	}

	@Test
	fun `free slots after growth should be growthAmount minus 1`() {
		// When we grow, we use one slot immediately and add the rest to freeSlots
		val currentCapacity = 64
		val growth = calculateGrowthAmount(currentCapacity)
		val freeSlotsAdded = growth - 1

		assertEquals(63, freeSlotsAdded, "Should add growth-1 slots to free list")
	}
}
