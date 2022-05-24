/**
 * WiFi-specialized networking functions. Must not be included alongside `ethernet.h`.
 *
 * Dependencies
 * ------------
 *
 * Functionality in this header depends on the WiFiNINA library.
 */

#ifndef WIFI_H_
#define WIFI_H_

#include <SPI.h>
#include <WiFiNINA.h>
#include <IPAddress.h>

#include "config.h"
#include "secrets.h"
#include "util.h"

#if !defined(WIFI_SSID) || !defined(WIFI_PASS)
# error "Must define WIFI_SSID and WIFI_PASS to use WiFi."
#endif

typedef WiFiServer ServerT;
typedef WiFiClient ClientT;

#ifdef WIFI_USER
# define WIFI_CONNECT() WiFi.beginEnterprise(WIFI_SSID, WIFI_USER, WIFI_PASS)
#else
# pragma message "Assuming access points are non-enterprise. Define WIFI_USER macro in secrets.h for enterprise connections."
# define WIFI_CONNECT() WiFi.begin(WIFI_SSID, WIFI_PASS)
#endif

/*
 * No maintenance is needed for WiFi connections.
 */
static inline void serverMaintain() {
}

/*
 * Set up the Arduino's network connection via WiFi.
 */
static inline IPAddress initServer() {
  if (WiFi.status() == WL_NO_MODULE)
    fatalError("Failed to find WiFi module.");

  // check if the firmware is up-to-date
  String fv = WiFi.firmwareVersion();

  if (fv < WIFI_FIRMWARE_LATEST_VERSION)
    warn("Outdated firmware detected in WiFi module.");

  byte wifiAttempts = 0;

  // try to connect to the configured access point
  while (WIFI_CONNECT() != WL_CONNECTED) {
    ++wifiAttempts;

    // error out if too many connection failures occur
    if (wifiAttempts > WIFI_FAIL_LIMIT)
      fatalError("Failed to initialize WiFi connection.");

    Serial.print("Failed to connect to WiFi... Trying again in ");
    Serial.print(WIFI_FAIL_DELAY_MS);
    Serial.println(" ms");

    delay(WIFI_FAIL_DELAY_MS);
  }

  delay(WIFI_STARTUP_DELAY_MS);
  return WiFi.localIP();
}

#endif // WIFI_H_
