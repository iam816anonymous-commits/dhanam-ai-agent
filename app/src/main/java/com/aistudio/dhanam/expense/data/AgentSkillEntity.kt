package com.aistudio.dhanam.expense.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A safe, data-only skill: an exact expense description mapped to a validated category.
 * It cannot contain executable code, regular expressions, URLs or arbitrary tool permissions.
 */
@Entity(
    tableName = "agent_skills",
    indices = [
        Index(value = ["skill_key", "version"], unique = true),
        Index(value = ["status"])
    ]
)
data class AgentSkillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "skill_key") val skillKey: String,
    val version: Int,
    val title: String,
    @ColumnInfo(name = "match_text") val matchText: String,
    val category: String,
    @ColumnInfo(name = "example_count") val exampleCount: Int,
    @ColumnInfo(name = "test_pass_count") val testPassCount: Int,
    @ColumnInfo(name = "test_total_count") val testTotalCount: Int,
    val status: String,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "approved_at_epoch_millis") val approvedAtEpochMillis: Long? = null
) {
    init {
        require(skillKey.matches(Regex("[a-f0-9]{64}")))
        require(version >= 1)
        require(title.length in 1..120)
        require(matchText.length in 1..120)
        require(category in setOf("Food", "Transport", "Shopping", "Bills", "Health", "Education", "Other"))
        require(exampleCount >= 3)
        require(testTotalCount >= 3)
        require(testPassCount in 0..testTotalCount)
        require(status in setOf("PROPOSED", "ACTIVE", "REJECTED", "DISABLED"))
    }
}
