echo "Starting RabbitMQ broker..."
docker run -it --rm --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4.0-management
echo "Broker shutdown."
