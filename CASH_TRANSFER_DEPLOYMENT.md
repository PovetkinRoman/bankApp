# Развертывание модулей Cash и Transfer в Kubernetes

## Обзор

Данная инструкция описывает процесс добавления и развертывания модулей `cash` и `transfer` в Kubernetes кластере с использованием Helm charts.

## Что было сделано

### 1. Созданы Helm charts для новых сервисов

#### Cash Service (порт 8082)
- `helm/charts/cash/Chart.yaml` - описание чарта
- `helm/charts/cash/values.yaml` - конфигурация сервиса
- `helm/charts/cash/templates/deployment.yaml` - Deployment манифест
- `helm/charts/cash/templates/service.yaml` - Service манифест
- `helm/charts/cash/templates/_helpers.tpl` - вспомогательные шаблоны

#### Transfer Service (порт 8083)
- `helm/charts/transfer/Chart.yaml` - описание чарта
- `helm/charts/transfer/values.yaml` - конфигурация сервиса
- `helm/charts/transfer/templates/deployment.yaml` - Deployment манифест
- `helm/charts/transfer/templates/service.yaml` - Service манифест
- `helm/charts/transfer/templates/_helpers.tpl` - вспомогательные шаблоны

### 2. Обновлены конфигурационные файлы

- `helm/Chart.yaml` - добавлены зависимости cash и transfer
- `helm/values.yaml` - добавлена конфигурация для обоих сервисов
- `helm/port-forward.sh` - добавлен проброс портов для новых сервисов
- `DEPLOYMENT.md` - обновлена документация

### 3. Упакованы Helm charts

- `helm/charts/cash-0.1.0.tgz`
- `helm/charts/transfer-0.1.0.tgz`

## Конфигурация сервисов

### Cash Service

**Порт**: 8082  
**Образ**: `bankapp/cash:0.0.1-SNAPSHOT`  
**Ресурсы**:
- CPU: 250m (запрос) / 500m (лимит)
- Memory: 256Mi (запрос) / 512Mi (лимит)

**Переменные окружения**:
- Интеграция с Consul для service discovery
- OAuth2 аутентификация через Keycloak
- Service Name: `cash`
- Hostname: `bankapp-cash.bankapp.svc.cluster.local`

### Transfer Service

**Порт**: 8083  
**Образ**: `bankapp/transfer:0.0.1-SNAPSHOT`  
**Ресурсы**:
- CPU: 250m (запрос) / 500m (лимит)
- Memory: 256Mi (запрос) / 512Mi (лимит)

**Переменные окружения**:
- Интеграция с Consul для service discovery
- OAuth2 аутентификация через Keycloak
- Service Name: `transfer`
- Hostname: `bankapp-transfer.bankapp.svc.cluster.local`

## Инструкция по развертыванию

### Шаг 1: Сборка Docker образов

Если используете Minikube, сначала настройте Docker окружение:

```bash
eval $(minikube docker-env)
```

Соберите JAR файлы и Docker образы:

```bash
cd /Users/roman/IdeaProjects/practicum/bankApp

# Сборка JAR для cash
cd cash
./mvnw clean package -DskipTests
cd ..

# Сборка JAR для transfer
cd transfer
./mvnw clean package -DskipTests
cd ..

# Сборка Docker образов
docker build -f cash/dockerfile -t bankapp/cash:0.0.1-SNAPSHOT .
docker build -f transfer/dockerfile -t bankapp/transfer:0.0.1-SNAPSHOT .
```

Проверьте, что образы созданы:

```bash
docker images | grep bankapp
```

Вы должны увидеть:
```
bankapp/cash        0.0.1-SNAPSHOT
bankapp/transfer    0.0.1-SNAPSHOT
```

### Шаг 2: Развертывание через Helm

#### Если у вас уже развернут bankapp

Обновите существующую установку:

```bash
cd helm

# Обновить зависимости
helm dependency update

# Обновить релиз
helm upgrade bankapp . -n bankapp
```

#### Если вы разворачиваете с нуля

```bash
cd helm

# Обновить зависимости
helm dependency update

# Установить приложение
helm install bankapp . -n bankapp --create-namespace

# Проверить статус
kubectl get pods -n bankapp
```

### Шаг 3: Проверка развертывания

Проверьте статус подов:

```bash
kubectl get pods -n bankapp
```

Вы должны увидеть поды для всех сервисов, включая:
```
bankapp-cash-xxxxx          1/1     Running
bankapp-transfer-xxxxx      1/1     Running
```

Проверьте логи сервисов:

```bash
# Логи cash сервиса
kubectl logs -n bankapp deployment/bankapp-cash

# Логи transfer сервиса
kubectl logs -n bankapp deployment/bankapp-transfer
```

Проверьте, что сервисы зарегистрировались в Consul:

```bash
# Проброс порта для Consul UI
kubectl port-forward -n bankapp svc/bankapp-consul 8500:8500

# Откройте в браузере http://localhost:8500
# Вы должны увидеть сервисы 'cash' и 'transfer' в списке
```

### Шаг 4: Проброс портов для локального доступа

Используйте обновленный скрипт:

```bash
cd helm
./port-forward.sh
```

Или настройте проброс портов вручную:

