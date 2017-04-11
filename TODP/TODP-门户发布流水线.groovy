node('Docker-4') {

	stage("Stage1 - 拉取Feature分支进行开发") {
		Stage1()
	}

	stage('Stage2 - 开发部署开发环境（可多次执行步骤）') {
		Stage2()
	}

	stage('Stage3 - 开发完成并移交QA') {
		Stage3()
	}

	stage('Stage4 - 测试部署测试环境（可多次执行步骤）') {
		Stage4()
	}

	stage('Stage5 - QA验收测试完成并准备上线') {
		Stage5()	
	}

	stage('Stage6 - 最终上线版本冒烟测试') {
		Stage6()
	}

	stage("Stage7 - 生产环境测试") {
		Stage7()
	}

}


def Stage1() {
	if ("${params.Action}" ==~ /1-.*/) {
		waitUntil {
			try {
				// 发送邮件通知开发组长拉取feature分支
				emailext (
					body: """
					<p>请开发组长用个人账户登录Jenkins Pipeline页面，拉取feature分支用于功能开发</p>
					<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
					""",
					to: "${env.Dev_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-请开发组长拉取feanture分支",
					attachLog: true
				)

				// 是否进行拉取feature分支步骤，并且输入feature分支名称
                def input_map = input(
				message: '是否为该项目拉取feature分支?（仅开发组长有权限执行此步）',
				ok: "同意拉取feature分支",
				parameters: [
					string(defaultValue: 'feature-*', description: 'feature分支名称，格式以feature-开头', name: 'Feature_Branch_Name'),
				],
				submitter: "${env.Dev_Leader_User}",
				submitterParameter: 'Stage_Submitter'
				)
				env.Feature_Branch_Name = input_map["Feature_Branch_Name"]
				env.Stage_Submitter = input_map["Stage_Submitter"]

				// 把Feature_Branch_Name写入文件，用于其他Stage读取该变量					
				sh 'echo "${Feature_Branch_Name}" > ../Feature_Branch_Name.txt'

				// 拉取git源码
				checkout([
					$class: 'GitSCM', 
					branches: [[name: '*/develop']], 		
					userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "${env.GitLab_URL_SSH}"]],
					extensions: [[$class: 'WipeWorkspace']]
				])	

				// 执行shell脚本用于拉取feature分支
				sh '''
					#!/bin/bash
					set -ex
					echo "step1 - 创建feature分支"
					cd ${WORKSPACE}
					branch_result=`git branch -a`
					echo "branch_result: ${branch_result}"
					if [[ $branch_result =~ $Feature_Branch_Name ]]
					then
					  echo "该feature分支：${Feature_Branch_Name}已经存在"
					  exit 1
					else
					  git checkout -b ${Feature_Branch_Name} origin/develop
					  git push origin ${Feature_Branch_Name}
					  git branch --set-upstream-to=origin/${Feature_Branch_Name} ${Feature_Branch_Name}
					  git branch -a
					fi
				'''

				//shell执行成功，则发送邮件给开发列表，feature分支已经拉取，可以开始进行开发
				emailext (
					body: """
					<p>拉取Feature分支${env.Feature_Branch_Name}成功，进入开发和自测阶段</p>
					<p>拉取分支用户：${env.Stage_Submitter} </p>
					<p>请开发成员用个人账户登录Jenkins Pipeline页面，部署开发环境进行后续开发和部署</p>
					<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
					""",
					to: "${Dev_Mail_List},${PM_Mail_List},${QA_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-拉取Feature分支成功",
					attachLog: true
				)

				//回应waitUntil条件，如果true则不用循环执行，跳出该代码块
				true
			}
			// 如果try部分执行失败
			catch (err) {
				println err
				// 发送邮件拉取feature分支失败，需要开发或者QA检查原因，由开发组长重新拉取
				emailext (
					body: """
					<p>拉取Feature分支${env.Feature_Branch_Name}失败，可能原因：<p>
					<p>1. ${env.Feature_Branch_Name}分支已经存在，请检查，并且输入正确的Feature分支名称</p>
					<p>2. 所运行的Jenkins节点无法连接Gitlab项目或者SSH验证失败，请和QA组联系</p>
					<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
					<p>定位原因请参照详细日志:  <a href='${env.BUILD_URL}console'>${env.JOB_NAME} [${env.BUILD_NUMBER}] (consolelog)</a></p>
					<p>查明原因后请开发组长重新在Jenkins Pipeline视图继续拉取feature分支</p>
					""",
					to: "${env.Dev_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-拉取Feature分支失败",
					attachLog: true
				)

				//报错后提示，检查原因后，再重新执行改步骤
				input (
					message: "出错，查明原因后，再次尝试该步骤?"
				)
				false
			}
		}
	}
	// 如果起始步骤不是1，则跳过该步骤，此时Feature_Branch_Name需要手动在jenkins的环境变量处设置
	else {
		echo "Skip Stage1"
		sh 'echo "${Feature_Exist}" > ../Feature_Branch_Name.txt'
	}
}


