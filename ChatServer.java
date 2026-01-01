import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    // List of connected clients
    private static List<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("Chat server started on port 5000");

        try (ServerSocket serverSocket = new ServerSocket(5000)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);

                Thread thread = new Thread(handler);
                thread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // Note how it now accepts (String message, ClientHandler sender)
    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            try {
                //only send the messages to other clients
                if (client != sender) {
                    client.sendMessage(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void removeClient(ClientHandler client) {
        clients.remove(client);
    }
}

