node('Prod-96.35') {
	stage("Stage1 - 生产环境源码下拉和编译打包") {
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

	stage("Stage2 - 将构建好的部署包放到指定的目录下") {

		sh '''
		#!/bin/bash
		set -ex
		backup_time=`date +%Y-%m-%d-%H-%M`
		backup_dir=todp-auth-backup-${backup_time}
		auth_target=${WORKSPACE}/todp-auth-web/target
		auth_jar_name=todp-auth-web.jar
		auth_tar_name=todp-auth-web-v1.tar.gz

		# check related dir
		if [ ! -d "${Package_Path}" ];then
			mkdir -p ${Package_Path}
		fi
		echo "Package_Path is: ${Package_Path}"

		echo "start backup old war files"
		if [ ! -d "${Package_Path}/${backup_dir}" ];then
			mkdir -p ${backup_dir}
		fi 

		if [ -f "${Package_Path}/${auth_jar_name}" ];then
			mv ${auth_jar_name} ${backup_dir}/
		fi 

		if [ -f "${Package_Path}/${auth_tar_name}" ];then
			mv ${auth_tar_name} ${backup_dir}/
		fi 

		cp ${auth_target}/${auth_jar_name} ${Package_Path}/
		cp ${auth_target}/${auth_tar_name} ${Package_Path}/
		cd ${Package_Path}
		ls -al
		'''
	}
}
