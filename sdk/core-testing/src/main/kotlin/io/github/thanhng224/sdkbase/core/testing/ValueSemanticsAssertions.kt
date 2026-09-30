package io.github.thanhng224.sdkbase.core.testing

/**
 * Asserts the hand-written value semantics of a public plain class (the SDK's public classes are
 * not `data class`es, see `public-data-class` in AGENTS.md, so `equals`/`hashCode` are written by
 * hand and a property added later is easy to forget).
 *
 * [create] must return a fresh instance with the same values on every call. Pass one of
 * [differing] per property: an instance that differs from `create()` in exactly that property. The
 * check fails with an [AssertionError] when:
 * - two instances built by [create] are not equal, or have different `hashCode`/`toString`;
 * - an instance equals `null`, a value of another type, or is not equal to itself;
 * - any of [differing] is equal to `create()` (a property missing from `equals`) or has the same
 *   `hashCode` (a property missing from `hashCode`; choose values that hash differently).
 */
public fun <T : Any> assertValueSemantics(create: () -> T, vararg differing: T) {
    val base = create()
    val twin = create()
    check(base == base) { "an instance must equal itself" }
    check(base == twin) { "instances built from the same values must be equal: $base vs $twin" }
    check(base.hashCode() == twin.hashCode()) { "equal instances must share a hashCode: $base" }
    check(base.toString() == twin.toString()) { "equal instances must share a toString: $base vs $twin" }
    check(!base.equals(null)) { "an instance must not equal null" }
    check(!base.equals(Any())) { "an instance must not equal a value of another type" }
    differing.forEachIndexed { index, other ->
        check(base != other) { "differing[$index] must not equal the base instance (missing from equals?): $other" }
        check(other != base) { "equals must be symmetric for differing[$index]: $other" }
        check(base.hashCode() != other.hashCode()) {
            "differing[$index] has the base instance's hashCode (missing from hashCode?): $other"
        }
    }
}

private inline fun check(condition: Boolean, message: () -> String) {
    if (!condition) throw AssertionError(message())
}
