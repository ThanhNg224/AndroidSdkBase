package {{pkg}}.session

import {{ns}}.core.error.SdkError

/** Immutable snapshot of the {{pascal}} flow. The UI renders this and nothing else. */
public class {{pascal}}State(
    public val phase: Phase,
    public val error: SdkError?,
) {
    public enum class Phase { Idle, Active, Completed, Failed }

    /** A plain class, not a `data class` (ABI: `copy`/`componentN` would freeze the property list
     * for every consumer). `internal`: only engine code inside this module needs to derive a new
     * state; a host only ever reads the properties above. */
    internal fun copy(
        phase: Phase = this.phase,
        error: SdkError? = this.error,
    ): {{pascal}}State = {{pascal}}State(phase = phase, error = error)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is {{pascal}}State) return false
        return phase == other.phase && error == other.error
    }

    override fun hashCode(): Int {
        var result = phase.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "{{pascal}}State(phase=$phase, error=$error)"

    public companion object {
        public fun initial(): {{pascal}}State = {{pascal}}State(phase = Phase.Idle, error = null)
    }
}
