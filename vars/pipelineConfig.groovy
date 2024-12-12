// vars/pipelineConfig.groovy
def call(Map config = [:]) {
    // Default values
    def defaults = [
        dockerHubCredentials: 'dockerhub-credentials',
        dockerImageName: 'mujimmy/lab-app',
        githubRepoUrl: 'https://github.com/mujemi26/jenkins.git',
        kindClusterName: 'kind-kind',
        kubeConfigFile: 'kind-kubeconfig',
        kindHome: '$HOME/.config/kind',
        kubeConfigPath: '$HOME/.kube/config'
    ]
    
    // Merge provided config with defaults
    def finalConfig = defaults + config
    
    return [
        DOCKER_HUB_CREDENTIALS: finalConfig.dockerHubCredentials,
        DOCKER_IMAGE_NAME: finalConfig.dockerImageName,
        GITHUB_REPO_URL: finalConfig.githubRepoUrl,
        KIND_CLUSTER_NAME: finalConfig.kindClusterName,
        KUBECONFIG_FILE: finalConfig.kubeConfigFile,
        KIND_HOME: finalConfig.kindHome,
        KUBE_CONFIG_PATH: finalConfig.kubeConfigPath
    ]
}
