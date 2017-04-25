node('Docker-3') {

  stage('SCM') {
    checkout([
    	$class: 'GitSCM', 
    	branches: [[name: "origin/${params.BRANCH_TO_BUILD}"]], 		
    	userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "http://10.142.78.33:8080/DataPlatform/todp-auth.git"]],
    	extensions: [[$class: 'WipeWorkspace']]
    ])	

    sh '''
    #!/bin/bash
    set -ex

    ct_maven_path="~/mavenrepo/com/chinatelecom"

    if [ -f "${ct_maven_pat}" ]; then
     rm -rf "${ct_maven_pat}"
    fi
    '''
  }

  stage('SonarQube analysis') {
    withSonarQubeEnv('SonarQube-58') {
      sh "mvn clean package $SONAR_MAVEN_GOAL -Dsonar.host.url=$SONAR_HOST_URL -Dsonar.projectName=todp-one -Dsonar.branch=develop"
    }

    junit (
      allowEmptyResults: true, 
      testResults: '**/surefire-reports/**.xml'
    )
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

  stage("send mail") {
    def emailBody = '${SCRIPT, template="test.template"}'
    emailext (
      body: emailBody, 
      attachLog: true, 
      mimeType: 'text/html',
      replyTo: 'qianqi@chinatelecom.cn', 
      subject: "${env.JOB_NAME} (${params.BRANCH_TO_BUILD}) - Build #${env.BUILD_NUMBER} - ${currentBuild.result}!", 
      to: 'qianqi@chinatelecom.cn'
    )
  }
}




