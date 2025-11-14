# BankApp Helm Umbrella Chart

Это umbrella Helm chart для развертывания микросервисов BankApp в Kubernetes.

## Структура

```
helm/
├── Chart.yaml              # Parent chart
├── values.yaml             # Global values
├── charts/
│   ├── accounts/           # Подчарт для accounts сервиса
│   ├── cash/               # Подчарт для cash сервиса
│   ├── consul/             # Подчарт для Consul
│   ├── front-ui/           # Подчарт для фронта
│   ├── keycloak/           # Подчарт для Keycloak
│   ├── postgresql/         # Подчарт для PostgreSQL
│   └── transfer/           # Подчарт для transfer сервиса
├── templates/
│   └── gatewayapi.yaml     # Ресурсы Kubernetes Gateway API (Gateway + HTTPRoute)
└── README.md
```

## Установка

1. Убедитесь, что Docker image'ы собраны и доступны в Kubernetes:
   ```bash
   eval $(minikube docker-env)
   docker build -f front-ui/dockerfile -t bankapp/front-ui:0.0.1-SNAPSHOT .
   docker build -f accounts/dockerfile -t bankapp/accounts:0.0.1-SNAPSHOT .
   docker build -f cash/dockerfile -t bankapp/cash:0.0.1-SNAPSHOT .
   docker build -f transfer/dockerfile -t bankapp/transfer:0.0.1-SNAPSHOT .
   ```

2. Установите chart:
   ```bash
   helm install bankapp . -n bankapp --create-namespace --values values.yaml
   ```

3. Проверьте статус:
   ```bash
   kubectl get pods -n bankapp
   helm list
   ```

## Обновление

```bash
helm upgrade bankapp . -n bankapp --values values.yaml
```

## Удаление

```bash
helm uninstall bankapp -n bankapp
```

## Примечания

- Для работы приложения необходимо развернуть зависимости: Keycloak, PostgreSQL
- Внешний вход реализован через Kubernetes Gateway API (`gatewayapi.yaml`). Убедитесь, что в кластере установлен соответствующий Gateway controller и `GatewayClass`.
- Переменные окружения настраиваются через `values.yaml`
- Image policy установлен в `IfNotPresent` для использования локальных образов


