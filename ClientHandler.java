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
    private String username = null;
    private ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public void run() {
        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();

            // Handshake
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
            } else {
                return;
            }

            while (true) {
                int firstByte = in.read();
                if (firstByte == -1) break;

                boolean isFin = (firstByte & 0x80) != 0;
                int lengthIndicator = in.read() - 128; 
                long payloadLength = 0;
                
                if (lengthIndicator < 0) payloadLength = lengthIndicator + 128;
                else if (lengthIndicator <= 125) payloadLength = lengthIndicator;
                else if (lengthIndicator == 126) payloadLength = ((in.read() & 255) << 8) | (in.read() & 255);
                else if (lengthIndicator == 127) {
                    long len = 0;
                    for (int i = 0; i < 8; i++) len = (len << 8) | (in.read() & 255);
                    payloadLength = len;
                }

                byte[] key = new byte[4];
                in.read(key, 0, 4);
                
                byte[] encoded = new byte[(int)payloadLength];
                int bytesRead = 0;
                while (bytesRead < payloadLength) {
                    int count = in.read(encoded, bytesRead, (int)payloadLength - bytesRead);
                    if (count == -1) break; 
                    bytesRead += count;
                }

                byte[] decoded = new byte[encoded.length];
                for (int i = 0; i < encoded.length; i++) {
                    decoded[i] = (byte) (encoded[i] ^ key[i & 0x3]);
                }

                messageBuffer.write(decoded);
                if (!isFin) continue; 

                String message = messageBuffer.toString("UTF-8");
                messageBuffer.reset(); 

                if (username == null) {
                    String requestedName = message.trim();
                    if (ChatServer.isUsernameTaken(requestedName)) {
                        String newName = requestedName + "_" + (int)(Math.random() * 1000);
                        sendMessage("System: Username taken. You are '" + newName + "'.");
                        username = newName;
                    } else {
                        username = requestedName;
                    }
                    ChatServer.broadcastUserList();
                } 
                else {
                    if (message.equals("TYPING")) {
                        ChatServer.broadcast("TYPING|" + username, this);
                    } 
                    else if (message.startsWith("DRAW|")) {
                        ChatServer.broadcast(message, this);
                    }
                    // --- NEW: PRIVATE MESSAGE HANDLING ---
                    else if (message.startsWith("PRIV|")) {
                        // Format: PRIV|TargetName|Message
                        String[] parts = message.split("\\|", 3); // Split into max 3 parts
                        if (parts.length == 3) {
                            String target = parts[1];
                            String content = parts[2];
                            ChatServer.sendPrivateMessage(target, content, this);
                        }
                    }
                    else {
                        ChatServer.broadcast(username + "|" + message, this);
                    }
                }
            }
        } 
        catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException e) {}
            ChatServer.removeClient(this);
        }
    }

    public void sendMessage(String msg) throws IOException {
        byte[] rawData = msg.getBytes();
        int frameCount = 0;
        byte[] frame = new byte[10];
        frame[0] = (byte) 129; 
        
        if (rawData.length <= 125) {
            frame[1] = (byte) rawData.length;
            frameCount = 2;
        } else if (rawData.length >= 126 && rawData.length <= 65535) {
            frame[1] = (byte) 126;
            int len = rawData.length;
            frame[2] = (byte) ((len >> 8) & (byte) 255);
            frame[3] = (byte) (len & (byte) 255);
            frameCount = 4;
        } else {
            frame[1] = (byte) 127;
            long len = rawData.length;
            frame[2] = (byte)((len >> 56) & (byte)255);
            frame[3] = (byte)((len >> 48) & (byte)255);
            frame[4] = (byte)((len >> 40) & (byte)255);
            frame[5] = (byte)((len >> 32) & (byte)255);
            frame[6] = (byte)((len >> 24) & (byte)255);
            frame[7] = (byte)((len >> 16) & (byte)255);
            frame[8] = (byte)((len >> 8) & (byte)255);
            frame[9] = (byte)(len & (byte)255);
            frameCount = 10;
        }
        out.write(frame, 0, frameCount);
        out.write(rawData);
        out.flush();
    }
}
