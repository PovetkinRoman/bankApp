# BankApp - Инструкция по развертыванию в Kubernetes

## Предварительные требования

- Kubernetes кластер (Minikube или Docker Desktop)
- Helm 3.x
- Docker
- kubectl

## Быстрый старт

### 1. Подготовка Docker образов

Если используете Minikube, настройте Docker окружение:

```bash
eval $(minikube docker-env)
```

Соберите образы:

```bash
cd /Users/roman/IdeaProjects/practicum/bankApp

# Собрать все сервисы
docker build -f accounts/dockerfile -t bankapp/accounts:0.0.1-SNAPSHOT .
docker build -f gateway/dockerfile -t bankapp/gateway:0.0.1-SNAPSHOT .
docker build -f front-ui/dockerfile -t bankapp/front-ui:0.0.1-SNAPSHOT .
docker build -f cash/dockerfile -t bankapp/cash:0.0.1-SNAPSHOT .
docker build -f transfer/dockerfile -t bankapp/transfer:0.0.1-SNAPSHOT .
```

### 2. Развертывание через Helm

```bash
cd helm

# Обновить зависимости
helm dependency update

# Установить приложение
helm install bankapp . -n bankapp --create-namespace

# Проверить статус
kubectl get pods -n bankapp
```

### 3. Проброс портов для локального доступа

```bash
# Использовать готовый скрипт
./helm/port-forward.sh

# Или вручную
kubectl port-forward -n bankapp svc/bankapp-front-ui 8080:8080 &
kubectl port-forward -n bankapp svc/bankapp-gateway 8088:8088 &
kubectl port-forward -n bankapp svc/bankapp-keycloak 8180:8080 &
```

### 4. Доступ к приложению

- **Web UI**: http://localhost:8080
  - Регистрация: http://localhost:8080/signup
  - Вход: http://localhost:8080/login
  
- **API Gateway**: http://localhost:8088
  - Health check: http://localhost:8088/actuator/health
  - API endpoints: http://localhost:8088/api/*

- **Keycloak Admin**: http://localhost:8180
  - Username: `admin`
  - Password: `admin`
  - Realm: `bankapp`

## Архитектура

```
┌─────────────┐
│  Front-UI   │ :8080
│  (Web UI)   │
└──────┬──────┘
       │
       ▼
┌─────────────┐       ┌─────────────┐
│   Gateway   │◄─────►│   Consul    │
│             │ :8088 │  (Service   │
└──────┬──────┘       │  Discovery) │
       │              └─────────────┘
       ▼
┌─────────────┐       ┌─────────────┐       ┌─────────────┐
│  Accounts   │◄─────►│  Keycloak   │◄─────►│ PostgreSQL  │
│  Service    │ :8081 │   (Auth)    │ :8080 │    (DB)     │
└─────────────┘       └─────────────┘       └─────────────┘
```

## Развернутые сервисы

| Сервис | Порт | Описание |
|--------|------|----------|
| front-ui | 8080 | Веб-интерфейс приложения |
| gateway | 8088 | API Gateway |
| accounts | 8081 | Сервис управления пользователями |
| cash | 8082 | Сервис управления операциями с наличными |
| transfer | 8083 | Сервис управления переводами |
| keycloak | 8080 | Identity Provider |
| consul | 8500 | Service Discovery |
| postgresql | 5432 | База данных |

## Тестирование регистрации

### Через API

```bash
curl -X POST http://localhost:8088/api/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "login": "testuser",
    "password": "password123",
    "confirmPassword": "password123",
    "name": "Test User",
    "birthdate": "1990-01-15"
  }'
```

### Через веб-интерфейс

1. Откройте http://localhost:8080/signup
2. Заполните форму регистрации
3. После успешной регистрации вы будете автоматически авторизованы

## Управление

### Проверка статуса

```bash
# Статус всех ресурсов
kubectl get all -n bankapp

# Статус подов
kubectl get pods -n bankapp

# Логи конкретного сервиса
kubectl logs -n bankapp deployment/bankapp-accounts
kubectl logs -n bankapp deployment/bankapp-cash
kubectl logs -n bankapp deployment/bankapp-transfer
kubectl logs -n bankapp deployment/bankapp-gateway
kubectl logs -n bankapp deployment/bankapp-front-ui
```

### Обновление

```bash
cd helm
helm upgrade bankapp . -n bankapp
```

### Удаление

```bash
# Удалить приложение
helm uninstall bankapp -n bankapp

# Удалить namespace (опционально)
kubectl delete namespace bankapp
```

### Остановка port-forward

```bash
pkill -f 'kubectl port-forward.*bankapp'
```

## Настройка

Основные параметры конфигурации находятся в `helm/values.yaml`:

- **PostgreSQL**: Настройки базы данных
- **Consul**: Конфигурация service discovery
- **Keycloak**: Параметры identity provider
- **Сервисы**: Переменные окружения, ресурсы, health checks

## Troubleshooting

### Поды не запускаются

```bash
# Проверить события
kubectl get events -n bankapp --sort-by='.lastTimestamp'

# Описание пода
kubectl describe pod <pod-name> -n bankapp

# Логи пода
kubectl logs <pod-name> -n bankapp
```

### Образы не найдены

Если используете Minikube, убедитесь, что образы собраны в Docker окружении Minikube:

```bash
eval $(minikube docker-env)
docker images | grep bankapp
```

### Сервисы не регистрируются в Consul

Проверьте логи Consul:

```bash
kubectl logs -n bankapp deployment/bankapp-consul
```

Проверьте переменные окружения сервиса:

```bash
kubectl describe pod <pod-name> -n bankapp | grep -A 10 "Environment:"
```

### База данных недоступна

Проверьте статус PostgreSQL:

```bash
kubectl logs -n bankapp deployment/bankapp-postgresql
kubectl exec -it -n bankapp deployment/bankapp-postgresql -- psql -U root -d bankapp -c "\dt"
```

## Keycloak realm конфигурация

Realm `bankapp` автоматически импортируется при запуске Keycloak из файла `helm/charts/keycloak/realm-export.json`.

Настроенные клиенты:
- `front-ui-service`
- `accounts-service`
- `cash-service`
- `transfer-service`
- `exchange-service`
- `exchange-generator-service`
- `blocker-service`
- `notifications-service`

Тестовый пользователь:
- Username: `testuser`
- Password: `testpassword`

## Дополнительная информация

- [Helm Chart README](helm/README.md)
- Все сервисы используют Spring Boot Actuator с endpoint `/actuator/health`
- Service Discovery работает через Consul
- Аутентификация через Keycloak OAuth2/OIDC

