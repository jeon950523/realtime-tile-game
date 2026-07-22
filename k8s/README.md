# Phase 5.5-B Local Kubernetes Runtime

최종 상태: `Phase 5.5-B FINAL`  
대상: Windows Docker Desktop Kubernetes / `docker-desktop`

## Architecture

```text
Browser localhost:30517
→ frontend NodePort Service 또는 Port-forward
→ frontend Deployment replicas 1 / Nginx :80
   ├─ /api/** → backend ClusterIP :8080
   └─ /ws     → backend ClusterIP :8080
                    → backend Deployment replicas 1 / Recreate
                    → mysql ClusterIP :3306
                    → mysql StatefulSet replicas 1
                    → mysql-data-mysql-0 PVC 2Gi
```

Backend의 Process-local Replay Store·Simple Broker·WebSocket Connection 때문에 `replicas: 1`, `strategy: Recreate`를 유지한다.

## 1. Context와 Storage 확인

```powershell
kubectl config current-context
kubectl get nodes
kubectl get storageclass
kubectl get svc -A
```

기대:

```text
Context docker-desktop
Node Ready
Default StorageClass 존재
NodePort 30517 미사용
```

## 2. Local Image Build

```powershell
docker build -t realtime-tile-game-backend:local ./backend
docker build -t realtime-tile-game-frontend:local ./frontend
```

## 3. kind 다중 Node Image Load

`kubectl get nodes`가 `desktop-control-plane`, `desktop-worker`, `desktop-worker2`처럼 여러 Node를 반환하면:

```powershell
kind load docker-image `
  realtime-tile-game-backend:local `
  realtime-tile-game-frontend:local `
  --name desktop
```

`ErrImageNeverPull`이면 Pod가 배치된 Node에 Image가 없는 것이다.

## 4. Compose 중지

```powershell
docker compose down
```

`docker compose down -v`는 사용하지 않는다.

## 5. Dry-run과 Namespace

```powershell
kubectl apply --dry-run=client -f k8s/
kubectl apply -f k8s/00-namespace.yaml
```

## 6. Secret 직접 생성

```powershell
$mysqlPassword = Read-Host "MySQL application user password"
$mysqlRootPassword = Read-Host "MySQL root password"
$jwtSecret = Read-Host "JWT Base64 secret"

kubectl -n realtime-tile-game create secret generic realtime-tile-game-secret `
  --from-literal=MYSQL_PASSWORD="$mysqlPassword" `
  --from-literal=MYSQL_ROOT_PASSWORD="$mysqlRootPassword" `
  --from-literal=JWT_ACCESS_SECRET_BASE64="$jwtSecret" `
  --dry-run=client `
  -o yaml |
kubectl apply -f -
```

Secret YAML은 저장하지 않는다.

## 7. Apply와 Rollout

```powershell
kubectl apply -f k8s/

kubectl rollout status statefulset/mysql -n realtime-tile-game --timeout=180s
kubectl rollout status deployment/backend -n realtime-tile-game --timeout=180s
kubectl rollout status deployment/frontend -n realtime-tile-game --timeout=180s

kubectl get pods,pvc,svc -n realtime-tile-game
```

기대:

```text
mysql-0       1/1 Running
backend-*     1/1 Running
frontend-*    1/1 Running
PVC           Bound 2Gi
```

## 8. 접속

NodePort 직접 연결이 되는 환경:

```text
http://localhost:30517
```

kind 다중 Node에서 연결되지 않으면:

```bash
kubectl port-forward service/frontend 30517:80 -n realtime-tile-game
```

다른 터미널:

```bash
curl -i http://localhost:30517/api/health
```

## 9. MySQL Probe FINAL 규칙

```text
Unix Socket 사용
Probe 명령에서만 MYSQL_PWD 사용
Container 전역 MYSQL_PWD 금지
```

기존 PVC가 있는 상태에서 Secret의 MySQL Password만 변경하면 DB 계정과 불일치할 수 있다.

## 10. Source 변경

