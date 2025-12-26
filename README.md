# BankApp - Микросервисное банковское приложение

Распределённое банковское приложение на основе микросервисной архитектуры с поддержкой операций со счетами, переводов, обмена валют и уведомлений.

## 📋 Содержание

- [Архитектура](#архитектура)
- [Модули системы](#модули-системы)
- [Технологический стек](#технологический-стек)
- [Быстрый старт](#быстрый-старт)
- [Развёртывание](#развёртывание)
- [Разработка](#разработка)
- [Документация](#документация)

## 🏗 Архитектура

BankApp построен на микросервисной архитектуре с использованием:
- **Kubernetes** для оркестрации и service discovery
- **Keycloak** для аутентификации и авторизации (OAuth2/JWT)
- **PostgreSQL** для хранения данных
- **Gateway API** для маршрутизации внешнего трафика
- **Helm** для управления развёртыванием
- **Apache Kafka** для асинхронной коммуникации между сервисами

### Схема взаимодействия

```
┌──────────────┐
│   Browser    │
└──────┬───────┘
       │
┌──────▼───────────────────────────────────────────┐
│         Kubernetes Gateway API                    │
│  (gateway.networking.k8s.io/HTTPRoute)           │
└──────┬───────────────────────────────────────────┘
       │
       ├─────► Front-UI (8080)
       │         └──► Accounts (8081) ──► PostgreSQL
       │         └──► Cash (8082) ──────► Blocker (8086)
       │         └──► Transfer (8083) ──► Exchange (8084)
       │                               └► Blocker (8086)
       │
       │      📨 Kafka-based Communication
       │
       ├─────► Accounts ─────┐
       │                     │
       ├─────► Cash ─────────┤ Kafka Topic:
       │                     ├► account-notifications
       └─────► Transfer ─────┘           │
                                          ▼
                                   ┌──────────────┐
                                   │Notifications │
                                   │   (8087)     │
                                   └──────────────┘

┌──────────────────────────────────────────────────┐
│         Exchange-Generator (8085)                 │
│    (Periodic rate updates via Kafka)              │
└───────────────────┬───────────────────────────────┘
                    │ Kafka Topic:
                    └► exchange-rates ──► Exchange (8084)

┌──────────────────────────────────────────────────┐
│              Keycloak (8080)                      │
│    (OAuth2 Provider / JWT Issuer)                 │
└───────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│              Apache Kafka                         │
│         (Message Broker для асинхронной           │
│          межсервисной коммуникации)               │
└───────────────────────────────────────────────────┘
```

## 🧩 Модули системы

### Frontend

#### **front-ui** (порт 8080)
- Веб-интерфейс на Thymeleaf (Spring MVC)
- Аутентификация пользователей
- Управление профилем и счетами
- Интерфейс для переводов и операций с наличными
- Просмотр курсов валют

### Backend сервисы

#### **accounts** (порт 8081)
- Управление пользователями и их данными
- Управление банковскими счетами (RUB, USD, CNY)
- Хранение данных в PostgreSQL
- Миграции через Liquibase
- OAuth2 Resource Server
- 📨 Kafka Producer для уведомлений

#### **cash** (порт 8082)
- Операции с наличными (пополнение/снятие)
- Проверка блокировок через `blocker`
- 📨 Kafka Producer для уведомлений

#### **transfer** (порт 8083)
- Переводы между пользователями
- Переводы между собственными счетами
- Конвертация валют через `exchange`
- Проверка блокировок (лимит 50,000)
- 📨 Kafka Producer для уведомлений

#### **exchange** (порт 8084)
- Хранение курсов валют (RUB/USD/CNY)
- API для получения текущих курсов
- Конвертация между валютами
- 📨 Kafka Consumer для курсов валют

#### **exchange-generator** (порт 8085)
- Генерация курсов валют по расписанию
- 📨 Kafka Producer для обновления курсов в `exchange`
- Симуляция колебаний рынка

#### **blocker** (порт 8086)
- Проверка правил безопасности
- Блокировка подозрительных операций
- Лимиты по суммам (>50,000)

#### **notifications** (порт 8087)
- 📨 Kafka Consumer для уведомлений (основной канал)
- Создание и логирование уведомлений
- Email-эмуляция (вывод в логи)
- История операций пользователей
- REST API (deprecated, для обратной совместимости)

### Инфраструктура

#### **keycloak** (порт 8080)
- OAuth2 Provider
- JWT токены для межсервисного взаимодействия
- Realm: `bankapp`
- Client Credentials Flow для backend-to-backend

#### **postgresql** (порт 5432)
- Хранилище данных для `accounts`
- База данных: `bankapp`

## 🛠 Технологический стек

### Backend
- **Java 21**
- **Spring Boot 3.5.0**
- **Spring Cloud** (WebFlux, Security, OAuth2)
- **Spring Kafka** (асинхронная коммуникация)
- **Hibernate/JPA**
- **Liquibase**
- **PostgreSQL 13**
- **Apache Kafka** (message broker)
- **Maven**

### Frontend
- **Thymeleaf**
- **Bootstrap**
- **JavaScript/AJAX**

### DevOps
- **Kubernetes** (местный кластер или Minikube)
- **Helm 3** (пакетный менеджер)
- **Docker** & **Docker Compose**
- **Jenkins** (CI/CD с поддержкой Kafka)
- **Keycloak** (Identity Provider)
- **Apache Kafka** (развёртывается через Helm)

### Мониторинг
- **Spring Boot Actuator**
- **Kubernetes Health Checks** (liveness, readiness, startup probes)
- **Zipkin Distributed Tracing** (Micrometer Tracing)

## 🚀 Быстрый старт

### Предварительные требования

- **Docker** и **Docker Compose** (для Jenkins и локальной разработки)
- **Kubernetes** кластер (Minikube, Kind, Docker Desktop или облачный)
- **kubectl** настроенный для работы с вашим кластером
- **Helm 3**
- **Maven 3.9+** и **Java 21** (для локальной разработки)
- **Apache Kafka** (автоматически развёртывается в Kubernetes через Helm)

### 1. Клонирование репозитория

```bash
git clone <repository-url>
cd bankApp
```

### 2. Развёртывание в Kubernetes

#### Вариант A: Полное развёртывание через Helm (Рекомендуется)

**Включает Kafka для асинхронной коммуникации:**

```bash
# Развернуть все компоненты включая Kafka
cd helm
helm dependency update
helm install bankapp . --namespace test --create-namespace --wait

# Проверить статус всех компонентов
kubectl get pods -n test
kubectl get svc -n test

# Проверить что Kafka запущена
kubectl get pods -n test | grep kafka
```

**Что будет развёрнуто:**
- PostgreSQL (база данных)
- Keycloak (аутентификация)
- Apache Kafka (message broker)
- Zipkin (distributed tracing)
- Все микросервисы (accounts, cash, transfer, exchange, exchange-generator, blocker, notifications)
- Front-UI (веб-интерфейс)
- Gateway API для маршрутизации

#### Вариант B: Развёртывание отдельных компонентов

```bash
cd helm

# PostgreSQL
helm install bankapp-postgresql charts/postgresql -n test --create-namespace

# Keycloak
helm install keycloak charts/keycloak -n test

# Backend сервисы
helm install accounts charts/accounts -n test
helm install cash charts/cash -n test
helm install transfer charts/transfer -n test
helm install exchange charts/exchange -n test
helm install exchange-generator charts/exchange-generator -n test
helm install blocker charts/blocker -n test
helm install notifications charts/notifications -n test

# Frontend
helm install front-ui charts/front-ui -n test
```

### 3. Настройка Port-Forward для локального доступа

```bash
# Запустить все port-forwards
./start-port-forward.sh

# Или вручную для отдельных сервисов
kubectl port-forward -n test svc/front-ui 8080:8080 &
kubectl port-forward -n test svc/keycloak 8090:8080 &
kubectl port-forward -n test svc/accounts 8081:8081 &
```

### 4. Доступ к приложению

- **Веб-интерфейс**: http://localhost:8080
- **Keycloak Admin**: http://localhost:8090 (admin/admin)

### 5. Регистрация и вход

1. Откройте http://localhost:8080
2. Нажмите "Регистрация"
3. Заполните форму регистрации
4. После успешной регистрации вы будете автоматически перенаправлены в личный кабинет

## 🚀 Apache Kafka интеграция

### Обзор

BankApp использует Apache Kafka для асинхронной коммуникации между микросервисами, обеспечивая:
- ✅ **Высокую производительность** - устранены синхронные HTTP вызовы
- ✅ **Надёжность** - гарантия доставки "At least once"
- ✅ **Масштабируемость** - легко масштабируется горизонтально
- ✅ **Упрощение** - нет необходимости в OAuth2 токенах для межсервисной коммуникации

### Kafka Topics

| Topic | Producer | Consumer | Назначение |
|-------|----------|----------|------------|
| `account-notifications` | accounts, cash, transfer | notifications | Отправка уведомлений пользователям |
| `exchange-rates` | exchange-generator | exchange | Обновление курсов валют |

### Архитектура Kafka

```
┌─────────────┐   ┌──────────┐   ┌─────────────┐
│  Accounts   │──►│          │◄──│Notifications│
│   Service   │   │          │   │   Service   │
└─────────────┘   │          │   └─────────────┘
                  │  Kafka   │
┌─────────────┐   │  Topic   │
│    Cash     │──►│ account- │
│   Service   │   │  notif.  │
└─────────────┘   │          │
                  │          │
┌─────────────┐   │          │
│  Transfer   │──►│          │
│   Service   │   │          │
└─────────────┘   └──────────┘
```

### Конфигурация Producer

Модули `accounts`, `cash`, `transfer` отправляют сообщения в Kafka:

```yaml
spring:
  kafka:
    bootstrap-servers: kafka:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      # At least once delivery
      acks: all
      retries: 3
      enable-idempotence: true
    topics:
      notifications: account-notifications
```

### Конфигурация Consumer

Модуль `notifications` получает сообщения из Kafka:

```yaml
spring:
  kafka:
    bootstrap-servers: kafka:9092
    consumer:
      group-id: notifications-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
      auto-offset-reset: earliest
```

### Мигрированные модули

✅ **Полностью мигрированы на Kafka:**
- `accounts` - отправка уведомлений через Kafka
- `cash` - отправка уведомлений через Kafka
- `transfer` - отправка уведомлений через Kafka
- `exchange` - получение курсов валют через Kafka
- `exchange-generator` - отправка курсов валют через Kafka
- `notifications` - получение уведомлений через Kafka

📌 **Примечание:** REST API в `notifications` сохранён как `@Deprecated` для обратной совместимости.

## 📦 Развёртывание

### Локальная разработка с Docker Compose

Для быстрой локальной разработки и тестирования:

```bash
# Сборка всех модулей
./mvnw clean install -DskipTests

# Запуск всей системы
docker compose up -d

# Просмотр логов
docker compose logs -f

# Остановка
docker compose down
```

### Production развёртывание

См. подробную документацию:
- [DEPLOYMENT.md](DEPLOYMENT.md) - Полное руководство по развёртыванию
- [helm/README.md](helm/README.md) - Документация по Helm charts

### CI/CD с Jenkins

Полная автоматизация сборки и развёртывания через Jenkins с поддержкой всех инфраструктурных компонентов.

#### Быстрый старт Jenkins

```bash
cd jenkins
docker-compose up -d

# Откройте Jenkins UI
open http://localhost:8080
```

#### Доступные Pipeline Jobs

**Микросервисы приложения:**
- `accounts` - сервис аккаунтов (с Kafka)
- `cash` - операции с наличными (с Kafka)
- `transfer` - переводы (с Kafka)
- `exchange` - обмен валют (с Kafka)
- `exchange-generator` - генератор курсов (с Kafka)
- `notifications` - уведомления (Kafka consumer)
- `blocker` - блокировки
- `front-ui` - веб-интерфейс
- `keycloak` - управление Keycloak

**Инфраструктурные компоненты:**
- `kafka` - Apache Kafka message broker
- `zipkin` - Distributed Tracing
- `prometheus` - Metrics Collection
- `grafana` - Metrics Visualization
- `elk` - ELK Stack (Elasticsearch, Logstash, Kibana)

**Главный Pipeline:**
- `Jenkinsfile` - Umbrella pipeline для развёртывания всей инфраструктуры и приложений

#### Применение Jenkinsfile в CI/CD

**1. Создание Pipeline Jobs в Jenkins:**

```groovy
// Для каждого компонента создайте Pipeline job:
// New Item → Pipeline → Pipeline script from SCM

// Пример для Zipkin:
- Name: zipkin-pipeline
- SCM: Git
- Repository URL: <your-repo-url>
- Script Path: jenkins/jenkinsfiles/zipkin.Jenkinsfile
```

**2. Параметры Pipeline Jobs:**

Каждый infrastructure pipeline поддерживает параметры:
- `ACTION`: deploy, upgrade, update-config, rollback, status
- Специфичные параметры (версии, ресурсы, конфигурация)

**3. Запуск через Jenkins UI:**

```bash
# 1. Выберите нужный pipeline
# 2. Нажмите "Build with Parameters"
# 3. Выберите ACTION и настройте параметры
# 4. Нажмите "Build"
```

**4. Автоматическое развёртывание через главный Pipeline:**

```bash
# Главный pipeline автоматически развёртывает:
# - Kafka
# - Zipkin
# - Prometheus
# - Grafana
# - ELK Stack
# - Все микросервисы

# Параметры:
# - DEPLOY_INFRASTRUCTURE: true/false
# - DEPLOY_APPLICATIONS: true/false
# - RUN_TESTS: true/false
```

## 🏠 Локальное развёртывание для разработчиков

### Полный гайд для локальной разработки и тестирования

Этот раздел поможет вам развернуть проект локально для разработки и тестирования всех функций, включая Kafka интеграцию.

#### Шаг 1: Установка необходимых инструментов

```bash
# Проверьте наличие необходимых инструментов
docker --version        # Docker 20.10+
kubectl version        # kubectl 1.25+
helm version          # Helm 3.10+
mvn --version         # Maven 3.9+ (опционально)
java --version        # Java 21+ (опционально)

# Установка Minikube (если ещё не установлен)
# macOS
brew install minikube

# Linux
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
```

#### Шаг 2: Запуск локального Kubernetes кластера

```bash
# Запустите Minikube с достаточными ресурсами
minikube start --cpus=4 --memory=8192 --disk-size=20g

# Проверьте статус
minikube status

# Настройте kubectl для работы с Minikube
kubectl config use-context minikube

# Проверьте подключение
kubectl get nodes
```

#### Шаг 3: Сборка Docker образов

```bash
# Перейдите в корень проекта
cd /path/to/bankApp

# Настройте Docker для использования Minikube registry
eval $(minikube docker-env)

# Соберите все модули через Maven
./mvnw clean package -DskipTests

# Соберите Docker образы (используйте тег который указан в Helm charts)
docker build -f accounts/dockerfile -t bankapp/accounts:0.0.2-SNAPSHOT .
docker build -f cash/dockerfile -t bankapp/cash:0.0.2-SNAPSHOT .
docker build -f transfer/dockerfile -t bankapp/transfer:0.0.2-SNAPSHOT .
docker build -f exchange/dockerfile -t bankapp/exchange:0.0.2-SNAPSHOT .
docker build -f exchange-generator/dockerfile -t bankapp/exchange-generator:0.0.2-SNAPSHOT .
docker build -f blocker/dockerfile -t bankapp/blocker:0.0.2-SNAPSHOT .
docker build -f notifications/dockerfile -t bankapp/notifications:0.0.2-SNAPSHOT .
docker build -f front-ui/dockerfile -t bankapp/front-ui:0.0.2-SNAPSHOT .

# Проверьте что образы созданы
docker images | grep bankapp
```

#### Шаг 4: Развёртывание через Helm

```bash
# Перейдите в директорию Helm
cd helm

# Обновите зависимости (включая Kafka)
helm dependency update

# Разверните приложение в namespace test
helm install bankapp . --namespace test --create-namespace --wait --timeout=10m

# Проверьте что все поды запустились
kubectl get pods -n test

# Дождитесь пока все поды будут Ready
kubectl wait --for=condition=ready pod --all -n test --timeout=600s
```

**Ожидаемые поды:**
- `bankapp-postgresql-0` - База данных PostgreSQL
- `bankapp-keycloak-*` - Identity Provider
- `bankapp-kafka-*` - Kafka broker(s)
- `bankapp-zipkin-*` - Distributed tracing server
- `bankapp-accounts-*` - Сервис аккаунтов
- `bankapp-cash-*` - Сервис операций с наличными
- `bankapp-transfer-*` - Сервис переводов
- `bankapp-exchange-*` - Сервис обмена валют
- `bankapp-exchange-generator-*` - Генератор курсов
- `bankapp-blocker-*` - Сервис блокировок
- `bankapp-notifications-*` - Сервис уведомлений
- `bankapp-front-ui-*` - Веб-интерфейс

#### Шаг 5: Настройка Port-Forward для доступа

Используйте готовый скрипт:

```bash
# Из корня проекта
./start-port-forward.sh

# Или вручную для отдельных сервисов
kubectl port-forward -n test svc/front-ui 8080:8080 &
kubectl port-forward -n test svc/keycloak 8090:8080 &
kubectl port-forward -n test svc/accounts 8081:8081 &
kubectl port-forward -n test svc/cash 8082:8082 &
kubectl port-forward -n test svc/transfer 8083:8083 &
kubectl port-forward -n test svc/exchange 8084:8084 &
kubectl port-forward -n test svc/notifications 8087:8087 &

# Для Kafka (опционально, для отладки)
kubectl port-forward -n test svc/kafka 9092:9092 &

# Для Zipkin UI (distributed tracing)
kubectl port-forward -n test svc/bankapp-zipkin 9411:9411 &
```

#### Шаг 6: Проверка доступности приложения

```bash
# Веб-интерфейс
open http://localhost:8080

# Keycloak Admin Console
open http://localhost:8090
# Логин: admin / admin

# Zipkin UI (Distributed Tracing)
open http://localhost:9411

# Health checks для микросервисов
curl http://localhost:8081/actuator/health  # Accounts
curl http://localhost:8082/actuator/health  # Cash
curl http://localhost:8083/actuator/health  # Transfer
curl http://localhost:8087/actuator/health  # Notifications
curl http://localhost:9411/health           # Zipkin
```

#### Шаг 7: Тестирование через UI

1. **Откройте** http://localhost:8080
2. **Зарегистрируйтесь** (создайте нового пользователя)
3. **Выполните операции:**
   - Создайте счёт
   - Пополните счёт (cash deposit)
   - Сделайте перевод другому пользователю
   - Обменяйте валюту

4. **Проверьте уведомления** в логах notifications сервиса:

```bash
kubectl logs -n test deployment/notifications --tail=50 -f
```

Вы должны увидеть сообщения типа:
```
Received notification from Kafka: userId=..., type=SUCCESS, source=CASH
Received notification from Kafka: userId=..., type=SUCCESS, source=TRANSFER
```

#### Шаг 8: Обновление после изменений

После внесения изменений в код:

```bash
# 1. Пересоберите изменённый модуль
cd /path/to/bankApp
./mvnw clean package -DskipTests -pl cash

# 2. Пересоберите Docker образ с уникальным тегом
eval $(minikube docker-env)
docker build -f cash/dockerfile -t bankapp/cash:test-fix .

# 3. Обновите deployment в Kubernetes
kubectl set image deployment/cash -n test cash=bankapp/cash:test-fix
kubectl rollout status deployment/cash -n test

# Или используйте Helm upgrade
helm upgrade bankapp . -n test \
  --set cash.image.tag=test-fix \
  --wait
```


### Troubleshooting локального развёртывания

#### Проблема: Поды не запускаются

```bash
# Проверьте события
kubectl get events -n test --sort-by='.lastTimestamp' | tail -20

# Проверьте конкретный под
kubectl describe pod <pod-name> -n test

# Проверьте логи
kubectl logs <pod-name> -n test
```

#### Проблема: Образы не найдены

```bash
# Убедитесь что используете Minikube Docker
eval $(minikube docker-env)
docker images | grep bankapp

# Проверьте imagePullPolicy в Helm values
# Должно быть: imagePullPolicy: IfNotPresent или Never
```

#### Проблема: Kafka не запускается

```bash
# Проверьте Kafka логи
kubectl logs -n test deployment/kafka

# Проверьте ресурсы Minikube
minikube status

# Увеличьте ресурсы если нужно
minikube stop
minikube delete
minikube start --cpus=4 --memory=8192
```

#### Проблема: Services не отправляют в Kafka

```bash
# Проверьте конфигурацию Kafka в application.yml
kubectl exec -it -n test deployment/cash -- env | grep KAFKA

# Проверьте что KafkaTemplate bean создан
kubectl logs -n test deployment/cash | grep "KafkaTemplate"

# Проверьте наличие KafkaProducerConfig класса в JAR
kubectl exec -it -n test deployment/cash -- \
  jar -tf /app/app.jar | grep KafkaProducerConfig
```

## 💻 Разработка

### Сборка проекта

#### Полная сборка всех модулей

```bash
# Сборка без тестов (быстро)
./mvnw clean package -DskipTests

# Сборка с тестами
./mvnw clean install

# Сборка конкретного модуля
./mvnw -pl accounts clean package -DskipTests

# Параллельная сборка (быстрее)
./mvnw -T 4 clean package -DskipTests
```

#### Сборка Docker образов

```bash
# Для локальной разработки с Minikube
eval $(minikube docker-env)

# Сборка всех образов
docker build -f accounts/dockerfile -t bankapp/accounts:0.0.1-SNAPSHOT .
docker build -f cash/dockerfile -t bankapp/cash:0.0.1-SNAPSHOT .
docker build -f transfer/dockerfile -t bankapp/transfer:0.0.1-SNAPSHOT .
docker build -f exchange/dockerfile -t bankapp/exchange:0.0.1-SNAPSHOT .
docker build -f exchange-generator/dockerfile -t bankapp/exchange-generator:0.0.1-SNAPSHOT .
docker build -f blocker/dockerfile -t bankapp/blocker:0.0.1-SNAPSHOT .
docker build -f notifications/dockerfile -t bankapp/notifications:0.0.1-SNAPSHOT .
docker build -f front-ui/dockerfile -t bankapp/front-ui:0.0.1-SNAPSHOT .

# Или используйте скрипт для пересборки
./rebuild-and-deploy-all.sh
```

### Локальный запуск в среде разработки

#### Вариант 1: Запуск отдельного микросервиса локально

```bash
# 1. Запустите зависимости через Docker Compose
docker-compose up -d postgresql keycloak

# 2. Запустите микросервис
cd accounts
./mvnw spring-boot:run

# Или с профилем
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

#### Вариант 2: Полное локальное развёртывание через Docker Compose

```bash
# Сборка всех модулей
./mvnw clean package -DskipTests

# Запуск всей системы
docker-compose up -d

# Просмотр логов
docker-compose logs -f

# Остановка
docker-compose down
```

#### Вариант 3: Развёртывание в локальном Kubernetes (Minikube)

```bash
# 1. Запустите Minikube
minikube start --cpus=4 --memory=8192

# 2. Соберите образы для Minikube
eval $(minikube docker-env)
./mvnw clean package -DskipTests
# ... сборка Docker образов (см. выше)

# 3. Разверните через Helm
cd helm
helm dependency update
helm install bankapp . -n test --create-namespace --wait

# 4. Настройте port-forward
./start-port-forward.sh

# 5. Откройте приложение
open http://localhost:8080
```

### Пересборка и обновление после изменений

#### Обновление микросервиса в Kubernetes

```bash
# 1. Пересоберите JAR
cd accounts
./mvnw clean package -DskipTests

# 2. Пересоберите Docker образ БЕЗ КЭША
cd ..
eval $(minikube docker-env)
docker build --no-cache -f accounts/dockerfile -t bankapp/accounts:0.0.1-SNAPSHOT .

# 3. Загрузите образ в Minikube (если нужно)
minikube image load bankapp/accounts:0.0.1-SNAPSHOT

# 4. Удалите старый pod (Kubernetes создаст новый)
kubectl delete pod -l app.kubernetes.io/name=accounts -n test

# 5. Проверьте статус
kubectl get pods -n test -l app.kubernetes.io/name=accounts
kubectl logs -n test -l app.kubernetes.io/name=accounts -f
```

#### Обновление конфигурации через Helm

```bash
# 1. Обновите helm/values.yaml или helm/charts/<service>/values.yaml

# 2. Примените изменения
helm upgrade bankapp helm/ -n test --wait

# Или для конкретного сервиса
helm upgrade accounts helm/charts/accounts -n test --wait
```

**ВАЖНО:** ConfigMap всегда перекрывает application.yml!
- Обновляйте конфигурацию в Helm values, а не в application.yml
- После обновления ConfigMap перезапустите pod

### Проверка здоровья сервисов

#### Локально (Docker Compose или Spring Boot)

```bash
# Health checks
curl http://localhost:8081/actuator/health  # Accounts
curl http://localhost:8082/actuator/health  # Cash
curl http://localhost:8083/actuator/health  # Transfer

# Metrics
curl http://localhost:8081/actuator/prometheus

# Info
curl http://localhost:8081/actuator/info
```

#### В Kubernetes

```bash
# Статус всех pods
kubectl get pods -n test

# Логи конкретного сервиса
kubectl logs -n test -l app.kubernetes.io/name=accounts -f

# Описание pod (события, ошибки)
kubectl describe pod -n test <pod-name>

# Health check внутри pod
kubectl exec -n test <pod-name> -- curl localhost:8081/actuator/health

# Port-forward для доступа
kubectl port-forward -n test svc/accounts 8081:8081
curl http://localhost:8081/actuator/health
```

### Отладка

#### Просмотр логов

```bash
# Все логи сервиса
kubectl logs -n test deployment/accounts

# Последние 100 строк
kubectl logs -n test deployment/accounts --tail=100

# Следить за логами в реальном времени
kubectl logs -n test deployment/accounts -f

# Логи предыдущего контейнера (если pod перезапустился)
kubectl logs -n test <pod-name> --previous
```

#### Проверка переменных окружения

```bash
# Все переменные окружения
kubectl exec -n test deployment/accounts -- env

# Конкретная переменная
kubectl exec -n test deployment/accounts -- env | grep KAFKA

# Проверка application.yml внутри контейнера
kubectl exec -n test deployment/accounts -- cat /app/application.yml
```

#### Интерактивный shell в контейнере

```bash
# Войти в контейнер
kubectl exec -it -n test deployment/accounts -- /bin/sh

# Внутри контейнера можно:
# - Проверить файлы: ls -la /app
# - Проверить процессы: ps aux
# - Проверить сеть: wget http://kafka:9092
# - Проверить JAR: jar -tf /app/app.jar | grep KafkaConfig
```

## 🔧 Конфигурация

### Переменные окружения

Каждый модуль можно настроить через переменные окружения:

#### Keycloak
- `KEYCLOAK_AUTH_SERVER_URL` - URL Keycloak сервера (default: `http://keycloak:8080`)
- `KEYCLOAK_JWK_SET_URI` - URI для проверки JWT токенов

#### База данных (только accounts)
- `DB_HOST` - Хост PostgreSQL (default: `bankapp-postgresql`)
- `DB_PORT` - Порт (default: `5432`)
- `DB_NAME` - Имя БД (default: `bankapp`)
- `DB_USER` - Пользователь (default: `root`)
- `DB_PASSWORD` - Пароль (default: `root`)

#### OAuth2
- `OAUTH2_CLIENT_ID` - ID клиента для OAuth2
- `OAUTH2_CLIENT_SECRET` - Секрет клиента

#### URLs сервисов
- `ACCOUNTS_SERVICE_URL` - URL сервиса accounts
- `CASH_SERVICE_URL` - URL сервиса cash
- `TRANSFER_SERVICE_URL` - URL сервиса transfer
- `EXCHANGE_SERVICE_URL` - URL сервиса exchange
- `BLOCKER_SERVICE_URL` - URL сервиса blocker
- `NOTIFICATIONS_SERVICE_URL` - URL сервиса notifications (deprecated, используйте Kafka)

#### Kafka
- `KAFKA_BOOTSTRAP_SERVERS` - Адрес Kafka брокеров (default: `kafka:9092`)
- `SPRING_KAFKA_TOPICS_NOTIFICATIONS` - Topic для уведомлений (default: `account-notifications`)
- `SPRING_KAFKA_TOPICS_EXCHANGE` - Topic для курсов валют (default: `exchange-rates`)
- `SPRING_KAFKA_CONSUMER_GROUP_ID` - Consumer group ID для сервиса

### Изменение конфигурации через Helm

```bash
# Изменить values.yaml
vim helm/charts/accounts/values.yaml

# Применить изменения
helm upgrade accounts helm/charts/accounts -n test
```

## 🔐 Безопасность

### Аутентификация и авторизация

- **Keycloak OAuth2** для всех межсервисных вызовов
- **JWT токены** с проверкой подписи
- **Client Credentials Flow** для backend-to-backend
- **Spring Security** для защиты endpoints

### Блокировка подозрительных операций

- Переводы >50,000 блокируются автоматически
- Проверка через сервис `blocker`
- Логирование всех блокировок

## 🧪 Тестирование

```bash
# Запуск всех тестов
./mvnw clean test

# Тесты конкретного модуля
./mvnw -pl accounts test

# Пропустить тесты при сборке
./mvnw clean package -DskipTests
```

### Логи и диагностика

```bash
# Просмотр логов
kubectl logs -n test <pod-name> --tail=100 -f

# Описание пода (события)
kubectl describe pod -n test <pod-name>

# Проверка health endpoints
kubectl exec -n test <pod-name> -- curl localhost:8081/actuator/health
```

## 📊 Мониторинг и Observability

### Полный стек мониторинга

BankApp включает полный стек для observability:

| Компонент | Назначение | URL | Credentials |
|-----------|-----------|-----|-------------|
| **Zipkin** | Distributed Tracing | http://localhost:9411 | - |
| **Prometheus** | Metrics Collection | http://localhost:9090 | - |
| **Grafana** | Metrics Visualization | http://localhost:3000 | admin/admin |
| **Kibana** | Logs Visualization | http://localhost:5601 | - |
| **Kafka UI** | Kafka Monitoring | http://localhost:8085 | - |

### Развёртывание мониторинга

#### Через главный Pipeline (рекомендуется)

```bash
# В Jenkins запустите главный pipeline с параметрами:
# - DEPLOY_INFRASTRUCTURE: true
# - DEPLOY_APPLICATIONS: false

# Или через Helm:
helm upgrade --install bankapp helm/ -n test \
  --set zipkin.enabled=true \
  --set prometheus.enabled=true \
  --set grafana.enabled=true \
  --set elasticsearch.enabled=true \
  --set logstash.enabled=true \
  --set kibana.enabled=true \
  --wait
```

#### Через отдельные Pipeline Jobs

```bash
# В Jenkins создайте и запустите:
# - zipkin-pipeline (ACTION: deploy)
# - prometheus-pipeline (ACTION: deploy)
# - grafana-pipeline (ACTION: deploy)
# - elk-pipeline (ACTION: deploy, COMPONENT: all)
```

### Distributed Tracing (Zipkin)

**Что трассируется:**
- HTTP запросы между микросервисами
- Database queries
- Kafka messages
- External API calls

**Использование:**
```bash
# 1. Откройте Zipkin UI
open http://localhost:9411

# 2. Выполните операции в приложении
# 3. В Zipkin UI нажмите "Run Query"
# 4. Просмотрите трейсы и span'ы

# Проверка интеграции
curl http://localhost:9411/api/v2/services  # Список сервисов
curl http://localhost:9411/api/v2/traces    # Трейсы
```

**Конфигурация в приложениях:**
```yaml
spring:
  zipkin:
    base-url: http://bankapp-zipkin:9411
    enabled: true
  sleuth:
    sampler:
      probability: 1.0  # 100% трейсов
```

### Metrics Collection (Prometheus)

**Что собирается:**
- JVM метрики (heap, threads, GC)
- HTTP метрики (requests, latency, errors)
- Database метрики (connections, queries)
- Kafka метрики (producer/consumer)
- Custom business метрики

**Использование:**
```bash
# Откройте Prometheus UI
open http://localhost:9090

# Примеры PromQL запросов:
# - http_server_requests_seconds_count
# - jvm_memory_used_bytes
# - kafka_producer_record_send_total
# - process_cpu_usage
```

**Endpoints метрик:**
```bash
# Prometheus собирает метрики с:
curl http://localhost:8081/actuator/prometheus  # Accounts
curl http://localhost:8082/actuator/prometheus  # Cash
curl http://localhost:8083/actuator/prometheus  # Transfer
```

### Metrics Visualization (Grafana)

**Предустановленные дашборды:**
- JVM Metrics Dashboard
- HTTP Requests Dashboard
- Kafka Metrics Dashboard
- Business Metrics Dashboard

**Использование:**
```bash
# 1. Откройте Grafana
open http://localhost:3000
# Логин: admin / admin

# 2. Datasource (автоматически настроен):
# - Prometheus: http://bankapp-prometheus:9090

# 3. Импортируйте дашборды или создайте свои
```

### Centralized Logging (ELK Stack)

**Архитектура логирования:**
```
Приложения → Logback → Kafka (logs-topic) → Logstash → Elasticsearch → Kibana
```

**Что логируется:**
- Application logs (INFO, WARN, ERROR)
- Access logs
- Audit logs
- Kafka events

**Использование:**
```bash
# 1. Откройте Kibana
open http://localhost:5601

# 2. Создайте Index Pattern:
# - Pattern: logstash-*
# - Time field: @timestamp

# 3. Перейдите в Discover для просмотра логов

# 4. Создайте визуализации и дашборды
```

**Конфигурация в приложениях:**
```xml
<!-- logback-spring.xml -->
<appender name="KAFKA" class="com.github.danielwegener.logback.kafka.KafkaAppender">
    <topic>logs-topic</topic>
    <keyingStrategy class="com.github.danielwegener.logback.kafka.keying.NoKeyKeyingStrategy"/>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</appender>
```

### Health Checks

Все сервисы имеют Kubernetes probes:
- **Startup Probe** - проверка успешного запуска (до 30 попыток)
- **Liveness Probe** - проверка работоспособности (каждые 10 сек)
- **Readiness Probe** - готовность принимать трафик (каждые 5 сек)

```bash
# Проверка health всех компонентов
kubectl get pods -n test

# Health check конкретного сервиса
kubectl exec -n test <pod-name> -- curl localhost:8081/actuator/health

# Или через port-forward
curl http://localhost:8081/actuator/health
```

### Actuator Endpoints

```bash
# Health (общее состояние)
curl http://localhost:8081/actuator/health

# Metrics (Prometheus формат)
curl http://localhost:8081/actuator/prometheus

# Info (информация о приложении)
curl http://localhost:8081/actuator/info

# Loggers (текущие уровни логирования)
curl http://localhost:8081/actuator/loggers

# Изменение уровня логирования
curl -X POST http://localhost:8081/actuator/loggers/ru.rpovetkin \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'
```

### Мониторинг Kafka

```bash
# Через Kafka UI
open http://localhost:8085

# Или через kubectl
kubectl exec -n test deploy/kafka -- \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

kubectl exec -n test deploy/kafka -- \
  /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# Проверка lag в consumer group
kubectl exec -n test deploy/kafka -- \
  /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notifications-group --describe
```

### Troubleshooting через мониторинг

**Проблема: Высокая latency**
```bash
# 1. Проверьте трейсы в Zipkin
# 2. Найдите медленные span'ы
# 3. Проверьте метрики в Prometheus:
#    - http_server_requests_seconds{quantile="0.95"}
#    - jdbc_connections_active
```

**Проблема: Ошибки в приложении**
```bash
# 1. Проверьте логи в Kibana
# 2. Фильтр: level:ERROR
# 3. Проверьте метрики ошибок в Prometheus:
#    - http_server_requests_seconds_count{status="5xx"}
```

**Проблема: Memory leak**
```bash
# 1. Проверьте JVM метрики в Grafana
# 2. Prometheus запрос:
#    - jvm_memory_used_bytes{area="heap"}
# 3. Heap dump:
kubectl exec -n test <pod-name> -- jmap -dump:live,format=b,file=/tmp/heap.bin 1
```

**Управление приложением:**
```bash
# Port forwarding
./start-port-forward.sh          # Запуск всех port-forward
./stop-port-forward.sh           # Остановка port-forward

## 🎯 Ключевые особенности проекта

### ✅ Архитектура
- Микросервисная архитектура с 8+ сервисами
- Kubernetes native приложение
- Service discovery через Kubernetes DNS
- Gateway API для маршрутизации трафика
- Event-driven architecture с Apache Kafka

### ✅ Безопасность
- OAuth2/OIDC через Keycloak
- JWT токены для аутентификации
- Client Credentials Flow для backend-to-backend
- Role-based access control
- Блокировка подозрительных операций

### ✅ Асинхронная коммуникация
- Apache Kafka для межсервисной коммуникации
- Гарантия доставки "At least once"
- Idempotent producers
- Consumer groups для масштабирования
- Kafka UI для мониторинга

### ✅ Полный стек Observability
**Distributed Tracing (Zipkin):**
- Трассировка всех HTTP запросов между сервисами
- Трассировка Kafka messages
- Трассировка database queries
- Sample rate: 100%

**Metrics (Prometheus + Grafana):**
- Сбор метрик со всех микросервисов
- JVM, HTTP, Database, Kafka метрики
- Предустановленные Grafana дашборды
- Retention: 15d (TEST), 30d (PROD)

**Centralized Logging (ELK Stack):**
- Elasticsearch для хранения логов
- Logstash для обработки (читает из Kafka)
- Kibana для визуализации
- Структурированное логирование (JSON)

**Health Checks:**
- Kubernetes probes (startup, liveness, readiness)
- Spring Boot Actuator endpoints
- Автоматический мониторинг всех компонентов

### ✅ CI/CD Automation
**Jenkins Pipelines для всех компонентов:**
- Микросервисы (accounts, cash, transfer, exchange, и др.)
- Apache Kafka (message broker)
- Zipkin (distributed tracing)
- Prometheus (metrics collection)
- Grafana (metrics visualization)
- ELK Stack (centralized logging)

**Возможности:**
- Автоматическое развёртывание через главный umbrella pipeline
- Параллельное развёртывание инфраструктуры
- Параметризованные pipelines (deploy, upgrade, rollback, status)
- Автоматические health checks
- Поддержка TEST и PROD окружений
- Ручное подтверждение для PROD

**Тестирование:**
- Unit тесты с embedded Kafka
- Интеграционные тесты
- Тестовые скрипты для локального развёртывания
- Автоматическая валидация Helm charts

## 📝 Лицензия

Проект создан в учебных целях.

---

**Последнее обновление:** Декабрь 2025  
**Версия:** 3.0 (с полным CI/CD и Observability стеком)

**Что нового в версии 3.0:**
- ✅ Полная CI/CD автоматизация для всех компонентов
- ✅ Zipkin distributed tracing интеграция
- ✅ Prometheus + Grafana для метрик
- ✅ ELK Stack для централизованного логирования
- ✅ Jenkins pipelines для инфраструктуры
- ✅ Тестовые скрипты для всех компонентов
- ✅ Обширная документация по CI/CD  

