package testutil

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A simple, in-memory DataStore for tests.
 * - Atomic updates guarded by a Mutex
 * - Hot Flow that immediately emits the current value
 * - No IO, no closing needed
 */
class FakeDataStore<T>(
	initial: T
) : DataStore<T> {

	private val state = MutableStateFlow(initial)
	private val mutex = Mutex()

	override val data: Flow<T> = state

	override suspend fun updateData(transform: suspend (t: T) -> T): T {
		// Matches DataStore semantics: if transform throws, value is unchanged.
		return mutex.withLock {
			val newValue = transform(state.value)
			state.value = newValue
			newValue
		}
	}

	/** Directly set the value (useful to seed state in tests). */
	suspend fun set(value: T) {
		mutex.withLock { state.value = value }
	}

	/** Read the current value synchronously. */
	fun currentValue(): T = state.value
}
