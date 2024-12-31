FROM vegardit/graalvm-maven:latest-java23
LABEL authors="Mark W. Stewart <zouzousdad@gmail.com>"

RUN apt-get update
RUN apt-get install musl
RUN apt-get install musl-dev

# As of 12/26/2024, building a fully statically linked graalvm native image is only supported on linux for x86_64.
# This is sad. The project seems old enough that it shouldn't still be a problem.
# For now let's use the base image directly:
#  docker run --rm -it \
#    -v "$HOME/.m2/repository":/root/.m2/repository:rw \
#    -v "$(pwd)":/mnt/sndrRcvr \
#    -w /mnt/sndrRcvr vegardit/graalvm-maven:latest-java23 \
#    mvn -X -Pnative clean package