def Stage2() {
	if ("${params.Action}" ==~ /1-.*|2-.*/) {
		waitUntil {
			try {
				// 读取之前写文件的变量
				def Feature_Branch_Name = readFile '../Feature_Branch_Name.txt'
				env.Feature_Branch_Name = Feature_Branch_Name.trim()

				//询问开发开发是否完成
                def input_map_1 = input(
				message: '开发及自测是否完成?（No继续开发部署，Yes确认完成）（相关开发有权限执行此步）',
				ok: "下一步",
				submitter: "${env.Dev_User_List}",
				submitterParameter: 'Stage_Submitter_1',
				parameters: [
					choice(choices: "No\nYes\n", description: '开发是否完成，没完成请选择No!', name: 'Dev_Completion')
				],
				)
				env.Dev_Completion = input_map_1["Dev_Completion"]
				env.Stage_Submitter_1 = input_map_1["Stage_Submitter_1"]

				// 如果开发没有完成
				if (!"${env.Dev_Completion}".contains("Yes")) {

					//询问开发是否需要执行部署步骤
	                def input_map_2 = input(
					message: '是否开始部署开发环境?（相关开发有权限执行此步）',
					ok: "同意进行开发环境部署",
					parameters: [
						string(defaultValue: 'DP-40', description: '部署环境节点', name: 'Dev_Env'),
						string(defaultValue: '/home/op/hjt', description: '部署路径', name: 'Dev_Deploy_Path'),
					],
					submitter: "${env.Dev_User_List}",
					submitterParameter: 'Stage_Submitter_2'
					)

					env.Dev_Env = input_map_2["Dev_Env"]
					env.Stage_Submitter_2 = input_map_2["Stage_Submitter_2"]
					env.Dev_Deploy_Path = input_map_2["Dev_Deploy_Path"]

					// 如果执行部署，则调用部署子任务，把需要的参数传入
					def dev_build_step = build (
						job: 'TODP-Web门户-源码构建部署-V2', 
						parameters: [
						string(name: 'Build_Config', value: "dev"), 
						string(name: 'Operation_Web_Deploy', value: 'yes'),
						string(name: 'Operation_Web_Build_Branch', value: "origin/${env.Feature_Branch_Name}"), 
						string(name: 'Home_Web_Deploy', value: 'yes'),
						string(name: 'Home_Web_Build_Branch', value: "origin/${env.Feature_Branch_Name}"),
						string(name: 'Auth_Deploy', value: 'no'),
						string(name: 'Auth_Build_Branch', value: 'develop'),
						string(name: 'Web_Deploy_Path', value: "${env.Dev_Deploy_Path}"), 
						string(name: 'Web_Deploy_Node', value: "${env.Dev_Env}")
						], 
						quietPeriod: 3,
						propagate: false
					)

					env.dev_build_result = dev_build_step.result
					if ("${env.dev_build_result}".contains("FAILURE")) {
						error "开发环境部署失败"
					}else{
						echo "开发环境部署任务成功"
					}

					// 邮件通知其他开发，开发环境刚刚被部署过，并且邮件说明部署人
					emailext (
						body: """
						<p>最新的开发环境部署成功</p>
						<p>开发环境部署触发用户： ${env.Stage_Submitter_2}</p>
						<p>如后续需要重复部署，用个人账户登录Jenkins Pipeline视图再次触发该步骤</p>
						<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>

						""",
						to: "${env.Dev_Mail_List}",
						subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-最新的开发环境部署成功",
						attachLog: true
					)
					// 因为开发没有完成，设置为false，
					false
				}
				else {
					// 邮件提示开发PM确认开发完成
					emailext (
						body: """
						<p>开发阶段完成，由PM进行确认是否完成，才可进行后续分支操作</p>
						<p>请PM用个人账户登录Jenkins Pipeline页面按照提示执行步骤</p>
						<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
						""",
						to: "${Dev_Mail_List},${PM_Mail_List},${QA_Mail_List}",
						subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}任务-开发阶段完成，等待PM进行确认",
						attachLog: true
					)

					//PM确认开发完成步骤
	                env.Dev_Confirm = input(
					message: 'PM确认开发阶段是否完成?（仅PM有权限执行此步）',
					ok: "下一步",
					submitter: "${env.PM_Leader_User}",
					parameters: [
					choice(choices: "Yes\nNo\n", description: 'PM确认开发是否完成，没完成请选择No，返回开发部署和自测流程!', name: 'Dev_Confirm')
					],
					)

					if (!"${env.Dev_Confirm}".contains("Yes")) {
						//如果PM不通过，退回开发继续开发部署
						emailext (
							body: """
							<p>PM不通过开发完成，请相关开发重新开发部署和自测<p>
							<p>需要重新部署在Jenkins Pipeline视图继续执行步骤 <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}${env.JOB_NAME} (pipeline)</a> ！！！</p>
							""",
							to: "${env.Dev_Mail_List}",
							subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-PM不通过开发完成，请相关开发重新开发部署和自测",
							attachLog: true
						)
						false
					}
					else {
						emailext (
							body: """
							<p>PM已经确认开发阶段完成，请开发组长执行如下分支操作：</p>
							<p> 1)合并feature分支（如有冲突，请手动解决冲突后再运行）</p>
							<p> 2)删除feature分支</p>
							<p> 3)拉取release分支以及移交QA</p>
							<p>请开发组长用个人账户登录Jenkins Pipeline页面按照提示执行步骤</p>
							<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
							""",
							to: "${Dev_Mail_List},${PM_Mail_List},${QA_Mail_List}",
							subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}任务-开发阶段完成，等待开发组长进行分支操作",
							attachLog: true
						)
						true
					}
				}
			}
			catch (err) {
				println err
				emailext (
					body: """
					<p>开发环境部署失败，可能原因：<p>
					<p> 1)构建失败</p>
					<p> 2)服务启动失败</p>
					<p>定位原因请参照详细日志:  <a href='${env.BUILD_URL}console'>${env.JOB_NAME} [${env.BUILD_NUMBER}] (consolelog)</a></p>
					<p>查明原因后请相关人员重新在Jenkins Pipeline页面继续重新部署 <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}${env.JOB_NAME} (pipeline)</a> ！！！</p>
					""",
					to: "${env.Dev_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-开发环境部署失败",
					attachLog: true
				)

				input (
					message: "出错，查明原因后，再次尝试该步骤?"
				)
				false

			}
		}
	}
	else {
		echo "Skip Stage2"
	}
}


