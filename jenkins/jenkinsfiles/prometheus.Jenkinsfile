pipeline {
    agent any

    environment {
        MODULE_NAME     = 'prometheus'
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
            name: 'PROMETHEUS_VERSION',
            defaultValue: 'v2.48.0',
            description: 'Версия Prometheus Docker образа'
        )
        string(
            name: 'RETENTION',
            defaultValue: '15d',
            description: 'Время хранения метрик (например: 15d, 30d, 90d)'
        )
        string(
            name: 'STORAGE_SIZE',
            defaultValue: '10Gi',
            description: 'Размер хранилища для метрик'
        )
        string(
            name: 'MEMORY_LIMIT',
            defaultValue: '512Mi',
            description: 'Лимит памяти для Prometheus'
        )
        string(
            name: 'CPU_LIMIT',
            defaultValue: '500m',
            description: 'Лимит CPU для Prometheus'
        )
        string(
            name: 'SCRAPE_INTERVAL',
            defaultValue: '15s',
            description: 'Интервал сбора метрик'
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
                      --set image.tag=${params.PROMETHEUS_VERSION} \\
                      --set retention=${params.RETENTION} \\
                      --set storage.size=${params.STORAGE_SIZE} \\
                      --set resources.limits.memory=${params.MEMORY_LIMIT} \\
                      --set resources.limits.cpu=${params.CPU_LIMIT} \\
                      > /tmp/prometheus-manifests.yaml
                    
                    echo "✅ Helm chart валидация пройдена"
                    echo "📄 Сгенерированные манифесты:"
                    head -n 50 /tmp/prometheus-manifests.yaml
                """
            }
        }

        stage('Check Prometheus Status') {
            when {
                expression { params.ACTION == 'status' }
            }
            steps {
                script {
                    def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                    
                    sh """
                        echo "📊 Проверка статуса Prometheus в namespace: ${namespace}"
                        echo "================================================"
                        
                        # Проверяем наличие Prometheus
                        if ! helm list -n ${namespace} | grep -q ${MODULE_NAME}; then
                            echo "⚠️  Prometheus не установлен в namespace ${namespace}"
                            exit 0
                        fi
                        
                        echo "📦 Helm Release:"
                        helm list -n ${namespace} | grep ${MODULE_NAME}
                        
                        echo ""
                        echo "🔍 Prometheus Pods:"
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/name=prometheus
                        
                        echo ""
                        echo "🔌 Services:"
                        kubectl get svc -n ${namespace} | grep prometheus
                        
                        echo ""
                        echo "💾 PVCs:"
                        kubectl get pvc -n ${namespace} | grep prometheus || echo "Нет PVC"
                        
                        echo ""
                        echo "📊 Pod Details:"
                        kubectl describe pods -n ${namespace} -l app.kubernetes.io/name=prometheus | grep -A 10 "Conditions:\\|Events:"
                        
                        echo ""
                        echo "🏥 Health Check:"
                        PROMETHEUS_POD=\$(kubectl get pods -n ${namespace} -l app.kubernetes.io/name=prometheus -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
                        if [ -n "\$PROMETHEUS_POD" ]; then
                            echo "Проверка /-/healthy endpoint..."
                            kubectl exec -n ${namespace} \$PROMETHEUS_POD -- wget -q -O- http://localhost:9090/-/healthy || echo "Health check не прошел"
                            
                            echo ""
                            echo "Проверка /-/ready endpoint..."
                            kubectl exec -n ${namespace} \$PROMETHEUS_POD -- wget -q -O- http://localhost:9090/-/ready || echo "Ready check не прошел"
                        else
                            echo "⚠️  Prometheus pod не найден"
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
                        echo "🚀 Deploying Prometheus to TEST environment..."
                        echo "📦 Version: ${params.PROMETHEUS_VERSION}"
                        echo "📅 Retention: ${params.RETENTION}"
                        echo "💾 Storage Size: ${params.STORAGE_SIZE}"
                        
                        # Создаем namespace если не существует
                        kubectl create namespace ${NAMESPACE_TEST} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Деплоим через Helm
                        helm upgrade --install ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${NAMESPACE_TEST} \\
                          --set image.tag=${params.PROMETHEUS_VERSION} \\
                          --set retention=${params.RETENTION} \\
                          --set storage.size=${params.STORAGE_SIZE} \\
                          --set resources.limits.memory=${params.MEMORY_LIMIT} \\
                          --set resources.limits.cpu=${params.CPU_LIMIT} \\
                          --set resources.requests.memory=256Mi \\
                          --set resources.requests.cpu=250m \\
                          --set scrapeInterval=${params.SCRAPE_INTERVAL} \\
                          --wait --timeout=10m
                        
                        echo "✅ Деплой ${MODULE_NAME} в TEST завершен"
                        
                        # Показываем статус
                        echo ""
                        echo "📊 Статус развертывания:"
                        kubectl get pods -n ${NAMESPACE_TEST} -l app.kubernetes.io/name=prometheus
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
                    
                    # Ждем готовности Prometheus
                    echo "Ожидание готовности Prometheus..."
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/name=prometheus \\
                      -n ${NAMESPACE_TEST} \\
                      --timeout=600s || {
                        echo "⚠️  Prometheus не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/name=prometheus -n ${NAMESPACE_TEST} --tail=50
                        exit 1
                      }
                    
                    echo "✅ Prometheus готов"
                    
                    # Проверяем health endpoints
                    echo ""
                    echo "🏥 Проверка Health Endpoints:"
                    PROMETHEUS_POD=\$(kubectl get pods -n ${NAMESPACE_TEST} -l app.kubernetes.io/name=prometheus -o jsonpath='{.items[0].metadata.name}')
                    
                    kubectl exec -n ${NAMESPACE_TEST} \$PROMETHEUS_POD -- wget -q -O- http://localhost:9090/-/healthy || {
                        echo "❌ Health check не прошел"
                        exit 1
                    }
                    
                    kubectl exec -n ${NAMESPACE_TEST} \$PROMETHEUS_POD -- wget -q -O- http://localhost:9090/-/ready || {
                        echo "❌ Ready check не прошел"
                        exit 1
                    }
                    
                    echo ""
                    echo "✅ Deployment в TEST успешно проверен"
                    
                    # Показываем информацию о сервисах
                    echo ""
                    echo "🔌 Services:"
                    kubectl get svc -n ${NAMESPACE_TEST} | grep prometheus
                    
                    # Показываем endpoint для доступа
                    echo ""
                    echo "🌐 Prometheus UI доступен через:"
                    echo "   kubectl port-forward -n ${NAMESPACE_TEST} svc/bankapp-prometheus 9090:9090"
                    echo "   Затем откройте: http://localhost:9090"
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
                        echo "⚙️  Обновление конфигурации Prometheus в ${namespace}..."
                        
                        # Обновляем через Helm с новыми параметрами
                        helm upgrade ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${namespace} \\
                          --set image.tag=${params.PROMETHEUS_VERSION} \\
                          --set retention=${params.RETENTION} \\
                          --set storage.size=${params.STORAGE_SIZE} \\
                          --set resources.limits.memory=${params.MEMORY_LIMIT} \\
                          --set resources.limits.cpu=${params.CPU_LIMIT} \\
                          --set scrapeInterval=${params.SCRAPE_INTERVAL} \\
                          --wait --timeout=10m
                        
                        echo "✅ Конфигурация обновлена"
                        
                        # Проверяем статус
                        echo ""
                        echo "📊 Статус после обновления:"
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/name=prometheus
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
                        echo "⏪ Откат Prometheus в ${namespace}..."
                        
                        # Показываем историю релизов
                        echo "История релизов:"
                        helm history ${MODULE_NAME} -n ${namespace}
                        
                        # Откат к предыдущей версии
                        helm rollback ${MODULE_NAME} -n ${namespace} --wait --timeout=10m
                        
                        echo "✅ Откат выполнен"
                        
                        # Проверяем статус
                        echo ""
                        echo "📊 Статус после отката:"
                        kubectl get pods -n ${namespace} -l app.kubernetes.io/name=prometheus
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
                    echo "🔔 Требуется подтверждение для деплоя Prometheus в PRODUCTION"
                    echo "🎯 Namespace: ${NAMESPACE_PROD}"
                    echo "📦 Prometheus Version: ${params.PROMETHEUS_VERSION}"
                    echo "📅 Retention: ${params.RETENTION}"
                    echo "💾 Storage Size: ${params.STORAGE_SIZE}"
                    echo "💻 Resources: CPU=${params.CPU_LIMIT}, Memory=${params.MEMORY_LIMIT}"
                    echo "⚠️  ВНИМАНИЕ: Prometheus - критический сервис для мониторинга!"
                }
                input message: 'Deploy Prometheus to PROD environment?', ok: 'Yes, deploy'
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
                        echo "🚀 Deploying Prometheus to PRODUCTION..."
                        echo "📦 Version: ${params.PROMETHEUS_VERSION}"
                        echo "📅 Retention: 30d (PROD default)"
                        echo "💾 Storage Size: 50Gi (PROD default)"
                        
                        # Создаем namespace если не существует
                        kubectl create namespace ${NAMESPACE_PROD} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Деплоим через Helm с production настройками
                        helm upgrade --install ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${NAMESPACE_PROD} \\
                          --set image.tag=${params.PROMETHEUS_VERSION} \\
                          --set retention=30d \\
                          --set storage.size=50Gi \\
                          --set resources.limits.memory=1Gi \\
                          --set resources.limits.cpu=1000m \\
                          --set resources.requests.memory=512Mi \\
                          --set resources.requests.cpu=500m \\
                          --set scrapeInterval=${params.SCRAPE_INTERVAL} \\
                          --wait --timeout=15m
                        
                        echo "✅ Деплой ${MODULE_NAME} в PROD завершен"
                        
                        # Показываем статус
                        echo ""
                        echo "📊 Статус развертывания:"
                        kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=prometheus
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
                    
                    # Ждем готовности Prometheus
                    echo "Ожидание готовности Prometheus..."
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/name=prometheus \\
                      -n ${NAMESPACE_PROD} \\
                      --timeout=900s || {
                        echo "⚠️  Prometheus не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/name=prometheus -n ${NAMESPACE_PROD} --tail=100
                        exit 1
                      }
                    
                    echo "✅ Prometheus готов"
                    
                    # Проверяем health endpoints
                    echo ""
                    echo "🏥 Проверка Health Endpoints:"
                    PROMETHEUS_POD=\$(kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=prometheus -o jsonpath='{.items[0].metadata.name}')
                    
                    kubectl exec -n ${NAMESPACE_PROD} \$PROMETHEUS_POD -- wget -q -O- http://localhost:9090/-/healthy || {
                        echo "❌ Health check не прошел"
                        exit 1
                    }
                    
                    kubectl exec -n ${NAMESPACE_PROD} \$PROMETHEUS_POD -- wget -q -O- http://localhost:9090/-/ready || {
                        echo "❌ Ready check не прошел"
                        exit 1
                    }
                    
                    echo ""
                    echo "✅ Deployment в PROD успешно проверен"
                    
                    # Показываем информацию о сервисах
                    echo ""
                    echo "🔌 Services:"
                    kubectl get svc -n ${NAMESPACE_PROD} | grep prometheus
                    
                    # Показываем все pods
                    echo ""
                    echo "📦 All Pods:"
                    kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=prometheus
                    
                    # Показываем endpoint для доступа
                    echo ""
                    echo "🌐 Prometheus UI доступен через:"
                    echo "   kubectl port-forward -n ${NAMESPACE_PROD} svc/bankapp-prometheus 9090:9090"
                    echo "   Затем откройте: http://localhost:9090"
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
                        echo "🏥 Выполнение health check для Prometheus..."
                        
                        # Получаем имя pod
                        PROMETHEUS_POD=\$(kubectl get pods -n ${namespace} -l app.kubernetes.io/name=prometheus -o jsonpath='{.items[0].metadata.name}')
                        
                        if [ -z "\$PROMETHEUS_POD" ]; then
                            echo "❌ Prometheus pod не найден"
                            exit 1
                        fi
                        
                        # Проверяем /-/healthy endpoint
                        echo "Проверка /-/healthy endpoint..."
                        kubectl exec -n ${namespace} \$PROMETHEUS_POD -- wget -q -O- http://localhost:9090/-/healthy
                        
                        # Проверяем /-/ready endpoint
                        echo ""
                        echo "Проверка /-/ready endpoint..."
                        kubectl exec -n ${namespace} \$PROMETHEUS_POD -- wget -q -O- http://localhost:9090/-/ready
                        
                        # Проверяем targets
                        echo ""
                        echo "Проверка targets..."
                        kubectl exec -n ${namespace} \$PROMETHEUS_POD -- wget -q -O- http://localhost:9090/api/v1/targets | head -n 20
                        
                        # Проверяем конфигурацию
                        echo ""
                        echo "Retention: ${params.RETENTION}"
                        echo "Storage Size: ${params.STORAGE_SIZE}"
                        echo "Scrape Interval: ${params.SCRAPE_INTERVAL}"
                        
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
                echo "✅ Prometheus pipeline успешно завершен!"
                
                def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                
                if (params.ACTION == 'status') {
                    echo "📊 Статус Prometheus проверен в namespace: ${namespace}"
                } else if (params.ACTION in ['deploy', 'upgrade']) {
                    echo "🚀 Prometheus задеплоен в namespace: ${namespace}"
                    echo "📦 Версия: ${params.PROMETHEUS_VERSION}"
                    echo "📅 Retention: ${params.RETENTION}"
                    echo "💾 Storage Size: ${params.STORAGE_SIZE}"
                    echo "💻 Resources: CPU=${params.CPU_LIMIT}, Memory=${params.MEMORY_LIMIT}"
                    
                    echo ""
                    echo "🌐 Для доступа к Prometheus UI:"
                    echo "    kubectl port-forward -n ${namespace} svc/bankapp-prometheus 9090:9090"
                    echo "    Затем откройте: http://localhost:9090"
                    
                    echo ""
                    echo "📊 Для просмотра метрик:"
                    echo "    Откройте Prometheus UI и используйте PromQL запросы"
                    echo "    Или используйте Grafana для визуализации"
                } else if (params.ACTION == 'update-config') {
                    echo "⚙️  Конфигурация Prometheus обновлена в namespace: ${namespace}"
                } else if (params.ACTION == 'rollback') {
                    echo "⏪ Откат Prometheus выполнен в namespace: ${namespace}"
                }
            }
        }
        failure {
            echo "❌ Prometheus pipeline завершился с ошибкой"
            echo "📋 Проверьте Console Output для деталей"
            echo "💡 Prometheus может требовать больше времени для запуска"
            echo "💡 Убедитесь, что достаточно ресурсов в кластере"
            echo "💡 Проверьте доступность storage для PVC"
        }
        always {
            echo "🏁 Pipeline завершен"
        }
    }
}