```powershell
docker build -t realtime-tile-game-backend:local ./backend
docker build -t realtime-tile-game-frontend:local ./frontend
```

kind 다중 Node면 Image를 다시 Load한다. 이후:

```powershell
kubectl rollout restart deployment/backend -n realtime-tile-game
kubectl rollout restart deployment/frontend -n realtime-tile-game
```

## 11. 진단

```powershell
kubectl get pods -n realtime-tile-game -o wide
kubectl get endpoints frontend backend mysql -n realtime-tile-game
kubectl get events -n realtime-tile-game --sort-by=.lastTimestamp
kubectl logs statefulset/mysql -n realtime-tile-game
kubectl logs deployment/backend -n realtime-tile-game
kubectl logs deployment/backend -n realtime-tile-game -c wait-for-mysql
kubectl logs deployment/frontend -n realtime-tile-game
```

## 12. Self-healing과 PVC

```powershell
kubectl delete pod -n realtime-tile-game -l app.kubernetes.io/component=backend
kubectl delete pod -n realtime-tile-game -l app.kubernetes.io/component=frontend
kubectl delete pod mysql-0 -n realtime-tile-game
```

MySQL Pod 삭제는 PVC를 삭제하지 않는다. StatefulSet이 기존 PVC를 다시 연결해야 한다.

## 13. 일시 중지

```powershell
kubectl scale deployment/backend deployment/frontend --replicas=0 -n realtime-tile-game
kubectl scale statefulset/mysql --replicas=0 -n realtime-tile-game
```

재시작은 MySQL부터 올린다.

## 14. 삭제 경고

```text
kubectl delete pvc mysql-data-mysql-0 -n realtime-tile-game
kubectl delete namespace realtime-tile-game
Docker Desktop Kubernetes Reset
```

위 작업은 데이터 삭제 또는 Cluster 초기화다. 일반 종료로 사용하지 않는다.

## 15. 학원 내부망 Client 연결 — Phase 5.5-C

Docker Desktop kind Cluster의 NodePort가 Windows LAN Adapter에 직접 노출되지 않는 환경에서는 수동 `kubectl port-forward` 대신 프로젝트 관리 스크립트를 사용한다.

```powershell
.\scripts\classroom-lan\Invoke-ClassroomLanSelfTest.ps1
```

관리자 PowerShell에서 LocalSubnet Firewall Rule 생성:

```powershell
.\scripts\classroom-lan\Enable-ClassroomLanFirewall.ps1 -Port 30517
```

일반 PowerShell에서 실제 Host LAN IPv4를 명시해 시작:

```powershell
.\scripts\classroom-lan\Start-ClassroomLan.ps1 -LanIp 192.168.0.10
```

처리 흐름:

```text
정확한 LAN IPv4 검증
→ ConfigMap CORS_ALLOWED_ORIGINS를 localhost/127.0.0.1/선택 IP로 교체
→ Backend Recreate Rollout
→ 127.0.0.1과 선택 IP에 frontend Service Port-forward
→ Host Health 검증
```

상태·검증·중지:

```powershell
.\scripts\classroom-lan\Get-ClassroomLanStatus.ps1
.\scripts\classroom-lan\Test-ClassroomLan.ps1
.\scripts\classroom-lan\Stop-ClassroomLan.ps1
```

두 번째 Client PC:

```powershell
Test-NetConnection 192.168.0.10 -Port 30517
Invoke-RestMethod http://192.168.0.10:30517/api/health
```

보안 경계:

```text
Backend와 MySQL은 ClusterIP 유지
Frontend Service만 기존 NodePort 유지
Port-forward는 0.0.0.0이 아닌 loopback + 선택 LAN IP에만 바인딩
Firewall은 RemoteAddress LocalSubnet만 허용
실제 LAN IP와 Secret은 Manifest에 저장하지 않음
공유기 Port Forwarding과 인터넷 공개는 범위 밖
```

Stop 스크립트는 Port-forward만 종료하며 StatefulSet, Deployment, Namespace, PVC를 수정하지 않는다. Runtime 상태는 `.runtime/classroom-lan/`에만 저장된다.
