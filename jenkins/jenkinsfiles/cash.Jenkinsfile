pipeline {
    agent any

    environment {
        MODULE_NAME     = 'cash'
        IMAGE_TAG       = "${env.BUILD_NUMBER}"
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

        stage('Build & Unit Tests') {
            steps {
                dir(MODULE_NAME) {
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        stage('Run Tests') {
            steps {
                dir(MODULE_NAME) {
                    sh 'mvn test'
                }
            }
            post {
                always {
                    junit "${MODULE_NAME}/target/surefire-reports/*.xml"
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                withCredentials([
                    string(credentialsId: 'DOCKER_REGISTRY', variable: 'DOCKER_REGISTRY')
                ]) {
                    script {
                        // Преобразуем в нижний регистр, так как Docker требует lowercase
                        def imageName = "${DOCKER_REGISTRY}/${MODULE_NAME}:${IMAGE_TAG}".toLowerCase()
                        def imageNameLatest = "${DOCKER_REGISTRY}/${MODULE_NAME}:latest".toLowerCase()
                        sh """
                            docker build -t ${imageName} -f ${MODULE_NAME}/dockerfile .
                            docker tag ${imageName} ${imageNameLatest}
                        """
                    }
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                withCredentials([
                    string(credentialsId: 'GHCR_TOKEN', variable: 'GHCR_TOKEN'),
                    string(credentialsId: 'GITHUB_USERNAME', variable: 'GITHUB_USERNAME'),
                    string(credentialsId: 'DOCKER_REGISTRY', variable: 'DOCKER_REGISTRY')
                ]) {
                    script {
                        // Преобразуем в нижний регистр, так как Docker требует lowercase
                        def imageName = "${DOCKER_REGISTRY}/${MODULE_NAME}:${IMAGE_TAG}".toLowerCase()
                        def imageNameLatest = "${DOCKER_REGISTRY}/${MODULE_NAME}:latest".toLowerCase()
                        
                        // Логин в GHCR
                        sh """
                            set +x
                            echo "Логин в GHCR..."
                            echo "\$GHCR_TOKEN" | docker login ghcr.io -u "\$GITHUB_USERNAME" --password-stdin || {
                                echo "Первая попытка не удалась, повторяем..."
                                sleep 5
                                echo "\$GHCR_TOKEN" | docker login ghcr.io -u "\$GITHUB_USERNAME" --password-stdin
                            }
                        """
                        
                        // Push образов с retry
                        def maxRetries = 3
                        def retryDelay = 10
                        
                        for (int i = 1; i <= maxRetries; i++) {
                            try {
                                sh """
                                    echo "Pushing image: ${imageName} (попытка ${i}/${maxRetries})"
                                    timeout 300 docker push ${imageName}
                                    echo "Pushing image: ${imageNameLatest} (попытка ${i}/${maxRetries})"
                                    timeout 300 docker push ${imageNameLatest}
                                """
                                echo "✅ Push успешен!"
                                break
                            } catch (Exception e) {
                                if (i == maxRetries) {
                                    error "❌ Не удалось запушить образ после ${maxRetries} попыток: ${e.message}"
                                }
                                echo "⚠️  Попытка ${i} не удалась, повторяем через ${retryDelay} секунд..."
                                sleep retryDelay
                            }
                        }
                    }
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when {
                branch 'dev'
            }
            steps {
                withCredentials([
                    string(credentialsId: 'DOCKER_REGISTRY', variable: 'DOCKER_REGISTRY'),
                    string(credentialsId: 'GITHUB_USERNAME', variable: 'GITHUB_USERNAME'),
                    string(credentialsId: 'GHCR_TOKEN', variable: 'GHCR_TOKEN')
                ]) {
                    script {
                        def imageName = "${DOCKER_REGISTRY}/${MODULE_NAME}".toLowerCase()
                        
                        sh """
                            echo "Deploying ${MODULE_NAME} to Kubernetes..."
                            
                            # Проверяем наличие helm chart
                            if [ ! -d "helm/charts/${MODULE_NAME}" ]; then
                                echo "⚠️  Helm chart для ${MODULE_NAME} не найден, пропускаем деплой"
                                exit 0
                            fi
                            
                            # Создаем namespace если не существует
                            kubectl create namespace ${NAMESPACE_TEST} --dry-run=client -o yaml | kubectl apply -f -
                            
                            # Создаем imagePullSecret для GHCR
                            kubectl create secret docker-registry ghcr-secret \\
                              --docker-server=ghcr.io \\
                              --docker-username=\$GITHUB_USERNAME \\
                              --docker-password=\$GHCR_TOKEN \\
                              --docker-email=jenkins@example.com \\
                              -n ${NAMESPACE_TEST} \\
                              --dry-run=client -o yaml | kubectl apply -f -
                            
                            # Деплоим через Helm
                            helm upgrade --install ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                              --namespace ${NAMESPACE_TEST} \\
                              --set image.repository=${imageName} \\
                              --set image.tag=${IMAGE_TAG} \\
                              --set image.pullPolicy=Always \\
                              --wait --timeout=5m
                            
                            echo "✅ Деплой ${MODULE_NAME} в TEST завершен"
                            
                            # Показываем статус
                            kubectl get pods -n ${NAMESPACE_TEST} -l app.kubernetes.io/name=${MODULE_NAME}
                        """
                    }
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
                    
                    # Ждем готовности pod'а
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/name=${MODULE_NAME} \\
                      -n ${NAMESPACE_TEST} \\
                      --timeout=300s || {
                        echo "⚠️  Pod не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/name=${MODULE_NAME} -n ${NAMESPACE_TEST} --tail=50
                        exit 1
                      }
                    
                    echo "✅ Deployment в TEST успешно проверен"
                    
                    # Показываем информацию о сервисе
                    kubectl get svc ${MODULE_NAME} -n ${NAMESPACE_TEST} || echo "Service не найден"
                """
            }
        }

        stage('Manual Approval for PROD') {
            when {
                branch 'main'
            }
            steps {
                script {
                    echo "🔔 Требуется подтверждение для деплоя в PRODUCTION"
                    echo "📦 Образ: ${DOCKER_REGISTRY}/${MODULE_NAME}:${IMAGE_TAG}"
                    echo "🎯 Namespace: ${NAMESPACE_PROD}"
                }
                input message: 'Deploy to PROD environment?', ok: 'Yes, deploy'
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

        stage('Create DB Secrets for PROD') {
            when {
                branch 'main'
            }
            steps {
                sh """
                    echo "Создание DB secrets в PROD..."
                    
                    # Создаем secrets для различных сервисов
                    kubectl create secret generic customer-service-customer-db \\
                      --from-literal=password=${DB_PASSWORD} \\
                      -n ${NAMESPACE_PROD} \\
                      --dry-run=client -o yaml | kubectl apply -f -
                    
                    kubectl create secret generic order-service-order-db \\
                      --from-literal=password=${DB_PASSWORD} \\
                      -n ${NAMESPACE_PROD} \\
                      --dry-run=client -o yaml | kubectl apply -f -
                    
                    echo "✅ DB Secrets созданы в PROD"
                """
            }
        }

        stage('Deploy to PROD') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([
                    string(credentialsId: 'DOCKER_REGISTRY', variable: 'DOCKER_REGISTRY'),
                    string(credentialsId: 'GITHUB_USERNAME', variable: 'GITHUB_USERNAME'),
                    string(credentialsId: 'GHCR_TOKEN', variable: 'GHCR_TOKEN')
                ]) {
                    script {
                        def imageName = "${DOCKER_REGISTRY}/${MODULE_NAME}".toLowerCase()
                        
                        sh """
                            echo "Deploying ${MODULE_NAME} to PRODUCTION..."
                            
                            # Проверяем наличие helm chart
                            if [ ! -d "helm/charts/${MODULE_NAME}" ]; then
                                echo "⚠️  Helm chart для ${MODULE_NAME} не найден, пропускаем деплой"
                                exit 0
                            fi
                            
                            # Создаем imagePullSecret для GHCR
                            kubectl create secret docker-registry ghcr-secret \\
                              --docker-server=ghcr.io \\
                              --docker-username=\$GITHUB_USERNAME \\
                              --docker-password=\$GHCR_TOKEN \\
                              --docker-email=jenkins@example.com \\
                              -n ${NAMESPACE_PROD} \\
                              --dry-run=client -o yaml | kubectl apply -f -
                            
                            # Деплоим через Helm с production настройками
                            helm upgrade --install ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                              --namespace ${NAMESPACE_PROD} \\
                              --set image.repository=${imageName} \\
                              --set image.tag=${IMAGE_TAG} \\
                              --set image.pullPolicy=Always \\
                              --set replicaCount=2 \\
                              --wait --timeout=5m
                            
                            echo "✅ Деплой ${MODULE_NAME} в PROD завершен"
                            
                            # Показываем статус
                            kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=${MODULE_NAME}
                        """
                    }
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
                    
                    # Ждем готовности всех pod'ов
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/name=${MODULE_NAME} \\
                      -n ${NAMESPACE_PROD} \\
                      --timeout=300s || {
                        echo "⚠️  Pods не готовы, показываем логи:"
                        kubectl logs -l app.kubernetes.io/name=${MODULE_NAME} -n ${NAMESPACE_PROD} --tail=50
                        exit 1
                      }
                    
                    echo "✅ Deployment в PROD успешно проверен"
                    
                    # Показываем информацию о сервисе
                    kubectl get svc ${MODULE_NAME} -n ${NAMESPACE_PROD}
                    
                    # Показываем все pods
                    kubectl get pods -n ${NAMESPACE_PROD} -l app.kubernetes.io/name=${MODULE_NAME}
                """
            }
        }
    }

    post {
        success {
            withCredentials([string(credentialsId: 'DOCKER_REGISTRY', variable: 'DOCKER_REGISTRY')]) {
                script {
                    def imageName = "${DOCKER_REGISTRY}/${MODULE_NAME}:${IMAGE_TAG}".toLowerCase()
                    echo "✅ Build успешно завершен!"
                    echo "📦 Образ: ${imageName}"
                    
                    if (env.BRANCH_NAME == 'dev') {
                        echo "🚀 Деплой в TEST namespace: ${NAMESPACE_TEST}"
                    } else if (env.BRANCH_NAME == 'main') {
                        echo "🚀 Деплой в PROD namespace: ${NAMESPACE_PROD}"
                        echo "⚠️  PRODUCTION deployment с 2 репликами"
                    }
                }
            }
        }
        failure {
            echo "❌ Build завершился с ошибкой"
            echo "📋 Проверьте Console Output для деталей"
        }
        always {
            echo "🏁 Pipeline завершен"
        }
    }
}

