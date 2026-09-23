package sdkbase.abi

/** A minimal line diff for the apiCheck failure message: `-` lines only in [old], `+` only in [new]. */
internal fun lineDiff(old: List<String>, new: List<String>): List<String> {
    val lcs = Array(old.size + 1) { IntArray(new.size + 1) }
    for (i in old.indices.reversed()) {
        for (j in new.indices.reversed()) {
            lcs[i][j] = if (old[i] == new[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
    }
    val out = mutableListOf<String>()
    var i = 0
    var j = 0
    while (i < old.size && j < new.size) {
        when {
            old[i] == new[j] -> { i++; j++ }
            lcs[i + 1][j] >= lcs[i][j + 1] -> out += "- ${old[i++]}"
            else -> out += "+ ${new[j++]}"
        }
    }
    while (i < old.size) out += "- ${old[i++]}"
    while (j < new.size) out += "+ ${new[j++]}"
    return out
}
