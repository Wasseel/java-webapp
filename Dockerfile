FROM eclipse-temurin:17-jre-alpine

# Run as a normal user. Many clusters refuse containers that run as root,
# and a compromised process should not be root inside the container either.
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

# Wildcard, not the exact filename: bumping the version in pom.xml would
# otherwise break this build with a confusing "file not found".
COPY target/*.jar app.jar

USER app
EXPOSE 8080

# MaxRAMPercentage makes the JVM size its heap against the container's memory
# limit. Without it the JVM reads the host's total RAM and gets OOM-killed
# by Kubernetes for using far more than its limit allows.
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
