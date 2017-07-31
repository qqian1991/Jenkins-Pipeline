node("${env.Build_Node}") {

	stage("Stage1 - 拉取指定分支代码，进行Maven构建") {

		// checkout步骤是拉取GitLab的代码
		// GitLab代码仓库URL地址GitLab_URL和分支Deploy_Branch都是从构建参数中传入
		checkout([
			$class: 'GitSCM', 
			branches: [[name: "*/${params.BRANCH_TO_BUILD}"]], 
			extensions: [
				[$class: 'CleanBeforeCheckout'], 
				[$class: 'PruneStaleBranch']
			], 
			userRemoteConfigs: [
				[credentialsId: 'Gitlab-jenkins-account', url: "${params.GitLab_URL}"]
			]
		])

		// readMavenPom指定Maven构建使用的POM路径（相对于项目根目录的路径），从环境变量Pom_Path中读取
      	// 如果没有这一步骤，下面的withMaven会默认使用根目录下面的pom.xml
		readMavenPom (
			file: "${env.Pom_Path}"
		)

		// 这里try catch，防止maven构建失败，发送邮件给相关人员进行通知
		try {
			// withMaven会执行Maven构建指令以及在Jenkins任务页面生成Unit Test结果
			// 这里参数jdk和maven参数填写的是在Jenkins中配置的一个ID，不是具体的执行路径，在Jenkins中已经配置好，所以这里无需修改
	      	// 配置人员需要修改的是这里的sh部分的执行指令，根据情况来填写，Build_Config是从构建参数中传入
			withMaven(jdk: 'JDK1.8', maven: 'MVN3') {
			  sh 'mvn clean package ${SONAR_MAVEN_GOAL} -Dsonar.host.url=${Sonar_Host_Url} -Dsonar.branch=${BRANCH_TO_BUILD}'
			} 
		} catch (err) {
			// 打印错误
			println err
			// 发送构建失败邮件通知
			emailext (
				body: """
				<p>拉取${params.BRANCH_TO_BUILD}源码Maven构建打包失败<p>
				<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
				<p>请查看Pipeline页面的日志定位问题</a></p>
				""",
				to: "${env.Mail_List}",
				subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-Maven打包构建失败",
				attachLog: true
			)
		}

		def content = '${SCRIPT, template="trigger-sonar.template"}'
		emailext (
			body: '${SCRIPT, template="trigger-sonar.template"}',
			mimeType: 'text/html',
			to: "${env.Mail_List}",
			subject: "代码提交触发构建结果 ${module_name}(${BRANCH_TO_BUILD}) - Build #${BUILD_NUMBER} - ${BUILD_STATUS}!",
			attachLog: true
			)
	}
}