def Stage3() {
	if ("${params.Action}" ==~ /1-.*|2-.*|3-.*/) {
		def Feature_Branch_Name = readFile '../Feature_Branch_Name.txt'
		env.Feature_Branch_Name = Feature_Branch_Name.trim()
			
		waitUntil {
			try {
				env.Stage_Submitter = input(
				message: "是否将${env.Feature_Branch_Name}分支合并到develop分支?（仅开发组长有权限执行此步）",
				ok: "同意进行分支合并",
				submitter: "${env.Dev_Leader_User}",
				submitterParameter: 'Stage_Submitter'
				)

				checkout([
					$class: 'GitSCM', 
					branches: [[name: '*/develop']], 		
					userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "${env.GitLab_URL_SSH}"]],
					extensions: [[$class: 'WipeWorkspace']]
				])

				sh '''
				#!/bin/bash
				set -ex
				echo "合并feature分支到develop分支"
				cd ${WORKSPACE}
				git checkout -b develop origin/develop
				git pull origin develop
				git merge --no-ff origin/${Feature_Branch_Name}
				git push origin develop
				'''
				sleep 3

				emailext (
					body: """
					<p>合并feature分支${env.Feature_Branch_Name}到develop分支成功</p>
					<p>代码合并触发用户：${env.Stage_Submitter}</p>
					""",
					to: "${Dev_Mail_List},${PM_Mail_List},${QA_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}任务-合并${env.Feature_Branch_Name}分支到develop分支成功",
					attachLog: true
				)

				true

			}
			catch (err) {
				println err

				emailext (
					body: """
					<p>合并feature分支${env.Feature_Branch_Name}到develop分支失败</p>
					<p>可能合并发生冲突，请开发本地手动执行分支合并</p>
					<p>冲突详情可以查看错误步骤日志： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}步骤日志</a></p>
					<p>手动解决冲突的指令步骤：</p>
					<p>git checkout develop</p>
					<p>git pull origin develop</p>
					<p>git merge --no-ff origin/${env.Feature_Branch_Name}</p>
					<p>git status （查看冲突文件，并且vim手动修改）</p>
					<p>git add 冲突文件</p>
					<p>git commit -m 'fix merge conflicts when merge ${env.Feature_Branch_Name} branch into develop branch'</p>
					<p>git push origin develop</p>
					<p>如果有其他习惯用于解决合并冲突的GUI工具也可以使用自己的方法，上述指令步骤供参考</p>
					<p>解决冲突后请开发组长重新在Jenkins Pipeline视图继续执行合并步骤 <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}${env.JOB_NAME} (pipeline)</a> ！！！</p>
					""",
					to: "${env.Dev_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-合并${env.Feature_Branch_Name}分支到develop失败",
					attachLog: true
				)

				input (
					message: "出错，查明原因后，再次尝试该步骤?"
				)
				false

			}
		}

		waitUntil {
			try {
				def input_map_0 = input(
				message: "合并成功，是否删除该${env.Feature_Branch_Name}分支?（仅开发组长有权限执行此步）",
				ok: "继续",
				parameters: [
				choice(choices: "Yes\nNo\n", description: "是否删除${env.Feature_Branch_Name}分支!（如确认合并成功，建议删除）", name: 'Feature_Branch_Delete')
				],
				submitter: "${env.Dev_Leader_User}",
				submitterParameter: 'Stage_Submitter'
				)

				env.Feature_Branch_Delete = input_map_0["Feature_Branch_Delete"]	
				env.Stage_Submitter = input_map_0["Stage_Submitter"]

				if ("${env.Feature_Branch_Delete}".contains("Yes")) {
					checkout([
						$class: 'GitSCM', 
						branches: [[name: '*/develop']], 		
						userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "${env.GitLab_URL_SSH}"]],
						extensions: [[$class: 'WipeWorkspace']]
					])

					sh '''
					#!/bin/bash
					set -ex
					echo "删除成功合并的feature分支"
					cd ${WORKSPACE}
					git push origin --delete ${Feature_Branch_Name}
					'''
					sleep 3

					emailext (
						body: """
						<p>删除feature分支${env.Feature_Branch_Name}成功</p>
						<p>代码删除触发用户：${env.Stage_Submitter}</p>
						""",
						to: "${Dev_Mail_List},${PM_Mail_List},${QA_Mail_List}",
						subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-删除${env.Feature_Branch_Name}分支成功",
						attachLog: true
					)
				}			
				true
			}
			catch (err) {
				println err

				emailext (
					body: """
					<p>删除feature分支${env.Feature_Branch_Name}失败</p>
					<p>可能和Gitlab的连接失败或者执行脚本错误</p>
					<p>请Jenkins负责人进行检查</p>
					<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
					<p>查明原因后请开发组长重新在Jenkins Pipeline视图执行分支删除步骤！</p>
					""",
					to: "${env.Dev_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-删除feature分支${env.Feature_Branch_Name}失败",
					attachLog: true
				)

				input (
					message: "出错，查明原因后，再次尝试该步骤?"
				)
				false
			}
		}

		waitUntil {
			try {
                def input_map = input(
				message: '是否为该项目拉取release分支?（仅开发组长有权限执行此步）',
				ok: "同意拉取release分支",
				parameters: [
					string(defaultValue: 'release-*', description: 'release分支名称，格式以release-开头', name: 'Release_Branch_Name'),
				],
				submitter: "${env.Dev_Leader_User}",
				submitterParameter: 'Stage_Submitter'
				)
				env.Release_Branch_Name = input_map["Release_Branch_Name"]	
				env.Stage_Submitter = input_map["Stage_Submitter"]

				sh 'echo "${Release_Branch_Name}" > ../Release_Branch_Name.txt'

				checkout([
					$class: 'GitSCM', 
					branches: [[name: '*/develop']], 		
					userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "${env.GitLab_URL_SSH}"]],
					extensions: [[$class: 'WipeWorkspace']]
				])

				sh '''
				#!/bin/bash
				set -ex
				echo "step3 - 从develop分支拉取release分支"
				cd ${WORKSPACE}
				branch_result=`git branch -a`
				echo "branch_result: ${branch_result}"
				if [[ $branch_result =~ $Release_Branch_Name ]]
				then
					echo "该release分支：${Release_Branch_Name}已经存在"
				    exit 1
				else
					git checkout -b ${Release_Branch_Name} origin/develop
					git push origin ${Release_Branch_Name}
					git branch --set-upstream-to=origin/${Release_Branch_Name} ${Release_Branch_Name}
					git branch -a
				fi
				'''
				sleep 3

				emailext (
					body: """
					<p>开发完成，交付测试，并且成功拉取${env.Release_Branch_Name}分支，请QA使用该分支进行验收测试</p>
					<p>请测试成员用个人账户登录Jenkins Pipeline页面执行测试环境部署步骤</p>
					<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
					<p>Release分支拉取用户：${env.Stage_Submitter} </p>
					""",
					to: "${env.Dev_Mail_List},${env.QA_Mail_List},${env.PM_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-开发完成，拉取${env.Release_Branch_Name}分支，并交付QA测试",
					attachLog: true
				)

				true

			}
			catch (err) {
				println err
				emailext (
					body: """
					<p>拉取Release分支${env.Release_Branch_Name}失败，可能原因：<p>
					<p>1. ${env.Release_Branch_Name}分支已经存在，请检查，并且输入正确的Release分支名称</p>
					<p>2. 所运行的Jenkins节点无法连接Gitlab项目或者SSH验证失败，请和QA组联系</p>
					<p>定位原因请参照详细日志:  <a href='${env.BUILD_URL}console'>${env.JOB_NAME} [${env.BUILD_NUMBER}] (consolelog)</a></p>
					<p>查明原因后请开发组长重新在Jenkins Pipeline页面重新拉取release分支 <a href='${env.JOB_URL}workflow-stage'>${env.JOB_NAME} (pipeline)</a> ！</p>
					""",
					to: "${env.Dev_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-拉取${env.Release_Branch_Name}分支失败",
					attachLog: true
				)

				input (
					message: "出错，查明原因后，再次尝试该步骤?"
				)
				false

			}
		}
	}
	else {
		echo "Skip Stage3"
		sh 'echo "${Release_Exist}" > ../Release_Branch_Name.txt'
	}
}

