node('Docker-5') {
	stage("Stage1 - 源码拉取和Artifactory配置") {
		echo "${params.Build_Branch}"
		echo "${params.Build_Config}"

		checkout([
			$class: 'GitSCM', 
			branches: [[name: "*/${params.Build_Branch}"]], 		
			userRemoteConfigs: [[url: 'git@10.142.78.33:DataPlatform/todp-one.git']],
			extensions: [[$class: 'CleanBeforeCheckout'], [$class: 'PruneStaleBranch']]
		])	


		def Build_Config = "${params.Build_Config}"

		def server = Artifactory.server "jfrog-artifactory-58"
		def rtMaven = Artifactory.newMavenBuild()
		rtMaven.tool = "MVN3"
		rtMaven.deployer releaseRepo: 'test-release', snapshotRepo: 'test-snapshot', server: server

		def buildInfo = Artifactory.newBuildInfo()

		echo "Build_Config: "+Build_Config

		rtMaven.run pom: 'pom.xml', goals: "clean package -Pdev -DskipTests", buildInfo: buildInfo

		server.publishBuildInfo buildInfo
	}
}