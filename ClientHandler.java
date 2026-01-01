import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientHandler implements Runnable {
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private String username = null; // Track the user's name

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();

            // 1. HANDSHAKE
            Scanner s = new Scanner(in, "UTF-8");
            String data = s.useDelimiter("\\r\\n\\r\\n").next();
            Matcher get = Pattern.compile("^GET").matcher(data);

            if (get.find()) {
                Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
                match.find();
                byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Sec-WebSocket-Accept: "
                        + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")))
                        + "\r\n\r\n").getBytes("UTF-8");
                out.write(response, 0, response.length);
                System.out.println("Handshake complete! Browser connected.");
            } else {
                return;
            }

            // 2. LISTEN LOOP (Updated for Long Messages)
            while (true) {
                int firstByte = in.read();
                if (firstByte == -1) break;

                int lengthIndicator = in.read() - 128; // remove mask bit
                long payloadLength = 0;

                // DETECT LENGTH
                if (lengthIndicator <= 125) {
                    // Small message
                    payloadLength = lengthIndicator;
                } else if (lengthIndicator == 126) {
                    // Medium message: Read next 2 bytes
                    payloadLength = ((in.read() & 255) << 8) | (in.read() & 255);
                } else if (lengthIndicator == 127) {
                    // Large message: Read next 8 bytes (We ignore huge ones for simplicity)
                    // Just consuming bytes to prevent errors
                    for(int i=0; i<8; i++) in.read(); 
                    continue; // Skip huge messages (>65KB) for this demo
                }

                // READ MASK KEY
                byte[] key = new byte[4];
                in.read(key, 0, 4);

                // READ ENCODED MESSAGE
                byte[] encoded = new byte[(int)payloadLength];
                in.read(encoded);

                // DECODE
                byte[] decoded = new byte[encoded.length];
                for (int i = 0; i < encoded.length; i++) {
                    decoded[i] = (byte) (encoded[i] ^ key[i & 0x3]);
                }

                String message = new String(decoded);

                // LOGIC: Username vs Chat
                if (username == null) {
                    username = message;
                    System.out.println("User joined: " + username);
                    // Confirm login to the user
                    sendMessage("System: Welcome, " + username + "!");
                } else {
                    System.out.println(username + " says: " + message);
                    // Broadcast
                    ChatServer.broadcast(username + "|" + message, this);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException e) {}
        }
    }

    // UPDATED: Helper to Send Frames (Supports Long Messages too!)
    public void sendMessage(String msg) throws IOException {
        byte[] rawData = msg.getBytes();
        int frameCount = 0;
        byte[] frame = new byte[10];

        frame[0] = (byte) 129; // Text Frame

        if (rawData.length <= 125) {
            frame[1] = (byte) rawData.length;
            frameCount = 2;
        } else if (rawData.length >= 126 && rawData.length <= 65535) {
            frame[1] = (byte) 126;
            int len = rawData.length;
            frame[2] = (byte) ((len >> 8) & (byte) 255);
            frame[3] = (byte) (len & (byte) 255);
            frameCount = 4;
        }

        out.write(frame, 0, frameCount);
        out.write(rawData);
        out.flush();
    }
}

