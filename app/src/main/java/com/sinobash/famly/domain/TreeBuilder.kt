package com.sinobash.famly.domain

import com.sinobash.famly.data.local.PersonEntity
import com.sinobash.famly.data.local.RelationEntity
import com.sinobash.famly.data.local.RelationType

data class TreeNode(val person: PersonEntity, val generation: Int, val column: Float)
data class TreeEdge(val fromId: Long, val toId: Long, val isSpouse: Boolean)
data class TreeLayout(
    val nodes: List<TreeNode>,
    val edges: List<TreeEdge>,
    val generationCount: Int,
    val columnCount: Int
) {
    companion object { val EMPTY = TreeLayout(emptyList(), emptyList(), 0, 0) }
}

/** Hubungan keluarga satu orang (untuk layar detail). */
data class FamilyOf(
    val parents: List<PersonEntity>,
    val spouses: List<Pair<PersonEntity, RelationEntity>>,
    val children: List<PersonEntity>,
    val parentRelations: Map<Long, RelationEntity>,
    val childRelations: Map<Long, RelationEntity>
)

object TreeBuilder {

    fun familyOf(personId: Long, persons: List<PersonEntity>, relations: List<RelationEntity>): FamilyOf {
        val byId = persons.associateBy { it.id }
        val parentRels = relations.filter { it.type == RelationType.PARENT && it.toId == personId }
        val childRels = relations.filter { it.type == RelationType.PARENT && it.fromId == personId }
        val spouseRels = relations.filter { it.type == RelationType.SPOUSE && (it.fromId == personId || it.toId == personId) }
        return FamilyOf(
            parents = parentRels.mapNotNull { byId[it.fromId] },
            spouses = spouseRels.mapNotNull { r ->
                byId[if (r.fromId == personId) r.toId else r.fromId]?.let { it to r }
            },
            children = childRels.mapNotNull { byId[it.toId] },
            parentRelations = parentRels.associateBy { it.fromId },
            childRelations = childRels.associateBy { it.toId }
        )
    }

    /**
     * Layout sederhana berbasis generasi:
     * 1. generasi = jarak terdalam dari leluhur (pasangan disamakan generasinya)
     * 2. urutan kolom per generasi memakai rata-rata posisi orang tua (barycenter),
     *    pasangan diletakkan bersebelahan, lalu tiap baris dipusatkan.
     */
    fun build(persons: List<PersonEntity>, relations: List<RelationEntity>): TreeLayout {
        if (persons.isEmpty()) return TreeLayout.EMPTY
        val ids = persons.map { it.id }.toSet()
        val rels = relations.filter { it.fromId in ids && it.toId in ids }

        val parentsOf = HashMap<Long, MutableList<Long>>()
        val spousesOf = HashMap<Long, MutableList<Long>>()
        rels.forEach { r ->
            if (r.type == RelationType.PARENT) {
                parentsOf.getOrPut(r.toId) { mutableListOf() }.add(r.fromId)
            } else {
                spousesOf.getOrPut(r.fromId) { mutableListOf() }.add(r.toId)
                spousesOf.getOrPut(r.toId) { mutableListOf() }.add(r.fromId)
            }
        }

        // 1. generasi
        val gen = HashMap<Long, Int>().apply { ids.forEach { put(it, 0) } }
        val cap = persons.size
        for (i in 0..cap) {
            var changed = false
            for (r in rels) {
                if (r.type == RelationType.PARENT) {
                    val g = gen.getValue(r.fromId) + 1
                    if (g <= cap && gen.getValue(r.toId) < g) { gen[r.toId] = g; changed = true }
                } else {
                    val m = maxOf(gen.getValue(r.fromId), gen.getValue(r.toId))
                    if (gen.getValue(r.fromId) != m) { gen[r.fromId] = m; changed = true }
                    if (gen.getValue(r.toId) != m) { gen[r.toId] = m; changed = true }
                }
            }
            if (!changed) break
        }

        // 2. kolom
        val rows = persons.groupBy { gen.getValue(it.id) }.toSortedMap()
        val columnCount = rows.values.maxOf { it.size }
        val x = HashMap<Long, Float>()
        val nodes = ArrayList<TreeNode>()

        fun parentKey(id: Long): Float? =
            parentsOf[id].orEmpty().mapNotNull { x[it] }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

        rows.forEach { (g, list) ->
            val sorted = list.sortedWith(
                compareBy<PersonEntity> {
                    parentKey(it.id)
                        ?: spousesOf[it.id].orEmpty().mapNotNull { s -> parentKey(s) }.minOrNull()
                        ?: Float.MAX_VALUE
                }.thenBy { it.fullName.lowercase() }
            )
            // pasangan bersebelahan
            val placed = LinkedHashSet<Long>()
            val byId = list.associateBy { it.id }
            sorted.forEach { p ->
                if (placed.add(p.id)) {
                    spousesOf[p.id].orEmpty().forEach { s -> if (s in byId) placed.add(s) }
                }
            }
            val offset = (columnCount - placed.size) / 2f
            placed.forEachIndexed { index, id ->
                val col = index + offset
                x[id] = col
                nodes += TreeNode(byId.getValue(id), g, col)
            }
        }

        val edges = rels.map { TreeEdge(it.fromId, it.toId, it.type == RelationType.SPOUSE) }
        return TreeLayout(nodes, edges, rows.size, columnCount)
    }
}