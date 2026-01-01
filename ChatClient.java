import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.startClient();
    }

    public void startClient() {
        try {
            System.out.println("Connecting to chat server...");
            socket = new Socket("127.0.0.1", 5000);
            System.out.println("connected to chat server. Type a message and press Enter to send.");

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            Thread listenerThread = new Thread(new IncomingReader());
            listenerThread.start();

            Scanner scanner = new Scanner(System.in);
            while (true) {
                String userInput = scanner.nextLine();
                if (userInput.equalsIgnoreCase("/quit")) {
                    break;
                }
                out.println(userInput);
            }
            
            socket.close();
            scanner.close();

        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    private class IncomingReader implements Runnable {  
        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("server: " + message);
                }

            } catch (IOException e) {
                System.out.println("Connection to server lost: ");
            }
        }
    }
}