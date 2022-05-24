package com.example.fp_test.net

enum class ServerStatus(val code: UByte) {
    OK(0u),    ///< Server completed command successfully
    ERR(255u); ///< An error occurred while retrieving or executing command

    companion object {
        fun fromUByte(b: UByte): ServerStatus? = values().firstOrNull { it.code == b }
    }
}
