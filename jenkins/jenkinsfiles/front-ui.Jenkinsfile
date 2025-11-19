pipeline {
    agent any

    environment {
        MODULE_NAME     = 'front-ui'
        IMAGE_TAG       = "${env.BUILD_NUMBER}"
        NAMESPACE       = 'test'
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
                    string(credentialsId: 'DOCKER_REGISTRY', variable: 'DOCKER_REGISTRY')
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
                            kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                            
                            # Деплоим через Helm
                            helm upgrade --install ${MODULE_NAME} helm/charts/${MODULE_NAME} \\
                              --namespace ${NAMESPACE} \\
                              --set image.repository=${imageName} \\
                              --set image.tag=${IMAGE_TAG} \\
                              --set image.pullPolicy=Always \\
                              --wait --timeout=5m
                            
                            echo "✅ Деплой ${MODULE_NAME} завершен"
                            
                            # Показываем статус
                            kubectl get pods -n ${NAMESPACE} -l app=${MODULE_NAME}
                        """
                    }
                }
            }
        }

        stage('Verify Deployment') {
            when {
                branch 'dev'
            }
            steps {
                sh """
                    echo "Проверка деплоя..."
                    
                    # Ждем готовности pod'а
                    kubectl wait --for=condition=ready pod \\
                      -l app=${MODULE_NAME} \\
                      -n ${NAMESPACE} \\
                      --timeout=300s || {
                        echo "⚠️  Pod не готов, показываем логи:"
                        kubectl logs -l app=${MODULE_NAME} -n ${NAMESPACE} --tail=50
                        exit 1
                      }
                    
                    echo "✅ Deployment успешно проверен"
                    
                    # Показываем информацию о сервисе
                    kubectl get svc ${MODULE_NAME} -n ${NAMESPACE} || echo "Service не найден"
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
                    echo "🚀 Деплой в namespace: ${NAMESPACE}"
                }
            }
        }
        failure {
            echo "❌ Build завершился с ошибкой"
        }
        always {
            echo "🏁 Pipeline завершен"
        }
    }
}
