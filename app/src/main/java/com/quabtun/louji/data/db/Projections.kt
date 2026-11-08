package com.quabtun.louji.data.db

/** Lightweight projections used by the radar, insights and CSV export queries. */
data class FloorRow(
    val floor: String = "",
    val createdAt: Long = 0L,
)

data class CustomerAgg(
    val total: Int = 0,
    val noStairs: Int = 0,
    val willComeDown: Int = 0,
    val warnings: Int = 0,
)
