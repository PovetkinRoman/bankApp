# BankApp Helm Umbrella Chart

Это umbrella Helm chart для развертывания микросервисов BankApp в Kubernetes.

## Структура

```
helm/
├── Chart.yaml              # Parent chart
├── values.yaml             # Global values
├── charts/
│   └── front-ui/           # Подчарт для front-ui сервиса
│       ├── Chart.yaml
│       ├── values.yaml
│       └── templates/
│           ├── deployment.yaml
│           ├── service.yaml
│           ├── configmap.yaml
│           └── _helpers.tpl
└── README.md
```

## Установка

1. Убедитесь, что Docker image собран и доступен в Kubernetes:
   ```bash
   eval $(minikube docker-env)
   docker build -f front-ui/dockerfile -t bankapp/front-ui:0.0.1-SNAPSHOT .
   ```

2. Установите chart:
   ```bash
   helm install bankapp . --values values.yaml
   ```

3. Проверьте статус:
   ```bash
   kubectl get pods -l app.kubernetes.io/name=front-ui
   helm list
   ```

## Обновление

```bash
helm upgrade bankapp . --values values.yaml
```

## Удаление

```bash
helm uninstall bankapp
```

## Примечания

- Для работы приложения необходимо развернуть зависимости: Consul, Keycloak, PostgreSQL
- Переменные окружения настраиваются через values.yaml
- Image policy установлен в `IfNotPresent` для использования локальных образов

