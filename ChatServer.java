import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    // Thread-safe list of clients
    private static List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());

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

    public static void broadcast(String message, ClientHandler sender) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                try {
                    if (client != sender) { 
                        client.sendMessage(message);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // --- NEW: PRIVATE MESSAGE LOGIC ---
    public static void sendPrivateMessage(String recipientName, String message, ClientHandler sender) {
        ClientHandler recipient = null;
        
        // 1. Find the recipient
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.getUsername() != null && client.getUsername().equalsIgnoreCase(recipientName)) {
                    recipient = client;
                    break;
                }
            }
        }

        // 2. Send the message
        try {
            if (recipient != null) {
                // Send to Target: "PRIV|SenderName|Message"
                recipient.sendMessage("PRIV|" + sender.getUsername() + "|" + message);
                
                // Echo back to Sender (so they see it in their own chat): "PRIV|RecipientName|Message"
                // We use a special prefix "PRIV_SENT" to let the frontend know this is an outgoing message
                sender.sendMessage("PRIV_SENT|" + recipientName + "|" + message);
            } else {
                sender.sendMessage("System: User '" + recipientName + "' not found.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void broadcastUserList() {
        StringBuilder sb = new StringBuilder("USERS"); 
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.getUsername() != null) {
                    sb.append("|").append(client.getUsername()); 
                }
            }
        }
        
        String listMessage = sb.toString();
        
        synchronized (clients) {
            for (ClientHandler client : clients) {
                try {
                    client.sendMessage(listMessage);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean isUsernameTaken(String name) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.getUsername() != null && client.getUsername().equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        broadcastUserList(); 
    }
}
