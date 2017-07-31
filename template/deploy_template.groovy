node("${env.Build_Node}") {

	stage("Stage1 - 拉取指定分支代码，进行Maven构建") {

		// checkout步骤是拉取GitLab的代码
		// GitLab代码仓库URL地址GitLab_URL和分支Deploy_Branch都是从构建参数中传入
		checkout([
			$class: 'GitSCM', 
			branches: [[name: "*/${params.Deploy_Branch}"]], 
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
			  sh 'mvn clean package -P${Build_Config} -DskipTests'
			} 
		} catch (err) {
			// 打印错误
			println err
			// 发送构建失败邮件通知
			emailext (
				body: """
				<p>拉取${params.Deploy_Branch}源码Maven构建打包失败<p>
				<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
				<p>请查看Pipeline页面的日志定位问题</a></p>
				""",
				to: "${env.Mail_List}",
				subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-Maven打包构建失败",
				attachLog: true
			)
		}	
	}

	stage("Stage2 - 把构建包传到部署的机器上") {

		// 这里try catch，防止传输文件到远端机器失败，发送邮件给相关人员进行通知
		try {
			// sshagent的参数是一个credential列表，可以多个也可以一个
	      	// 这里我们只在环境配置中配置了一个Server1的认证，添加到这里，可以登录到该远程Server1进行部署操作
			sshagent(["${env.Remote_Server1_Credential}"]) {
	          	// 这里的shell脚本部分主要是把Jenkins节点构建的可执行文件包传到远程部署机器上，然后测试下是否可以远程登录过去，并且查看文件是否传输成功
				sh '''
				scp ${Maven_Package_Path} ${Remote_Server1_Username}@${Remote_Server1_IP}:${Remote_Server1_CopyToPath}
			    ssh -t -t -o StrictHostKeyChecking=no ${Remote_Server1_Username}@${Remote_Server1_IP} \
			    cd ${Remote_Server1_CopyToPath}
			    ls -al
				'''
			}
		} catch (err) {
			// 打印错误
			println err
			// 发送构建失败邮件通知
			emailext (
				body: """
				<p>传输可执行文件${env.Maven_Package_Path}到远端机器${env.Remote_Server1_IP}的${env.Remote_Server1_CopyToPath}目录失败<p>
				<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
				<p>请查看Pipeline页面的日志定位问题</a></p>
				<p>查看是否是credential无效或者远端目录权限限制问题</p>
				""",
				to: "${env.Mail_List}",
				subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-传输可执行文件到远端机器失败",
				attachLog: true
			)
		}
    
      	// 打包和传输步骤都成功，则发送邮件通知构建打包和传输成功，并且把文件传输位置信息告知
		emailext (
			body: """
			<p>拉取${params.Deploy_Branch}分支进行了构建打包成功</p>
			<p>Maven构建的包已经传送到远端机器${env.Remote_Server1_IP}的${env.Remote_Server1_CopyToPath}目录下
			<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
			""",
			to: "${env.Mail_List}",
			subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-构建打包（没有部署）",
			attachLog: true
		)

	}

	//　根据是否进行部署的参数Is_Deploy来执行不同分支任务
	if ("${params.Is_Deploy}" == "yes") {
		stage("stage3 - 在远端机器进行部署操作") {
			// 这里的try-catch设定如果部署脚本失败，则发送邮件通知相关的人员
			try {
				// 如果部署涉及多台机器，这里可以添加多个Remote_Credential
				sshagent(["${env.Remote_Server1_Credential}"]) {
					// 这里的sh部分的shell脚本是部署的实际内容，这里首先登陆到远端机器
					// 然后可以执行具体的指令，或者调用远端机器上的部署脚本（注意设定的远端用户的权限）
					sh '''
				    ssh -t -t -o StrictHostKeyChecking=no ${Remote_Server1_Username}@${Remote_Server1_IP} \
				    cd ${Remote_Server1_CopyToPath} 
				    ls -al 
					'''
				}

				// 发送邮件给相关的人员，通知部署成功
				emailext (
					body: """
					<p>拉取${params.Deploy_Branch}分支在${env.Remote_Server1_IP}节点部署成功</p>
					<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
					""",
					to: "${env.Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-部署成功",
					attachLog: true
				)

			} catch (err) {
				// 打印错误
				println err
				// 发送邮件通知部署失败
				emailext (
					body: """
					<p>拉取${params.Deploy_Branch}分支在${env.Remote_Server1_IP}节点部署失败<p>
					<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
					<p>请查看Pipeline页面的日志定位问题</a></p>
					""",
					to: "${env.Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-部署失败",
					attachLog: true
				)
			}	
		}
	} else {
		stage("stage3 - 不进行部署") {
			emailext (
				body: """
				<p>只进行了构建打包，没有进行部署</p>
				<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
				""",
				to: "${env.Mail_List}",
				subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-构建打包（没有部署）",
				attachLog: true
			)
		}
	}
}