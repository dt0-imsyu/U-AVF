"""Read-only retained KD FILE_IO audit, including malformed/truncated frames."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

def scan(path):
    data = path.read_bytes(); packets = []; anomalies = []; position = 0
    while position + 4 <= len(data):
        marker = data[position:position+4]
        if marker not in (b'0000', b'iiii'):
            position += 1; continue
        if position + 16 > len(data):
            anomalies.append(dict(offset=position, reason='truncated header')); break
        _, kind, size, identity, checksum = struct.unpack_from('<IHHII', data, position)
        control = marker == b'iiii'
        if (control and (size or kind not in (4, 5, 6))) or size > 16384:
            position += 1; continue
        if not control and (kind not in (2, 3, 7, 11) or identity & ~0x801 != 0x80800000):
            position += 1; continue
        length = 16 if control else 17 + size
        if position + length > len(data):
            anomalies.append(dict(offset=position, reason='truncated packet', kind=kind,
                                  expected=length, available=len(data)-position)); break
        body = data[position+16:position+16+size]
        if not control and (sum(body) != checksum or data[position+length-1] != 170):
            anomalies.append(dict(offset=position, reason='checksum/trailer mismatch', kind=kind))
            position += 1; continue
        item = dict(offset=position, kind=kind, identity=hex(identity), size=size)
        if not control: item['body_sha256'] = hashlib.sha256(body).hexdigest()
        if kind == 11 and len(body) >= 64:
            item.update(api=hex(struct.unpack_from('<I', body)[0]),
                        status=hex(struct.unpack_from('<I', body, 4)[0]),
                        header=body[:64].hex(),
                        path=body[64:].decode('utf-16-le', 'replace').rstrip('\0'))
        packets.append(item); position += length
    return dict(bytes=len(data), packets=packets, anomalies=anomalies)

def audit(directory):
    tx, rx = scan(directory/'tx.bin'), scan(directory/'rx.bin')
    requests = [x for x in rx['packets'] if x['kind'] == 11]
    replies = [x for x in tx['packets'] if x['kind'] == 11]
    last_reply = replies[-1] if replies else None
    # Last guest ACK matching the last sent ID is reported; independently
    # alternating IDs are not a unique transaction key without timestamps.
    ack = [x for x in rx['packets'] if last_reply and x['kind'] == 4
           and x['identity'] == last_reply['identity']]
    events = [json.loads(x) for x in (directory/'events.jsonl').read_text().splitlines() if x]
    previous=None; repeated=[]
    for packet in rx['packets']:
        if packet['kind']==6:
            previous=None; continue
        if packet['kind'] in (4,5): continue
        if int(packet['identity'],16)&0x800:
            previous=None; continue
        if previous and previous['identity']==packet['identity']:
            repeated.append(dict(previous=previous, current=packet,
                                 identical=previous['kind']==packet['kind'] and previous['body_sha256']==packet['body_sha256']))
        else: previous=packet
    return dict(directory=str(directory), requests=len(requests), replies=len(replies),
                tx_anomalies=tx['anomalies'], rx_anomalies=rx['anomalies'],
                last_request=requests[-1] if requests else None, last_reply=last_reply,
                rx_tail=rx['packets'][-6:], tx_tail=tx['packets'][-6:],
                last_matching_id_ack=ack[-1] if ack else None,
                last_activity=[e for e in events if e['tag'] in
                               ('FILE_OVERRIDE_DECLINED','RESEND','OBSERVER_STOP')][-5:],
                kernel_connected=any(e['tag']=='KERNEL_CONNECTED' for e in events),
                repeated_rx_ids=repeated,
                caveat='Host-valid bytes are not proof of guest UART consumption; recycled IDs are not timestamps')

if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('output', type=Path)
    p.add_argument('directories', nargs='+', type=Path); a = p.parse_args()
    assert not a.output.exists(), 'Preserve evidence'
    result = [audit(directory) for directory in a.directories]
    a.output.write_text(json.dumps(result, indent=2) + '\n')
    for entry in result:
        print(json.dumps(dict(directory=entry['directory'], requests=entry['requests'],
             replies=entry['replies'], tx_anomalies=entry['tx_anomalies'],
             rx_anomalies=entry['rx_anomalies'], repeated_ids=[dict(identical=x['identical'],
                  previous_path=x['previous'].get('path'),current_path=x['current'].get('path'),
                  offset=x['current']['offset']) for x in entry['repeated_rx_ids']],last_activity=entry['last_activity'])))
