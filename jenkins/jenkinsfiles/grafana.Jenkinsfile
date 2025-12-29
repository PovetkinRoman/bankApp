pipeline {
    agent any

    environment {
        MODULE_NAME     = 'grafana'
        NAMESPACE_TEST  = 'test'
        NAMESPACE_PROD  = 'prod'
    }

    parameters {
        choice(
            name: 'ACTION',
            choices: ['deploy', 'upgrade', 'update-config', 'rollback', 'status'],
            description: 'Выберите действие для выполнения'
        )
        string(
            name: 'GRAFANA_VERSION',
            defaultValue: '10.2.2',
            description: 'Версия Grafana Docker образа'
        )
        string(
            name: 'ADMIN_PASSWORD',
            defaultValue: 'admin',
            description: 'Пароль администратора Grafana'
        )
        string(
            name: 'MEMORY_LIMIT',
            defaultValue: '512Mi',
            description: 'Лимит памяти для Grafana'
        )
        string(
            name: 'CPU_LIMIT',
            defaultValue: '500m',
            description: 'Лимит CPU для Grafana'
        )
        booleanParam(
            name: 'ENABLE_PERSISTENCE',
            defaultValue: false,
            description: 'Включить постоянное хранилище для дашбордов'
        )
        string(
            name: 'STORAGE_SIZE',
            defaultValue: '5Gi',
            description: 'Размер хранилища (если persistence включен)'
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
                      --set image.tag=${params.GRAFANA_VERSION} \\
                      --set adminPassword=${params.ADMIN_PASSWORD} \\
                      --set resources.limits.memory=${params.MEMORY_LIMIT} \\
                      --set resources.limits.cpu=${params.CPU_LIMIT} \\
                      --set persistence.enabled=${params.ENABLE_PERSISTENCE} \\
                      --set persistence.size=${params.STORAGE_SIZE} \\
                      > /tmp/grafana-manifests.yaml
                    
                    echo "✅ Helm chart валидация пройдена"
                    echo "📄 Сгенерированные манифесты:"
                    head -n 50 /tmp/grafana-manifests.yaml
                """
            }
        }

        stage('Check Grafana Status') {
            when {
                expression { params.ACTION == 'status' }
            }
            steps {
                script {
                    def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                    
                    sh """
                        echo "📊 Проверка статуса Grafana в namespace: ${namespace}"
                        echo "================================================"
                        
                        # Проверяем наличие Grafana
                        if ! helm list -n ${namespace} | grep -q ${MODULE_NAME}; then
                            echo "⚠️  Grafana не установлен в namespace ${namespace}"
                            exit 0
                        fi
                        
                        echo "📦 Helm Release:"
                        helm list -n ${namespace} | grep ${MODULE_NAME}
                        
                        echo ""
                        echo "🔍 Grafana Pods:"
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/name=grafana
                        
                        echo ""
                        echo "🔌 Services:"
                        kubectl get svc -n ${namespace} | grep grafana
                        
                        echo ""
                        echo "💾 PVCs:"
                        kubectl get pvc -n ${namespace} | grep grafana || echo "Нет PVC"
                        
                        echo ""
                        echo "📊 Pod Details:"
                        kubectl describe pods -n ${namespace} -l app.kubernetes.io/name=grafana | grep -A 10 "Conditions:\\|Events:"
                        
                        echo ""
                        echo "🏥 Health Check:"
                        GRAFANA_POD=\$(kubectl get pods -n ${namespace} -l app.kubernetes.io/name=grafana -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
                        if [ -n "\$GRAFANA_POD" ]; then
                            echo "Проверка /api/health endpoint..."
                            kubectl exec -n ${namespace} \$GRAFANA_POD -- wget -q -O- http://localhost:3000/api/health || echo "Health check не прошел"
                        else
                            echo "⚠️  Grafana pod не найден"
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
                        echo "🚀 Deploying Grafana to TEST environment..."
                        echo "📦 Version: ${params.GRAFANA_VERSION}"
                        echo "👤 Admin Password: ${params.ADMIN_PASSWORD}"
                        echo "💾 Persistence: ${params.ENABLE_PERSISTENCE}"
                        
                        # Создаем namespace если не существует
                        kubectl create namespace ${NAMESPACE_TEST} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Деплоим через Helm
                        helm upgrade --install ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${NAMESPACE_TEST} \\
                          --set image.tag=${params.GRAFANA_VERSION} \\
                          --set adminPassword=${params.ADMIN_PASSWORD} \\
                          --set resources.limits.memory=${params.MEMORY_LIMIT} \\
                          --set resources.limits.cpu=${params.CPU_LIMIT} \\
                          --set resources.requests.memory=256Mi \\
                          --set resources.requests.cpu=250m \\
                          --set persistence.enabled=${params.ENABLE_PERSISTENCE} \\
                          --set persistence.size=${params.STORAGE_SIZE} \\
                          --wait --timeout=10m
                        
                        echo "✅ Деплой ${MODULE_NAME} в TEST завершен"
                        
                        # Показываем статус
                        echo ""
                        echo "📊 Статус развертывания:"
                        kubectl get pods -n ${NAMESPACE_TEST} -l app.kubernetes.io/name=grafana
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
                    
                    # Ждем готовности Grafana
                    echo "Ожидание готовности Grafana..."
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/name=grafana \\
                      -n ${NAMESPACE_TEST} \\
                      --timeout=600s || {
                        echo "⚠️  Grafana не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/name=grafana -n ${NAMESPACE_TEST} --tail=50
                        exit 1
                      }
                    
                    echo "✅ Grafana готов"
                    
                    # Проверяем health endpoint
                    echo ""
                    echo "🏥 Проверка Health Endpoint:"
                    GRAFANA_POD=\$(kubectl get pods -n ${NAMESPACE_TEST} -l app.kubernetes.io/name=grafana -o jsonpath='{.items[0].metadata.name}')
                    kubectl exec -n ${NAMESPACE_TEST} \$GRAFANA_POD -- wget -q -O- http://localhost:3000/api/health || {
                        echo "❌ Health check не прошел"
                        exit 1
                    }
                    
                    echo ""
                    echo "✅ Deployment в TEST успешно проверен"
                    
                    # Показываем информацию о сервисах
                    echo ""
                    echo "🔌 Services:"
                    kubectl get svc -n ${NAMESPACE_TEST} | grep grafana
                    
                    # Показываем endpoint для доступа
                    echo ""
                    echo "🌐 Grafana UI доступен через:"
                    echo "   kubectl port-forward -n ${NAMESPACE_TEST} svc/bankapp-grafana 3000:3000"
                    echo "   Затем откройте: http://localhost:3000"
                    echo "   Логин: admin"
                    echo "   Пароль: ${params.ADMIN_PASSWORD}"
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
                        echo "⚙️  Обновление конфигурации Grafana в ${namespace}..."
                        
                        # Обновляем через Helm с новыми параметрами
                        helm upgrade ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${namespace} \\
                          --set image.tag=${params.GRAFANA_VERSION} \\
                          --set adminPassword=${params.ADMIN_PASSWORD} \\
                          --set resources.limits.memory=${params.MEMORY_LIMIT} \\
                          --set resources.limits.cpu=${params.CPU_LIMIT} \\
                          --set persistence.enabled=${params.ENABLE_PERSISTENCE} \\
                          --set persistence.size=${params.STORAGE_SIZE} \\
                          --wait --timeout=10m
                        
                        echo "✅ Конфигурация обновлена"
                        
                        # Проверяем статус
                        echo ""
                        echo "📊 Статус после обновления:"
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/name=grafana
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
                        echo "⏪ Откат Grafana в ${namespace}..."
                        
                        # Показываем историю релизов
                        echo "История релизов:"
                        helm history ${MODULE_NAME} -n ${namespace}
                        
                        # Откат к предыдущей версии
                        helm rollback ${MODULE_NAME} -n ${namespace} --wait --timeout=10m
                        
                        echo "✅ Откат выполнен"
                        
                        # Проверяем статус
                        echo ""
                        echo "📊 Статус после отката:"
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/name=grafana
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
                    echo "🔔 Требуется подтверждение для деплоя Grafana в PRODUCTION"
                    echo "🎯 Namespace: ${NAMESPACE_PROD}"
                    echo "📦 Grafana Version: ${params.GRAFANA_VERSION}"
                    echo "💾 Persistence: ${params.ENABLE_PERSISTENCE}"
                    echo "💻 Resources: CPU=${params.CPU_LIMIT}, Memory=${params.MEMORY_LIMIT}"
                    echo "⚠️  ВНИМАНИЕ: Grafana - критический сервис для визуализации метрик!"
                }
                input message: 'Deploy Grafana to PROD environment?', ok: 'Yes, deploy'
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
                        echo "🚀 Deploying Grafana to PRODUCTION..."
                        echo "📦 Version: ${params.GRAFANA_VERSION}"
                        echo "💾 Persistence: enabled (PROD default)"
                        
                        # Создаем namespace если не существует
                        kubectl create namespace ${NAMESPACE_PROD} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Деплоим через Helm с production настройками
                        helm upgrade --install ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${NAMESPACE_PROD} \\
                          --set image.tag=${params.GRAFANA_VERSION} \\
                          --set adminPassword=${params.ADMIN_PASSWORD} \\
                          --set resources.limits.memory=1Gi \\
                          --set resources.limits.cpu=1000m \\
                          --set resources.requests.memory=512Mi \\
                          --set resources.requests.cpu=500m \\
                          --set persistence.enabled=true \\
                          --set persistence.size=10Gi \\
                          --wait --timeout=15m
                        
                        echo "✅ Деплой ${MODULE_NAME} в PROD завершен"
                        
                        # Показываем статус
                        echo ""
                        echo "📊 Статус развертывания:"
                        kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=grafana
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
                    
                    # Ждем готовности Grafana
                    echo "Ожидание готовности Grafana..."
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/name=grafana \\
                      -n ${NAMESPACE_PROD} \\
                      --timeout=900s || {
                        echo "⚠️  Grafana не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/name=grafana -n ${NAMESPACE_PROD} --tail=100
                        exit 1
                      }
                    
                    echo "✅ Grafana готов"
                    
                    # Проверяем health endpoint
                    echo ""
                    echo "🏥 Проверка Health Endpoint:"
                    GRAFANA_POD=\$(kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=grafana -o jsonpath='{.items[0].metadata.name}')
                    kubectl exec -n ${NAMESPACE_PROD} \$GRAFANA_POD -- wget -q -O- http://localhost:3000/api/health || {
                        echo "❌ Health check не прошел"
                        exit 1
                    }
                    
                    echo ""
                    echo "✅ Deployment в PROD успешно проверен"
                    
                    # Показываем информацию о сервисах
                    echo ""
                    echo "🔌 Services:"
                    kubectl get svc -n ${NAMESPACE_PROD} | grep grafana
                    
                    # Показываем все pods
                    echo ""
                    echo "📦 All Pods:"
                    kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=grafana
                    
                    # Показываем endpoint для доступа
                    echo ""
                    echo "🌐 Grafana UI доступен через:"
                    echo "   kubectl port-forward -n ${NAMESPACE_PROD} svc/bankapp-grafana 3000:3000"
                    echo "   Затем откройте: http://localhost:3000"
                    echo "   Логин: admin"
                    echo "   Пароль: ${params.ADMIN_PASSWORD}"
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
                        echo "🏥 Выполнение health check для Grafana..."
                        
                        # Получаем имя pod
                        GRAFANA_POD=\$(kubectl get pods -n ${namespace} -l app.kubernetes.io/name=grafana -o jsonpath='{.items[0].metadata.name}')
                        
                        if [ -z "\$GRAFANA_POD" ]; then
                            echo "❌ Grafana pod не найден"
                            exit 1
                        fi
                        
                        # Проверяем /api/health endpoint
                        echo "Проверка /api/health endpoint..."
                        HEALTH_RESPONSE=\$(kubectl exec -n ${namespace} \$GRAFANA_POD -- wget -q -O- http://localhost:3000/api/health)
                        echo "Health Response: \$HEALTH_RESPONSE"
                        
                        # Проверяем datasources
                        echo ""
                        echo "Проверка datasources..."
                        kubectl exec -n ${namespace} \$GRAFANA_POD -- wget -q -O- http://localhost:3000/api/datasources || echo "Не удалось получить datasources"
                        
                        # Проверяем persistence
                        echo ""
                        echo "Persistence: ${params.ENABLE_PERSISTENCE}"
                        if [ "${params.ENABLE_PERSISTENCE}" = "true" ]; then
                            echo "✅ Persistence включен - дашборды будут сохраняться"
                            kubectl get pvc -n ${namespace} | grep grafana
                        else
                            echo "⚠️  Persistence отключен - дашборды будут потеряны при рестарте"
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
                echo "✅ Grafana pipeline успешно завершен!"
                
                def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                
                if (params.ACTION == 'status') {
                    echo "📊 Статус Grafana проверен в namespace: ${namespace}"
                } else if (params.ACTION in ['deploy', 'upgrade']) {
                    echo "🚀 Grafana задеплоен в namespace: ${namespace}"
                    echo "📦 Версия: ${params.GRAFANA_VERSION}"
                    echo "💾 Persistence: ${params.ENABLE_PERSISTENCE}"
                    echo "💻 Resources: CPU=${params.CPU_LIMIT}, Memory=${params.MEMORY_LIMIT}"
                    
                    echo ""
                    echo "🌐 Для доступа к Grafana UI:"
                    echo "    kubectl port-forward -n ${namespace} svc/bankapp-grafana 3000:3000"
                    echo "    Затем откройте: http://localhost:3000"
                    echo "    Логин: admin"
                    echo "    Пароль: ${params.ADMIN_PASSWORD}"
                    
                    echo ""
                    echo "📊 Datasources:"
                    echo "    Prometheus должен быть автоматически настроен"
                    echo "    URL: http://bankapp-prometheus:9090"
                } else if (params.ACTION == 'update-config') {
                    echo "⚙️  Конфигурация Grafana обновлена в namespace: ${namespace}"
                } else if (params.ACTION == 'rollback') {
                    echo "⏪ Откат Grafana выполнен в namespace: ${namespace}"
                }
            }
        }
        failure {
            echo "❌ Grafana pipeline завершился с ошибкой"
            echo "📋 Проверьте Console Output для деталей"
            echo "💡 Grafana может требовать больше времени для запуска"
            echo "💡 Убедитесь, что достаточно ресурсов в кластере"
            echo "💡 Проверьте доступность Prometheus datasource"
        }
        always {
            echo "🏁 Pipeline завершен"
        }
    }
}

