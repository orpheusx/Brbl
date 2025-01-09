echo Building Linux binary...
#docker run -it --rm --name mvnc -v "$(pwd)":/Users/mark/Development/sndrRcvr/src -w /Users/mark/Development/sndrRcvr/src maven:latest mvn package
docker run --rm -it -v "$HOME/.m2/repository":/root/.m2/repository:rw -v "$(pwd)":/mnt/sndrRcvr -w /mnt/sndrRcvr vegardit/graalvm-maven:latest-java23 mvn -Pnative clean package
echo Done

