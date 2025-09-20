# 🔐 Руководство по проверке Keycloak интеграции

## 📋 Что проверять

### 1. **Keycloak доступен и работает**
### 2. **JWT токены генерируются для всех сервисов**
### 3. **Защищенные эндпоинты блокируют запросы без JWT**
### 4. **Межсервисные вызовы работают с JWT**
### 5. **Cash ограничен доступом только к разрешенным сервисам**

---

## 🧪 Пошаговая проверка

### **Шаг 1: Проверка Keycloak**

```bash
# Проверяем, что Keycloak запущен
curl -s http://localhost:8090/realms/bankapp

# Должен вернуть JSON с информацией о realm
# Если получили ошибку - Keycloak не работает
```

### **Шаг 2: Проверка JWT токенов**

```bash
# Получаем JWT для cash-service
CASH_TOKEN=$(curl -s -X POST http://localhost:8090/realms/bankapp/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=cash-service&client_secret=cash-secret-key-12345" | \
  grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

echo "Cash JWT: ${#CASH_TOKEN} символов"

# Получаем JWT для blocker-service
BLOCKER_TOKEN=$(curl -s -X POST http://localhost:8090/realms/bankapp/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=blocker-service&client_secret=blocker-secret-key-12345" | \
  grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

echo "Blocker JWT: ${#BLOCKER_TOKEN} символов"

# Получаем JWT для notifications-service
NOTIFICATIONS_TOKEN=$(curl -s -X POST http://localhost:8090/realms/bankapp/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=notifications-service&client_secret=notifications-secret-key-12345" | \
  grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

echo "Notifications JWT: ${#NOTIFICATIONS_TOKEN} символов"

# ✅ Все токены должны быть длиной > 500 символов
```

### **Шаг 3: Проверка защищенных эндпоинтов (БЕЗ JWT)**

```bash
# Blocker без JWT - должен вернуть 401
curl -s -w "HTTP:%{http_code}\n" -o /dev/null \
    -H "Content-Type: application/json" \
    -d '{"fromUser":"test","toUser":"test2","currency":"USD","amount":100,"transferType":"CASH","description":"test"}' \
    http://localhost:8086/api/blocker/check-transfer

# Notifications без JWT - должен вернуть 401
curl -s -w "HTTP:%{http_code}\n" -o /dev/null \
    -H "Content-Type: application/json" \
    -d '{"userId":"test","type":"INFO","title":"Test","message":"Test"}' \
    http://localhost:8087/api/notifications/send

# ✅ Оба должны вернуть HTTP:401
```

### **Шаг 4: Проверка с JWT токенами**

```bash
# Используем токены из Шага 2

# Blocker с JWT - должен работать
curl -s -w "\nHTTP:%{http_code}" \
    -H "Authorization: Bearer $BLOCKER_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"fromUser":"test","toUser":"test2","currency":"USD","amount":100,"transferType":"CASH","description":"test"}' \
    http://localhost:8086/api/blocker/check-transfer

# Notifications с JWT - должен работать
curl -s -w "\nHTTP:%{http_code}" \
    -H "Authorization: Bearer $NOTIFICATIONS_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"userId":"test","type":"INFO","title":"Test","message":"Test message","source":"TEST"}' \
    http://localhost:8087/api/notifications/send

# ✅ Оба должны вернуть HTTP:200 и JSON ответ
```

### **Шаг 5: Проверка межсервисной интеграции Cash**

```bash
# Создаем тестового пользователя
curl -s -X POST http://localhost:8081/api/users/register \
    -H "Content-Type: application/json" \
    -d '{
        "login": "testuser",
        "password": "test123",
        "confirmPassword": "test123", 
        "name": "Test User",
        "birthdate": "1990-01-01"
    }'

# Создаем счет
curl -s -H "Content-Type: application/json" \
    -d '{"login":"testuser","currency":"USD"}' \
    http://localhost:8081/api/accounts/create

# Проверяем cash → accounts интеграцию
curl -s http://localhost:8082/api/cash/currencies/testuser

# Тестируем полную цепочку: cash → blocker → accounts → notifications
curl -s -H "Content-Type: application/json" \
    -d '{"login":"testuser","currency":"USD","amount":100,"operation":"deposit"}' \
    http://localhost:8082/api/cash/deposit

# ✅ Должны получить успешные ответы с данными
```

### **Шаг 6: Проверка ограничений доступа**

```bash
# Проверяем логи cash на ограничения (если попытаться обратиться к неразрешенному сервису)
docker compose logs cash-app --tail=10

# В логах НЕ должно быть сообщений "RESTRICTED ACCESS VIOLATION"
# Это означает, что cash обращается только к разрешенным сервисам
```

### **Шаг 7: Проверка без Keycloak**

```bash
# Останавливаем Keycloak
docker compose stop keycloak

# Пытаемся выполнить операцию - должна упасть с ошибкой токена
curl -s -w "\nHTTP:%{http_code}" \
    -H "Content-Type: application/json" \
    -d '{"login":"testuser","currency":"USD","amount":50,"operation":"deposit"}' \
    http://localhost:8082/api/cash/deposit

# Восстанавливаем Keycloak
docker compose start keycloak
sleep 15

# ✅ Операция без Keycloak должна завершиться ошибкой
```

---

## 🎯 Ожидаемые результаты

### ✅ **Успешная проверка:**

1. **Keycloak realm**: Возвращает JSON информацию
2. **JWT токены**: Генерируются для всех 3 сервисов (cash, blocker, notifications)
3. **Защита без JWT**: HTTP 401 для blocker и notifications
4. **Работа с JWT**: HTTP 200 для всех сервисов
5. **Cash интеграция**: Успешные операции пополнения/снятия
6. **Без Keycloak**: Операции падают с ошибками токенов

### ❌ **Проблемы и решения:**

| Проблема | Причина | Решение |
|----------|---------|---------|
| JWT не генерируется | Клиент не найден в Keycloak | Проверить realm-export.json, перезапустить Keycloak |
| HTTP 401 с JWT | Неверная конфигурация Resource Server | Проверить SecurityConfig и application.yml |
| Cash не видит счета | OAuth2 WebClient не настроен | Проверить RestrictedWebClientConfig |
| Операции без Keycloak работают | Кэширование токенов | Подождать истечения токена или перезапустить cash |

---

## 🔧 Быстрая проверка одной командой

```bash
# Выполните этот скрипт для полной проверки
echo "🔐 ПРОВЕРКА KEYCLOAK ИНТЕГРАЦИИ"
echo "==============================="

# 1. Keycloak
echo -n "Keycloak: "
curl -s -w "HTTP:%{http_code}\n" -o /dev/null http://localhost:8090/realms/bankapp

# 2. JWT токены
echo -n "Cash JWT: "
CASH_TOKEN=$(curl -s -X POST http://localhost:8090/realms/bankapp/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=cash-service&client_secret=cash-secret-key-12345" | \
  grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
echo "${#CASH_TOKEN} символов"

# 3. Защищенные эндпоинты
echo -n "Blocker без JWT: "
curl -s -w "HTTP:%{http_code}\n" -o /dev/null \
    -H "Content-Type: application/json" \
    -d '{"fromUser":"test","toUser":"test2","currency":"USD","amount":100}' \
    http://localhost:8086/api/blocker/check-transfer

# 4. Cash операции
echo -n "Cash API: "
curl -s -w "HTTP:%{http_code}\n" -o /dev/null http://localhost:8082/api/cash/currencies/testuser

echo "✅ Проверка завершена!"
```

Скопируйте и выполните любой из этих блоков команд для проверки конкретных аспектов интеграции!
