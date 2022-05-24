package com.example.fp_test.net

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime

class ServerResponse(rawResponse: ByteArray) {
    val status: ServerStatus
    private val data: Float?

    init {
        val minRspLength = 2
        val dataRspLength = minRspLength + Float.SIZE_BYTES
        val length = rawResponse[0].toInt()

        require(rawResponse.size == length) {
            "Malformed server response; expected response length ${rawResponse.size}, got $length"
        }

        val tmpStatus = ServerStatus.fromUByte(rawResponse[1].toUByte())
        checkNotNull(tmpStatus) { "Failed to convert value to status code: ${rawResponse[1]}" }

        status = tmpStatus
        data = if (status == ServerStatus.OK && length > minRspLength) {
            check(length == dataRspLength)
            val buf = ByteBuffer
                .wrap(rawResponse.copyOfRange(2, length))
                .order(ByteOrder.LITTLE_ENDIAN)
            buf.getFloat(0)
        } else {
            null
        }
    }

    fun<T> processData(dataHandler: (Float, LocalDateTime) -> T): T? {
        return if (data != null) {
            val now = LocalDateTime.now()
            dataHandler(data, now)
        } else {
            null
        }
    }

    override fun toString(): String = "ServerResponse { status=$status, data=$data }"
}
