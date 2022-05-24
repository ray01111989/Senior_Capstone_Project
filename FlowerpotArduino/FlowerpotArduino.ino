/*
 * Overview
 * --------
 *
 * This is the main file for the Arduino flowerpot server. Various configuration macros
 * are defined in config.h, as well as comments explaining what each macro does.
 *
 * The required libraries are `Ethernet` (if using an Ethernet connection) and `WiFiNINA`
 * (if using a WiFi connection).
 *
 * Project Structure
 * -----------------
 *
 * This project is essentially a dummy project that allows testing Arduino code without
 * the engineers' hardware or source code. The command.h header contains the main logic
 * for networking with the app.
 *
 * Use with Engineers' Code
 * ------------------------
 *
 * To use with the engineers' flowerpot project, the following files must be placed directly
 * in their project folder:
 *
 *  - command.h
 *  - config.h
 *  - ethernet.h
 *  - wifi.h
 *  - network.h
 *
 * Network Protocol
 * ----------------
 *
 * The protocol used in the network communication is a request-response protocol. Requests
 * are sent by the client, and are formatted as follows:
 *
 *  - Byte 0: The length of the message, including Byte 0.
 *  - Byte 1: The command to perform, as defined in command.h's `enum Command`.
 *  - Byte 3: An 8-bit parameter for the command. Meaning depends on the command; see
 *            command.h for more documentation.
 *
 * Responses are returned by the Arduino, and are formatted as follows:
 *
 *  - Byte 0: The length of the message, including Byte 0.
 *  - Byte 1: The status of the command's execution, as defined in command.h's
 *            `enum ServerStatus`.
 *  - Bytes 2-5 (optional): The bytes of a 32-bit floating point (IEEE-754
 *                          single-precision) value, with little-endian byte ordering.
 *                          This value is the sensor data requested, if the command was
 *                          a request for sensor data.
 *
 * Using WiFi
 * ----------
 *
 * If ENABLE_WIFI is set to 1 in config.h, the following additional macros are expected to
 * be defined in a file called secrets.h as string literals:
 *
 *  - WIFI_SSID -- The access point SSID to connect to.
 *  - WIFI_USER -- The username to authenticate to the access point with. Set to NULL if
 *                 no username is needed.
 *  - WIFI_PASS -- The password to authenticate to the access point with.
 *
 * For now, this is mainly to allow testing on non-Ethernet connections. In the final project,
 * WiFi isn't required, but it's preferable to have since the project is meant to be used by
 * a variety of people in different settings.
 */

#include <SPI.h>
#include <IPAddress.h>

#include <limits.h>

#include "config.h"
#include "network.h"
#include "command.h"

/*
   Server info.
*/
static const ServerT server(SERVER_PORT);

/**
   Initialize GPIO pins, serial connection, and the network server.
*/
void setup() {
  pinMode(LED_PIN, OUTPUT);
  pinMode(DATA_PIN, INPUT);

  Serial.begin(BAUD_RATE);
  Serial.println(
    "Server initializing "
#ifdef ENABLE_WIFI
    "WiFi"
#else
    "Ethernet"
#endif
    " connection..."
  );

  IPAddress localIP = initServer();
  server.begin();

  Serial.print("Successfully started server listening at ");
  Serial.print(localIP);
  Serial.print(":");
  Serial.println(SERVER_PORT);
}

/**
   Handle a command from the given client. The available commands are described in the definition
   for `enum Command`.
*/
static ServerStatus process_cmd(Command cmd, uint8_t qty, float *outData) {
  ServerStatus result = STATUS_ERR;
  Serial.print(__func__);
  Serial.print(": ");

  /*
   * For some reason, using a switch statement for `cmd` doesn't work here.
   * I have no idea why -- I spent hours trying to find out why some commands
   * weren't working. If the engineers' code sometimes decides to not handle
   * commands properly, check if converting the switch-case block in their
   * command handler fixes it.
   */
  if (cmd == CMD_GET_TEMP) {
      Serial.println("GET_TEMP");
      const float coeff = 0.0625f;
      const int pinValue = analogRead(DATA_PIN);
      *outData = coeff * pinValue;
      result = STATUS_OK;
  } else if (cmd == CMD_GET_HUMIDITY) {
      Serial.println("GET_HUMIDITY");
      *outData = 2.0f;
      result = STATUS_OK;
  } else if (cmd == CMD_GET_LIGHT) {
      Serial.println("GET_LIGHT");
      *outData = 3.0f;
      result = STATUS_OK;
  } else if (cmd == CMD_GET_WATER) {
      Serial.println("GET_WATER");
      *outData = 4.0f;
      result = STATUS_OK;
  } else if (cmd == CMD_GET_MOISTURE) {
      Serial.println("GET_MOISTURE");
      *outData = 5.0f;
      result = STATUS_OK;
  } else if (cmd == CMD_GET_FERTILIZER) {
      Serial.println("GET_FERTILIZER");
      *outData = 6.0f;
      result = STATUS_OK;
  } else if (cmd == CMD_DISPENSE_WATER) {
      Serial.println("DISPENSE_WATER");

      if (qty <= 3) {
        digitalWrite(LED_PIN, HIGH);
        delay(qty * 1000);
        digitalWrite(LED_PIN, LOW);
        result = STATUS_OK;
      }
  } else if (cmd == CMD_QUIT) {
      Serial.println("CMD_QUIT");
      result = STATUS_OK;
  } else {
      Serial.println("Unknown command");
  }

  return result;
}

void loop() {
  serverMaintain();
  ClientT client = server.available();

  if (client.available())
    handleCommand(client, 1000, process_cmd);
}
