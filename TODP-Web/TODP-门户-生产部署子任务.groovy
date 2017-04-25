node('Prod-97.11') {
	stage("Stage1 - 生产环境源码下拉和编译打包后端可执行包") {
		checkout([
			$class: 'GitSCM', 
			branches: [[name: 'origin/master']], 		
			userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "${env.Backend_GitLab_URL_HTTP}"]],
			extensions: [[$class: 'WipeWorkspace']]
		])	

		withMaven(jdk: 'JDK1.8', maven: 'MVN3') {
			sh "mvn clean package -Pprod -DskipTests"
		}
	}

	stage("Stage2 - 将构建好的后端部署包放到指定的目录下") {

		sh '''
		#!/bin/bash
		set -ex

		backup_time=`date +%Y-%m-%d-%H-%M`
		backup_dir=todp-web-backend-backup-${backup_time}
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
		if [ ! -d "${Package_Path}" ];then
			mkdir -p ${Package_Path}
		fi
		echo "Package_Path is: ${Package_Path}"

		echo "start backup old war files"
		cd ${Package_Path}
		if [ ! -d "${backup_dir}" ];then
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

	stage("Stage3 - 生产环境下拉前端代码") {
		checkout([
			$class: 'GitSCM', 
			branches: [[name: 'origin/master']], 		
			userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "${env.Frontend_GitLab_URL_HTTP}"]],
			extensions: [[$class: 'WipeWorkspace']]
		])	
	}

	stage("Stage4 - 将下拉的前端编译文件夹放到指定的目录下") {

		sh '''
		#!/bin/bash
		set -ex

		backup_time=`date +%Y-%m-%d-%H-%M`
		frontend_dir=todp-one-web-pc

		# check related dir
		if [ ! -d "${Package_Path}/${frontend_dir}" ];then
			mkdir -p ${Package_Path}/${frontend_dir}
		else
			mv ${Package_Path}/${frontend_dir} ${Package_Path}/${frontend_dir}-${backup_time}
			mkdir -p ${Package_Path}/${frontend_dir}
		fi

		echo "start backup"
		cd ${WORKSPACE}
		ls -al
		cp -r ${WORKSPACE}/todp-home-web ${Package_Path}/${frontend_dir}/
		cp -r ${WORKSPACE}/todp-operation-web ${Package_Path}/${frontend_dir}/
		cd ${Package_Path}
		ls -al
		cd ${Package_Path}/${frontend_dir}
		ls -al
		'''
	}
}
