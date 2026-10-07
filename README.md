# java-webapp

A small Spring Boot service, used to build and prove a full delivery pipeline.

## Endpoints

| Path | Returns |
|---|---|
| `/api/hello?name=` | A greeting |
| `/api/time` | The current time |
| `/api/whoami` | The hostname that answered — the pod name in Kubernetes |
| `/actuator/health/readiness` | Readiness probe |
| `/actuator/health/liveness` | Liveness probe |

## How a change reaches production

A push triggers Jenkins, which runs the stages in `Jenkinsfile`:

1. **Guard** - skips the build if the commit only touched `chart/`, so the
   pipeline's own tag-bump commit cannot retrigger it
2. **Build and test** - `mvnw clean verify`
3. **Scan** - SonarQube, behind a blocking quality gate
4. **Publish jar to Nexus** - versioned build output
5. **Build and push image** - to the container registry
6. **Load into cluster** - imports the image into each node
7. **Update the helm values file** - writes the new tag into
   `chart/envs/dev.yaml` and commits it

Jenkins never deploys. ArgoCD watches this repo and makes the cluster match
whatever tag is committed.

## Environments

`chart/` holds one Helm chart; `chart/envs/<env>.yaml` holds what differs.

| Environment | Replicas | Syncs |
|---|---|---|
| dev | 1 | automatically |
| qa | 1 | on click |
| stage | 2 | on click |
| prod | 3 | on click |

Promotion is copying the image tag from one env file to the next and clicking
Sync - never a rebuild. The bytes that passed dev are the bytes that ship.

## Running it locally

    mvn clean package
    docker build -t java-webapp:local .
    docker run -d -p 8080:8080 --name app java-webapp:local
