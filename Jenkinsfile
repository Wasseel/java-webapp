// The pipeline. Build, scan, publish, then write the new version into the
// chart. Jenkins never deploys - it commits a tag, and ArgoCD rolls it out.
pipeline {
    agent any

    environment {
        IMAGE    = 'java-webapp'
        REGISTRY = 'registry:5000'          // container name: Jenkins is on the ci network
        VALUES   = 'chart/envs/dev.yaml'
        REPO     = 'github.com/Wasseel/java-webapp.git'
        NODES    = 'desktop-control-plane desktop-worker desktop-worker2'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {

        // The app and the chart share one repo, so the tag-bump commit at the
        // end of this pipeline would trigger it again, forever. Three checks,
        // because any one of them can be defeated on its own.
        stage('Guard') {
            steps {
                script {
                    def author  = sh(script: 'git log -1 --pretty=%an', returnStdout: true).trim()
                    def message = sh(script: 'git log -1 --pretty=%B',  returnStdout: true).trim()
                    def touched = sh(script: 'git show --name-only --pretty=format: HEAD',
                                     returnStdout: true).trim()

                    def paths     = touched.split('\n').findAll { it.trim() }
                    def chartOnly = paths && paths.every { it.startsWith('chart/') }

                    if (author == 'jenkins-bot' || message.contains('[skip ci]') || chartOnly) {
                        env.SKIP = 'true'
                        currentBuild.result = 'NOT_BUILT'
                        currentBuild.description = 'chart-only commit, nothing to rebuild'
                        echo "Skipping: author=${author}  chartOnly=${chartOnly}"
                    } else {
                        def sha = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
                        env.TAG = "v${env.BUILD_NUMBER}-${sha}"
                        currentBuild.displayName = env.TAG
                        echo "Building ${env.IMAGE}:${env.TAG}"
                    }
                }
            }
        }

        // "build steps" - compile and run the tests. No mvn in the Jenkins
        // image, so the project's own wrapper fetches one.
        stage('Build and test') {
            when { expression { env.SKIP != 'true' } }
            steps {
                sh 'chmod +x mvnw && ./mvnw -B clean verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        // "scan steps (sonarqube)"
        // The plugin is named in full rather than as `sonar:sonar`, because
        // Maven only resolves that short prefix if org.sonarsource.scanner.maven
        // is in its plugin groups - it is not, by default. The version is pinned
        // to one that works with SonarQube 9.9 LTS.
        stage('Scan') {
            when { expression { env.SKIP != 'true' } }
            steps {
                withSonarQubeEnv('sonarqube') {
                    sh './mvnw -B org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184:sonar -Dsonar.projectKey=java-webapp'
                }
            }
        }

        // Waits for SonarQube's verdict, which arrives via the webhook set up
        // in step 4.2. Without that webhook this waits until it times out.
        stage('Quality gate') {
            when { expression { env.SKIP != 'true' } }
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // "store built jar (nexus)" - the versioned build output.
        stage('Publish jar to Nexus') {
            when { expression { env.SKIP != 'true' } }
            steps {
                configFileProvider([configFile(fileId: 'maven-settings', variable: 'MAVEN_SETTINGS')]) {
                    withCredentials([usernamePassword(credentialsId: 'nexus-creds',
                                                      usernameVariable: 'NEXUS_USER',
                                                      passwordVariable: 'NEXUS_PASS')]) {
                        sh './mvnw -B -s $MAVEN_SETTINGS deploy -DskipTests'
                    }
                }
            }
        }

        // "registry -> push"
        stage('Build and push image') {
            when { expression { env.SKIP != 'true' } }
            steps {
                sh '''
                    set -e
                    docker build -t $IMAGE:$TAG .
                    docker tag $IMAGE:$TAG $REGISTRY/$IMAGE:$TAG
                    docker push $REGISTRY/$IMAGE:$TAG
                '''
            }
        }

        // The cluster nodes are on a different Docker network and cannot reach
        // the registry, so import the image straight into each node's
        // containerd. Delete this stage once the registry is reachable from
        // the cluster, and set image.repository to $REGISTRY/$IMAGE.
        stage('Load into cluster') {
            when { expression { env.SKIP != 'true' } }
            steps {
                sh '''
                    set -e
                    docker save $IMAGE:$TAG -o /tmp/$IMAGE.tar
                    for node in $NODES; do
                        echo "loading into $node"
                        docker exec -i "$node" ctr -n=k8s.io images import - < /tmp/$IMAGE.tar
                    done
                    rm -f /tmp/$IMAGE.tar
                '''
            }
        }

        // The crossed-out "deploy", replaced. This edits one line and commits.
        // ArgoCD sees the commit and does the actual rollout.
        stage('Update the helm values file') {
            when { expression { env.SKIP != 'true' } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'github-creds',
                                                  usernameVariable: 'GIT_USER',
                                                  passwordVariable: 'GIT_TOKEN')]) {
                    // Single quotes: the shell expands the credentials, not
                    // Groovy, so they never appear in the build log.
                    sh '''
                        set -e
                        yq -i ".image.tag = \\"$TAG\\"" "$VALUES"
                        git config user.name  "jenkins-bot"
                        git config user.email "jenkins-bot@localhost"
                        git add "$VALUES"
                        if git diff --cached --quiet; then
                            echo "tag unchanged, nothing to commit"
                        else
                            git commit -m "Deploy $TAG to dev [skip ci]"
                            git push "https://${GIT_USER}:${GIT_TOKEN}@${REPO}" HEAD:main
                        fi
                    '''
                }
            }
        }
    }

    post {
        success { echo "Done. ArgoCD will pick up ${env.TAG ?: 'no new tag'} once it is installed." }
    }
}
