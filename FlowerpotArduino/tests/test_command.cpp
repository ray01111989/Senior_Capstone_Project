// Tests for handleCommand() in command.h, using the mock client from mock/network.h.
// Run it with: sh tests/run_tests.sh   (from the FlowerpotArduino folder)
#include <cstdio>
#include <cstring>
#include "network.h"
#include "command.h"

static int failures = 0;
static void check(const char *name, bool ok) { printf("%s  %s\n", ok ? "PASS" : "FAIL", name); if (!ok) failures++; }

// The device side: returns 12.5 for a temperature request, records the last quantity, refuses everything else
static uint8_t lastQty = 0;
static int calls = 0;
static ServerStatus device(Command cmd, uint8_t qty, float *out) {
  calls++;
  lastQty = qty;
  if (cmd == CMD_GET_TEMP) { *out = 12.5f; return STATUS_OK; }
  if (cmd == CMD_DISPENSE_WATER || cmd == CMD_QUIT) return STATUS_OK;
  return STATUS_ERR;   // unknown command: does not set *out
}

static ClientT clientSending(std::initializer_list<int> bytes) {
  ClientT c;
  for (int b : bytes) c.s->in.push_back((uint8_t)b);
  return c;
}

int main() {
  { // a valid temperature request: [length=2, 't']
    ClientT c = clientSending({2, 't'}); calls = 0;
    bool ok = handleCommand(c, 1000, device);
    float f; if (c.s->out.size() == 6) memcpy(&f, &c.s->out[2], 4); else f = -1;
    check("GET_TEMP: handled, response is 6 bytes [6, OK, float]", ok && c.s->out.size() == 6 && c.s->out[0] == 6 && c.s->out[1] == STATUS_OK);
    check("GET_TEMP: the float in the response is 12.5", f == 12.5f);
  }
  { // dispense 3 mL: [3, 'W', 3]
    ClientT c = clientSending({3, 'W', 3}); lastQty = 99;
    bool ok = handleCommand(c, 1000, device);
    check("DISPENSE_WATER: handled, quantity 3 passed on", ok && lastQty == 3);
    check("DISPENSE_WATER: response is [2, OK] (no data)", c.s->out.size() == 2 && c.s->out[0] == 2 && c.s->out[1] == STATUS_OK);
  }
  { // dispense without a quantity: [2, 'W']
    ClientT c = clientSending({2, 'W'}); calls = 0;
    bool ok = handleCommand(c, 1000, device);
    check("DISPENSE_WATER without a quantity: rejected, device not called", !ok && calls == 0 && c.s->out.size() == 2 && c.s->out[1] == STATUS_ERR);
  }
  { // THE BUG: length 0 followed by lots of bytes
    ClientT c = clientSending({0}); for (int i = 0; i < 40; i++) c.s->in.push_back(0x41); calls = 0;
    bool ok = handleCommand(c, 1000, device);
    check("length 0 + 40 bytes: rejected with an error response, device not called (no overflow under ASan)", !ok && calls == 0 && c.s->out.size() == 2 && c.s->out[1] == STATUS_ERR);
  }
  { // length 255, and nothing at all
    ClientT c1 = clientSending({255, 1, 2, 3}); calls = 0;
    check("length 255: rejected", !handleCommand(c1, 1000, device) && calls == 0);
    ClientT c2 = clientSending({});
    check("no bytes at all: rejected", !handleCommand(c2, 1000, device) && c2.s->out.size() == 2 && c2.s->out[1] == STATUS_ERR);
  }
  { // biggest accepted length is 19 (18 payload bytes); 20 is too big for the buffer
    ClientT c1 = clientSending({19}); for (int i = 0; i < 18; i++) c1.s->in.push_back('x'); calls = 0;
    check("length 19 (the largest that fits): handled", handleCommand(c1, 1000, device) && calls == 1);
    ClientT c2 = clientSending({20}); for (int i = 0; i < 19; i++) c2.s->in.push_back('x'); calls = 0;
    check("length 20: rejected", !handleCommand(c2, 1000, device) && calls == 0);
  }
  { // message shorter than announced: says 5 bytes but only 2 follow
    ClientT c = clientSending({5, 't', 1}); calls = 0;
    check("message shorter than its length byte: rejected", !handleCommand(c, 1000, device) && calls == 0);
  }
  { // length 1 (no command byte at all)
    ClientT c = clientSending({1}); calls = 0;
    bool ok = handleCommand(c, 1000, device);
    check("length 1 (no command): answered with an error status", ok && c.s->out.size() >= 2 && c.s->out[1] == STATUS_ERR);
  }
  { // unknown command: the response carries data bytes, which must be 0 and not leftover memory
    ClientT c = clientSending({2, 'z'});
    bool ok = handleCommand(c, 1000, device);
    bool zeros = c.s->out.size() == 6 && c.s->out[2] == 0 && c.s->out[3] == 0 && c.s->out[4] == 0 && c.s->out[5] == 0;
    check("unknown command: ERR status, data bytes are all 0 (nothing uninitialized sent)", ok && c.s->out[1] == STATUS_ERR && zeros);
  }
  { // quit closes the connection
    ClientT c = clientSending({2, 'Q'});
    bool ok = handleCommand(c, 1000, device);
    check("QUIT: handled and the connection is closed", ok && c.s->stopped && c.s->out.size() == 2);
  }
  printf(failures ? "\n%d FAILED\n" : "\nall passed\n", failures);
  return failures != 0;
}
