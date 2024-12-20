FROM eclipse-temurin:23
LABEL authors="mark"
# Need to compile with the flag that sets the libc substitute for the -alpine version.
# FROM eclipse-temurin:23-alpine

EXPOSE 2424
EXPOSE 4242

ENV JAR_FILE=sndrRcvr-1.0-SNAPSHOT-jar-with-dependencies.jar
ENV DEST_DIR=/opt/app

RUN mkdir $DEST_DIR

COPY target/$JAR_FILE $DEST_DIR

ENTRYPOINT ["sh", "-c"]
CMD ["exec java -jar $DEST_DIR/$JAR_FILE"]
