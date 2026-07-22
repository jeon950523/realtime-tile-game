# Deploy And Migration Guide

## 중요

이번 수정에는 Backend, Frontend, Flyway V8이 모두 포함된다.  
두 이미지를 모두 새 태그로 빌드하고 Kubernetes에 반영해야 한다.

권장 태그:

```text
realtime-tile-game-backend:phase7-exitfix-0720
realtime-tile-game-frontend:phase7-exitfix-0720
```

## 빌드

```powershell
docker build --no-cache -t realtime-tile-game-backend:phase7-exitfix-0720 .\backend
docker build --no-cache -t realtime-tile-game-frontend:phase7-exitfix-0720 .\frontend
```

## Docker Desktop Kubernetes 노드에 Import

```powershell
docker save -o .\phase7-exitfix-images.tar realtime-tile-game-backend:phase7-exitfix-0720 realtime-tile-game-frontend:phase7-exitfix-0720
docker cp .\phase7-exitfix-images.tar desktop-control-plane:/phase7-exitfix-images.tar
docker exec desktop-control-plane ls -lh /phase7-exitfix-images.tar
docker exec desktop-control-plane ctr -n k8s.io images import /phase7-exitfix-images.tar
docker exec desktop-control-plane ctr -n k8s.io images ls | findstr /I "phase7-exitfix-0720"
```

## Deployment 교체

```powershell
kubectl scale deployment/frontend deployment/backend --replicas=0 -n realtime-tile-game

kubectl set image deployment/frontend frontend=realtime-tile-game-frontend:phase7-exitfix-0720 -n realtime-tile-game
kubectl set image deployment/backend backend=realtime-tile-game-backend:phase7-exitfix-0720 -n realtime-tile-game

kubectl scale deployment/frontend deployment/backend --replicas=1 -n realtime-tile-game

kubectl rollout status deployment/frontend -n realtime-tile-game --timeout=180s
kubectl rollout status deployment/backend -n realtime-tile-game --timeout=180s
```

Backend 최초 기동 시 Flyway V8이 자동 적용되어 과거 terminal/closed room의 남은 active membership을 정리한다.

## 확인

```powershell
kubectl get pods -n realtime-tile-game -o custom-columns="NAME:.metadata.name,STATUS:.status.phase,IMAGE:.spec.containers[0].image,IMAGE_ID:.status.containerStatuses[0].imageID"
kubectl logs deployment/backend -n realtime-tile-game --tail=200 | findstr /I "V8 Flyway error exception"
```

MySQL StatefulSet과 PVC는 삭제하지 않는다.
