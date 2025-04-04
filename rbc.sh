# Rebuild jar (skipping tests) and then rebuild container image (no test)
# At some point we need/want to incorporate the docker build into our Maven pom.xml.
mvn package -DskipTests && docker build -t burble-jvm:0.1.0 -f Dockerfile.dev.jvm .
