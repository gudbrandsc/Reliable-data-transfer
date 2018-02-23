
package cs682;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;

/**
 * @author gudbrand schistad
 * Class that listens for incoming packets to the udp port.
 * Stores all threads that handles sending and receiving data
 * Sends packets to the right threads*/
public class UdpHandler implements Runnable {
    private HashMap<String, PacketReciver> packetRecivers = new HashMap<>();
    private HashMap<String, PacketSender> packetSenders = new HashMap<>();
    private boolean running = true; //TODO Change for shutdown
    private HistoryData historyData;
    private DatagramSocket socket;
    private float dropPrecent;
    private boolean skip;

    /**
     * Constructor
     * @param port udp port
     * @param historyData object
     */
    UdpHandler(String port, HistoryData historyData){
        this.historyData = historyData;
        try {
            socket = new DatagramSocket(Integer.parseInt(port));
        } catch (SocketException e) {
            e.printStackTrace();
        }
        this.dropPrecent = 0.0f; // Sets default value for drop percent
        this.skip = false; //Sets default value not to skip sending requests

    }
    /**
     * Run method that waits for incoming packets, creates receiver threads and passes data to correct thread */
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
                    if(!packetSenders.containsKey(key) && !historyData.getbCastMessage().isEmpty()) { //If user has not a ongoing request start
                        PacketSender packetSender = new PacketSender(socket,this,packetPort, packetIp, dropPrecent); // create packet sender
                        new Thread(packetSender).start(); // start sending packets
                        packetSenders.put(key, packetSender); // store thread in sender map
                    }
                    // If the data ip and port has a ongoing session send data to thread else skip packet
                } else if (type == Chatproto.Data.packetType.DATA) {
                    if(packetRecivers.containsKey(key)){
                        packetRecivers.get(key).addPacket(protoPkt);
                    }
                    // Sends ack to thread if thread with key
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

    /**
     * Get the history data and return a protobuf object of history
     */
    Chatproto.History getHistory(){
        return Chatproto.History.newBuilder().addAllHistory(historyData.getbCastMessage()).build();
    }

    /**
     * Remove receiver thread from map
     * @param key ip+port
     */
    void removeReceiver(String key){
        this.packetRecivers.remove(key);
    }

    /**
     * Remove sender thread from map
     * @param key ip+port
     */
    void removeSender(String key){
        this.packetSenders.remove(key);
    }

    /**
     * Create thread to receive a users history and stores it in the receiver map
     * @param data ZkData object
     */
    void requestHistory(Chatproto.ZKData data){
        String key = data.getIp()+data.getUdpport();
        PacketReciver receiver = new PacketReciver(socket, data, historyData, this, dropPrecent, skip);
        new Thread(receiver).start();
        packetRecivers.put(key,receiver);
    }

    /**
     * Ends thread
     */
    void shutDown(){
        this.running = false;
        socket.close();
    }

    /**
     * Set percent of packets that should be droped
     * @param percent of packets that should be dropped
     */
    void setDrop(float percent){
        this.dropPrecent = percent;
    }

    /**
     * Set if the receiver should drop sending some requests
     * Will only work if drop rate is not 0
     * @param value if requests should be dropped or not
     */
    void setSkip(boolean value){
        this.skip = value;
    }
}