```bash
kubectl port-forward -n bankapp svc/bankapp-cash 8082:8082 &
kubectl port-forward -n bankapp svc/bankapp-transfer 8083:8083 &
```

### Шаг 5: Проверка работоспособности

Проверьте health endpoints:

```bash
# Cash service
curl http://localhost:8082/actuator/health

# Transfer service
curl http://localhost:8083/actuator/health
```

Ожидаемый ответ:
```json
{"status":"UP"}
```

## Управление сервисами

### Масштабирование

```bash
# Масштабирование cash сервиса
kubectl scale deployment bankapp-cash -n bankapp --replicas=2

# Масштабирование transfer сервиса
kubectl scale deployment bankapp-transfer -n bankapp --replicas=2
```

### Обновление конфигурации

Отредактируйте `helm/values.yaml` и примените изменения:

```bash
cd helm
helm upgrade bankapp . -n bankapp
```

### Просмотр логов

```bash
# Все логи cash сервиса
kubectl logs -n bankapp -l app.kubernetes.io/name=cash --tail=100 -f

# Все логи transfer сервиса
kubectl logs -n bankapp -l app.kubernetes.io/name=transfer --tail=100 -f
```

### Перезапуск сервисов

```bash
# Перезапуск cash
kubectl rollout restart deployment bankapp-cash -n bankapp

# Перезапуск transfer
kubectl rollout restart deployment bankapp-transfer -n bankapp
```

## Отладка проблем

### Под не запускается

```bash
# Проверить описание пода
kubectl describe pod <pod-name> -n bankapp

# Проверить события
kubectl get events -n bankapp --sort-by='.lastTimestamp'
```

### Проблемы с образами

```bash
# Если используете Minikube, убедитесь что используете правильный Docker daemon
eval $(minikube docker-env)

# Пересоберите образы
docker build -f cash/dockerfile -t bankapp/cash:0.0.1-SNAPSHOT .
docker build -f transfer/dockerfile -t bankapp/transfer:0.0.1-SNAPSHOT .
```

### Проблемы с подключением к другим сервисам

Проверьте, что все сервисы запущены:

```bash
kubectl get svc -n bankapp
```

Проверьте DNS разрешение из пода:

```bash
kubectl exec -it -n bankapp <pod-name> -- nslookup bankapp-consul
kubectl exec -it -n bankapp <pod-name> -- nslookup bankapp-keycloak
```

### Проблемы с регистрацией в Consul

Проверьте логи и убедитесь, что переменные окружения установлены правильно:

```bash
kubectl exec -it -n bankapp <pod-name> -- env | grep CONSUL
```

## Удаление сервисов

### Удаление только cash и transfer

```bash
# Отключить сервисы в values.yaml
# Установить cash.enabled: false и transfer.enabled: false

helm upgrade bankapp . -n bankapp
```

### Полное удаление приложения

```bash
helm uninstall bankapp -n bankapp
kubectl delete namespace bankapp
```

## Архитектура

Обновленная архитектура включает новые сервисы:

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
       │
       ├──────────────────────────────────┐
       ▼                                  ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  Accounts   │  │    Cash     │  │  Transfer   │
│  Service    │  │  Service    │  │  Service    │
│   :8081     │  │   :8082     │  │   :8083     │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │
       └────────────────┴────────────────┘
                        │
                        ▼
       ┌─────────────┬──────────┬─────────────┐
       ▼             ▼          ▼             ▼
┌─────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│  Keycloak   │ │PostgreSQL│ │ Blocker  │ │Notificns │
│   :8080     │ │  :5432   │ │          │ │          │
└─────────────┘ └──────────┘ └──────────┘ └──────────┘
```

## Список развернутых сервисов

| Сервис | Порт | Описание |
|--------|------|----------|
| front-ui | 8080 | Веб-интерфейс приложения |
| gateway | 8088 | API Gateway |
| accounts | 8081 | Сервис управления пользователями |
| **cash** | **8082** | **Сервис управления операциями с наличными** |
| **transfer** | **8083** | **Сервис управления переводами** |
| keycloak | 8080 | Identity Provider |
| consul | 8500 | Service Discovery |
| postgresql | 5432 | База данных |

## Полезные команды

```bash
# Просмотр всех ресурсов
kubectl get all -n bankapp

# Статус Helm релиза
helm status bankapp -n bankapp

# История релизов
helm history bankapp -n bankapp

# Откат к предыдущей версии
helm rollback bankapp -n bankapp

# Получить значения текущей конфигурации
helm get values bankapp -n bankapp

# Проверить шаблоны перед применением
helm template bankapp . --debug
```

## Контакты и поддержка

При возникновении проблем проверьте:
1. Логи подов: `kubectl logs -n bankapp <pod-name>`
2. События кластера: `kubectl get events -n bankapp`
3. Статус сервисов в Consul: http://localhost:8500
4. Health endpoints: http://localhost:8082/actuator/health и http://localhost:8083/actuator/health

## Заключение

Модули `cash` и `transfer` успешно интегрированы в Kubernetes кластер и готовы к использованию. Они автоматически регистрируются в Consul, используют Keycloak для аутентификации и могут взаимодействовать с другими сервисами через service discovery.

