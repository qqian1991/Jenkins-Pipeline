node('Prod-97.11') {
	stage("Stage1 - 生产环境源码下拉和编译后端可执行包") {
		checkout([
			$class: 'GitSCM', 
			branches: [[name: 'origin/master']], 		
			userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "${env.Backend_GitLab_URL_HTTP}"]],
			extensions: [[$class: 'WipeWorkspace']]
		])	

		// Clean up the maven cache Dependency
		sh '''
		#!/bin/bash
		set -ex
		rm -rf ~/.m2/repository/com/chinatelecom/*
		'''

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

		# check related dir
		if [ ! -d "${Package_Path}" ];then
			mkdir -p ${Package_Path}
		fi

		# start backup old war files
		cd ${Package_Path}
		if [ ! -d "${Package_Path}/${backup_dir}" ];then
			mkdir -p ${Package_Path}/${backup_dir}
		fi 

		if [ -f "${Package_Path}/${home_war_name}" ];then
			mv ${Package_Path}/${home_war_name} ${Package_Path}/${backup_dir}/
		fi 

		if [ -f "${Package_Path}/${operation_war_name}" ];then
			mv ${Package_Path}/${operation_war_name} ${Package_Path}/${backup_dir}/
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

	stage("Stage4 - 替换生产原来的前端文件夹") {

		sh '''
		#!/bin/bash
		set -ex

		backup_time=`date +%Y-%m-%d-%H-%M`
		home_frontend_path=/usr/lib/todp-one-web-pc/todp-home-web
		operation_frontend_path=/usr/lib/todp-one-web-pc/todp-operation-web
		dist_backup_dir=dist-${backup_time}-bk

		# check related dir
		if [ -d "${home_frontend_path}/dist" ];then
			mv ${home_frontend_path}/dist ${home_frontend_path}/${dist_backup_dir}
		fi

		if [ -d "${operation_frontend_path}/dist" ];then
			mv ${operation_frontend_path}/dist ${operation_frontend_path}/${dist_backup_dir}
		fi

		echo "start backup"
		cp -r ${WORKSPACE}/todp-home-web/dist ${home_frontend_path}/
		cp -r ${WORKSPACE}/todp-operation-web/dist ${operation_frontend_path}/
		cd ${home_frontend_path}
		ls -al
		cd ${operation_frontend_path}
		ls -al
		'''
	}
}
