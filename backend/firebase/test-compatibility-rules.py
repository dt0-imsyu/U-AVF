"""Run against the local Auth and Firestore emulators, using a demo project."""

import copy
import json
import os
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen

PROJECT = 'demo-uavf'
DOCUMENTS = f'projects/{PROJECT}/databases/(default)/documents'


def local_endpoint(variable, default):
    endpoint = os.environ.get(variable, default)
    if not endpoint.startswith(('127.0.0.1:', 'localhost:')):
        raise ValueError(f'{variable} must point to a loopback emulator')
    return 'http://' + endpoint


AUTH = local_endpoint('FIREBASE_AUTH_EMULATOR_HOST', '127.0.0.1:19099')
FIRESTORE = local_endpoint('FIRESTORE_EMULATOR_HOST', '127.0.0.1:18085') + '/v1/' + DOCUMENTS


def request(url, token=None, data=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    body = None if data is None else json.dumps(data).encode()
    try:
        with urlopen(Request(url, data=body, headers=headers), timeout=15) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, json.load(error)


def expect(label, result, *allowed):
    code, data = result
    assert code in allowed, (label, code, data)
    print(label, code, flush=True)
    return data


def anonymous():
    account = expect('ANONYMOUS_AUTH', request(
        AUTH + '/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo',
        data={'returnSecureToken': True}), 200)
    return account['localId'], account['idToken']


def fields(data):
    def value(item):
        if isinstance(item, bool):
            return {'booleanValue': item}
        if isinstance(item, int):
            return {'integerValue': str(item)}
        if isinstance(item, dict):
            return {'mapValue': {'fields': fields(item)}}
        return {'stringValue': item}
    return {key: value(item) for key, item in data.items()}


def create(path, data, timestamp=False):
    write = {'update': {'name': DOCUMENTS + path, 'fields': fields(data)},
             'currentDocument': {'exists': False}}
    if timestamp:
        write['updateTransforms'] = [{'fieldPath': 'submittedAt', 'setToServerValue': 'REQUEST_TIME'}]
    return write


def v2(owner, run, slot, config='api36-ram12-cpu8', legacy_layout=False, **changes):
    group = {'manufacturer': 'samsung', 'model': 'sm-x736b', 'configuration': config}
    base = f'/compatibility_reporters/{owner}'
    prefix = '/compatibility_devices/samsung'
    location = f'models/sm-x736b/configurations/{config}' if legacy_layout else f'sm-x736b/{config}'
    if not legacy_layout:
        group['layout'] = 'direct'
    report = f'{prefix}/{location}/reporters/{owner}/tests/{run}'
    receipt = base + '/receipts/' + run
    checks = dict.fromkeys(('arm64', 'avf', 'permissions', 'platform', 'created',
                            'boot', 'memory', 'storage', 'vsock'), 'pass')
    data = {'schemaVersion': 2, 'consent': True, 'appVersion': '1.2-test',
            'androidRelease': '16', 'status': 'COMPATIBLE', 'checks': checks, 'slot': slot,
            'device': {'androidApi': 36, 'ramGiB': 12, 'cpuCount': 8},
            'avf': {'guestMemoryMiB': 384}}
    data.update(changes)
    return report, receipt, [
        create(report, data, True), create(receipt, dict(group, slot=slot)),
        create(base + '/slots/' + slot, {'run': run}),
    ]


def commit(writes, token=None):
    return request(FIRESTORE + ':commit', token, {'writes': writes})


def main():
    owner, token = anonymous()
    _, other = anonymous()
    run = uuid.uuid4().hex
    report, receipt, writes = v2(owner, run, '0')
    expect('UNAUTHENTICATED_DENIED', commit(writes), 403)
    expect('OTHER_OWNER_DENIED', commit(writes, other), 403)
    expect('REPORT_ALONE_DENIED', commit(writes[:1], token), 403)
    expect('RECEIPT_ALONE_DENIED', commit(writes[1:2], token), 403)
    expect('SLOT_ALONE_DENIED', commit(writes[2:], token), 403)
    expect('MISSING_SLOT_DENIED', commit(writes[:2], token), 403)
    expect('CONSENT_FALSE_DENIED', commit(v2(owner, run, '0', consent=False)[2], token), 403)
    expect('EXTRA_FIELD_DENIED', commit(v2(owner, run, '0', log='private')[2], token), 403)
    expect('EXTRA_AVF_FIELD_DENIED', commit(v2(owner, run, '0', avf={'guestMemoryMiB': 384, 'log': 'private'})[2], token), 403)
    expect('INVALID_DEVICE_DENIED', commit(v2(owner, run, '0', device={'androidApi': 36, 'ramGiB': 12, 'cpuCount': 0})[2], token), 403)
    invalid = copy.deepcopy(writes)
    invalid[0]['update']['fields']['checks']['mapValue']['fields']['memory']['stringValue'] = 'not_tested'
    expect('FALSE_COMPATIBLE_DENIED', commit(invalid, token), 403)
    expect('MISSING_TIMESTAMP_DENIED', commit([dict(writes[0], updateTransforms=[]), *writes[1:]], token), 403)
    expect('MISSING_RECEIPT', request(FIRESTORE + receipt, token), 404)
    expect('ATOMIC_GROUPED_CREATE', commit(writes, token), 200)
    expect('OWN_RECEIPT', request(FIRESTORE + receipt, token), 200)
    expect('CROSS_OWNER_RECEIPT_DENIED', request(FIRESTORE + receipt, other), 403)
    expect('REPORT_READ_DENIED', request(FIRESTORE + report, token), 403)
    expect('DUPLICATE_DENIED', commit(writes, token), 403, 409)
    expect('SAME_RUN_OTHER_CONFIG_DENIED',
           commit(v2(owner, run, '1', 'api36-ram16-cpu8')[2], token), 403, 409)
    expect('OCCUPIED_SLOT_DENIED', commit(v2(owner, uuid.uuid4().hex, '0')[2], token), 403, 409)
    expect('SLOT_32_DENIED', commit(v2(owner, uuid.uuid4().hex, '32')[2], token), 403)
    earlier_v2 = v2(owner, uuid.uuid4().hex, '1', legacy_layout=True)[2]
    del earlier_v2[0]['update']['fields']['device']
    del earlier_v2[0]['update']['fields']['avf']
    expect('EARLIER_V2_ACCEPTED', commit(earlier_v2, token), 200)
    expect('REPORT_DELETE_DENIED', commit([{'delete': DOCUMENTS + report}], token), 403)
    update = copy.deepcopy(writes[0])
    del update['currentDocument']
    expect('REPORT_UPDATE_DENIED', commit([update], token), 403)
    legacy = {'schemaVersion': 1, 'consent': True, 'appVersion': 'legacy', 'androidApi': 36,
              'androidRelease': '16', 'manufacturer': 'samsung', 'model': 'SM-X736B',
              'results': dict.fromkeys(('arm64','avf','permissions','storage','iso','platform'), 'not_tested'),
              'runtime': dict.fromkeys(('boot','vsock','openGL'), 'not_tested')}
    path = f'/compatibility_reports/{owner}/runs/0'
    expect('LEGACY_CREATE_STILL_ALLOWED', commit([create(path, legacy, True)], token), 200)
    expect('LEGACY_READ_STILL_DENIED', request(FIRESTORE + path, token), 403)


if __name__ == '__main__':
    main()
