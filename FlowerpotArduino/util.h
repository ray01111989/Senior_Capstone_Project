/*
 * util.h: General utility macros.
 */

#ifndef UTIL_H_
#define UTIL_H_

/**
 * Print the given error message and perform a software reset.
 */
#define fatalError(msg)            \
  do {                             \
    Serial.print("Fatal Error: "); \
    Serial.println(msg);           \
    while (true);                  \
  } while (0)

/**
 * Print a warning message.
 */
#define warn(msg)              \
  do {                         \
    Serial.print("Warning: "); \
    Serial.println(msg);       \
  } while (0)

#endif // UTIL_H_
