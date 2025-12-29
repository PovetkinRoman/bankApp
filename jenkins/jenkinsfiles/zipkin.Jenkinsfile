pipeline {
    agent any

    environment {
        MODULE_NAME     = 'zipkin'
        NAMESPACE_TEST  = 'test'
        NAMESPACE_PROD  = 'prod'
    }

    parameters {
        choice(
            name: 'ACTION',
            choices: ['deploy', 'upgrade', 'update-config', 'rollback', 'status'],
            description: 'Выберите действие для выполнения'
        )
        choice(
            name: 'STORAGE_TYPE',
            choices: ['mem', 'elasticsearch'],
            description: 'Тип хранилища для Zipkin (mem - в памяти, elasticsearch - постоянное хранение)'
        )
        string(
            name: 'ZIPKIN_VERSION',
            defaultValue: '3.4.2',
            description: 'Версия Zipkin Docker образа'
        )
        string(
            name: 'MEMORY_LIMIT',
            defaultValue: '1Gi',
            description: 'Лимит памяти для Zipkin'
        )
        string(
            name: 'CPU_LIMIT',
            defaultValue: '500m',
            description: 'Лимит CPU для Zipkin'
        )
        string(
            name: 'ELASTICSEARCH_HOSTS',
            defaultValue: 'http://bankapp-elasticsearch:9200',
            description: 'Адрес Elasticsearch (используется только при storage.type=elasticsearch)'
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
                    echo "🔍 Validating Helm chart for ${MODULE_NAME}..."
                    
                    # Проверяем наличие helm chart
                    if [ ! -d "helm/charts/${MODULE_NAME}" ]; then
                        echo "❌ Helm chart для ${MODULE_NAME} не найден"
                        exit 1
                    fi
                    
                    # Валидация Helm chart
                    helm lint helm/charts/${MODULE_NAME}
                    
                    # Проверка шаблонов
                    helm template ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                      --namespace ${NAMESPACE_TEST} \\
                      --set image.tag=${params.ZIPKIN_VERSION} \\
                      --set storage.type=${params.STORAGE_TYPE} \\
                      --set resources.limits.memory=${params.MEMORY_LIMIT} \\
                      --set resources.limits.cpu=${params.CPU_LIMIT} \\
                      > /tmp/zipkin-manifests.yaml
                    
                    echo "✅ Helm chart валидация пройдена"
                    echo "📄 Сгенерированные манифесты:"
                    head -n 50 /tmp/zipkin-manifests.yaml
                """
            }
        }

        stage('Check Zipkin Status') {
            when {
                expression { params.ACTION == 'status' }
            }
            steps {
                script {
                    def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                    
                    sh """
                        echo "📊 Проверка статуса Zipkin в namespace: ${namespace}"
                        echo "================================================"
                        
                        # Проверяем наличие Zipkin
                        if ! helm list -n ${namespace} | grep -q ${MODULE_NAME}; then
                            echo "⚠️  Zipkin не установлен в namespace ${namespace}"
                            exit 0
                        fi
                        
                        echo "📦 Helm Release:"
                        helm list -n ${namespace} | grep ${MODULE_NAME}
                        
                        echo ""
                        echo "🔍 Zipkin Pods:"
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/name=zipkin
                        
                        echo ""
                        echo "🔌 Services:"
                        kubectl get svc -n ${namespace} | grep zipkin
                        
                        echo ""
                        echo "📊 Pod Details:"
                        kubectl describe pods -n ${namespace} -l app.kubernetes.io/name=zipkin | grep -A 10 "Conditions:\\|Events:"
                        
                        echo ""
                        echo "🏥 Health Check:"
                        ZIPKIN_POD=\$(kubectl get pods -n ${namespace} -l app.kubernetes.io/name=zipkin -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
                        if [ -n "\$ZIPKIN_POD" ]; then
                            echo "Проверка /health endpoint..."
                            kubectl exec -n ${namespace} \$ZIPKIN_POD -- wget -q -O- http://localhost:9411/health || echo "Health check не прошел"
                        else
                            echo "⚠️  Zipkin pod не найден"
                        fi
                        
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
                        echo "🚀 Deploying Zipkin to TEST environment..."
                        echo "📦 Version: ${params.ZIPKIN_VERSION}"
                        echo "💾 Storage Type: ${params.STORAGE_TYPE}"
                        
                        # Создаем namespace если не существует
                        kubectl create namespace ${NAMESPACE_TEST} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Деплоим через Helm
                        helm upgrade --install ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${NAMESPACE_TEST} \\
                          --set image.tag=${params.ZIPKIN_VERSION} \\
                          --set storage.type=${params.STORAGE_TYPE} \\
                          --set resources.limits.memory=${params.MEMORY_LIMIT} \\
                          --set resources.limits.cpu=${params.CPU_LIMIT} \\
                          --set resources.requests.memory=512Mi \\
                          --set resources.requests.cpu=250m \\
                          ${params.STORAGE_TYPE == 'elasticsearch' ? "--set storage.elasticsearch.hosts=${params.ELASTICSEARCH_HOSTS}" : ""} \\
                          --wait --timeout=5m
                        
                        echo "✅ Деплой ${MODULE_NAME} в TEST завершен"
                        
                        # Показываем статус
                        echo ""
                        echo "📊 Статус развертывания:"
                        kubectl get pods -n ${NAMESPACE_TEST} -l app.kubernetes.io/name=zipkin
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
                    echo "🔍 Проверка деплоя в TEST..."
                    
                    # Ждем готовности Zipkin
                    echo "Ожидание готовности Zipkin..."
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/name=zipkin \\
                      -n ${NAMESPACE_TEST} \\
                      --timeout=300s || {
                        echo "⚠️  Zipkin не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/name=zipkin -n ${NAMESPACE_TEST} --tail=50
                        exit 1
                      }
                    
                    echo "✅ Zipkin готов"
                    
                    # Проверяем health endpoint
                    echo ""
                    echo "🏥 Проверка Health Endpoint:"
                    ZIPKIN_POD=\$(kubectl get pods -n ${NAMESPACE_TEST} -l app.kubernetes.io/name=zipkin -o jsonpath='{.items[0].metadata.name}')
                    kubectl exec -n ${NAMESPACE_TEST} \$ZIPKIN_POD -- wget -q -O- http://localhost:9411/health || {
                        echo "❌ Health check не прошел"
                        exit 1
                    }
                    
                    echo ""
                    echo "✅ Deployment в TEST успешно проверен"
                    
                    # Показываем информацию о сервисах
                    echo ""
                    echo "🔌 Services:"
                    kubectl get svc -n ${NAMESPACE_TEST} | grep zipkin
                    
                    # Показываем endpoint для доступа
                    echo ""
                    echo "🌐 Zipkin UI доступен через:"
                    echo "   kubectl port-forward -n ${NAMESPACE_TEST} svc/bankapp-zipkin 9411:9411"
                    echo "   Затем откройте: http://localhost:9411"
                """
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
                        echo "⚙️  Обновление конфигурации Zipkin в ${namespace}..."
                        
                        # Обновляем через Helm с новыми параметрами
                        helm upgrade ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${namespace} \\
                          --set image.tag=${params.ZIPKIN_VERSION} \\
                          --set storage.type=${params.STORAGE_TYPE} \\
                          --set resources.limits.memory=${params.MEMORY_LIMIT} \\
                          --set resources.limits.cpu=${params.CPU_LIMIT} \\
                          ${params.STORAGE_TYPE == 'elasticsearch' ? "--set storage.elasticsearch.hosts=${params.ELASTICSEARCH_HOSTS}" : ""} \\
                          --wait --timeout=5m
                        
                        echo "✅ Конфигурация обновлена"
                        
                        # Проверяем статус
                        echo ""
                        echo "📊 Статус после обновления:"
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/name=zipkin
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
                        echo "⏪ Откат Zipkin в ${namespace}..."
                        
                        # Показываем историю релизов
                        echo "История релизов:"
                        helm history ${MODULE_NAME} -n ${namespace}
                        
                        # Откат к предыдущей версии
                        helm rollback ${MODULE_NAME} -n ${namespace} --wait --timeout=5m
                        
                        echo "✅ Откат выполнен"
                        
                        # Проверяем статус
                        echo ""
                        echo "📊 Статус после отката:"
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/name=zipkin
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
                    echo "🔔 Требуется подтверждение для деплоя Zipkin в PRODUCTION"
                    echo "🎯 Namespace: ${NAMESPACE_PROD}"
                    echo "📦 Zipkin Version: ${params.ZIPKIN_VERSION}"
                    echo "💾 Storage Type: ${params.STORAGE_TYPE}"
                    echo "💻 Resources: CPU=${params.CPU_LIMIT}, Memory=${params.MEMORY_LIMIT}"
                    echo "⚠️  ВНИМАНИЕ: Zipkin - критический сервис для distributed tracing!"
                }
                input message: 'Deploy Zipkin to PROD environment?', ok: 'Yes, deploy'
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
                        echo "🚀 Deploying Zipkin to PRODUCTION..."
                        echo "📦 Version: ${params.ZIPKIN_VERSION}"
                        echo "💾 Storage Type: ${params.STORAGE_TYPE}"
                        
                        # Создаем namespace если не существует
                        kubectl create namespace ${NAMESPACE_PROD} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Деплоим через Helm с production настройками
                        helm upgrade --install ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${NAMESPACE_PROD} \\
                          --set image.tag=${params.ZIPKIN_VERSION} \\
                          --set storage.type=${params.STORAGE_TYPE} \\
                          --set resources.limits.memory=2Gi \\
                          --set resources.limits.cpu=1000m \\
                          --set resources.requests.memory=1Gi \\
                          --set resources.requests.cpu=500m \\
                          ${params.STORAGE_TYPE == 'elasticsearch' ? "--set storage.elasticsearch.hosts=${params.ELASTICSEARCH_HOSTS}" : ""} \\
                          --wait --timeout=10m
                        
                        echo "✅ Деплой ${MODULE_NAME} в PROD завершен"
                        
                        # Показываем статус
                        echo ""
                        echo "📊 Статус развертывания:"
                        kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=zipkin
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
                    echo "🔍 Проверка деплоя в PROD..."
                    
                    # Ждем готовности Zipkin
                    echo "Ожидание готовности Zipkin..."
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/name=zipkin \\
                      -n ${NAMESPACE_PROD} \\
                      --timeout=600s || {
                        echo "⚠️  Zipkin не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/name=zipkin -n ${NAMESPACE_PROD} --tail=100
                        exit 1
                      }
                    
                    echo "✅ Zipkin готов"
                    
                    # Проверяем health endpoint
                    echo ""
                    echo "🏥 Проверка Health Endpoint:"
                    ZIPKIN_POD=\$(kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=zipkin -o jsonpath='{.items[0].metadata.name}')
                    kubectl exec -n ${NAMESPACE_PROD} \$ZIPKIN_POD -- wget -q -O- http://localhost:9411/health || {
                        echo "❌ Health check не прошел"
                        exit 1
                    }
                    
                    echo ""
                    echo "✅ Deployment в PROD успешно проверен"
                    
                    # Показываем информацию о сервисах
                    echo ""
                    echo "🔌 Services:"
                    kubectl get svc -n ${NAMESPACE_PROD} | grep zipkin
                    
                    # Показываем все pods
                    echo ""
                    echo "📦 All Pods:"
                    kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=zipkin
                    
                    # Показываем endpoint для доступа
                    echo ""
                    echo "🌐 Zipkin UI доступен через:"
                    echo "   kubectl port-forward -n ${NAMESPACE_PROD} svc/bankapp-zipkin 9411:9411"
                    echo "   Затем откройте: http://localhost:9411"
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
                        echo "🏥 Выполнение health check для Zipkin..."
                        
                        # Получаем имя pod
                        ZIPKIN_POD=\$(kubectl get pods -n ${namespace} -l app.kubernetes.io/name=zipkin -o jsonpath='{.items[0].metadata.name}')
                        
                        if [ -z "\$ZIPKIN_POD" ]; then
                            echo "❌ Zipkin pod не найден"
                            exit 1
                        fi
                        
                        # Проверяем /health endpoint
                        echo "Проверка /health endpoint..."
                        HEALTH_RESPONSE=\$(kubectl exec -n ${namespace} \$ZIPKIN_POD -- wget -q -O- http://localhost:9411/health)
                        echo "Health Response: \$HEALTH_RESPONSE"
                        
                        # Проверяем /api/v2/services endpoint
                        echo ""
                        echo "Проверка /api/v2/services endpoint..."
                        kubectl exec -n ${namespace} \$ZIPKIN_POD -- wget -q -O- http://localhost:9411/api/v2/services || echo "Нет сервисов (это нормально для нового деплоя)"
                        
                        # Проверяем storage type
                        echo ""
                        echo "Storage Type: ${params.STORAGE_TYPE}"
                        if [ "${params.STORAGE_TYPE}" = "elasticsearch" ]; then
                            echo "⚠️  Используется Elasticsearch storage - убедитесь, что Elasticsearch доступен"
                        else
                            echo "ℹ️  Используется in-memory storage - данные будут потеряны при рестарте"
                        fi
                        
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
                echo "✅ Zipkin pipeline успешно завершен!"
                
                def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                
                if (params.ACTION == 'status') {
                    echo "📊 Статус Zipkin проверен в namespace: ${namespace}"
                } else if (params.ACTION in ['deploy', 'upgrade']) {
                    echo "🚀 Zipkin задеплоен в namespace: ${namespace}"
                    echo "📦 Версия: ${params.ZIPKIN_VERSION}"
                    echo "💾 Storage Type: ${params.STORAGE_TYPE}"
                    echo "💻 Resources: CPU=${params.CPU_LIMIT}, Memory=${params.MEMORY_LIMIT}"
                    
                    echo ""
                    echo "🌐 Для доступа к Zipkin UI:"
                    echo "    kubectl port-forward -n ${namespace} svc/bankapp-zipkin 9411:9411"
                    echo "    Затем откройте: http://localhost:9411"
                    
                    echo ""
                    echo "🔌 Для подключения из приложений используйте:"
                    echo "    ZIPKIN_BASE_URL: http://bankapp-zipkin:9411"
                } else if (params.ACTION == 'update-config') {
                    echo "⚙️  Конфигурация Zipkin обновлена в namespace: ${namespace}"
                } else if (params.ACTION == 'rollback') {
                    echo "⏪ Откат Zipkin выполнен в namespace: ${namespace}"
                }
            }
        }
        failure {
            echo "❌ Zipkin pipeline завершился с ошибкой"
            echo "📋 Проверьте Console Output для деталей"
            echo "💡 Zipkin может требовать больше времени для запуска"
            echo "💡 Убедитесь, что достаточно ресурсов в кластере"
            if (params.STORAGE_TYPE == 'elasticsearch') {
                echo "💡 Проверьте доступность Elasticsearch: ${params.ELASTICSEARCH_HOSTS}"
            }
        }
        always {
            echo "🏁 Pipeline завершен"
        }
    }
}