def Stage4() {
	if ("${params.Action}" ==~ /1-.*|2-.*|3-.*|4-.*/) {
		waitUntil {
			try {
				// 读取之前写文件的变量
				def Release_Branch_Name = readFile '../Release_Branch_Name.txt'
				env.Release_Branch_Name = Release_Branch_Name.trim()

				//询问测试验收测试是否完成
                def input_map_1 = input(
				message: '验收测试是否完成?（No继续部署测试，Yes确认完成）（QA有权限执行此步）',
				ok: "下一步",
				submitter: "${env.QA_User_List}",
				submitterParameter: 'Stage_Submitter_1',
				parameters: [
					choice(choices: "No\nYes\n", description: '验收测试是否完成，没完成请选择No!', name: 'QA_Completion')
				],
				)
				env.QA_Completion = input_map_1["QA_Completion"]
				env.Stage_Submitter_1 = input_map_1["Stage_Submitter_1"]

				// 如果验收测试没有完成
				if (!"${env.QA_Completion}".contains("Yes")) {

					//询问测试是否需要执行部署步骤
	                def input_map_2 = input(
					message: '是否开始部署测试环境?（QA有权限执行此步）',
					ok: "同意进行测试环境部署",
					parameters: [
						string(defaultValue: 'QA-54', description: '部署环境', name: 'QA_Env'),
						string(defaultValue: '/usr/bdusr01/qa', description: '部署路径', name: 'QA_Deploy_Path'),
					],
					submitter: "${env.QA_User_List}",
					submitterParameter: 'Stage_Submitter_2'
					)

					env.QA_Env = input_map_2["QA_Env"]
					env.Stage_Submitter_2 = input_map_2["Stage_Submitter_2"]
					env.QA_Deploy_Path = input_map_2["QA_Deploy_Path"]

					// 如果执行部署，则调用部署子任务，把需要的参数传入
					def test_build_step = build (
						job: 'TODP-Web门户-源码构建部署-V2', 
						parameters: [
						string(name: 'Build_Config', value: "test"), 
						string(name: 'Operation_Web_Deploy', value: 'yes'),
						string(name: 'Operation_Web_Build_Branch', value: "origin/${env.Release_Branch_Name}"), 
						string(name: 'Home_Web_Deploy', value: 'yes'),
						string(name: 'Home_Web_Build_Branch', value: "origin/${env.Release_Branch_Name}"),
						string(name: 'Auth_Deploy', value: 'no'),
						string(name: 'Auth_Build_Branch', value: 'develop'),
						string(name: 'Web_Deploy_Path', value: "${env.QA_Deploy_Path}"), 
						string(name: 'Web_Deploy_Node', value: "${env.QA_Env}")
						], 
						quietPeriod: 3,
						propagate: false
					)

					env.test_build_result = test_build_step.result
					if ("${env.test_build_result}".contains("FAILURE")) {
						error "测试环境部署失败"
					}else{
						echo "测试环境部署任务成功"
					}

					// 邮件通知其他测试，测试环境刚刚被部署过，并且邮件说明部署人
					emailext (
						body: """
						<p>最新的测试环境部署成功</p>
						<p>测试环境部署触发用户： ${env.Stage_Submitter_2}</p>
						<p>如需后续重复部署，用个人账户登录Jenkins Pipeline视图再次触发该步骤</p>
						<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>

						""",
						to: "${env.QA_Mail_List}",
						subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-最新的测试环境部署成功",
						attachLog: true
					)
					// 因为验收测试没有完成，设置为false，
					false
				}
				else {
					emailext (
						body: """
						<p>测试完成验收测试，请测试组长进行确认</p>
						<p>请用个人账户登录Jenkins Pipeline页面，确认验收测试阶段完成</p>
						<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
						""",
						to: "${env.QA_Mail_List}",
						subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-请确认验收测试完成",
						attachLog: true
					)

	                env.QA_Confirm = input(
					message: '组长确认验收测试是否完成?（仅测试组长有权限执行此步）',
					ok: "下一阶段",
					submitter: "${env.QA_Leader_User}",
					parameters: [
					choice(choices: "Yes\nNo\n", description: '测试组长确认验收是否完成，没完成请选择No，继续进行验收测试!', name: 'QA_Confirm')
					],
					)

					if (!"${env.QA_Confirm}".contains("Yes")) {
						emailext (
							body: """
							<p>测试组长不通过验收测试完成，请相关测试重新开发部署和自测<p>
							<p>需要重新部署在Jenkins Pipeline页面继续执行 <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}${env.JOB_NAME} (pipeline)</a> ！！！</p>
							""",
							to: "${env.QA_Mail_List}",
							subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-组长不通过验收测试完成，请相关测试重新进行验收测试",
							attachLog: true
						)
						false
					}
					else {
						emailext (
							body: """
							<p>QA验收测试已经完成，请开发组长对分支进行相应操作，用来准备上线</p>
							<p>请开发组长用个人账户登录Jenkins Pipeline页面，然后按照提示执行如下步骤：</p>
							<p> 1)合并release分支到develop分支（如有冲突，请手动解决冲突后再运行）</p>
							<p> 2)合并release分支到master分支（如有冲突，请手动解决冲突后再运行）</p>
							<p> 3)合并成功后，删除release分支</p>
							<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
							""",
							to: "${env.Dev_Mail_List},${env.QA_Mail_List},${env.PM_Mail_List}",
							subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-QA验收测试完成，请开发组长执行上线准备步骤",
							attachLog: true
						)
						true
					}
				}
			}
			catch (err) {
				println err
				emailext (
					body: """
					<p>测试环境部署失败，可能原因：<p>
					<p> 1)构建失败</p>
					<p> 2)服务启动失败</p>
					<p>定位原因请参照详细日志:  <a href='${env.BUILD_URL}console'>${env.JOB_NAME} [${env.BUILD_NUMBER}] (consolelog)</a></p>
					<p>查明原因后请相关测试人员重新在Jenkins Pipeline页面继续重新部署 <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}${env.JOB_NAME} (pipeline)</a> ！！！</p>
					""",
					to: "${env.QA_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-测试环境部署失败",
					attachLog: true
				)	

				input (
					message: "出错，查明原因后，再次尝试该步骤?"
				)
				false

			}
		}
	}
	else {
		echo "Skip Stage4"
	}
}

