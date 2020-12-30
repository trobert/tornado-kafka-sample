package model

import java.time.Instant

data class KafkaRecord(val date: Instant, val key: String?, val partition: Int, val offset: Long, val value: String)
