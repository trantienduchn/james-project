pipeline {
  agent {
    dockerfile {
      filename 'dockerfiles/compilation/java-8/Dockerfile'
    }
    
  }
  stages {
    stage('build') {
      steps {
        dir(path: 'dockerfiles/compilation/java-8/') {
          sh '''mvn package -DskipTests
'''
        }
        
      }
    }
  }
}