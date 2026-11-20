"""Optional bounded control-only recovery; never synthesize a data/RAM request."""
import struct


class AckRecovery:
    def __init__(self):
        self.buffers = {"tx": bytearray(), "rx": bytearray()}
        self.last_ack = None
        self.replays = 0
        self.last_rx_data = None

    def feed(self, direction, data):
        buf = self.buffers[direction]
        buf.extend(data)
        result = []
        while len(buf) >= 16:
            if buf[:4] not in (b"iiii", b"0000"):
                del buf[0]
                continue
            _, kind, count, packet_id, checksum = struct.unpack_from("<IHHII", buf)
            control = buf[:4] == b"iiii"
            if count > 16384 or (control and (count or kind not in (4, 5, 6))):
                del buf[0]
                continue
            length = 16 if control else 17+count
            if len(buf) < length:
                break
            packet = bytes(buf[:length])
            if not control and (packet[-1] != 170 or sum(packet[16:-1]) != checksum):
                del buf[0]
                continue
            del buf[:length]
            if control and kind == 6:
                self.last_ack = None
                self.replays = 0
                self.last_rx_data = None
            elif direction == "rx" and not control and packet != self.last_rx_data:
                # A new response starts a new bounded exchange even when its
                # ACK ID equals a much earlier (alternating-ID) exchange.
                self.last_rx_data = packet
                self.replays = 0
            elif direction == "tx" and control and kind == 4:
                if packet != self.last_ack:
                    self.replays = 0
                self.last_ack = packet
            elif direction == "rx" and control and kind == 5 and self.last_ack and self.replays < 3:
                self.replays += 1
                result.append(self.last_ack)
        return result
