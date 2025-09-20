# Интеграция Keycloak для service-to-service аутентификации

## Обзор

Интеграция Keycloak обеспечивает безопасную аутентификацию между микросервисами с использованием OAuth2 Client Credentials flow, при этом сохраняет существующую form-based аутентификацию для пользователей.

## Компоненты

### 1. Keycloak Server
- **URL**: http://localhost:8090
- **Admin Console**: http://localhost:8090/admin
- **Realm**: bankapp
- **Credentials**: admin/admin

### 2. Клиенты в Keycloak

Каждый микросервис имеет свой клиент:

| Сервис | Client ID | Secret |
|--------|-----------|--------|
| front-ui | front-ui-service | front-ui-secret-key-12345 |
| accounts | accounts-service | accounts-secret-key-12345 |
| cash | cash-service | cash-secret-key-12345 |
| transfer | transfer-service | transfer-secret-key-12345 |

### 3. Конфигурация микросервисов

#### Front-UI
- **Сохранена**: Form-based аутентификация для веб-интерфейса
- **Добавлена**: JWT аутентификация для API endpoints (`/api/**`)
- **OAuth2 Client**: Для исходящих запросов к другим сервисам

#### Accounts  
- **Сохранена**: Открытые endpoints для регистрации и аутентификации пользователей
- **Добавлена**: JWT аутентификация для protected endpoints
- **OAuth2 Client**: Для исходящих запросов к другим сервисам

#### Cash
- **Добавлена**: JWT аутентификация для всех endpoints (кроме actuator)
- **OAuth2 Client**: Для исходящих запросов к accounts сервису

## Схема работы

### Пользовательская аутентификация (без изменений)
1. Пользователь заходит в веб-интерфейс front-ui
2. Form-based логин через CustomAuthenticationProvider
3. Аутентификация проверяется через accounts сервис
4. Пользователь получает доступ к веб-интерфейсу

### Service-to-Service аутентификация (новое)
1. Микросервис получает Access Token от Keycloak через Client Credentials flow
2. Токен автоматически добавляется к исходящим запросам через WebClient
3. Получающий сервис валидирует JWT токен через Keycloak
4. При успешной валидации запрос обрабатывается

## Переменные окружения

### Keycloak URLs
- `KEYCLOAK_TOKEN_URI`: URL для получения токенов
- `KEYCLOAK_JWK_SET_URI`: URL для валидации JWT

### OAuth2 Credentials
- `OAUTH2_CLIENT_ID`: ID клиента для каждого сервиса
- `OAUTH2_CLIENT_SECRET`: Секрет клиента для каждого сервиса

## Безопасность

### JWT Tokens
- **Время жизни**: 1 час (3600 секунд)
- **Алгоритм**: RS256
- **Роли**: Через claim `realm_access.roles`

### Endpoints Security

#### Front-UI
- **Публичные**: `/signup`, `/css/**`, `/js/**`, `/images/**`, `/login`, `/api/rates`
- **JWT Protected**: `/api/**` (кроме rates)
- **Form Protected**: Все остальные endpoints

#### Accounts
- **Публичные**: `/api/users/register`, `/api/users/authenticate`, `/api/users/**`, `/actuator/**`
- **JWT Protected**: Все остальные endpoints

#### Cash
- **Публичные**: `/actuator/**`
- **JWT Protected**: Все остальные endpoints

## Запуск и тестирование

### 1. Запуск системы
```bash
docker-compose up -d
```

### 2. Проверка Keycloak
1. Откройте http://localhost:8090/admin
2. Войдите как admin/admin
3. Перейдите в realm "bankapp"
4. Проверьте клиентов в разделе "Clients"

### 3. Тестирование service-to-service вызовов
```bash
# Получить токен для cash сервиса
curl -X POST http://localhost:8090/realms/bankapp/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=cash-service&client_secret=cash-secret-key-12345"

# Использовать токен для вызова accounts сервиса
curl -X GET http://localhost:8081/api/accounts/testuser \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## Мониторинг и логирование

- Все OAuth2 операции логируются в стандартных логах приложений
- Проблемы с токенами видны в логах Spring Security
- Keycloak имеет собственные логи в контейнере

## Устранение неполадок

### Типичные проблемы

1. **Ошибка соединения с Keycloak**
   - Проверьте доступность http://localhost:8090
   - Убедитесь что контейнер keycloak запущен

2. **Invalid JWT token**
   - Проверьте время на серверах (JWT чувствителен к времени)
   - Убедитесь что realm "bankapp" импортирован

3. **Client authentication failed**
   - Проверьте client_id и client_secret в переменных окружения
   - Убедитесь что клиенты созданы в Keycloak

### Логи для диагностики
```bash
# Логи микросервисов
docker-compose logs front-ui-app
docker-compose logs accounts-app
docker-compose logs cash-app

# Логи Keycloak
docker-compose logs keycloak
```

## Дальнейшее развитие

1. **Добавление scope-based авторизации**
2. **Интеграция с другими микросервисами** (transfer, exchange)
3. **Мониторинг через Keycloak metrics**
4. **Кастомные claim mappings**
