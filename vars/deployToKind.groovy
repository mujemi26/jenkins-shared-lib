// vars/deployToKind.groovy
def call() {
    def config = pipelineConfig()
    def pipeline = new org.devops.Pipeline(this, config)
    pipeline.deployToKindCluster()
}
