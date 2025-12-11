pipeline {
    agent any

    environment {
        MODULE_NAME     = 'kafka'
        NAMESPACE_TEST  = 'test'
        NAMESPACE_PROD  = 'prod'
    }

    parameters {
        choice(
            name: 'ACTION',
            choices: ['deploy', 'upgrade', 'update-topics', 'update-config', 'rollback', 'status'],
            description: 'Выберите действие для выполнения'
        )
        string(
            name: 'KAFKA_REPLICAS',
            defaultValue: '1',
            description: 'Количество реплик Kafka (для PROD рекомендуется 3)'
        )
        booleanParam(
            name: 'ENABLE_KAFKA_UI',
            defaultValue: true,
            description: 'Включить Kafka UI для мониторинга'
        )
        text(
            name: 'ADDITIONAL_TOPICS',
            defaultValue: '',
            description: 'Дополнительные топики в формате JSON (опционально)'
        )
        string(
            name: 'KAFKA_VERSION',
            defaultValue: '7.5.3',
            description: 'Версия Confluent Platform'
        )
    }

    stages {
        stage('Checkout') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: scm.branches,
                    extensions: scm.extensions + [
                        [$class: 'CloneOption', timeout: 20, depth: 0, noTags: false, shallow: false]
                    ],
                    userRemoteConfigs: scm.userRemoteConfigs
                ])
            }
        }

        stage('Validate Helm Chart') {
            when {
                expression { params.ACTION in ['deploy', 'upgrade', 'update-config'] }
            }
            steps {
                sh """
                    echo "Validating Helm chart for ${MODULE_NAME}..."
                    
                    # Проверяем наличие helm chart
                    if [ ! -d "helm/charts/${MODULE_NAME}" ]; then
                        echo "❌ Helm chart для ${MODULE_NAME} не найден"
                        exit 1
                    fi
                    
                    # Валидация Helm chart
                    helm lint helm/charts/${MODULE_NAME}
                    
                    # Проверка шаблонов (KRaft mode)
                    echo "Using KRaft mode (без Zookeeper)"
                    helm template ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                      --namespace ${NAMESPACE_TEST} \\
                      --set kafka.replicaCount=${params.KAFKA_REPLICAS} \\
                      --set kafkaUI.enabled=${params.ENABLE_KAFKA_UI} \\
                      > /tmp/kafka-manifests.yaml
                    
                    echo "✅ Helm chart валидация пройдена"
                    echo "📄 Сгенерированные манифесты:"
                    head -n 50 /tmp/kafka-manifests.yaml
                """
            }
        }

        stage('Check Kafka Status') {
            when {
                expression { params.ACTION == 'status' }
            }
            steps {
                script {
                    def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                    
                    sh """
                        echo "📊 Проверка статуса Kafka в namespace: ${namespace}"
                        echo "================================================"
                        
                        # Проверяем наличие Kafka
                        if ! helm list -n ${namespace} | grep -q ${MODULE_NAME}; then
                            echo "⚠️  Kafka не установлен в namespace ${namespace}"
                            exit 0
                        fi
                        
                        echo "📦 Helm Release:"
                        helm list -n ${namespace} | grep ${MODULE_NAME}
                        
                        echo ""
                        echo "🔧 KRaft Mode (без Zookeeper)"
                        
                        echo ""
                        echo "📨 Kafka Pods:"
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/component=kafka
                        
                        echo ""
                        echo "🎨 Kafka UI Pods:"
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/component=kafka-ui || echo "Kafka UI не установлен"
                        
                        echo ""
                        echo "🔌 Services:"
                        kubectl get svc -n ${namespace} | grep -E "kafka|zookeeper"
                        
                        echo ""
                        echo "💾 PVCs:"
                        kubectl get pvc -n ${namespace} | grep -E "kafka|zookeeper"
                        
                        echo ""
                        echo "📋 Kafka Topics (если Kafka доступен):"
                        kubectl exec -n ${namespace} deploy/kafka -- kafka-topics --bootstrap-server localhost:9092 --list || echo "Не удалось получить список топиков"
                        
                        echo ""
                        echo "✅ Проверка статуса завершена"
                    """
                }
            }
        }

        stage('Deploy to TEST') {
            when {
                allOf {
                    branch 'dev'
                    expression { params.ACTION in ['deploy', 'upgrade'] }
                }
            }
            steps {
                script {
                    sh """
                        echo "🚀 Deploying Kafka to TEST environment (KRaft mode)..."
                        
                        # Создаем namespace если не существует
                        kubectl create namespace ${NAMESPACE_TEST} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Деплоим через Helm (KRaft mode)
                        helm upgrade --install ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${NAMESPACE_TEST} \\
                          --set kafka.image.tag=${params.KAFKA_VERSION} \\
                          --set kafka.replicaCount=${params.KAFKA_REPLICAS} \\
                          --set kafkaUI.enabled=${params.ENABLE_KAFKA_UI} \\
                          --wait --timeout=10m
                        
                        echo "✅ Деплой ${MODULE_NAME} в TEST завершен"
                        
                        # Показываем статус
                        echo ""
                        echo "📊 Статус развертывания:"
                        kubectl get pods -n ${NAMESPACE_TEST} -l app.kubernetes.io/name=${MODULE_NAME}
                    """
                }
            }
        }

        stage('Verify Deployment TEST') {
            when {
                allOf {
                    branch 'dev'
                    expression { params.ACTION in ['deploy', 'upgrade'] }
                }
            }
            steps {
                sh """
                    echo "🔍 Проверка деплоя в TEST (KRaft mode)..."
                    
                    # Ждем готовности Kafka
                    echo "Ожидание готовности Kafka..."
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/component=kafka \\
                      -n ${NAMESPACE_TEST} \\
                      --timeout=300s || {
                        echo "⚠️  Kafka не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/component=kafka -n ${NAMESPACE_TEST} --tail=50
                        exit 1
                      }
                    
                    echo "✅ Kafka готов"
                    
                    # Проверяем Kafka UI если включен
                    if [ "${params.ENABLE_KAFKA_UI}" = "true" ]; then
                        echo "Ожидание готовности Kafka UI..."
                        kubectl wait --for=condition=ready pod \\
                          -l app.kubernetes.io/component=kafka-ui \\
                          -n ${NAMESPACE_TEST} \\
                          --timeout=180s || echo "⚠️  Kafka UI не готов"
                    fi
                    
                    echo ""
                    echo "✅ Deployment в TEST успешно проверен"
                    
                    # Показываем информацию о сервисах
                    echo ""
                    echo "🔌 Services:"
                    kubectl get svc -n ${NAMESPACE_TEST} | grep -E "kafka|zookeeper"
                    
                    # Проверяем топики
                    echo ""
                    echo "📋 Kafka Topics:"
                    kubectl exec -n ${NAMESPACE_TEST} deploy/kafka -- \\
                      kafka-topics --bootstrap-server localhost:9092 --list || echo "Топики еще не созданы"
                """
            }
        }

        stage('Update Topics') {
            when {
                expression { params.ACTION == 'update-topics' }
            }
            steps {
                script {
                    def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                    
                    sh """
                        echo "📝 Обновление топиков Kafka в ${namespace}..."
                        
                        # Проверяем, что Kafka запущен
                        if ! kubectl get deployment kafka -n ${namespace} > /dev/null 2>&1; then
                            echo "❌ Kafka не найден в namespace ${namespace}"
                            exit 1
                        fi
                        
                        # Получаем текущие топики
                        echo "Текущие топики:"
                        kubectl exec -n ${namespace} deploy/kafka -- \\
                          kafka-topics --bootstrap-server localhost:9092 --list
                        
                        # Обновляем через Helm (это пересоздаст Job для топиков)
                        helm upgrade ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${namespace} \\
                          --reuse-values \\
                          --wait --timeout=5m
                        
                        echo "✅ Топики обновлены"
                        
                        # Показываем обновленный список топиков
                        echo ""
                        echo "Обновленный список топиков:"
                        kubectl exec -n ${namespace} deploy/kafka -- \\
                          kafka-topics --bootstrap-server localhost:9092 --list
                        
                        # Показываем детали топиков
                        echo ""
                        echo "Детали топиков:"
                        kubectl exec -n ${namespace} deploy/kafka -- \\
                          kafka-topics --bootstrap-server localhost:9092 --describe
                    """
                }
            }
        }

        stage('Update Configuration') {
            when {
                expression { params.ACTION == 'update-config' }
            }
            steps {
                script {
                    def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                    
                    sh """
                        echo "⚙️  Обновление конфигурации Kafka в ${namespace}..."
                        
                        # Обновляем через Helm с новыми параметрами
                        helm upgrade ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${namespace} \\
                          --set kafka.image.tag=${params.KAFKA_VERSION} \\
                          --set zookeeper.image.tag=${params.KAFKA_VERSION} \\
                          --set kafka.replicaCount=${params.KAFKA_REPLICAS} \\
                          --set zookeeper.replicaCount=${params.ZOOKEEPER_REPLICAS} \\
                          --set kafkaUI.enabled=${params.ENABLE_KAFKA_UI} \\
                          --wait --timeout=10m
                        
                        echo "✅ Конфигурация обновлена"
                        
                        # Проверяем статус
                        echo ""
                        echo "📊 Статус после обновления:"
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/name=${MODULE_NAME}
                    """
                }
            }
        }

        stage('Rollback') {
            when {
                expression { params.ACTION == 'rollback' }
            }
            steps {
                script {
                    def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                    
                    sh """
                        echo "⏪ Откат Kafka в ${namespace}..."
                        
                        # Показываем историю релизов
                        echo "История релизов:"
                        helm history ${MODULE_NAME} -n ${namespace}
                        
                        # Откат к предыдущей версии
                        helm rollback ${MODULE_NAME} -n ${namespace} --wait --timeout=10m
                        
                        echo "✅ Откат выполнен"
                        
                        # Проверяем статус
                        echo ""
                        echo "📊 Статус после отката:"
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/name=${MODULE_NAME}
                    """
                }
            }
        }

        stage('Manual Approval for PROD') {
            when {
                allOf {
                    branch 'main'
                    expression { params.ACTION in ['deploy', 'upgrade', 'update-config'] }
                }
            }
            steps {
                script {
                    echo "🔔 Требуется подтверждение для деплоя Kafka в PRODUCTION"
                    echo "🎯 Namespace: ${NAMESPACE_PROD}"
                    echo "🔧 Mode: KRaft (без Zookeeper)"
                    echo "📦 Kafka Version: ${params.KAFKA_VERSION}"
                    echo "🔢 Kafka Replicas: ${params.KAFKA_REPLICAS}"
                    echo "⚠️  ВНИМАНИЕ: Kafka - критический сервис для обмена сообщениями!"
                }
                input message: 'Deploy Kafka to PROD environment?', ok: 'Yes, deploy'
            }
        }

        stage('Deploy to PROD') {
            when {
                allOf {
                    branch 'main'
                    expression { params.ACTION in ['deploy', 'upgrade'] }
                }
            }
            steps {
                script {
                    sh """
                        echo "🚀 Deploying Kafka to PRODUCTION (KRaft mode)..."
                        
                        # Создаем namespace если не существует
                        kubectl create namespace ${NAMESPACE_PROD} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Деплоим через Helm с production настройками (KRaft mode)
                        helm upgrade --install ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${NAMESPACE_PROD} \\
                          --set kafka.image.tag=${params.KAFKA_VERSION} \\
                          --set kafka.replicaCount=${params.KAFKA_REPLICAS} \\
                          --set kafkaUI.enabled=${params.ENABLE_KAFKA_UI} \\
                          --set kafka.resources.limits.cpu=2000m \\
                          --set kafka.resources.limits.memory=4Gi \\
                          --set kafka.resources.requests.cpu=1000m \\
                          --set kafka.resources.requests.memory=2Gi \\
                          --set kafka.persistence.size=50Gi \\
                          --wait --timeout=15m
                        
                        echo "✅ Деплой ${MODULE_NAME} в PROD завершен"
                        
                        # Показываем статус
                        echo ""
                        echo "📊 Статус развертывания:"
                        kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=${MODULE_NAME}
                    """
                }
            }
        }

        stage('Verify Deployment PROD') {
            when {
                allOf {
                    branch 'main'
                    expression { params.ACTION in ['deploy', 'upgrade'] }
                }
            }
            steps {
                sh """
                    echo "🔍 Проверка деплоя в PROD (KRaft mode)..."
                    
                    # Ждем готовности Kafka
                    echo "Ожидание готовности Kafka..."
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/component=kafka \\
                      -n ${NAMESPACE_PROD} \\
                      --timeout=600s || {
                        echo "⚠️  Kafka не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/component=kafka -n ${NAMESPACE_PROD} --tail=100
                        exit 1
                      }
                    
                    echo "✅ Kafka готов"
                    
                    # Проверяем Kafka UI если включен
                    if [ "${params.ENABLE_KAFKA_UI}" = "true" ]; then
                        echo "Ожидание готовности Kafka UI..."
                        kubectl wait --for=condition=ready pod \\
                          -l app.kubernetes.io/component=kafka-ui \\
                          -n ${NAMESPACE_PROD} \\
                          --timeout=300s || echo "⚠️  Kafka UI не готов"
                    fi
                    
                    echo ""
                    echo "✅ Deployment в PROD успешно проверен"
                    
                    # Показываем информацию о сервисах
                    echo ""
                    echo "🔌 Services:"
                    kubectl get svc -n ${NAMESPACE_PROD} | grep -E "kafka|zookeeper"
                    
                    # Показываем все pods
                    echo ""
                    echo "📦 All Pods:"
                    kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=${MODULE_NAME}
                    
                    # Проверяем топики
                    echo ""
                    echo "📋 Kafka Topics:"
                    kubectl exec -n ${NAMESPACE_PROD} deploy/kafka -- \\
                      kafka-topics --bootstrap-server localhost:9092 --list
                    
                    # Показываем детали топиков
                    echo ""
                    echo "📊 Topics Details:"
                    kubectl exec -n ${NAMESPACE_PROD} deploy/kafka -- \\
                      kafka-topics --bootstrap-server localhost:9092 --describe
                """
            }
        }

        stage('Health Check') {
            when {
                expression { params.ACTION in ['deploy', 'upgrade', 'update-config'] }
            }
            steps {
                script {
                    def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                    
                    sh """
                        echo "🏥 Выполнение health check для Kafka..."
                        
                        # Проверяем broker
                        echo "Проверка Kafka broker..."
                        kubectl exec -n ${namespace} deploy/kafka -- \\
                          kafka-broker-api-versions --bootstrap-server localhost:9092 || {
                            echo "❌ Kafka broker недоступен"
                            exit 1
                          }
                        
                        echo "✅ Kafka broker работает корректно"
                        
                        # Проверяем consumer groups
                        echo ""
                        echo "Consumer Groups:"
                        kubectl exec -n ${namespace} deploy/kafka -- \\
                          kafka-consumer-groups --bootstrap-server localhost:9092 --list || echo "Нет consumer groups"
                        
                        # Проверяем cluster ID
                        echo ""
                        echo "Cluster ID:"
                        kubectl exec -n ${namespace} deploy/kafka -- \\
                          kafka-cluster --bootstrap-server localhost:9092 cluster-id || echo "Не удалось получить cluster ID"
                        
                        echo ""
                        echo "✅ Health check завершен успешно"
                    """
                }
            }
        }
    }

    post {
        success {
            script {
                echo "✅ Kafka pipeline успешно завершен!"
                
                def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                
                if (params.ACTION == 'status') {
                    echo "📊 Статус Kafka проверен в namespace: ${namespace}"
                } else                 if (params.ACTION in ['deploy', 'upgrade']) {
                    echo "🚀 Kafka задеплоен в namespace: ${namespace}"
                    echo "🔧 Mode: KRaft (без Zookeeper)"
                    echo "📦 Версия: ${params.KAFKA_VERSION}"
                    echo "🔢 Kafka Replicas: ${params.KAFKA_REPLICAS}"
                    
                    if (params.ENABLE_KAFKA_UI) {
                        echo ""
                        echo "🎨 Kafka UI доступен. Для доступа настройте port-forward:"
                        echo "    kubectl port-forward -n ${namespace} svc/kafka-ui 8080:8080"
                        echo "    Kafka UI: http://localhost:8080"
                    }
                    
                    echo ""
                    echo "📋 Для просмотра топиков:"
                    echo "    kubectl exec -n ${namespace} deploy/kafka -- kafka-topics --bootstrap-server localhost:9092 --list"
                    
                    echo ""
                    echo "🔌 Для подключения к Kafka из приложений используйте:"
                    echo "    Bootstrap servers: kafka.${namespace}.svc.cluster.local:9092"
                } else if (params.ACTION == 'update-topics') {
                    echo "📝 Топики Kafka обновлены в namespace: ${namespace}"
                } else if (params.ACTION == 'update-config') {
                    echo "⚙️  Конфигурация Kafka обновлена в namespace: ${namespace}"
                } else if (params.ACTION == 'rollback') {
                    echo "⏪ Откат Kafka выполнен в namespace: ${namespace}"
                }
            }
        }
        failure {
            echo "❌ Kafka pipeline завершился с ошибкой"
            echo "📋 Проверьте Console Output для деталей"
            echo "💡 Kafka может требовать больше времени для запуска"
            echo "💡 Убедитесь, что достаточно ресурсов в кластере"
        }
        always {
            echo "🏁 Pipeline завершен"
        }
    }
}
