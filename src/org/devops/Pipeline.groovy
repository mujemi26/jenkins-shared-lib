// src/org/devops/Pipeline.groovy
package org.devops

class Pipeline implements Serializable {
    def script
    def config

    Pipeline(script, config) {
        this.script = script
        this.config = config
    }

    def validateEnvironment() {
        script.echo "Validating environment..."
        try {
            script.sh '''
                echo "Checking required tools..."
                docker version
                kubectl version --client
                kind version
                
                echo "Checking cluster status..."
                kind get clusters
            '''
        } catch (Exception e) {
            script.error "Environment validation failed: ${e.message}"
        }
    }

    def checkoutCode() {
        script.echo "Checking out code..."
        try {
            script.checkout script.scm
        } catch (Exception e) {
            script.error "Code checkout failed: ${e.message}"
        }
    }

    def buildDockerImage() {
        script.echo "Building Docker image..."
        try {
            script.sh """
                echo "Building image: ${config.DOCKER_IMAGE_NAME}:${script.env.BUILD_NUMBER}"
                docker build -t ${config.DOCKER_IMAGE_NAME}:${script.env.BUILD_NUMBER} . || exit 1
            """
        } catch (Exception e) {
            script.error "Docker build failed: ${e.message}"
        }
    }

    def pushToDockerHub() {
        script.echo "Pushing to Docker Hub..."
        try {
            script.withDockerRegistry([credentialsId: config.DOCKER_HUB_CREDENTIALS, url: '']) {
                script.sh """
                    echo "Pushing images to Docker Hub..."
                    docker push ${config.DOCKER_IMAGE_NAME}:${script.env.BUILD_NUMBER}
                    docker tag ${config.DOCKER_IMAGE_NAME}:${script.env.BUILD_NUMBER} ${config.DOCKER_IMAGE_NAME}:latest
                    docker push ${config.DOCKER_IMAGE_NAME}:latest
                """
            }
        } catch (Exception e) {
            script.error "Failed to push to Docker Hub: ${e.message}"
        }
    }
def deployToKindCluster() {
    script.echo "Deploying to Kind cluster..."
    script.timeout(time: 10, unit: 'MINUTES') {
        try {
            script.withCredentials([script.file(credentialsId: config.KUBECONFIG_FILE, variable: 'KUBECONFIG_FILE')]) {
                // Get deployment template
                def deploymentTemplate = script.libraryResource 'kubernetes/deployment.yaml'
                
                // Replace variables in template
                def imageTag = "${config.DOCKER_IMAGE_NAME}:${script.env.BUILD_NUMBER}"
                def deploymentYaml = deploymentTemplate.replace('${image}', imageTag)
                
                // Debug output
                script.echo "Generated deployment YAML:"
                script.echo deploymentYaml
                
                // Write deployment file
                script.writeFile file: 'deployment.yaml', text: deploymentYaml
                
                script.sh """
                    # Set KUBECONFIG
                    export KUBECONFIG=\${KUBECONFIG_FILE}

                    # Verify cluster and context
                    echo "Available clusters:"
                    kind get clusters
                    
                    echo "Current context:"
                    kubectl config current-context

                    # Load image to Kind cluster
                    echo "Loading image to Kind cluster..."
                    kind load docker-image ${imageTag} --name ${config.KIND_CLUSTER_NAME}

                    # Validate YAML before applying
                    echo "Validating deployment YAML..."
                    kubectl apply -f deployment.yaml --dry-run=client

                    # Apply deployment
                    echo "Applying deployment..."
                    kubectl apply -f deployment.yaml
                    
                    # Wait for deployment to be ready
                    echo "Waiting for deployment rollout..."
                    kubectl rollout status deployment/lab-app-deployment --timeout=300s
                    
                    # Get deployment status
                    echo "Deployment status:"
                    kubectl get deployments -o wide
                    kubectl get pods -o wide
                    kubectl get services
                    
                    # Get NodePort URL
                    echo "Application should be accessible at: http://localhost:30080"
                """
            }
        } catch (Exception e) {
            script.error "Deployment to Kind cluster failed: ${e.message}"
        }
    }
    def verifyKindDeployment() {
        script.echo "Verifying Kind deployment..."
        try {
            script.withCredentials([script.file(credentialsId: config.KUBECONFIG_FILE, variable: 'KUBECONFIG_FILE')]) {
                script.sh """
                    export KUBECONFIG=\${KUBECONFIG_FILE}
                    
                    echo "Checking pod status..."
                    kubectl get pods -l app=lab-app
                    
                    echo "Checking pod logs..."
                    for pod in \$(kubectl get pods -l app=lab-app -o name); do
                        echo "Logs for \$pod:"
                        kubectl logs \$pod
                    done
                    
                    echo "Checking service..."
                    kubectl get service lab-app-service
                    
                    echo "Testing application endpoint..."
                    curl -v http://localhost:30080 || true
                    
                    echo "Detailed pod information:"
                    kubectl describe pods -l app=lab-app
                """
            }
        } catch (Exception e) {
            script.error "Deployment verification failed: ${e.message}"
        }
    }

    def handleDeploymentSuccess() {
        script.echo "Handling successful deployment..."
        try {
            script.withCredentials([script.file(credentialsId: config.KUBECONFIG_FILE, variable: 'KUBECONFIG_FILE')]) {
                script.sh """
                    export KUBECONFIG=\${KUBECONFIG_FILE}
                    echo "Deployment successful!"
                    echo "Application is accessible at: http://localhost:30080"
                    echo "Pod status:"
                    kubectl get pods -l app=lab-app
                    echo "Service status:"
                    kubectl get service lab-app-service
                """
            }
        } catch (Exception e) {
            script.echo "Warning during success handling: ${e.message}"
        }
    }

    def handleDeploymentFailure() {
        script.echo "Handling deployment failure..."
        try {
            script.withCredentials([script.file(credentialsId: config.KUBECONFIG_FILE, variable: 'KUBECONFIG_FILE')]) {
                script.sh """
                    export KUBECONFIG=\${KUBECONFIG_FILE}
                    echo "Deployment failed - collecting debug information"
                    echo "Pod status:"
                    kubectl get pods -l app=lab-app
                    echo "Pod descriptions:"
                    kubectl describe pods -l app=lab-app
                    echo "Pod logs:"
                    for pod in \$(kubectl get pods -l app=lab-app -o name); do
                        echo "Logs for \$pod:"
                        kubectl logs \$pod
                    done
                """
            }
        } catch (Exception e) {
            script.echo "Warning during failure handling: ${e.message}"
        }
    }

    def cleanup() {
        script.echo "Cleaning up resources..."
        try {
            script.sh """
                echo "Cleaning up Docker images..."
                docker rmi ${config.DOCKER_IMAGE_NAME}:${script.env.BUILD_NUMBER} || true
                docker rmi ${config.DOCKER_IMAGE_NAME}:latest || true
                docker logout
                docker image prune -f
            """
            script.cleanWs()
        } catch (Exception e) {
            script.echo "Warning during cleanup: ${e.message}"
        }
    }
}  
