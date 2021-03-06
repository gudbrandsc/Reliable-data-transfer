package cs682;

import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author gudbrand schistad
 * Class that receives packets and updates history
 */
public class PacketReceiver implements Runnable{
    private boolean running = true;
    private DatagramSocket socket;
    private int expectedPacket;
    private ConcurrentLinkedQueue<Chatproto.Data> packetList;
    private HistoryData historyData;
    private UdpHandler udpHandler;
    private Chatproto.ZKData zkData;
    private ByteString byteString = ByteString.EMPTY;
    private Random random = new Random();
    private float dropPercent;
    private boolean skip;


    /**
     * constructor
     * @param socket
     * @param zkData
     * @param historyData
     * @param udpHandler
     * @param dropPercent
     * @param skip
     */
    public PacketReceiver(DatagramSocket socket, Chatproto.ZKData zkData, HistoryData historyData, UdpHandler udpHandler, float dropPercent, boolean skip){
        this.socket = socket;
        this.zkData = zkData;
        this.expectedPacket = 1;
        this.historyData = historyData;
        this.udpHandler = udpHandler;
        this.packetList =  new ConcurrentLinkedQueue<>();
        this.dropPercent = dropPercent;
        this.skip = skip;
    }
    /**
     * Sends a request to a user to initialize a download
     */
    private void sendRequest()  {
        try {
            Chatproto.Data req = Chatproto.Data.newBuilder().setTypeValue(0).build();
            ByteArrayOutputStream outstream = new ByteArrayOutputStream(1024);
            req.writeDelimitedTo(outstream);

            byte[] item = outstream.toByteArray();
            InetAddress ipAddress = InetAddress.getByName(zkData.getIp());
            int port = Integer.parseInt(zkData.getUdpport());
            DatagramPacket datagramPacket = new DatagramPacket(item, item.length, ipAddress, port);
            float chance = random.nextFloat();

            if(skip && chance <= dropPercent){
                System.out.println("Skipped sending request");
            }else{
                socket.send(datagramPacket);
                System.out.println("Sent request..");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends ACK packets to the sender
     @param seqNo of received packet
     */
    private void sendACK(int seqNo) throws IOException {
        float chance = random.nextFloat();

        if (chance <= dropPercent){
            System.out.println("Did not send ACK for packet: " + seqNo);
        }else {
            Chatproto.Data ack = Chatproto.Data.newBuilder().setTypeValue(1).setSeqNo(seqNo).build();
            ByteArrayOutputStream outstream = new ByteArrayOutputStream(1024);
            ack.writeDelimitedTo(outstream);

            byte[] item = outstream.toByteArray();
            InetAddress ipAddress = InetAddress.getByName(zkData.getIp());
            int port = Integer.parseInt(zkData.getUdpport());
            DatagramPacket datagramPacket = new DatagramPacket(item, item.length, ipAddress, port);
            socket.send(datagramPacket);
            System.out.println("Sent ACK for packet: " + seqNo);
        }
    }

    /**
     * Method user by udpHandler to add data packets to the que
     * If the packet is not the expected packet, then drop it
     * @param data
     */
    public void addPacket(Chatproto.Data data){
        System.out.println("Received packet: "+ data.getSeqNo() + " expected packet: " + expectedPacket);
        synchronized (this) {
            if(this.expectedPacket == data.getSeqNo()){
                try {
                    sendACK(data.getSeqNo());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                this.packetList.add(data);
                this.notify();
            }
        }
    }

    /**
     * Run method that waits for packets and builds a byte array.
     * When all packets are received then updated list
     * times out if no packets are received for 5 sec
     */
    @Override
    public void run() {
        sendRequest();
        synchronized (this) {
            int numRequest = 0;
            boolean requestReceived = false;
            while (running) {
                try {
                    this.wait(2000);
                    if(!packetList.isEmpty()){
                        requestReceived = true;
                        for (Chatproto.Data packet : packetList) {
                            if (packet.getSeqNo() == expectedPacket) {
                                byteString = byteString.concat(packet.getData());
                                if (packet.getIsLast()) {
                                    ByteArrayInputStream inputStream = new ByteArrayInputStream(byteString.toByteArray());
                                    Chatproto.History newHistory = Chatproto.History.parseDelimitedFrom(inputStream);
                                    historyData.updateHistory(newHistory.getHistoryList());
                                    finish();
                                    System.out.println("List was updated");
                                }
                                expectedPacket++;
                            }
                            packetList.remove(packet);
                        }
                    }else if(packetList.isEmpty() && (numRequest < 5) && !requestReceived){
                        System.out.println("Didn't receive any data, resending request..");
                        sendRequest();
                        numRequest++;
                    }else{
                        finish();
                        System.out.println("Session timed out..");
                    }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Stop thread and remove it from the receiver map
     */
    private void finish(){
        running = false;
        String key = zkData.getIp()+zkData.getUdpport();
        udpHandler.removeReceiver(key);
    }

}
