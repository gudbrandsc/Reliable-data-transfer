package cs682;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

/**
 * @author Gudbrand Schistad
 * Thread class that is used to print a message recived from an other user, and send a reply.
 */
public class MessageListener extends Thread{
    private Socket socket;
    private List<String> list;

    /**Constructor*/
    MessageListener(Socket socket, List<String> list) {
        this.list = list;
        this.socket = socket;
    }

    /**
     * Run method that prints the message and sends a reply
     */
    public void run() {
        Chatproto.Chat chat = null;

        try {
            chat = Chatproto.Chat.parseDelimitedFrom(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(chat!=null){
            if (chat.getIsBcast()) {
                list.add(chat.getMessage());
            }

            System.out.println(" ------- NEW MESSAGE -------");
            System.out.println("|From: " + chat.getFrom());
            System.out.println("|Message: " + chat.getMessage());
            System.out.println("|Broadcast: " + chat.getIsBcast());
            System.out.println(" ---------------------------");
            System.out.println("Enter command: ");

            Chatproto.Reply reply = Chatproto.Reply.newBuilder()
                    .setStatus(200)
                    .setMessage("OK")
                    .build();
            try {
                reply.writeDelimitedTo(socket.getOutputStream());
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
