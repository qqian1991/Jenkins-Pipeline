node('Docker-3') {
  stage('SCM') {
    checkout([
    	$class: 'GitSCM', 
    	branches: [[name: 'origin/develop']], 		
    	userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "http://10.142.78.33:8080/DataPlatform/todp-auth.git"]],
    	extensions: [[$class: 'WipeWorkspace']]
    ])	
  }

  stage('SonarQube analysis') {
    withSonarQubeEnv('SonarQube-58') {
      sh "mvn clean package $SONAR_MAVEN_GOAL -Dsonar.host.url=$SONAR_HOST_URL -Dsonar.projectName=todp-one -Dsonar.branch=develop -DskipTests"
    }
  }
  
  stage("Quality Gate") {
    def qg = waitForQualityGate() 
    echo "qg.status: " + qg.status
    env.SonarScanResult = qg.status
    env.sonarScanResult = qg.status
    echo "SonarScanResult value is: ${env.SonarScanResult}"
    if (qg.status != 'OK') {
      error "Pipeline aborted due to quality gate failure: ${qg.status}"
    } 
  }
}

