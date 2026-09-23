// Stand-in for the Arduino environment so command.h can be compiled and tested on a computer.
// It replaces network.h, which normally pulls in the WiFi/Ethernet libraries.
#pragma once
#include <cstdint>
#include <cstddef>
#include <cstring>
#include <memory>
#include <vector>

typedef unsigned char byte;
#define HEX 16

// What a network client would send and receive; shared between copies like a real socket
struct MockState {
  std::vector<uint8_t> in;   // bytes the "remote client" sent
  size_t pos = 0;            // how many were read already
  std::vector<uint8_t> out;  // bytes the server wrote back
  bool stopped = false;
};

struct ClientT {
  std::shared_ptr<MockState> s = std::make_shared<MockState>();
  int available() { return (int)(s->in.size() - s->pos); }
  int read() { return s->pos < s->in.size() ? s->in[s->pos++] : -1; }
  // Like WiFiNINA/Ethernet: copies up to n bytes, but never more than what has arrived
  int read(void *buf, size_t n) {
    size_t avail = s->in.size() - s->pos;
    size_t k = n < avail ? n : avail;
    memcpy(buf, &s->in[s->pos], k);
    s->pos += k;
    return (int)k;
  }
  size_t write(const void *b, size_t n) { auto p = (const uint8_t *)b; s->out.insert(s->out.end(), p, p + n); return n; }
  void flush() {}
  void stop() { s->stopped = true; }
};

struct SerialT { template <class... A> void print(A...) {} };
static SerialT Serial;
