package cs682;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/*Thread class that is used to send a message to a user*/
public class MessageSender extends Thread{
    private Chatproto.Chat chat;
    private Chatproto.ZKData zkData;

    /*Constructor*/
    public MessageSender(Chatproto.Chat chat, Chatproto.ZKData zkData) {
        this.chat = chat;
        this.zkData = zkData;
    }

    /**
     * @author gudbrand schistad
     * Run method that checks if the port is a valid number and
     * then opens a socket and sends a message to the user.
     * Then waits for a reply.
    */
    public void run() {
        try {
            //Regex pattern to check that the port is valid
            String portPattern = "[1-9]{1,4}";
            Pattern r = Pattern.compile(portPattern);
            Matcher m = r.matcher(zkData.getPort());


            if(m.find()){
                Socket socket = new Socket(zkData.getIp(),Integer.parseInt(zkData.getPort()));
                InputStream instream = socket.getInputStream();
                OutputStream outstream = socket.getOutputStream();
                chat.writeDelimitedTo(outstream);
                System.out.println("Message was sendt");

                Chatproto.Reply reply = null;
                try {
                    reply = Chatproto.Reply.parseDelimitedFrom(instream);
                } catch (IOException e) {
                   // e.printStackTrace();
                }
              // System.out.println("reply = " + reply.getMessage() + " status : " + reply.getStatus());
            }
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }
}
