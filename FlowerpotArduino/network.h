/**
 * Header to abstract network connections away from Ethernet and WiFi.
 * 
 * Configuration Macros
 * --------------------
 * 
 * - `ENABLE_WIFI`: If defined, the header uses WiFi for network connections. Otherwise,
 *                  Ethernet is used.
 *
 * In addition, if `ENABLE_WIFI` is defined, the following additional macros are expected to be
 * be defined as string literals:
 *
 * - `WIFI_SSID`: The access point SSID to connect to.
 * - `WIFI_USER`: The username to authenticate to the access point with. Set to `NULL` if
 *                   no username is needed.
 * - `WIFI_PASS`: The password to authenticate to the access point with.
 *  
 * Typedefs
 * --------
 * 
 * - `ServerT`: The type of the system's server. Has a constructor with the signature
 *   `ServerT(uint16_t port)`, as well as methods shared by the `Server` classes from the
 *   Arduino libraries WiFiNINA and Ethernet (e.g., `ServerT.available()`).
 * - `ClientT`: The type of the system's client(s). Has methods shared by the `Client` classes
 *   from the Arduino libraries WiFiNINA and Ethernet (e.g., `ClientT.println()`).
 *
 * Dependencies
 * ------------
 *
 * If `ENABLE_WIFI` is defined, dependencies are those of `wifi.h`. Otherwise, dependencies are
 * those of `ethernet.h`.
 */

#ifndef NETWORK_H_
#define NETWORK_H_

/**
 * Perform any connection maintenance required. Must be called once per Arduino `loop()`
 * invocation.
 */
static inline void serverMaintain();

/**
 * Perform any setup needed for the network connection and return the server's local IP address.
 */
static inline IPAddress initServer();

/*
 * Include the correct header depending on what type of connection is configured.
 */
#ifdef ENABLE_WIFI
# include "wifi.h"
#else
# include "ethernet.h"
#endif

#endif // NETWORK_H_
