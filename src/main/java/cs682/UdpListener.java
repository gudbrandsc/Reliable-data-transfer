package cs682;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;

public class UdpListener implements Runnable {
    private int port;
    HashMap<String, Thread> threadMap = new HashMap<String, Thread>();
    private boolean running = true; //TODO Change for shutdown
    private List<Chatproto.Chat> list;


    public UdpListener(String port, List<Chatproto.Chat> list){
        this.port = Integer.parseInt(port);
        this.list = list;
        this.list = list;
    }

    public void run() {
        try {
            DatagramSocket socket = new DatagramSocket(4401);

            byte[] data = new byte[1024];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            while (running) {
                try {
                    socket.receive(packet);
                    byte[] rcvdData = packet.getData();

                    ByteArrayInputStream instream = new ByteArrayInputStream(rcvdData);
                    Chatproto.Data protoPkt = Chatproto.Data.parseDelimitedFrom(instream);
                    Chatproto.Data.packetType type = protoPkt.getType();
                    String packetIp = packet.getAddress().getHostAddress();
                    int packetPort = packet.getPort();


                    if (type == Chatproto.Data.packetType.REQUEST) {
                       //Create and start thread. Then send first packets and wait for ACK request
                        if(!threadMap.containsKey(packetIp)) {
                            System.out.println(type.toString());
                            PacketSender packetSender = new PacketSender(packetIp, packetPort, socket, protoPkt, this);
                            Thread senderThread = new Thread(packetSender);
                            senderThread.start();
                            threadMap.put(packetIp, senderThread);
                        }
                    } else if (type == Chatproto.Data.packetType.ACK) {
                        if(threadMap.containsKey(packetIp)){
                            //threadMap.get(packetIp).getAck(protoPkt);
                        }
                    } else if (type == Chatproto.Data.packetType.DATA) {
                        if(threadMap.containsKey(packetIp)) {
                            //  threadMap.get(packetIp).setPacket(protoPkt);
                        }
                        //If i have sent a request and are waiting for packages
                        //Need thread to handle
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

    }
    public Chatproto.History getHistory(){
        Chatproto.History history = Chatproto.History.newBuilder().addAllHistory(list).build();
        return history;
    }

    public void removeReciver(String ip){
        threadMap.remove(ip);
    }
}
