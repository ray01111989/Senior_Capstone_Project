package com.example.fp_test.net

/**
 * A command to send to the server.
 */
enum class ServerCommand(val code: Char) {
    /**
     * Get ambient temperature
     */
    GET_TEMP('t'),

    /**
     * Get ambient air relative humidity
     */
    GET_HUMIDITY('h'),

    /**
     * Get light level
     */
    GET_LIGHT('l'),

    /**
     * Get reservoir water level
     */
    GET_WATER('w'),

    /**
     * Get soil moisture content (%)
     */
    GET_MOISTURE('m'),

    /**
     * Get liquid fertilizer level
     */
    GET_FERTILIZER('f'),

    /**
     * Dispense a quantity of water
     */
    DISPENSE_WATER('W'),

    /**
     * Close the connection
     */
    QUIT('Q');

    fun requiresQty(): Boolean = (this == DISPENSE_WATER)
}