def Stage5() {
	if ("${params.Action}" ==~ /1-.*|2-.*|3-.*|4-.*|5-.*/) {
		def Release_Branch_Name = readFile '../Release_Branch_Name.txt'
		env.Release_Branch_Name = Release_Branch_Name.trim()

		waitUntil {
			try {
				env.Stage_Submitter = input(
				message: "是否将${env.Release_Branch_Name}分支合并到develop分支?（仅开发组长有权限执行此步）",
				ok: "同意进行分支合并",
				submitter: "${env.Dev_Leader_User}",
				submitterParameter: 'Stage_Submitter'
				)

				checkout([
					$class: 'GitSCM', 
					branches: [[name: '*/develop']], 		
					userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "${env.GitLab_URL_SSH}"]],
					extensions: [[$class: 'WipeWorkspace']]
				])

				sh '''
				#!/bin/bash
				set -ex
				echo "合并release分支到develop分支"
				cd ${WORKSPACE}
				git checkout -b develop origin/develop
				git pull origin develop
				git merge --no-ff origin/${Release_Branch_Name}
				git push origin develop
				'''
				sleep 3

				true
			}
			catch (err) {
				println err
				emailext (
					body: """
					<p>合并release分支${env.Release_Branch_Name}到develop分支失败</p>
					<p>可能合并发生冲突，请开发本地手动执行分支合并</p>
					<p>冲突详情可以查看错误步骤日志： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}步骤日志</a></p>
					<p>手动解决冲突的指令步骤：</p>
					<p>git checkout develop</p>
					<p>git pull origin develop</p>
					<p>git merge --no-ff origin/${env.Release_Branch_Name}</p>
					<p>git status （查看冲突文件，并且vim手动修改）</p>
					<p>git add 冲突文件</p>
					<p>git commit -m 'fix merge conflicts when merge ${env.Release_Branch_Name} branch into develop branch'</p>
					<p>git push origin develop</p>
					<p>如果有其他习惯用于解决合并冲突的GUI工具也可以使用自己的方法，上述指令步骤供参考</p>
					<p>解决冲突后请开发组长重新在Jenkins Pipeline视图继续执行合并步骤 <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}${env.JOB_NAME} (pipeline)</a> ！！！</p>
					""",
					to: "${env.Dev_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}任务-合并${env.Release_Branch_Name}分支到develop分支失败",
					attachLog: true
				)

				input (
					message: "出错，查明原因后，再次尝试该步骤?"
				)
				false
			}
		}

		waitUntil {
			try {
				env.Stage_Submitter = input(
				message: "是否将${env.Release_Branch_Name}分支合并到master分支?（仅开发组长有权限执行此步）",
				ok: "同意进行分支合并",
				submitter: "${env.Dev_Leader_User}",
				submitterParameter: 'Stage_Submitter'
				)

				checkout([
					$class: 'GitSCM', 
					branches: [[name: '*/develop']], 		
					userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "${env.GitLab_URL_SSH}"]],
					extensions: [[$class: 'WipeWorkspace']]
				])

				sh '''
				echo "合并release分支到master分支"
				cd ${WORKSPACE}
				git checkout -b master origin/master
				git pull origin master
				git merge --no-ff origin/${Release_Branch_Name}
				git push origin master
				'''
				sleep 3

				emailext (
					body: """
					<p>合并release分支${env.Release_Branch_Name}到develop分支和master分支成功</p>
					<p>代码合并触发用户：${env.Stage_Submitter}</p>
					""",
					to: "${env.Dev_Mail_List},${env.QA_Mail_List},${env.PM_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-合并${env.Release_Branch_Name}分支到develop分支和master分支成功",
					attachLog: true
				)
				true
			}
			catch (err) {
				println err
				emailext (
					body: """
					<p>合并release分支${env.Release_Branch_Name}到master分支失败</p>
					<p>可能合并发生冲突，请开发本地手动执行分支合并</p>
					<p>冲突详情可以查看错误步骤日志： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}步骤日志</a></p>
					<p>手动解决冲突的指令步骤：</p>
					<p>git checkout master</p>
					<p>git pull origin master</p>
					<p>git merge --no-ff origin/${env.Release_Branch_Name}</p>
					<p>git status （查看冲突文件，并且vim手动修改）</p>
					<p>git add 冲突文件</p>
					<p>git commit -m 'fix merge conflicts when merge ${env.Release_Branch_Name} branch into master branch'</p>
					<p>git push origin develop</p>
					<p>如果有其他习惯用于解决合并冲突的GUI工具也可以使用自己的方法，上述指令步骤供参考</p>
					<p>解决冲突后请开发组长重新在Jenkins Pipeline视图继续执行合并步骤 <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}${env.JOB_NAME} (pipeline)</a> ！！！</p>
					""",
					to: "${env.Dev_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}任务-合并${env.Release_Branch_Name}分支到master分支失败",
					attachLog: true
				)

				input (
					message: "出错，查明原因后，再次尝试该步骤?"
				)
				false
			}
		}

		waitUntil {
			try {

                def input_map = input(
				message: "${env.Release_Branch_Name}已合并到master分支，请打上大版本tag?（仅开发组长有权限执行此步）",
				ok: "同意给master分支打tag",
				parameters: [
					string(defaultValue: 'v*.0.0', description: 'tag用于标记上线的大版本，类似v1.0.0的格式', name: 'Major_Tag_Name'),
				],
				submitter: "${env.Dev_Leader_User}",
				submitterParameter: 'Stage_Submitter'
				)
				env.Major_Tag_Name = input_map["Major_Tag_Name"]	
				env.Stage_Submitter = input_map["Stage_Submitter"]

				sh 'echo "${Major_Tag_Name}" > ../Major_Tag_Name.txt'

				checkout([
					$class: 'GitSCM', 
					branches: [[name: '*/develop']], 		
					userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "${env.GitLab_URL_SSH}"]],
					extensions: [[$class: 'WipeWorkspace']]
				])

				sh '''
				echo "在master分支打大版本tag"
				cd ${WORKSPACE}
				git checkout master
				git pull origin master
				git tag -a ${Major_Tag_Name} -m "Add major tag in master branch for product launch"
				git push origin ${Major_Tag_Name} 
				git show ${Major_Tag_Name} 
				'''
				sleep 3

				emailext (
					body: """
					<p>在master分支打tag - ${env.Major_Tag_Name}成功</p>
					<p>添加tag用户：${env.Stage_Submitter}</p>
					""",
					to: "${env.Dev_Mail_List},${env.QA_Mail_List},${env.PM_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-在master分支打tag - ${env.Major_Tag_Name}成功",
					attachLog: true
				)
				true
			}
			catch (err) {
				println err
				emailext (
					body: """
					<p>在master分支打tag - ${env.Major_Tag_Name}失败，可能原因</p>
					<p>1. ${env.Major_Tag_Name}标签已经存在，请检查，并且输入正确的tag标签名称</p>
					<p>2. 所运行的Jenkins节点无法连接Gitlab项目或者SSH验证失败，请和QA组联系</p>
					<p>定位原因请参照详细日志:  <a href='${env.BUILD_URL}console'>${env.JOB_NAME} [${env.BUILD_NUMBER}] (consolelog)</a></p>
					<p>查明原因后请开发组长重新在Jenkins Pipeline页面重新打tag <a href='${env.JOB_URL}workflow-stage'>${env.JOB_NAME} (pipeline)</a> ！</p>
					""",
					to: "${env.Dev_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}任务-在master分支打tag - ${env.Major_Tag_Name}失败",
					attachLog: true
				)

				input (
					message: "出错，查明原因后，再次尝试该步骤?"
				)
				false
			}
		}

		waitUntil {
			try {
				def input_map = input(
				message: "合并成功，是否删除该${env.Release_Branch_Name}分支?（仅开发组长有权限执行此步）",
				ok: "继续",
				parameters: [
				choice(choices: "Yes\nNo\n", description: "是否删除${env.Release_Branch_Name}分支!（如确认合并成功，建议删除）", name: 'Release_Branch_Delete')
				],
				submitter: "${env.Dev_Leader_User}",
				submitterParameter: 'Stage_Submitter'
				)

				env.Release_Branch_Delete = input_map["Release_Branch_Delete"]	
				env.Stage_Submitter = input_map["Stage_Submitter"]

				if ("${env.Release_Branch_Delete}".contains("Yes")) {
					checkout([
						$class: 'GitSCM', 
						branches: [[name: '*/develop']], 		
						userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "${env.GitLab_URL_SSH}"]],
						extensions: [[$class: 'WipeWorkspace']]
					])

					sh '''
					#!/bin/bash
					set -ex
					echo "删除成功合并的release分支"
					cd ${WORKSPACE}
					git push origin --delete ${Release_Branch_Name}
					'''
					sleep 3

					emailext (
						body: """
						<p>删除release分支${env.Release_Branch_Name}成功</p>
						<p>代码删除用户：${env.Stage_Submitter}</p>
						""",
						to: "${env.Dev_Mail_List},${env.QA_Mail_List},${env.PM_Mail_List}",
						subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-删除${env.Release_Branch_Name}分支成功",
						attachLog: true
					)
				}			
				true
			}
			catch (err) {
				println err
				emailext (
					body: """
					<p>删除release分支${env.Release_Branch_Name}失败</p>
					<p>可能和Gitlab的连接失败或者执行脚本错误</p>
					<p>请Jenkins负责人进行检查</p>
					<p>相关步骤日志请参考 <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}步骤日志</a></p>
					<p>查明原因后开发组长重新在Jenkins Pipeline页面执行删除分支步骤 <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}${env.JOB_NAME} (pipeline)</a> ！！！</p>
					""",
					to: "${env.Dev_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-删除feature分支${env.Release_Branch_Name}失败",
					attachLog: true
				)

				input (
					message: "出错，查明原因后，再次尝试该步骤?"
				)
				false

			}
		}
	}
	else {
		echo "Skip Stage5"
	}
}

