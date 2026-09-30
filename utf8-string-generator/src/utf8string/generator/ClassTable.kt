/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.generator

const val BLOCK_SIZE: Int = 64

// The code points that the index covers: planes 0 and 1, and U+E0000..U+E0FFF (tags and variation selectors). Every
// other code point must be Other. A block is indexed by cp shr 6 below U+20000, and by 2048 + ((cp shr 6) and 0x3F) in
// U+E0000..U+E0FFF, which is what the UTF-8 bytes of the code point give without decoding it.
val INDEXED_BLOCKS: List<Int> = (0..<0x20000 step BLOCK_SIZE) + (0xE0000..<0xE1000 step BLOCK_SIZE)

// The class of each code point in 64-entry blocks: the block of the code point starts at data[index[...]]. Blocks
// overlap in data where the end of one equals the start of another.
class ClassTable(val index: IntArray, val data: IntArray)

fun buildClassTable(classes: IntArray): ClassTable {
    val indexed = BooleanArray(CODE_POINT_LIMIT).also { for (start in INDEXED_BLOCKS) it.fill(true, start, start + BLOCK_SIZE) }
    for (codePoint in 0..<CODE_POINT_LIMIT) {
        check(indexed[codePoint] || classes[codePoint] == GraphemeClass.Other.ordinal) {
            "U+%04X is not Other but outside the indexed blocks".format(codePoint)
        }
    }
    val blocks = INDEXED_BLOCKS.map { classes.copyOfRange(it, it + BLOCK_SIZE).toList() }
    val unique = blocks.distinct()
    val data = ArrayList<Int>()
    val offsets = IntArray(unique.size)
    for (chain in chainByOverlap(unique)) {
        var overlap = 0
        for ((i, block) in chain.withIndex()) {
            offsets[block] = data.size - overlap
            data += unique[block].subList(overlap, BLOCK_SIZE)
            if (i + 1 < chain.size) overlap = overlap(unique[block], unique[chain[i + 1]])
        }
    }
    val offsetOf = unique.withIndex().associate { (i, block) -> block to offsets[i] }
    val table = ClassTable(IntArray(blocks.size) { offsetOf.getValue(blocks[it]) }, data.toIntArray())
    for ((i, start) in INDEXED_BLOCKS.withIndex()) {
        for (j in 0..<BLOCK_SIZE) check(table.data[table.index[i] + j] == classes[start + j]) { "Wrong table at ${start + j}" }
    }
    return table
}

// The greedy shortest-superstring heuristic: join the pairs of blocks with the largest overlap first, never giving a
// block two successors or two predecessors and never closing a cycle. Ties go to the lower block numbers.
private fun chainByOverlap(blocks: List<List<Int>>): List<List<Int>> {
    val pairs = ArrayList<Triple<Int, Int, Int>>()
    for (a in blocks.indices) for (b in blocks.indices) {
        if (a != b) overlap(blocks[a], blocks[b]).let { if (it > 0) pairs += Triple(it, a, b) }
    }
    pairs.sortWith(compareByDescending<Triple<Int, Int, Int>> { it.first }.thenBy { it.second }.thenBy { it.third })
    val next = IntArray(blocks.size) { -1 }
    val previous = IntArray(blocks.size) { -1 }
    val chainOf = IntArray(blocks.size) { it }
    fun root(block: Int): Int {
        var b = block
        while (chainOf[b] != b) b = chainOf[b]
        return b
    }
    for ((_, a, b) in pairs) {
        if (next[a] != -1 || previous[b] != -1 || root(a) == root(b)) continue
        next[a] = b
        previous[b] = a
        chainOf[root(b)] = root(a)
    }
    return blocks.indices.filter { previous[it] == -1 }.map { head -> generateSequence(head) { next[it].takeIf { n -> n != -1 } }.toList() }
}

// The length of the longest proper suffix of a that is a prefix of b.
private fun overlap(a: List<Int>, b: List<Int>): Int {
    for (length in BLOCK_SIZE - 1 downTo 1) {
        if ((0..<length).all { a[BLOCK_SIZE - length + it] == b[it] }) return length
    }
    return 0
}
