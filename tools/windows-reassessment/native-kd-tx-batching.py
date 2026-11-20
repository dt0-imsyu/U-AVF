"""Byte-identical KD stream batching; no packet synthesis, ID/checksum changes."""
import struct


class TxBatcher:
    def __init__(self):
        self.pending = bytearray()

    def feed(self, data):
        self.pending.extend(data)
        result = []
        while self.pending:
            prefix = bytes(self.pending[:4])
            if len(prefix) < 4 and any(leader.startswith(prefix) for leader in (b'iiii', b'0000')):
                break
            if prefix not in (b'iiii', b'0000'):
                # Break-in byte / other non-packet bytes keep immediate delivery.
                result.append(bytes(self.pending[:1]));del self.pending[:1]
                continue
            if len(self.pending) < 16:
                break
            _, _, count, _, _ = struct.unpack_from('<IHHII', self.pending)
            if prefix == b'iiii' and count != 0:
                result.append(bytes(self.pending[:1]));del self.pending[:1]
                continue
            length = 16 if prefix == b'iiii' else 17+count
            if len(self.pending) < length:
                break
            result.append(bytes(self.pending[:length]));del self.pending[:length]
        return result

    def flush(self):
        result = bytes(self.pending)
        self.pending.clear()
        return result
