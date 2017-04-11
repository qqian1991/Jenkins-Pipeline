node('Prod-96.35') {
	stage("Stage1 - 生产环境源码下拉和编译打包") {
		checkout([
			$class: 'GitSCM', 
			branches: [[name: 'origin/master']], 		
			userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "${env.GitLab_URL_HTTP}"]],
			extensions: [[$class: 'WipeWorkspace']]
		])	

		withMaven(jdk: 'JDK1.8', maven: 'MVN3') {
			sh "mvn clean package -Pprod"
		}
	}

	stage("Stage2 - 将构建好的部署包放到指定的目录下") {

		sh '''
		#!/bin/bash
		set -ex

		backup_time=`date +%Y-%m-%d-%H`
		backup_dir=todp-web-backup-${backup_time}
		home_war_target=${WORKSPACE}/todp-web/todp-home-web/target
		home_war_name=todp-home-web.war
		operation_war_target=${WORKSPACE}/todp-web/todp-operation-web/target
		operation_war_name=todp-operation-web.war

		echo "WORKSPACE is: ${WORKSPACE}"
		echo "backup_dir is: ${backup_dir}"
		echo "home_war_target is: ${home_war_target}"
		echo "home_war_name is: ${home_war_name}"
		echo "operation_war_target is: ${operation_war_target}"
		echo "operation_war_name is: ${operation_war_name}"

		# check related dir
		if [ ! -f "${Package_Path}" ];then
			mkdir -p ${Package_Path}
		fi
		echo "Package_Path is: ${Package_Path}"

		echo "start backup old war files"
		cd ${Package_Path}
		if [ ! -f "${backup_dir}" ];then
			mkdir -p ${backup_dir}
		fi 

		if [ -f "${home_war_name}" ];then
			mv ${home_war_name} ${backup_dir}/
		fi 

		if [ -f "${operation_war_name}" ];then
			mv ${operation_war_name} ${backup_dir}/
		fi 

		cp ${home_war_target}/${home_war_name} ${Package_Path}/
		cp ${operation_war_target}/${operation_war_name} ${Package_Path}/
		ls -al
		'''
	}
}