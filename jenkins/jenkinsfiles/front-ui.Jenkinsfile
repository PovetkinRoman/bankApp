pipeline {
    agent any

    environment {
        MODULE_NAME     = 'front-ui'
        IMAGE_TAG       = "${env.BUILD_NUMBER}"
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
                        
                        // Retry логина в GHCR с увеличенным timeout
                        retry(3) {
                            sh """
                                set +x
                                echo "Попытка логина в GHCR..."
                                echo \$GHCR_TOKEN | timeout 120 docker login ghcr.io -u \$GITHUB_USERNAME --password-stdin
                            """
                        }
                        
                        // Push образов
                        sh """
                            echo "Pushing image: ${imageName}"
                            docker push ${imageName}
                            echo "Pushing image: ${imageNameLatest}"
                            docker push ${imageNameLatest}
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            withCredentials([string(credentialsId: 'DOCKER_REGISTRY', variable: 'DOCKER_REGISTRY')]) {
                script {
                    def imageName = "${DOCKER_REGISTRY}/${MODULE_NAME}:${IMAGE_TAG}".toLowerCase()
                    echo "Build успешно завершен. Образ: ${imageName}"
                }
            }
        }
        failure {
            echo "Build завершился с ошибкой"
        }
        always {
            // Очистка рабочей директории
            echo "Build завершен"
        }
    }
}

