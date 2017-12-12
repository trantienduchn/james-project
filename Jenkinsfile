pipeline {
  agent {
    dockerfile {
      filename 'dockerfiles/compilation/java-8/Dockerfile'
    }
    
  }
  stages {
    stage('build') {
      steps {
        sh '''mvn package -DskipTests
'''
      }
    }
  }
}