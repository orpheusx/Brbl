echo Sending 5 test messages to Rcvr...
curl -X POST http://192.168.1.233:4242/enqueue -d '1 hello'
curl -X POST http://192.168.1.233:4242/enqueue -d '2 hello'
curl -X POST http://192.168.1.233:4242/enqueue -d '3 hello'
curl -X POST http://192.168.1.233:4242/enqueue -d '4 hello'
curl -X POST http://192.168.1.233:4242/enqueue -d '5 hello'
echo Complete.

