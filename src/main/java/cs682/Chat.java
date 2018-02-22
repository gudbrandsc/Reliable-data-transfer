package cs682;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.net.InetAddress;

/**
 * @author Gudbrand Schistad
 * Chat class that runes the main method and takes care of all user interaction.
 * */
public class Chat {
    private static ZookeeperInstance zk;
    private volatile static boolean running = true;
    private static String port;
    private static String udpPort;
    private static HistoryData historyData = new HistoryData();
    private static UdpHandler udpHandler;

/**
 * Main method that connects to zookeeper, starts the message listener and takes commands from the user
 * */
    public static void main(String[] args){
        String username;

        //Check that no arguments are missing, and are following the right format.
        System.out.println("Connecting to server...");
        if(args.length!=6){
            System.out.println("ERROR: To few arguments");
            System.exit(0);
        } else if((!args[0].equals("-user")) || (!args[2].equals("-port")) || (!args[4].equals("-udpport"))){
            System.out.println("Wrong syntax while passing args");
            System.out.println("Expected: -user username -port ****");
            System.out.println("Found: " + args[0] + " " + args[1] + " " + args[2] + " " + args[3]);
            System.exit(0);
        }

        username = args[1];
        port = args[3];
        udpPort = args[5];
        Chatproto.ZKData zkData = null;


        try {
            zkData = Chatproto.ZKData.newBuilder().setUdpport(udpPort).setPort(port).setIp(InetAddress.getLocalHost().getHostAddress()).build();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        zk = new ZookeeperInstance(username, zkData, udpPort);
        ServerSocket serve = null;
        try {
            serve = new ServerSocket(Integer.parseInt(port));
        } catch (IOException e) {
            e.printStackTrace();
        }
        readMessages(serve);
        udpHandler = new UdpHandler(udpPort,historyData);
        new Thread(udpHandler).start();

        while (running){
            System.out.println("Enter command: ");
            Scanner scanner = new Scanner(System.in);
            String command = scanner.nextLine();
            if(command.trim().equalsIgnoreCase("help")){
                listCommands();
            } else if(command.trim().equalsIgnoreCase("List")){
                listUsers(zk);
            } else if(command.trim().equalsIgnoreCase("Send")){
                sendMessage(zk,username);
            } else if(command.trim().equalsIgnoreCase("Broadcast")) {
                broadcast(zk, username);
            } else if(command.trim().equalsIgnoreCase("read")){
                listBroadcastMessages();
            }else if(command.trim().equalsIgnoreCase("Request")){
                sendHistoryRequest();
            } else if(command.trim().equalsIgnoreCase("Exit")){
                System.out.println("Bye =)");
                shutdown(serve);
            }
        }
    }

    /**
    * Method that prints all possible commands to the user
    */
    private static void listCommands(){
        System.out.println("-- Broadcast -- Send a message to  all other users.");
        System.out.println("-- Exit -- Exit the program");
        System.out.println("-- List --  Display all other users.");
        System.out.println("-- Read -- Display all received broadcast messages.");
        System.out.println("-- Send -- Send a message to a user.");
        System.out.println("-- Request -- Change your broadcast history to an other users.");

    }

    /**
    * Method that prints all users with their port and ip
     * @param zk zookeeperInstance object
    */
    private static void listUsers(ZookeeperInstance zk){
        try {
            zk.listAllMembers();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method that prints all users with their port and ip
     */
    private static void listBroadcastMessages(){
        System.out.println("--Broadcast Messages--");

        for(Chatproto.Chat chat : historyData.getbCastMessage()){
            System.out.println(chat.getMessage());
        }
        System.out.println("---------------------");
    }

    /**
     * Method that reads in the username and the message that the user want to send.
     * Then uses the the input username to get the receiver port and ip.
     * When all the necessary data to send a message is fetched it starts a thread that send the message
     * @param zk zookeeperInstance object
     * @param username of the user sending the message
     */
    private static void sendMessage(ZookeeperInstance zk, String username){
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter username:");
        String receiverName = "/" + sc.nextLine();
        System.out.println("Enter message:");
        String message = sc.nextLine();

        Chatproto.ZKData receiverData = null;
        Chatproto.Chat chatProto = null;
        if(zk.getZnodeData(receiverName)!= null){
            try {
                receiverData = Chatproto.ZKData.parseFrom(zk.getZnodeData(receiverName));
                chatProto = Chatproto.Chat.newBuilder().setFrom(username).setIsBcast(false).setMessage(message).build();

            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }

            MessageSender sender = new MessageSender(chatProto,receiverData);
            sender.start();

            System.out.println("Message was sendt to user: " + receiverName);

        }else {
            System.out.println("Not able to send message to " + receiverName);
        }
    }

    /**
     * Gets a list off all nodes and iterates over them.
     * For each name it creates a zkdata object and starts a thread to send the broadcast message.
     * @param username of the user sending the message
     * @param zk ZookeeperInstance object
     */
    private static void broadcast(ZookeeperInstance zk, String username){
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter message:");
        String message = sc.nextLine();

        try {
            List<String> names = zk.getAllMembers();
            for(String n : names){
                try {
                    if(zk.getZnodeData("/" + n) != null) {
                        Chatproto.ZKData zkData = Chatproto.ZKData.parseFrom(zk.getZnodeData("/" + n));
                        Chatproto.Chat chat = Chatproto.Chat.newBuilder()
                                .setFrom(username)
                                .setIsBcast(true)
                                .setMessage(message)
                                .build();

                        MessageSender sender = new MessageSender(chat, zkData);
                        sender.start();
                    }
                } catch (InvalidProtocolBufferException e) {
                    System.out.println("Not able to send message to: " + n);
                    //e.printStackTrace();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Broadcast was sendt");
    }

    /**
     * Method that runs in separate thread from main and waits for incoming messages at a port.
     * If a message is received it creates a new thread to handle it and continues to listen for new messages
     * @param serve ServerSocket object
     */
    private static void readMessages(final ServerSocket serve){
        final Runnable run = new Runnable() {
            public void run() {
                try {
                    while(running) {
                        Socket sock = serve.accept();
                        //Create thread to handle request
                        MessageListener messageListener = new MessageListener(sock,historyData);
                        messageListener.start();
                    }
                } catch(IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        };
        new Thread(run).start();
    }
    private static void sendHistoryRequest(){
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter username:");
        String receiverName = "/" + sc.nextLine();

        Chatproto.ZKData receiverData = null;
        if(zk.getZnodeData(receiverName)!= null) {
            try {
                receiverData = Chatproto.ZKData.parseFrom(zk.getZnodeData(receiverName));
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
            udpHandler.requestHistory(receiverData);
        }
    }

    /**
     * Method that stops the listening thread, by stopping the while loop and closing socket.
     * @param serve ServerSocket
     */
    private static void shutdown(ServerSocket serve){
        running=false;
        udpHandler.shutDown();
        try {
            serve.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
