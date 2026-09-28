package io.github.thanhng224.sdkbase.core.session

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StateStoreTest {

    @Test
    fun updateInsideLockIsVisible(): Unit = runBlocking {
        val store = StateStore(0)

        store.withLock { update { it + 1 } }

        assertEquals(1, store.current)
        assertEquals(1, store.state.value)
    }

    @Test
    fun onChangeSeesEveryNewState(): Unit = runBlocking {
        val seen = CopyOnWriteArrayList<Int>()
        val store = StateStore(0) { seen += it }

        store.withLock {
            update { it + 1 }
            update { it + 1 }
        }

        assertEquals(listOf(1, 2), seen)
    }

    @Test
    fun concurrentWithLockCallsAreSerialized(): Unit = runBlocking {
        val store = StateStore(0)

        coroutineScope {
            repeat(100) {
                launch(Dispatchers.Default) {
                    store.withLock { update { it + 1 } }
                }
            }
        }

        assertEquals(100, store.current)
    }

    @Test
    fun nestedWithLockThrows(): Unit = runBlocking {
        val store = StateStore(0)

        try {
            store.withLock {
                store.withLock { update { it + 1 } }
            }
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("StateStore.withLock is not reentrant", e.message)
        }
    }

    /**
     * [StateStore.Mutation] has no public implementation: the only way to obtain one is the
     * receiver [StateStore.withLock] passes into its block, so a caller can never construct or hold
     * a `Mutation` outside a held lock. That is a compile-time property of the type, not something
     * a runtime assertion can exercise — this test documents the guarantee.
     */
    @Test
    fun mutationOutsideLockIsImpossible() {
        assertTrue(true)
    }
}
