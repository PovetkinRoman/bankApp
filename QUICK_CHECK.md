# 🚀 Быстрая проверка Keycloak интеграции

## ⚡ Автоматическая проверка (рекомендуется)

```bash
# Запустите готовый скрипт
./verify-keycloak-integration.sh
```

## 🔧 Ручная проверка (5 команд)

### 1. Keycloak работает?
```bash
curl -s http://localhost:8090/realms/bankapp | grep -q "bankapp" && echo "✅ Keycloak OK" || echo "❌ Keycloak FAIL"
```

### 2. JWT токены генерируются?
```bash
curl -s -X POST http://localhost:8090/realms/bankapp/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=cash-service&client_secret=cash-secret-key-12345" \
  | grep -q "access_token" && echo "✅ JWT OK" || echo "❌ JWT FAIL"
```

### 3. Защищенные эндпоинты блокируют без JWT?
```bash
curl -s -w ":%{http_code}" -o /dev/null \
  -H "Content-Type: application/json" \
  -d '{"fromUser":"test","toUser":"test2","currency":"USD","amount":100}' \
  http://localhost:8086/api/blocker/check-transfer \
  | grep -q ":401" && echo "✅ Protection OK" || echo "❌ Protection FAIL"
```

### 4. Cash интеграция работает?
```bash
curl -s http://localhost:8082/api/cash/currencies/testuser \
  | grep -q "currency" && echo "✅ Cash Integration OK" || echo "❌ Cash Integration FAIL"
```

### 5. Все сервисы запущены?
```bash
docker compose ps | grep -q "Up" && echo "✅ Services OK" || echo "❌ Services FAIL"
```

## 🎯 Ожидаемый результат

Все команды должны показать `✅ OK`. Если видите `❌ FAIL` - есть проблемы.

## 🔍 Диагностика проблем

```bash
# Логи Keycloak
docker compose logs keycloak --tail=20

# Логи Cash
docker compose logs cash-app --tail=20

# Логи Blocker
docker compose logs blocker-app --tail=20

# Логи Notifications  
docker compose logs notifications-app --tail=20

# Статус всех контейнеров
docker compose ps
```

## 📚 Подробная документация

Смотрите `KEYCLOAK_VERIFICATION_GUIDE.md` для детального руководства.
