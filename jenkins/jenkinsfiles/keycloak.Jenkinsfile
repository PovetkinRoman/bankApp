pipeline {
    agent any

    environment {
        MODULE_NAME     = 'keycloak'
        NAMESPACE_TEST  = 'test'
        NAMESPACE_PROD  = 'prod'
        DB_NAME         = 'bankapp'
        DB_USER         = 'bankapp_user'
        DB_PASSWORD     = 'bankapp_password_123'
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
                    
                    echo "✅ Helm chart валидация пройдена"
                """
            }
        }

        stage('Deploy to Kubernetes TEST') {
            when {
                branch 'dev'
            }
            steps {
                script {
                    sh """
                        echo "Deploying ${MODULE_NAME} to Kubernetes TEST..."
                        
                        # Создаем namespace если не существует
                        kubectl create namespace ${NAMESPACE_TEST} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Деплоим через Helm
                        helm upgrade --install ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${NAMESPACE_TEST} \\
                          --set env.KC_DB_URL_HOST=bankapp-postgresql \\
                          --set env.KC_DB_USERNAME=${DB_USER} \\
                          --set env.KC_DB_PASSWORD=${DB_PASSWORD} \\
                          --set env.KC_DB_URL_DATABASE=${DB_NAME} \\
                          --wait --timeout=10m
                        
                        echo "✅ Деплой ${MODULE_NAME} в TEST завершен"
                        
                        # Показываем статус
                        kubectl get pods -n ${NAMESPACE_TEST} -l app.kubernetes.io/name=${MODULE_NAME}
                    """
                }
            }
        }

        stage('Verify Deployment TEST') {
            when {
                branch 'dev'
            }
            steps {
                sh """
                    echo "Проверка деплоя в TEST..."
                    
                    # Ждем готовности pod'а (Keycloak может запускаться долго)
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/name=${MODULE_NAME} \\
                      -n ${NAMESPACE_TEST} \\
                      --timeout=600s || {
                        echo "⚠️  Pod не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/name=${MODULE_NAME} -n ${NAMESPACE_TEST} --tail=100
                        exit 1
                      }
                    
                    echo "✅ Deployment в TEST успешно проверен"
                    
                    # Показываем информацию о сервисе
                    kubectl get svc ${MODULE_NAME} -n ${NAMESPACE_TEST} || echo "Service не найден"
                    
                    # Проверяем health endpoint
                    echo "Проверка health endpoint..."
                    kubectl exec -n ${NAMESPACE_TEST} deploy/${MODULE_NAME} -- curl -s http://localhost:8080/health || echo "Health check временно недоступен"
                """
            }
        }

        stage('Manual Approval for PROD') {
            when {
                branch 'main'
            }
            steps {
                script {
                    echo "🔔 Требуется подтверждение для деплоя Keycloak в PRODUCTION"
                    echo "🎯 Namespace: ${NAMESPACE_PROD}"
                    echo "⚠️  ВНИМАНИЕ: Keycloak - критический сервис для аутентификации!"
                }
                input message: 'Deploy Keycloak to PROD environment?', ok: 'Yes, deploy'
            }
        }

        stage('Install PostgreSQL to PROD') {
            when {
                branch 'main'
            }
            steps {
                sh """
                    echo "Установка PostgreSQL в PROD..."
                    
                    # Добавляем Helm репозиторий
                    helm repo add bitnami https://charts.bitnami.com/bitnami || true
                    helm repo update
                    
                    # Создаем namespace если не существует
                    kubectl create namespace ${NAMESPACE_PROD} --dry-run=client -o yaml | kubectl apply -f -
                    
                    # Устанавливаем PostgreSQL
                    helm upgrade --install postgres bitnami/postgresql \\
                      --namespace ${NAMESPACE_PROD} \\
                      --set auth.database=${DB_NAME} \\
                      --set auth.username=${DB_USER} \\
                      --set auth.password=${DB_PASSWORD} \\
                      --wait --timeout=5m
                    
                    echo "✅ PostgreSQL установлен в PROD"
                """
            }
        }

        stage('Deploy to PROD') {
            when {
                branch 'main'
            }
            steps {
                script {
                    sh """
                        echo "Deploying ${MODULE_NAME} to PRODUCTION..."
                        
                        # Деплоим через Helm с production настройками
                        helm upgrade --install ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                          --namespace ${NAMESPACE_PROD} \\
                          --set env.KC_DB_URL_HOST=postgres-postgresql \\
                          --set env.KC_DB_USERNAME=${DB_USER} \\
                          --set env.KC_DB_PASSWORD=${DB_PASSWORD} \\
                          --set env.KC_DB_URL_DATABASE=${DB_NAME} \\
                          --set resources.limits.cpu=2000m \\
                          --set resources.limits.memory=2Gi \\
                          --wait --timeout=10m
                        
                        echo "✅ Деплой ${MODULE_NAME} в PROD завершен"
                        
                        # Показываем статус
                        kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=${MODULE_NAME}
                    """
                }
            }
        }

        stage('Verify Deployment PROD') {
            when {
                branch 'main'
            }
            steps {
                sh """
                    echo "Проверка деплоя в PROD..."
                    
                    # Ждем готовности pod'а
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/name=${MODULE_NAME} \\
                      -n ${NAMESPACE_PROD} \\
                      --timeout=600s || {
                        echo "⚠️  Pod не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/name=${MODULE_NAME} -n ${NAMESPACE_PROD} --tail=100
                        exit 1
                      }
                    
                    echo "✅ Deployment в PROD успешно проверен"
                    
                    # Показываем информацию о сервисе
                    kubectl get svc ${MODULE_NAME} -n ${NAMESPACE_PROD}
                    
                    # Показываем pod
                    kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=${MODULE_NAME}
                """
            }
        }
    }

    post {
        success {
            script {
                echo "✅ Keycloak deployment успешно завершен!"
                
                if (env.BRANCH_NAME == 'dev') {
                    echo "🚀 Keycloak задеплоен в TEST namespace: ${NAMESPACE_TEST}"
                    echo "🔗 Для доступа настройте port-forward:"
                    echo "    kubectl port-forward -n ${NAMESPACE_TEST} svc/keycloak 8090:8080"
                    echo "    Keycloak UI: http://localhost:8090"
                    echo "    Admin credentials: admin / admin"
                } else if (env.BRANCH_NAME == 'main') {
                    echo "🚀 Keycloak задеплоен в PROD namespace: ${NAMESPACE_PROD}"
                    echo "⚠️  PRODUCTION deployment с увеличенными ресурсами"
                }
            }
        }
        failure {
            echo "❌ Keycloak deployment завершился с ошибкой"
            echo "📋 Проверьте Console Output для деталей"
            echo "💡 Keycloak может требовать больше времени для запуска"
        }
        always {
            echo "🏁 Pipeline завершен"
        }
    }
}

