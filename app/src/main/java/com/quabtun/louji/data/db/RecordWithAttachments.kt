package com.quabtun.louji.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class RecordWithAttachments(
    @Embedded val record: RecordEntity,
    @Relation(parentColumn = "id", entityColumn = "recordId")
    val attachments: List<AttachmentEntity>,
)
