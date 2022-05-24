/**
 * Ethernet-specialized networking functions. Must not be included alongside `wifi.h`.
 *
 * Dependencies
 * ------------
 *
 * Functionality in this header depends on the Ethernet library.
 */

#ifndef ETHERNET_H_
#define ETHERNET_H_

#include <SPI.h>
#include <Ethernet.h>
#include <IPAddress.h>

#include "util.h"

/*
 * Success value returned by Ethernet.begin().
 */
#define ETH_BEGIN_SUCCESS 1

typedef EthernetServer ServerT;
typedef EthernetClient ClientT;

static constexpr byte mac[] = { 0xa8, 0x61, 0x0a, 0xae, 0x6a, 0x44 };

/*
 * Ethernet connections need to be maintained to avoid DHCP lease issues.
 */ 
static inline void serverMaintain() {
  return Ethernet.maintain();
}

/*
 * Set up the Arduino's network connection via Ethernet.
 */
static inline IPAddress initServer() {
  if (Ethernet.begin(mac) != ETH_BEGIN_SUCCESS) {
    if (Ethernet.hardwareStatus() == EthernetNoHardware)
      fatalError("Ethernet shield was not found.");
    else if (Ethernet.linkStatus() != LinkON)
      fatalError("Ethernet cable is not connected.");
    else
      fatalError("Unknown error while initializing Ethernet connection.");
  }

  return Ethernet.localIP();
}


#endif // ETHERNET_H_
