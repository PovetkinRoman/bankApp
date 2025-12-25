pipeline {
    agent any

    environment {
        MODULE_NAME     = 'elk'
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
            name: 'COMPONENT',
            choices: ['all', 'elasticsearch', 'logstash', 'kibana'],
            description: 'Выберите компонент для развёртывания'
        )
        string(
            name: 'ELK_VERSION',
            defaultValue: '8.11.0',
            description: 'Версия ELK Stack'
        )
        string(
            name: 'ELASTICSEARCH_STORAGE',
            defaultValue: '20Gi',
            description: 'Размер хранилища для Elasticsearch'
        )
        string(
            name: 'ELASTICSEARCH_MEMORY',
            defaultValue: '2Gi',
            description: 'Лимит памяти для Elasticsearch'
        )
        string(
            name: 'LOGSTASH_MEMORY',
            defaultValue: '1Gi',
            description: 'Лимит памяти для Logstash'
        )
        string(
            name: 'KIBANA_MEMORY',
            defaultValue: '1Gi',
            description: 'Лимит памяти для Kibana'
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

        stage('Validate Helm Charts') {
            when {
                expression { params.ACTION in ['deploy', 'upgrade', 'update-config'] }
            }
            steps {
                script {
                    def components = params.COMPONENT == 'all' ? ['elasticsearch', 'logstash', 'kibana'] : [params.COMPONENT]
                    
                    components.each { component ->
                        sh """
                            echo "🔍 Validating Helm chart for ${component}..."
                            
                            if [ ! -d "helm/charts/${component}" ]; then
                                echo "❌ Helm chart для ${component} не найден"
                                exit 1
                            fi
                            
                            helm lint helm/charts/${component}
                            
                            helm template ${component} helm/charts/${component} \\
                              --namespace ${NAMESPACE_TEST} \\
                              > /tmp/${component}-manifests.yaml
                            
                            echo "✅ ${component} chart валидирован"
                        """
                    }
                }
            }
        }

        stage('Check ELK Status') {
            when {
                expression { params.ACTION == 'status' }
            }
            steps {
                script {
                    def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                    def components = params.COMPONENT == 'all' ? ['elasticsearch', 'logstash', 'kibana'] : [params.COMPONENT]
                    
                    components.each { component ->
                        sh """
                            echo "📊 Проверка статуса ${component} в namespace: ${namespace}"
                            echo "================================================"
                            
                            if ! helm list -n ${namespace} | grep -q ${component}; then
                                echo "⚠️  ${component} не установлен в namespace ${namespace}"
                                return 0
                            fi
                            
                            echo "📦 Helm Release:"
                            helm list -n ${namespace} | grep ${component}
                            
                            echo ""
                            echo "🔍 ${component} Pods:"
                            kubectl get pods -n ${namespace} -l app.kubernetes.io/name=${component}
                            
                            echo ""
                            echo "🔌 Services:"
                            kubectl get svc -n ${namespace} | grep ${component}
                            
                            echo ""
                            echo "💾 PVCs:"
                            kubectl get pvc -n ${namespace} | grep ${component} || echo "Нет PVC"
                            
                            echo ""
                        """
                    }
                    
                    sh """
                        echo "✅ Проверка статуса завершена"
                    """
                }
            }
        }

        stage('Deploy Elasticsearch to TEST') {
            when {
                allOf {
                    branch 'dev'
                    expression { params.ACTION in ['deploy', 'upgrade'] }
                    expression { params.COMPONENT in ['all', 'elasticsearch'] }
                }
            }
            steps {
                sh """
                    echo "🚀 Deploying Elasticsearch to TEST..."
                    echo "📦 Version: ${params.ELK_VERSION}"
                    echo "💾 Storage: ${params.ELASTICSEARCH_STORAGE}"
                    
                    kubectl create namespace ${NAMESPACE_TEST} --dry-run=client -o yaml | kubectl apply -f -
                    
                    helm upgrade --install elasticsearch helm/charts/elasticsearch \\
                      --namespace ${NAMESPACE_TEST} \\
                      --set image.tag=${params.ELK_VERSION} \\
                      --set persistence.size=${params.ELASTICSEARCH_STORAGE} \\
                      --set resources.limits.memory=${params.ELASTICSEARCH_MEMORY} \\
                      --set resources.limits.cpu=1000m \\
                      --set resources.requests.memory=1Gi \\
                      --set resources.requests.cpu=500m \\
                      --wait --timeout=15m
                    
                    echo "✅ Elasticsearch deployed"
                """
            }
        }

        stage('Verify Elasticsearch TEST') {
            when {
                allOf {
                    branch 'dev'
                    expression { params.ACTION in ['deploy', 'upgrade'] }
                    expression { params.COMPONENT in ['all', 'elasticsearch'] }
                }
            }
            steps {
                sh """
                    echo "🔍 Проверка Elasticsearch в TEST..."
                    
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/name=elasticsearch \\
                      -n ${NAMESPACE_TEST} \\
                      --timeout=900s || {
                        echo "⚠️  Elasticsearch не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/name=elasticsearch -n ${NAMESPACE_TEST} --tail=50
                        exit 1
                      }
                    
                    echo "✅ Elasticsearch готов"
                    
                    # Health check
                    ES_POD=\$(kubectl get pods -n ${NAMESPACE_TEST} -l app.kubernetes.io/name=elasticsearch -o jsonpath='{.items[0].metadata.name}')
                    kubectl exec -n ${NAMESPACE_TEST} \$ES_POD -- curl -s http://localhost:9200/_cluster/health || echo "Health check не прошел"
                """
            }
        }

        stage('Deploy Logstash to TEST') {
            when {
                allOf {
                    branch 'dev'
                    expression { params.ACTION in ['deploy', 'upgrade'] }
                    expression { params.COMPONENT in ['all', 'logstash'] }
                }
            }
            steps {
                sh """
                    echo "🚀 Deploying Logstash to TEST..."
                    echo "📦 Version: ${params.ELK_VERSION}"
                    
                    kubectl create namespace ${NAMESPACE_TEST} --dry-run=client -o yaml | kubectl apply -f -
                    
                    helm upgrade --install logstash helm/charts/logstash \\
                      --namespace ${NAMESPACE_TEST} \\
                      --set image.tag=${params.ELK_VERSION} \\
                      --set resources.limits.memory=${params.LOGSTASH_MEMORY} \\
                      --set resources.limits.cpu=500m \\
                      --set resources.requests.memory=512Mi \\
                      --set resources.requests.cpu=250m \\
                      --set elasticsearch.host=bankapp-elasticsearch \\
                      --set kafka.bootstrapServers=kafka:9092 \\
                      --wait --timeout=10m
                    
                    echo "✅ Logstash deployed"
                """
            }
        }

        stage('Verify Logstash TEST') {
            when {
                allOf {
                    branch 'dev'
                    expression { params.ACTION in ['deploy', 'upgrade'] }
                    expression { params.COMPONENT in ['all', 'logstash'] }
                }
            }
            steps {
                sh """
                    echo "🔍 Проверка Logstash в TEST..."
                    
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/name=logstash \\
                      -n ${NAMESPACE_TEST} \\
                      --timeout=600s || {
                        echo "⚠️  Logstash не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/name=logstash -n ${NAMESPACE_TEST} --tail=50
                        exit 1
                      }
                    
                    echo "✅ Logstash готов"
                """
            }
        }

        stage('Deploy Kibana to TEST') {
            when {
                allOf {
                    branch 'dev'
                    expression { params.ACTION in ['deploy', 'upgrade'] }
                    expression { params.COMPONENT in ['all', 'kibana'] }
                }
            }
            steps {
                sh """
                    echo "🚀 Deploying Kibana to TEST..."
                    echo "📦 Version: ${params.ELK_VERSION}"
                    
                    kubectl create namespace ${NAMESPACE_TEST} --dry-run=client -o yaml | kubectl apply -f -
                    
                    helm upgrade --install kibana helm/charts/kibana \\
                      --namespace ${NAMESPACE_TEST} \\
                      --set image.tag=${params.ELK_VERSION} \\
                      --set resources.limits.memory=${params.KIBANA_MEMORY} \\
                      --set resources.limits.cpu=500m \\
                      --set resources.requests.memory=512Mi \\
                      --set resources.requests.cpu=250m \\
                      --set elasticsearch.host=bankapp-elasticsearch \\
                      --wait --timeout=10m
                    
                    echo "✅ Kibana deployed"
                """
            }
        }

        stage('Verify Kibana TEST') {
            when {
                allOf {
                    branch 'dev'
                    expression { params.ACTION in ['deploy', 'upgrade'] }
                    expression { params.COMPONENT in ['all', 'kibana'] }
                }
            }
            steps {
                sh """
                    echo "🔍 Проверка Kibana в TEST..."
                    
                    kubectl wait --for=condition=ready pod \\
                      -l app.kubernetes.io/name=kibana \\
                      -n ${NAMESPACE_TEST} \\
                      --timeout=600s || {
                        echo "⚠️  Kibana не готов, показываем логи:"
                        kubectl logs -l app.kubernetes.io/name=kibana -n ${NAMESPACE_TEST} --tail=50
                        exit 1
                      }
                    
                    echo "✅ Kibana готов"
                    
                    # Health check
                    KIBANA_POD=\$(kubectl get pods -n ${NAMESPACE_TEST} -l app.kubernetes.io/name=kibana -o jsonpath='{.items[0].metadata.name}')
                    kubectl exec -n ${NAMESPACE_TEST} \$KIBANA_POD -- curl -s http://localhost:5601/api/status || echo "Health check не прошел"
                    
                    echo ""
                    echo "🌐 Kibana UI доступен через:"
                    echo "   kubectl port-forward -n ${NAMESPACE_TEST} svc/bankapp-kibana 5601:5601"
                    echo "   Затем откройте: http://localhost:5601"
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
                    def components = params.COMPONENT == 'all' ? ['elasticsearch', 'logstash', 'kibana'] : [params.COMPONENT]
                    
                    components.each { component ->
                        sh """
                            echo "⚙️  Обновление конфигурации ${component} в ${namespace}..."
                            
                            helm upgrade ${component} helm/charts/${component} \\
                              --namespace ${namespace} \\
                              --reuse-values \\
                              --wait --timeout=10m
                            
                            echo "✅ ${component} конфигурация обновлена"
                        """
                    }
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
                    def components = params.COMPONENT == 'all' ? ['kibana', 'logstash', 'elasticsearch'] : [params.COMPONENT]
                    
                    components.each { component ->
                        sh """
                            echo "⏪ Откат ${component} в ${namespace}..."
                            
                            echo "История релизов:"
                            helm history ${component} -n ${namespace}
                            
                            helm rollback ${component} -n ${namespace} --wait --timeout=10m
                            
                            echo "✅ ${component} откат выполнен"
                        """
                    }
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
                    echo "🔔 Требуется подтверждение для деплоя ELK Stack в PRODUCTION"
                    echo "🎯 Namespace: ${NAMESPACE_PROD}"
                    echo "📦 ELK Version: ${params.ELK_VERSION}"
                    echo "🔧 Component: ${params.COMPONENT}"
                    echo "💾 Elasticsearch Storage: ${params.ELASTICSEARCH_STORAGE}"
                    echo "⚠️  ВНИМАНИЕ: ELK Stack - критический сервис для логирования!"
                }
                input message: 'Deploy ELK Stack to PROD environment?', ok: 'Yes, deploy'
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
                    def components = params.COMPONENT == 'all' ? ['elasticsearch', 'logstash', 'kibana'] : [params.COMPONENT]
                    
                    sh """
                        echo "🚀 Deploying ELK Stack to PRODUCTION..."
                        kubectl create namespace ${NAMESPACE_PROD} --dry-run=client -o yaml | kubectl apply -f -
                    """
                    
                    if (params.COMPONENT in ['all', 'elasticsearch']) {
                        sh """
                            echo "Deploying Elasticsearch..."
                            helm upgrade --install elasticsearch helm/charts/elasticsearch \\
                              --namespace ${NAMESPACE_PROD} \\
                              --set image.tag=${params.ELK_VERSION} \\
                              --set persistence.size=100Gi \\
                              --set resources.limits.memory=4Gi \\
                              --set resources.limits.cpu=2000m \\
                              --set resources.requests.memory=2Gi \\
                              --set resources.requests.cpu=1000m \\
                              --wait --timeout=20m
                        """
                    }
                    
                    if (params.COMPONENT in ['all', 'logstash']) {
                        sh """
                            echo "Deploying Logstash..."
                            helm upgrade --install logstash helm/charts/logstash \\
                              --namespace ${NAMESPACE_PROD} \\
                              --set image.tag=${params.ELK_VERSION} \\
                              --set resources.limits.memory=2Gi \\
                              --set resources.limits.cpu=1000m \\
                              --set elasticsearch.host=bankapp-elasticsearch \\
                              --set kafka.bootstrapServers=kafka:9092 \\
                              --wait --timeout=15m
                        """
                    }
                    
                    if (params.COMPONENT in ['all', 'kibana']) {
                        sh """
                            echo "Deploying Kibana..."
                            helm upgrade --install kibana helm/charts/kibana \\
                              --namespace ${NAMESPACE_PROD} \\
                              --set image.tag=${params.ELK_VERSION} \\
                              --set resources.limits.memory=2Gi \\
                              --set resources.limits.cpu=1000m \\
                              --set elasticsearch.host=bankapp-elasticsearch \\
                              --wait --timeout=15m
                        """
                    }
                    
                    sh """
                        echo "✅ ELK Stack deployed to PROD"
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
                script {
                    def components = params.COMPONENT == 'all' ? ['elasticsearch', 'logstash', 'kibana'] : [params.COMPONENT]
                    
                    components.each { component ->
                        sh """
                            echo "🔍 Проверка ${component} в PROD..."
                            
                            kubectl wait --for=condition=ready pod \\
                              -l app.kubernetes.io/name=${component} \\
                              -n ${NAMESPACE_PROD} \\
                              --timeout=900s || {
                                echo "⚠️  ${component} не готов"
                                kubectl logs -l app.kubernetes.io/name=${component} -n ${NAMESPACE_PROD} --tail=100
                                exit 1
                              }
                            
                            echo "✅ ${component} готов"
                        """
                    }
                }
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
                        echo "🏥 Выполнение health check для ELK Stack..."
                        
                        # Elasticsearch
                        if kubectl get pods -n ${namespace} -l app.kubernetes.io/name=elasticsearch &>/dev/null; then
                            echo "Проверка Elasticsearch..."
                            ES_POD=\$(kubectl get pods -n ${namespace} -l app.kubernetes.io/name=elasticsearch -o jsonpath='{.items[0].metadata.name}')
                            kubectl exec -n ${namespace} \$ES_POD -- curl -s http://localhost:9200/_cluster/health | head -n 5
                        fi
                        
                        # Logstash
                        if kubectl get pods -n ${namespace} -l app.kubernetes.io/name=logstash &>/dev/null; then
                            echo ""
                            echo "Проверка Logstash..."
                            kubectl get pods -n ${namespace} -l app.kubernetes.io/name=logstash
                        fi
                        
                        # Kibana
                        if kubectl get pods -n ${namespace} -l app.kubernetes.io/name=kibana &>/dev/null; then
                            echo ""
                            echo "Проверка Kibana..."
                            KIBANA_POD=\$(kubectl get pods -n ${namespace} -l app.kubernetes.io/name=kibana -o jsonpath='{.items[0].metadata.name}')
                            kubectl exec -n ${namespace} \$KIBANA_POD -- curl -s http://localhost:5601/api/status | head -n 5
                        fi
                        
                        echo ""
                        echo "✅ Health check завершен"
                    """
                }
            }
        }
    }

    post {
        success {
            script {
                echo "✅ ELK Stack pipeline успешно завершен!"
                
                def namespace = env.BRANCH_NAME == 'main' ? NAMESPACE_PROD : NAMESPACE_TEST
                
                if (params.ACTION == 'status') {
                    echo "📊 Статус ELK Stack проверен в namespace: ${namespace}"
                } else if (params.ACTION in ['deploy', 'upgrade']) {
                    echo "🚀 ELK Stack задеплоен в namespace: ${namespace}"
                    echo "📦 Версия: ${params.ELK_VERSION}"
                    echo "🔧 Компонент: ${params.COMPONENT}"
                    
                    echo ""
                    echo "🌐 Для доступа к Kibana UI:"
                    echo "    kubectl port-forward -n ${namespace} svc/bankapp-kibana 5601:5601"
                    echo "    Затем откройте: http://localhost:5601"
                    
                    echo ""
                    echo "📊 Elasticsearch доступен на:"
                    echo "    http://bankapp-elasticsearch:9200 (внутри кластера)"
                    
                    echo ""
                    echo "🔌 Logstash читает логи из Kafka topic: logs-topic"
                } else if (params.ACTION == 'update-config') {
                    echo "⚙️  Конфигурация ELK Stack обновлена в namespace: ${namespace}"
                } else if (params.ACTION == 'rollback') {
                    echo "⏪ Откат ELK Stack выполнен в namespace: ${namespace}"
                }
            }
        }
        failure {
            echo "❌ ELK Stack pipeline завершился с ошибкой"
            echo "📋 Проверьте Console Output для деталей"
            echo "💡 Elasticsearch требует много времени для запуска (до 15 минут)"
            echo "💡 Убедитесь, что достаточно ресурсов в кластере"
            echo "💡 Проверьте доступность Kafka для Logstash"
        }
        always {
            echo "🏁 Pipeline завершен"
        }
    }
}

