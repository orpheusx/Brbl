echo Sending 5 test messages to Rcvr...
curl -X POST http://192.168.1.155:4242/brblEnqueue -d '17817299468:1234:hello'
curl -X POST http://192.168.1.155:4242/brblEnqueue -d '17817299469:1234:hi'
curl -X POST http://192.168.1.155:4242/brblEnqueue -d '17817299470:1234:heya'
curl -X POST http://192.168.1.155:4242/brblEnqueue -d '17817299471:1234:hey there'
curl -X POST http://192.168.1.155:4242/brblEnqueue -d '17817299472:1234:greetings'
echo Complete.

