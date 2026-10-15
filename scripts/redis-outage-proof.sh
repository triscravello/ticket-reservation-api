#!/usr/bin/env bash
set -euo pipefail
EVENT='20000000-0000-0000-0000-000000000001'
A1='10000000-0000-0000-0000-000000000001'
A2='10000000-0000-0000-0000-000000000002'
KEY="outage-$(date +%s)"
hold_json=$(curl -sf -X POST "http://localhost:8081/api/events/${EVENT}/holds" -H 'Content-Type: application/json' -d "{\"seatId\":\"${A1}\",\"buyerId\":\"outage-buyer\"}")
hold_id=$(printf '%s' "$hold_json" | sed -E 's/.*"holdId":"([^"]+)".*/\1/')
docker compose stop redis
curl -s -o /tmp/app1.json -w '%{http_code}' -X POST "http://localhost:8081/api/holds/${hold_id}/confirm" -H "Idempotency-Key: ${KEY}" -H 'Content-Type: application/json' -d '{}' > /tmp/app1.status & p1=$!
curl -s -o /tmp/app2.json -w '%{http_code}' -X POST "http://localhost:8082/api/holds/${hold_id}/confirm" -H "Idempotency-Key: ${KEY}" -H 'Content-Type: application/json' -d '{}' > /tmp/app2.status & p2=$!
wait "$p1" || true; wait "$p2" || true
first_status=$(cat /tmp/app1.status); second_status=$(cat /tmp/app2.status)
docker compose start redis; sleep 1
recovery_hold=$(curl -sf -X POST "http://localhost:8081/api/events/${EVENT}/holds" -H 'Content-Type: application/json' -d "{\"seatId\":\"${A2}\",\"buyerId\":\"recovery-buyer\"}")
recovery_id=$(printf '%s' "$recovery_hold" | sed -E 's/.*"holdId":"([^"]+)".*/\1/')
recovery_status=$(curl -s -o /tmp/recovery.json -w '%{http_code}' -X POST "http://localhost:8082/api/holds/${recovery_id}/confirm" -H 'Idempotency-Key: recovery-key' -H 'Content-Type: application/json' -d '{}')
reservations=$(docker compose exec -T postgres psql -U tickets -d tickets -tAc "SELECT COUNT(*) FROM reservations WHERE seat_id='${A1}'::uuid;")
duplicates=$(docker compose exec -T postgres psql -U tickets -d tickets -tAc "SELECT COUNT(*) FROM (SELECT seat_id FROM reservations GROUP BY seat_id HAVING COUNT(*) > 1) d;")
completed=$(docker compose exec -T postgres psql -U tickets -d tickets -tAc "SELECT COUNT(*) FROM idempotency_records WHERE idempotency_key='${KEY}' AND response_status=201;")
failures=0; [[ "$first_status" == "201" ]] || failures=$((failures + 1)); [[ "$second_status" == "201" ]] || failures=$((failures + 1))
printf 'app-1 status: %s\napp-2 status: %s\nexpected request failures: %s\nrecovery status: %s\nreservations for target seat: %s\nduplicate confirmed seats: %s\ncompleted rows for target idempotency key: %s\n' "$first_status" "$second_status" "$failures" "$recovery_status" "$reservations" "$duplicates" "$completed"
[[ "$reservations" == "1" ]]; [[ "$duplicates" == "0" ]]; [[ "$completed" == "1" ]]; [[ "$recovery_status" == "201" ]]