def Stage6() {
	if ("${params.Action}" ==~ /1-.*|2-.*|3-.*|4-.*|5-.*|6-.*/) {
		waitUntil {
			try {
				//询问测试是否需要执行部署步骤
                def input_map = input(
				message: '是否使用master分支部署用于冒烟测试的环境?（QA有权限执行此步）',
				ok: "同意进行测试环境部署",
				parameters: [
					string(defaultValue: 'QA-54', description: '部署环境', name: 'QA_Env'),
					string(defaultValue: '/usr/bdusr01/qa', description: '部署路径', name: 'QA_Deploy_Path'),
				],
				submitter: "${env.QA_User_List}",
				submitterParameter: 'Stage_Submitter'
				)

				env.QA_Env = input_map["QA_Env"]
				env.Stage_Submitter = input_map["Stage_Submitter"]
				env.QA_Deploy_Path = input_map["QA_Deploy_Path"]

				// 如果执行部署，则调用部署子任务，把需要的参数传入
				def smoke_build_step = build (
					job: 'TODP-Web门户-源码构建部署-V2', 
					parameters: [
					string(name: 'Build_Config', value: "test"), 
					string(name: 'Operation_Web_Deploy', value: 'yes'),
					string(name: 'Operation_Web_Build_Branch', value: "origin/master"), 
					string(name: 'Home_Web_Deploy', value: 'yes'),
					string(name: 'Home_Web_Build_Branch', value: "origin/master"),
					string(name: 'Auth_Deploy', value: 'no'),
					string(name: 'Auth_Build_Branch', value: 'develop'),
					string(name: 'Web_Deploy_Path', value: "${env.QA_Deploy_Path}"), 
					string(name: 'Web_Deploy_Node', value: "${env.QA_Env}")
					], 
					quietPeriod: 3,
					propagate: false
				)

				env.smoke_build_result = smoke_build_step.result
				if ("${env.smoke_build_result}".contains("FAILURE")) {
					error "冒烟测试环境部署失败"
				}else{
					echo "冒烟测试环境部署任务成功"
				}

				// 邮件通知其他测试，测试环境刚刚被部署过，并且邮件说明部署人
				emailext (
					body: """
					<p>拉取master分支部署测试环境成功，请测试在该部署环境上进行上线前的冒烟测试</p>
					<p>测试环境部署用户： ${env.Stage_Submitter}</p>
					<p>测试完成后，请用个人账户登录Jenkins Pipeline页面确认冒烟测试完成</p>
					<p>Jenkins Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}</a></p>
					""",
					to: "${env.QA_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-拉取master分支部署测试环境用于冒烟测试",
					attachLog: true
				)

                input(
				message: '冒烟测试是否完成?（QA有权限执行此步）',
				ok: "完成",
				submitter: "${env.QA_User_List}"
				)

                env.QA_Confirm = input(
				message: '测试组长确认冒烟测试是否完成?（仅测试组长有权限执行此步）',
				ok: "完成，可以用于上线",
				submitter: "${env.QA_Leader_User}",
				parameters: [
				choice(choices: "Yes\nNo\n", description: '测试组长确认开发是否完成，没完成请选择No，测试继续进行冒烟测试!', name: 'QA_Confirm')
				],
				)

				emailext (
					body: """
					<p>请测试组长用个人账户登录Jenkins Pipeline页面确认冒烟测试完成，可以进行上线</p>
					<p>页面链接请点击： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}</a></p>
					""",
					to: "${env.QA_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}任务-请确认冒烟测试完成",
					attachLog: true
				)

				if (!"${env.QA_Confirm}".contains("Yes")) {
					emailext (
						body: """
						<p>测试组长不通过冒烟测试完成，请相关测试重新进行冒烟测试<p>
						<p>需要重新部署在Jenkins Pipeline视图继续执行步骤 <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}${env.JOB_NAME} (pipeline)</a> ！！！</p>
						""",
						to: "${env.QA_Mail_List}",
						subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-测试组长不通过冒烟测试完成，请相关测试重新进行冒烟测试",
						attachLog: true
					)
					false
				}
				else {
					emailext (
						body: """
						<p>QA冒烟测试完成，该版本可用于上线</p>
						<p>Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}(pipeline page)</a></p>
						""",
						to: "${env.Dev_Mail_List},${env.QA_Mail_List},${env.PM_Mail_List}",
						subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-QA冒烟测试完成，该版本可用于上线",
						attachLog: true
					)
					true
				}
			}
			catch (err) {
				println err
				emailext (
					body: """
					<p>拉取master分支部署冒烟测试环境失败，可能原因：<p>
					<p> 1)构建失败</p>
					<p> 2)服务启动失败</p>
					<p>定位原因请参照详细日志:  <a href='${env.BUILD_URL}console'>${env.JOB_NAME} [${env.BUILD_NUMBER}] (consolelog)</a></p>
					<p>查明原因后请相关人员重新在Jenkins Pipeline视图继续执行步骤 <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}${env.JOB_NAME} (pipeline)</a> ！！！</p>
					""",
					to: "${env.QA_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-拉取master分支部署冒烟测试环境失败",
					attachLog: true
				)

				input (
					message: "出错，查明原因后，再次尝试该步骤?"
				)
				false
			}
		}
	}
	else {
		echo "Skip Stage6" 
	}
}

