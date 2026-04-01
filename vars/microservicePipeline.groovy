def call(Map config = [:]) {
    pipeline {
        agent any 

        environment {
            GIT_REPO_NAME = "${config.gitRepo}"
            GIT_EMAIL     = "${config.gitEmail ?: 'suhasini143u@gmail.com'}"
            GIT_USER_NAME  = "${config.gitUser}"
            IMAGE_NAME     = "${config.serviceName}"
            REPO_URL       = "${config.ecrRepoUrl}"
            YAML_FILE      = "${config.yamlFile}"
            SRC_DIR        = "${config.srcDir}"
        }

        stages {
            stage('Cleaning Workspace') {
                steps { cleanWs() }
            }

            stage('Checkout from Git') {
                steps {
                    git branch: 'dev', url: "https://github.com/${GIT_USER_NAME}/${GIT_REPO_NAME}.git"
                }
            }

            stage("Docker Image Build") {
                steps {
                    script {
                        dir(SRC_DIR) {
                            sh 'docker system prune -f'
                            sh 'docker build -t ${IMAGE_NAME} .'
                        }
                    }
                }
            }

            stage("ECR Image Pushing") {
                steps {
                    script {
                        // Extracting registry URL from REPO_URL
                        def registry = REPO_URL.split('/')[0]
                        sh """
                            aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${registry}
                            docker tag ${IMAGE_NAME}:latest ${REPO_URL}:${BUILD_NUMBER}
                            docker push ${REPO_URL}:${BUILD_NUMBER}
                        """
                    }
                }
            }

            stage('Update Deployment file') {
                steps {
                    dir('kubernetes-files') {
                        withCredentials([string(credentialsId: 'my-git-pattoken', variable: 'git_token')]) {
                            sh '''
                                git config user.email "${GIT_EMAIL}"
                                git config user.name "${GIT_USER_NAME}"
                                sed -i "s#image:.*#image: ${REPO_URL}:${BUILD_NUMBER}#g" ${YAML_FILE}
                                git add .
                                git commit -m "Update ${IMAGE_NAME} Image to version ${BUILD_NUMBER}"
                                git push https://${git_token}@github.com/${GIT_USER_NAME}/${GIT_REPO_NAME} HEAD:stage
                            '''
                        }
                    }
                }
            }
        }
    }
}
