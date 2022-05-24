package com.example.fp_test.net

class ServerMessage(val command: ServerCommand, private val qty: Byte?) {
    init {
        if (command.requiresQty())
            requireNotNull(qty)
    }

    /**
     * Convert message to a string readable by the server.
     * @return The server-formatted command.
     */
    fun toRawMessage(): ByteArray {
        // server expects the first byte to be the length of the entire message (excluding the
        // length byte)
        val baseLength = 2
        val cmdCode = command.code.code.toByte()

        val result: ByteArray

        if (command.requiresQty()) {
            requireNotNull(qty)
            val length = baseLength + UByte.SIZE_BYTES;

            result = ByteArray(length)
            result[0] = length.toByte()
            result[1] = cmdCode
            result[2] = qty
        } else {
            result = ByteArray(baseLength)
            result[0] = baseLength.toByte()
            result[1] = cmdCode
        }

        return result
    }

    override fun toString(): String = "ServerMessage { cmd=$command, qty=$qty }"
}