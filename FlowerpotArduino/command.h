#ifndef COMMAND_H_
#define COMMAND_H_

#include <stdbool.h>
#include <string.h>

#include "network.h"

/**
 * Max number of bytes in a command; not important to the engineering
 * side of the project.
 */
#define MAX_CMD_LENGTH 20

/*
 * Logging macro used within this header file; not important to the
 * engineering side of the project.
 */
#ifdef NDEBUG
# define LOG_DEBUG(...) ((void)0)
#else
# define LOG_DEBUG(...) Serial.print(__VA_ARGS__)
#endif

/**
 * The available commands for connected clients. In the protocol, this is the
 * second byte of the client request. Each command may optionally take an 8-bit
 * quantity, referred to as `qty`.
 */
enum Command {
  /// Get ambient temperature
  CMD_GET_TEMP = 't',

  /// Get ambient air relative humidity
  CMD_GET_HUMIDITY = 'h',

  /// Get light level
  CMD_GET_LIGHT = 'l',

  /// Get reservoir water level
  CMD_GET_WATER = 'w',

  /// Get soil moisture content (%)
  CMD_GET_MOISTURE = 'm',

  /// Get liquid fertilizer level
  CMD_GET_FERTILIZER = 'f',

  /// Dispense `qty` mL of water
  CMD_DISPENSE_WATER = 'W',

  /// Close the connection with the client
  CMD_QUIT = 'Q',
};

/**
 * Check if the given command sends data in the response.
 */
static inline bool cmdHasData(Command cmd) {
  switch (cmd) {
  case CMD_DISPENSE_WATER:
  case CMD_QUIT:
    return false;
  default:
    return true;
  }
}

/**
 * Check if the given command requires a quantity.
 */
static inline bool cmdRequiresQty(Command cmd) {
  switch (cmd) {
  case CMD_DISPENSE_WATER:
    return true;
  default:
    return false;
  }
}

/**
 * The server's status to send back to the client. This is the second byte of
 * the server response.
 */
enum ServerStatus {
  /// Server completed command successfully
  STATUS_OK  = 0,

  /// An error occurred while retrieving or executing command
  STATUS_ERR = 255,
};

static inline void printHexBuf(const char *buf, size_t bufLen) {
  for (size_t i = 0; i < bufLen; ++i) {
    LOG_DEBUG(buf[i], HEX);
    LOG_DEBUG(" ");
  }

  LOG_DEBUG("(length=");
  LOG_DEBUG(bufLen);
  LOG_DEBUG(")\n");
}

/**
 * Handle a single command from the client.
 * 
 * Parameters
 * ----------
 * 
 * - `client`: The client to retrieve the command from.
 * - `timeoutMs`: The number of milliseconds to time out after starting the client read. Currently
 *                unimplemented.
 * - `respond`: A function pointer that returns the status of the command after performing it. The
 *              callback takes three parameters:
 *     1. `cmd`, the command from the client.
 *     2. `qty`, a quantity for the command (unit depends on the command). E.g., watering the plant
 *        with 3 mL of water would result in `qty == 3`.
 *     3. `data`, a pointer to a `float` to write data to (e.g., converted sensor input). This must
 *        be written to if the command is a request for data (e.g., GET_TEMP).
 *
 * Example
 * -------
 * 
 * An example usage of this function is as follows:
 * 
 * ```
 * ServerStatus cmdRespond(Command cmd, uint8_t qty, float *data) {
 *   switch (cmd) {
 *   case CMD_GET_TEMP:
 *     *data = temperature;
 *     return STATUS_OK;
 *   case CMD_GET_WATER:
 *     *data = waterLevel;
 *     return STATUS_OK;
 *   case CMD_DISPENSE_WATER:
 *     dispenseWaterML(qty);
 *     return STATUS_OK;
 *   // ...
 *   default:
 *     return STATUS_ERR;
 *   }
 * }
 * 
 * void loop() {
 *   ClientT client = server.available();
 *   
 *   if (client.available()) {
 *     const bool result = handleCommand(client, 500, cmdRespond);
 *     // do something with `result`...
 *   }
 * }
 * ```
 *
 * Returns
 * -------
 * 
 * `true` if a command was successfully read from the client, or `false` otherwise.
 *
 */
static inline bool handleCommand(ClientT client, uint32_t timeoutMs,
    ServerStatus (*process_cmd)(Command cmd, uint8_t qty, float *outData)) {
  // TODO: Add connection timeout
  (void)timeoutMs;

  static const char errResponse[] = { 2, STATUS_ERR };

  char buf[MAX_CMD_LENGTH + 1] = {0};

  // The first byte of a request is the length of the whole message, including this byte. It is read
  // into an int because read() returns -1 when nothing could be read.
  const int firstByte = client.read();

  // Reject a missing length, a length of 0 and a message that does not fit in buf BEFORE reading anything
  // else. A length of 0 used to slip through: "cmdBytes - 1" became -1, which converts to a huge size, and
  // read() then copied everything the client sent into the 21-byte buf (a stack buffer overflow that any
  // client on the network could trigger).
  if (firstByte < 1 || firstByte >= (int)(sizeof buf - 1)) {
    client.write(errResponse, errResponse[0]);
    client.flush();
    return false;
  }

  const byte cmdBytes = (byte)firstByte;

  // read the rest of the message: cmdBytes - 1 bytes (the length byte itself was already read)
  const int bytesRead = client.read(buf, cmdBytes - 1);
  LOG_DEBUG("Raw command: ");
  printHexBuf(buf, bytesRead > 0 ? (size_t)bytesRead : 0);

  // reject commands that were shorter than they announced
  if (bytesRead != cmdBytes - 1) {
    client.write(errResponse, errResponse[0]);
    client.flush();
    return false;
  }

  // take the first character of the message as the command type,
  // and the rest of the message as the command arguments
  Command cmd = (Command)buf[0];

  // reject commands with missing quantities
  if (cmdRequiresQty(cmd) && bytesRead < 2) {
    client.write(errResponse, errResponse[0]);
    client.flush();
    return false;
  }

  // the quantity is the byte after the command, if the message has one
  const uint8_t qty = (bytesRead > 1) ? buf[1] : 0;

  LOG_DEBUG("Handling command: ");
  LOG_DEBUG((char)cmd);
  LOG_DEBUG(" (qty=");
  LOG_DEBUG(qty);
  LOG_DEBUG(")\n");

  // start at 0 so that a command that does not set the value (for example an unknown command) never sends
  // uninitialized memory back to the client
  float data = 0.0f;
  const ServerStatus responseStatus = process_cmd(cmd, qty, &data);

  // set the length to 2 by default -- length and status.
  // (This used to be memcpy(buf, 0, ...), which copies FROM address 0 instead of clearing the buffer.)
  memset(buf, 0, sizeof buf);
  buf[0] = 2;
  buf[1] = responseStatus;

  // add the data byte if data was requested
  if (cmdHasData(cmd)) {
    buf[0] += sizeof data;
    LOG_DEBUG("Data=");
    LOG_DEBUG(data);
    LOG_DEBUG("\n");
    memcpy(&buf[2], &data, sizeof data);
  }

  LOG_DEBUG("Raw response: ");
  printHexBuf(buf + 1, buf[0] - 1);
  client.write(buf, buf[0]);
  client.flush();

  if (cmd == CMD_QUIT)
    client.stop();

  return true;
}

#endif // COMMAND_H_
