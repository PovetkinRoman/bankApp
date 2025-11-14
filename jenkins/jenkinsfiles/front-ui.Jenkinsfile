pipeline {
    agent any

    environment {
        DOCKER_REGISTRY = credentials('DOCKER_REGISTRY')
        GITHUB_USERNAME = credentials('GITHUB_USERNAME')
        IMAGE_TAG       = "${env.BUILD_NUMBER}"
        MODULE_NAME     = 'front-ui'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
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
                script {
                    def imageName = "${DOCKER_REGISTRY}/${MODULE_NAME}:${IMAGE_TAG}"
                    sh """
                        docker build -t ${imageName} -f ${MODULE_NAME}/dockerfile .
                        docker tag ${imageName} ${DOCKER_REGISTRY}/${MODULE_NAME}:latest
                    """
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                withCredentials([string(credentialsId: 'GHCR_TOKEN', variable: 'GHCR_TOKEN')]) {
                    script {
                        def imageName = "${DOCKER_REGISTRY}/${MODULE_NAME}:${IMAGE_TAG}"
                        sh """
                            echo \$GHCR_TOKEN | docker login ghcr.io -u ${GITHUB_USERNAME} --password-stdin
                            docker push ${imageName}
                            docker push ${DOCKER_REGISTRY}/${MODULE_NAME}:latest
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Build успешно завершен. Образ: ${DOCKER_REGISTRY}/${MODULE_NAME}:${IMAGE_TAG}"
        }
        failure {
            echo "Build завершился с ошибкой"
        }
        always {
            cleanWs()
        }
    }
}

