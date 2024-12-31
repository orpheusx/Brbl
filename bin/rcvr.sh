if [ "$1" == "jvm" ]; then
  echo "Starting Rcvr in JVM..."
  docker run -it --rm -p 4242:4242 --name burble-jvm-rcvr burble-jvm:0.1.0 Rcvr
  echo "Rcvr JVM shutdown."
elif [ "$1" == "exe" ]; then
  echo "Starting Rcvr executable..."
  docker run -it --rm -p 4242:4242 --name burble-bin-rcvr burble-bin:0.1.0 Rcvr
  echo "Rcvr executable shutdown."
else
  echo "Argument not recognized: $1"
fi

#if [ "$1" == "jvm" ]; then
#    echo "Starting Rcvr in JVM..."
##    docker run -it --rm -p 4242:4242 --name burble-jvm-rcvr burble-jvm:0.1.0 Rcvr
##    echo "Rcvr JVM shutdown."
##fi
#elif [ "$1" == "exe"]; then
##    echo "Starting Rcvr executable..."
###    docker run -it --rm -p 4242:4242 --name burble-bin-rcvr burble-bin:0.1.0 Rcvr
###    echo "Rcvr executable shutdown."
#else
#  echo "Argument not recognized: $1"
#fi
#
#else
#  echo "Usage: $0 [jvm | exe]"
#fi