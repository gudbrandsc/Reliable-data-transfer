package cs682;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;

public class UdpHandler implements Runnable {
    private int port;
    private HashMap<String, PacketReciver> packetRecivers = new HashMap<>();
    private HashMap<String, PacketSender> packetSenders = new HashMap<>();
    private boolean running = true; //TODO Change for shutdown
    private HistoryData historyData;
    private DatagramSocket socket;


    public UdpHandler(String port, HistoryData historyData){
        this.port = Integer.parseInt(port);
        this.historyData = historyData;
        try {
            socket = new DatagramSocket(this.port);
        } catch (SocketException e) {
            e.printStackTrace();
        }


    }

    public void run() {
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
                String key = packetIp+packetPort;


                if (type == Chatproto.Data.packetType.REQUEST) {
                    if(!packetSenders.containsKey(key) && !historyData.getbCastMessage().isEmpty()) {
                        PacketSender packetSender = new PacketSender(socket,this,packetPort,packetIp);
                        new Thread(packetSender).start();
                        packetSenders.put(key, packetSender);
                    }

                } else if (type == Chatproto.Data.packetType.DATA) {
                    if(packetRecivers.containsKey(key)){
                        packetRecivers.get(key).addPacket(protoPkt);
                    }
                } else if(type == Chatproto.Data.packetType.ACK){
                    if(packetSenders.containsKey(key)){
                       packetSenders.get(key).addAck(protoPkt);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public Chatproto.History getHistory(){
        return Chatproto.History.newBuilder().addAllHistory(historyData.getbCastMessage()).build();
    }

    public void removeReceiver(String key){
        this.packetRecivers.remove(key);
    }
    public void removeSender(String key){
        System.out.println(packetSenders.size());
        this.packetSenders.remove(key);
    }
    public void requestHistory(Chatproto.ZKData data){
        String key = data.getIp()+data.getUdpport();
        PacketReciver receiver = new PacketReciver(socket, data, historyData, this);
        new Thread(receiver).start();
        packetRecivers.put(key,receiver);
    }
    public void shutDown(){
        this.running = false;
        socket.close();
    }

}
