node('Prod-97.11') {
	stage("Stage1 - 生产环境源码下拉和编译打包") {
		checkout([
			$class: 'GitSCM', 
			branches: [[name: 'origin/master']], 		
			userRemoteConfigs: [[credentialsId: 'Gitlab-jenkins-account', url: "${env.GitLab_URL_HTTP}"]],
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
		
		export JAVA_HOME=/usr/lib/jvm/java-1.7.0-openjdk-1.7.0.79.x86_64
		backup_time=`date +%Y-%m-%d-%H`
		backup_dir=todp-auth-backup-${backup_time}
		auth_target=${WORKSPACE}/todp-auth-web/target
		auth_jar_name=todp-auth-web.jar
		auth_tar_name=todp-auth-web-v1.tar.gz

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

		if [ -f "${auth_jar_name}" ];then
			mv ${auth_jar_name} ${backup_dir}/
		fi 

		if [ -f "${auth_tar_name}" ];then
			mv ${auth_tar_name} ${backup_dir}/
		fi 

		cp ${auth_target}/${auth_jar_name} ${Package_Path}/
		cp ${auth_target}/${auth_tar_name} ${Package_Path}/
		ls -al
		'''
	}
}
