package com.rug.gea.Client;

import com.rabbitmq.client.*;
import com.rug.gea.Client.building.Building;
import com.rug.gea.DataModels.Client;
import com.rug.gea.DataModels.Data;
import com.rug.gea.DataModels.Information;
import com.rug.gea.DataModels.Serialize;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * This class deals with sending/receiving the consumption data or the updated information
 * from the server through rabbitMQ.
 */
public class BuildingClient {

    public interface OnUpdatedClientListener {
        void onUpdatedClient();
    }

    private static final String SERVER_URL = "192.168.178.67";
    private final static String QUEUE_NAME = "periodic_data";

    private List<Client> clients;
    private OnUpdatedClientListener listener;

    public BuildingClient(Building building, String zip) {
        building.addListener(this::sendMessage);
        clients = GETRequest.fetchData(zip);
        try {
            receiveMessage(zip);
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public void addListener(OnUpdatedClientListener listener) {
        this.listener = listener;
    }

    public List<Client> getClients() {
        return clients;
    }

    /**
     * Sends a message that includes gas and electricity consumption to the server.
     * @param data local gas and electricity consumption
     * @throws IOException if it encounters a problem
     * @throws TimeoutException if a blocking operation times out
     */
    private void sendMessage(Data data) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(SERVER_URL);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        channel.basicPublish("", QUEUE_NAME, null, Serialize.serialize(data));
        System.out.println(" [x] Sent '" + data + "'");
        channel.close();
        connection.close();
    }

    /**
     * Receives a message from the server where it sends updated information in a certain zip-code.
     * @param zip zip code
     * @throws IOException if it encounters a problem
     * @throws TimeoutException if a blocking operation times out
     */
    public void receiveMessage(String zip) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(SERVER_URL);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(zip, "fanout");
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, zip, "");

        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                try {
                    Information info = (Information)Serialize.deserialize(body);
                    System.out.println(" [x] Received '" + info + "'");
                    handlingInfo(info);
                    listener.onUpdatedClient();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        };
        channel.basicConsume(queueName, true, consumer);
    }

    /**
     * Handles updated information about neighbors from the server.
     * @param info includes updated information for the neighbors
     */
    public void handlingInfo(Information info) {
        switch (info.getRequest()) {
            case Create:
                clients.add(info.getClient());
                break;
            case Update:
                Client c = info.getClient();
                if (clients.contains(c)) {
                    clients.remove(c);
                    clients.add(c);
                }
                break;
            case Delete:
                clients.remove(info.getClient());
                break;
        }
    }
}
