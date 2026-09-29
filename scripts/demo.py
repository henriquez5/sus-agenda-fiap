"""Demonstração HTTP reproduzível. Python 3, sem dependências externas."""
import argparse, base64, json, os, time, urllib.request, urllib.error
from datetime import datetime, timedelta, timezone

parser = argparse.ArgumentParser()
parser.add_argument('--url', default='http://localhost:8080')
parser.add_argument('--expiry', action='store_true', help='Também espera a expiração (use OFFER_TTL=PT3S na API).')
args = parser.parse_args()
password = os.environ.get('OPERATOR_PASSWORD', 'operador-demo')
auth = base64.b64encode(('operador:' + password).encode()).decode()

def call(method, path, body=None, expected=200):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(args.url + path, data=data, method=method,
        headers={'Authorization': 'Basic ' + auth, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            status, content = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, content = error.code, error.read()
    assert status == expected, f'{method} {path}: esperado {expected}, recebido {status}: {content.decode()}'
    return json.loads(content) if content else None

def create(path, body): return call('POST', path, body, 201)

suffix = datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S%f')
a = create('/api/patients', {'name': 'Ana Fictícia'})
b = create('/api/patients', {'name': 'Bruno Fictício'})
c = create('/api/patients', {'name': 'Carla Fictícia'})
agenda = create('/api/agendas', {'unitName': 'UBS Demonstração ' + suffix, 'specialty': 'Clínica geral'})['id']
future = (datetime.now(timezone.utc) + timedelta(days=1)).isoformat()
slot = create(f'/api/agendas/{agenda}/slots', {'startsAt': future})['id']
appt = create(f'/api/slots/{slot}/book', {'patientId': a['id']})['id']
create(f'/api/agendas/{agenda}/waitlist', {'patientId': b['id']})
create(f'/api/agendas/{agenda}/waitlist', {'patientId': c['id']})
print('1. Ana agendada; Bruno e Carla aguardam na fila.')
call('POST', f'/api/appointments/{appt}/cancel')
offers = call('GET', f'/api/agendas/{agenda}/offers')
offer = next(o for o in offers if o['status'] == 'PENDING')
assert offer['patientId'] == b['id']
print('2. Cancelamento gerou oferta exclusiva para Bruno.')
call('POST', f'/api/slots/{slot}/book', {'patientId': c['id']}, 409)
print('3. Tentativa de ocupar vaga reservada foi rejeitada (409).')
new = call('POST', f"/api/offers/{offer['id']}/accept")
assert new['patientId'] == b['id'] and new['source'] == 'WAITLIST'
assert call('POST', f"/api/offers/{offer['id']}/accept")['id'] == new['id']
call('POST', f'/api/appointments/{appt}/cancel')
assert call('GET', f"/api/patients/{b['id']}/appointments")[0]['status'] == 'CONFIRMED'
print('4. Bruno confirmou; repetição da confirmação e do cancelamento original foi segura.')
if args.expiry:
    call('POST', f"/api/appointments/{new['id']}/cancel")
    candidate = next(o for o in call('GET', f'/api/agendas/{agenda}/offers') if o['status'] == 'PENDING')
    assert candidate['patientId'] == c['id']
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        current = call('GET', f'/api/agendas/{agenda}/offers')
        if any(o['id'] == candidate['id'] and o['status'] == 'EXPIRED' for o in current): break
        time.sleep(1)
    else: raise AssertionError('Expiração não ocorreu em 30s; execute a API com OFFER_TTL=PT3S.')
    print('5. Job automático expirou a oferta sem confirmação.')
print('Indicadores:', json.dumps(call('GET', '/api/dashboard'), ensure_ascii=False))
print('Demonstração concluída. Agenda:', agenda)
