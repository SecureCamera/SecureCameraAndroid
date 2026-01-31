package com.darkrockstudios.app.securecamera.usecases

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PinStrengthCheckUseCaseTest {

	private lateinit var pinStrengthCheckUseCase: PinStrengthCheckUseCase

	@Before
	fun setup() {
		pinStrengthCheckUseCase = PinStrengthCheckUseCase()
	}

	@Test
	fun `test valid PINs`() {
		// Valid PINs: at least 4 digits, not all the same, not sequential
		assertTrue(pinStrengthCheckUseCase.isPinStrongEnough("1357"))     // 4 digits, not all same, not sequential
		assertTrue(pinStrengthCheckUseCase.isPinStrongEnough("2468"))     // 4 digits, not all same, not sequential
		assertTrue(pinStrengthCheckUseCase.isPinStrongEnough("1593"))     // 4 digits, not all same, not sequential
		assertTrue(pinStrengthCheckUseCase.isPinStrongEnough("7294"))     // 4 digits, not all same, not sequential
		assertTrue(pinStrengthCheckUseCase.isPinStrongEnough("13579"))    // 5 digits, not all same, not sequential
		assertTrue(pinStrengthCheckUseCase.isPinStrongEnough("24680"))    // 5 digits, not all same, not sequential
	}

	@Test
	fun `test invalid PINs - too short`() {
		// PINs with less than 4 digits
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("123"))     // 3 digits
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("12"))      // 2 digits
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("1"))       // 1 digit
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough(""))        // Empty string
	}

	@Test
	fun `test invalid PINs - non-numeric`() {
		// PINs with non-numeric characters
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("123a"))    // Contains letter
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("12.3"))    // Contains period
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("12-34"))   // Contains hyphen
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("1234!"))   // Contains special character
	}

	@Test
	fun `test invalid PINs - all same digit`() {
		// PINs with all the same digit
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("1111"))    // All 1's
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("0000"))    // All 0's
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("99999"))   // All 9's
	}

	@Test
	fun `test invalid PINs - sequential ascending`() {
		// PINs with sequential ascending digits
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("1234"))    // Sequential ascending
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("4567"))    // Sequential ascending
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("01234"))   // Sequential ascending
	}

	@Test
	fun `test invalid PINs - sequential descending`() {
		// PINs with sequential descending digits
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("4321"))    // Sequential descending
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("9876"))    // Sequential descending
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("54321"))   // Sequential descending
	}

	@Test
	fun `test invalid PINs - black list`() {
		// PINs with sequential descending digits
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("1212"))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("6969"))
	}

	// Alphanumeric PIN tests

	@Test
	fun `test valid alphanumeric PINs`() {
		assertTrue(pinStrengthCheckUseCase.isPinStrongEnough("1337zjew", isAlphanumeric = true))
		assertTrue(pinStrengthCheckUseCase.isPinStrongEnough("MyP1n123", isAlphanumeric = true))
		assertTrue(pinStrengthCheckUseCase.isPinStrongEnough("abcd", isAlphanumeric = true))
		assertTrue(pinStrengthCheckUseCase.isPinStrongEnough("Test1", isAlphanumeric = true))
		// Numeric patterns are OK when part of alphanumeric
		assertTrue(pinStrengthCheckUseCase.isPinStrongEnough("1234abcd", isAlphanumeric = true))
	}

	@Test
	fun `test invalid alphanumeric PINs - blacklisted passwords`() {
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("password", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("PASSWORD", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("Password", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("qwerty", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("abc123", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("letmein", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("admin", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("welcome", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("monkey", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("dragon", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("master", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("login", isAlphanumeric = true))
	}

	@Test
	fun `test invalid alphanumeric PINs - too short`() {
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("abc", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("ab", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("a", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("", isAlphanumeric = true))
	}

	@Test
	fun `test invalid alphanumeric PINs - all same character`() {
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("aaaa", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("AAAA", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("AaAa", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("1111", isAlphanumeric = true))
	}

	@Test
	fun `test invalid alphanumeric PINs - special characters`() {
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("pass!", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("test@123", isAlphanumeric = true))
		assertFalse(pinStrengthCheckUseCase.isPinStrongEnough("my-pin", isAlphanumeric = true))
	}
}
