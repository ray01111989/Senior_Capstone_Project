/*
 * config.h: Configuration macros to change various aspects of the server's
 *           behavior.
 */

#ifndef CONFIG_H_
#define CONFIG_H_

/**
 * The maximum number of characters allowed in a command.
 */
#define MAX_CMD_LENGTH 20

/**
 * How many milliseconds to wait after initiating a WiFi connection.
 */
#define WIFI_STARTUP_DELAY_MS 10000

/**
 * How many milliseconds to wait before retrying after connecting to WiFi fails.
 */
#define WIFI_FAIL_DELAY_MS 500

/**
 * How many attempts to make before giving up when connecting to WiFi fails.
 */
#define WIFI_FAIL_LIMIT 3

/**
 * The baud rate of the serial port.
 */
#define BAUD_RATE 9600

/**
 * Which port to have the server listen on.
 */
#define SERVER_PORT 2259

/**
 * Whether or not to use a WiFi connection. If undefined, an Ethernet connection is assumed.
 * Otherwise, a WiFi connection is assumed.
 */
#define ENABLE_WIFI

/*
 * The output pin that an LED is connected to.
 */
#define LED_PIN 0

/*
 * The input data pin that a sensor is connected to.
 */
#define DATA_PIN A0

#endif // CONFIG_H_
