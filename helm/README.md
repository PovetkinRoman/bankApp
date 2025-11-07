# BankApp Helm Umbrella Chart

Это umbrella Helm chart для развертывания микросервисов BankApp в Kubernetes.

## Структура

```
helm/
├── Chart.yaml              # Parent chart
├── values.yaml             # Global values
├── charts/
│   ├── accounts/           # Подчарт для accounts сервиса
│   ├── consul/             # Подчарт для Consul
│   ├── front-ui/           # Подчарт для фронта
│   ├── gateway/            # Подчарт для API gateway
│   ├── keycloak/           # Подчарт для Keycloak
│   └── postgresql/         # Подчарт для PostgreSQL
└── README.md
```

## Установка

1. Убедитесь, что Docker image'ы собраны и доступны в Kubernetes:
   ```bash
   eval $(minikube docker-env)
   docker build -f front-ui/dockerfile -t bankapp/front-ui:0.0.1-SNAPSHOT .
   docker build -f gateway/dockerfile -t bankapp/gateway:0.0.1-SNAPSHOT .
   docker build -f accounts/dockerfile -t bankapp/accounts:0.0.1-SNAPSHOT .
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

- Для работы приложения необходимо развернуть зависимости: Consul, Keycloak, PostgreSQL
- Gateway и Accounts зависят от Consul и Keycloak — убедитесь, что они подняты корректно
- Переменные окружения настраиваются через `values.yaml`
- Image policy установлен в `IfNotPresent` для использования локальных образов