def Stage7() {
	if ("${params.Action}" ==~ /1-.*|2-.*|3-.*|4-.*|5-.*|6-.*|7-.*/) {
		waitUntil {
			try {
				//询问开发组长是否可以在生产环境拉取源码构建可执行包
                def input_map = input(
				message: '是否使用master分支在生产环境编译打包?（开发组长有权限执行此步）',
				ok: "同意在生产环境编译打包",
				parameters: [
					string(defaultValue: '/home/hue/QA', description: '生产环境可执行包的生成位置', name: 'Package_Path')
				],
				submitter: "${env.Dev_Leader_User}",
				submitterParameter: 'Stage_Submitter'
				)

				env.Stage_Submitter = input_map["Stage_Submitter"]
				env.Package_Path = input_map["Package_Path"]


				def prod_build_step = build (
					job: 'TODP-门户生产部署子任务', 
					parameters: [
					string(name: 'GitLab_URL_HTTP', value: "${env.GitLab_URL_HTTP}"), 
					string(name: 'Package_Path', value: "${env.Package_Path}")
					], 
					quietPeriod: 3,
					propagate: false
				)

				env.prod_build_result = prod_build_step.result
				if ("${env.prod_build_result}".contains("FAILURE")) {
					error "生产环境部署失败"
				}else{
					echo "生产环境部署任务成功"
				}

				emailext (
					body: """
					<p>在生产环境97.11拉取master分支打包成功，请相关开发负责人去${env.Package_Path}目录下取最新的包进行生产环境包替换和启动</p>
					<p>生产环境打包触发用户： ${env.Stage_Submitter}</p>
					<p>整个流水线流程完成，查看具体步骤和细节，可以登录Jenkins Pipeline页面查看</p>
					<p>Jenkins Pipeline页面： <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}</a></p>
					""",
					to: "${env.QA_Mail_List},${env.Dev_Mail_List},${env.PM_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-在生产环境97.11拉取master分支打包成功",
					attachLog: true
				)

				true
			}
			catch (err) {
				println err
				emailext (
					body: """
					<p>在生产环境97.11拉取master分支打包失败，可能原因：<p>
					<p> 1)git源码拉取失败（查看是否权限和gitlab账号问题导致）</p>
					<p> 2)Maven构建失败（查看是否是maverepo缺失等原因导致）</p>
					<p> 3)打包存放目录不存在</p>
					<p>定位原因请参照详细日志:  <a href='${env.BUILD_URL}console'>${env.JOB_NAME} [${env.BUILD_NUMBER}] (consolelog)</a></p>
					<p>查明原因后请相关人员重新在Jenkins Pipeline视图继续执行步骤 <a href='${env.JENKINS_URL}blue/organizations/jenkins/${env.JOB_NAME}/detail/${env.JOB_NAME}/${env.BUILD_NUMBER}/pipeline'>${env.JOB_NAME}${env.JOB_NAME} (pipeline)</a> ！！！</p>
					""",
					to: "${env.QA_Mail_List},${env.Dev_Mail_List},${env.PM_Mail_List}",
					subject: "${env.JOB_NAME}-${env.BUILD_NUMBER}-在生产环境97.11拉取master分支打包失败",
					attachLog: true
				)

				input (
					message: "出错，查明原因后，再次尝试该步骤?"
				)
				false
			}
		}
	}
	else {
		echo "Skip Stage7" 
	}
}