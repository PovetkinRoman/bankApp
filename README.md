# bankApp

Микросервисное банковское приложение.

## Модули
- `gateway`: единая точка входа (Spring Cloud Gateway), маршрутизация `lb://<service>` через Consul, OAuth2 (Keycloak).
- `accounts`: управление пользователями и счетами (JPA + Liquibase), защищённый ресурс-сервер.
- `cash`: операции наличных (пополнение/снятие), интеграция с `blocker` и `notifications` через `gateway`.
- `transfer`: переводы между пользователями/счетами, проверка через `blocker`, уведомления через `notifications`.
- `blocker`: правила безопасности, блокировка подозрительных переводов (> 50 000 и др.).
- `exchange`: хранение и выдача курсов валют.
- `exchange-generator`: генерация и отправка курсов в `exchange` по расписанию.
- `notifications`: создание и логирование уведомлений (email-эмуляция в логах).
- `front-ui`: MVC фронт (Thymeleaf), обращается к backend только через `gateway`.

Все межсервисные HTTP‑вызовы идут через `gateway`, адреса сервисов берутся из Consul, backend‑to‑backend запросы подписываются Keycloak (client‑credentials Bearer).

## Сборка через mvnw
Из корня проекта:

```bash
# полная сборка всех модулей, без тестов
./mvnw clean install -DskipTests

# либо собрать/пересобрать отдельный модуль
./mvnw -pl accounts -am clean package -DskipTests
```

Полезно: для быстрых правок можно собирать конкретные модули перед пересборкой Docker образов соответствующих контейнеров.

## Запуск инфраструктуры и сервисов в Docker
Требуется Docker и Docker Compose.

```bash
# старт всей системы
docker compose up -d

# посмотреть состояние контейнеров
docker compose ps

# логи конкретного сервиса (пример: gateway)
docker compose logs -f gateway-app | cat

# остановить
docker compose down
```

После изменения кода конкретного модуля:

```bash
# пересобрать модуль (пример: transfer)
./mvnw -pl transfer -am clean package -DskipTests

# пересобрать и перезапустить только нужный контейнер
docker compose build transfer-app && docker compose up -d transfer-app
```

## Быстрые проверки
- Gateway маршрутизирует на: `/api/accounts/**`, `/api/cash/**`, `/api/transfer/**`, `/api/blocker/**`, `/api/exchange/**`, `/api/notifications/**`.
- Переводы > 50 000 блокируются `blocker` (проверка идёт из `transfer`).
- Уведомления видны в логах `notifications-app`.

## Аутентификация/авторизация
- Keycloak (realm `bankapp`), backend‑сервисы получают токен по client‑credentials и передают его как Bearer.
- `front-ui` авторизует пользователя и обращается к backend через `gateway`.

## Сервис‑дискавери
- Consul: сервисы регистрируются и резолвятся по имени (`lb://service`, либо через `ConsulDiscoveryClient`).

## Consul конфигурация (горячие изменения)
- Включена интеграция Spring Cloud Consul Config. Конфиги читаются из KV Consul по путям вида:
  - `config/<application>/<profile>/application.yml` (или `.properties`)
  - Примеры: `config/accounts/default/application.yml`, `config/transfer/default/application.yml`.
- Из коробки можно менять уровень логирования на горячую (без рестарта). Пример KV содержимого:

```yaml
logging:
  level:
    ru.rpovetkin: DEBUG
```

- Применение:
  1) Создайте/обновите ключ в Consul UI (KV) по указанному пути для нужного сервиса.
  2) Из‑за авто‑refresh (Spring Cloud Consul) изменения подхватываются автоматически; при необходимости можно дернуть `actuator/refresh`.

