import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
const EVENT = '20000000-0000-0000-0000-000000000001';
const BASES = ['http://app-1:8080', 'http://app-2:8080'];
const ATTEMPTS = 1000;
const attempts = new Counter('ticket_attempts');
const completed = new Counter('completed_reservations');
const admissionRejections = new Counter('redis_admission_rejections');
const retries = new Counter('retried_requests');
const replays = new Counter('idempotency_replays');
const conflicts = new Counter('idempotency_conflicts');
const temporaryFailures = new Counter('temporary_failures');
const unexpected = new Counter('unexpected_responses');
export const options = {
 scenarios: {ticket_drop: {executor: 'shared-iterations', vus: 50, iterations: ATTEMPTS, maxDuration: '2m'}},
 thresholds: {unexpected_responses: ['count==0']},
};
export function setup() {
 const response = http.get(`${BASES[0]}/api/events/${EVENT}/seats`);
 const seats = response.json().filter((seat) => seat.label.startsWith('L')).slice(0, ATTEMPTS).map((seat) => seat.id);
 if (seats.length < ATTEMPTS) throw new Error(`Expected ${ATTEMPTS} load seats, found ${seats.length}`);
 return {seats};
}
function confirm(base, holdId, key) {
 return http.post(`${base}/api/holds/${holdId}/confirm`, '{}', {
  headers: {'Content-Type': 'application/json', 'Idempotency-Key': key}, tags: {operation: 'confirm'},
 });
}
export default function (data) {
 const index = exec.scenario.iterationInTest;
 const firstBase = BASES[index % 2], secondBase = BASES[(index + 1) % 2];
 const key = `load-confirm-${index}`; attempts.add(1);
 const hold = http.post(`${firstBase}/api/events/${EVENT}/holds`, JSON.stringify({seatId: data.seats[index], buyerId: `buyer-${index}`}), {
  headers: {'Content-Type': 'application/json'}, tags: {operation: 'hold'},
 });
 if (!check(hold, {'hold created': (response) => response.status === 201})) { unexpected.add(1); return; }
 let result = confirm(secondBase, hold.json().holdId, key);
 if (result.status === 429) { admissionRejections.add(1); retries.add(1); sleep(0.05); result = confirm(firstBase, hold.json().holdId, key); }
 if (result.status === 201) {
  completed.add(1); const replay = confirm(firstBase, hold.json().holdId, key);
  if (replay.status === 201) replays.add(1); else if (replay.status === 429) admissionRejections.add(1);
  else if (replay.status === 409) conflicts.add(1); else if (replay.status === 503) temporaryFailures.add(1); else unexpected.add(1);
 } else if (result.status === 429) admissionRejections.add(1);
 else if (result.status === 409) conflicts.add(1); else if (result.status === 503) temporaryFailures.add(1); else unexpected.add(1);
}