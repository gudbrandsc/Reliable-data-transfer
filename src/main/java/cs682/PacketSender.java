package cs682;

import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author gudbrand schistad
 * Class that sends packets and receives ACK
 */
public class PacketSender implements Runnable {
    private boolean running = true;
    private DatagramSocket socket;
    private UdpHandler udpHandler;
    private Chatproto.History history;
    private ConcurrentLinkedQueue<Chatproto.Data> ackList;
    private ArrayList<Chatproto.Data>packetDataList;
    private String ip;
    private int port;
    private int expSeqNo;
    private int lastPacket;
    private float dropPercent;


    /**
     * Constructor
     * @param socket udp socket
     * @param udpHandler
     * @param port
     * @param ip
     * @param dropPercent number of packets that should be dropped
     */
    public PacketSender(DatagramSocket socket, UdpHandler udpHandler, int port, String ip, float dropPercent) {
        this.socket = socket;
        this.udpHandler = udpHandler;
        this.history = this.udpHandler.getHistory();
        this.ackList = new ConcurrentLinkedQueue<>();
        this.packetDataList = new ArrayList<>();
        this.expSeqNo = 1;
        this.ip = ip;
        this.port = port;
        this.dropPercent = dropPercent;

    }

    /**
     * Check if the ACK is between expected sequence number and last packet sent.
     * If true, adds the ACK to the que and notify
     * @param data
     */
    public void addAck(Chatproto.Data data) {
        System.out.println("Received ACK for: "+data.getSeqNo());
        synchronized (this) {
            if (data.getSeqNo() >= this.expSeqNo && data.getSeqNo() <= this.lastPacket) {
                ackList.add(data);
                this.notify();
            }
        }
    }

    /**
     * Get number of packets created
     * @return number of packets in packet list
     */
    private int getNumPackets(){
        return this.packetDataList.size();
    }

    /**
     * Creates all packets and store them in a list
     * @param history byte representation of the protobuf history object
     */
    private void createPackets(byte[] history){
        int end = history.length-1;
        int count = 0;
        boolean isLast = false;
        byte[] packetData = new byte[10];
        int packetNum = 1; //Seq number for the packet

        for(int i = 0; i <= end; i++){
            packetData[count] = history[i];
            if(count == 9){
                if(i == end){
                    isLast = true;
                }
                ByteString packetString = ByteString.copyFrom(packetData);
                Chatproto.Data packet = Chatproto.Data.newBuilder().setSeqNo(packetNum).setData(packetString).setIsLast(isLast).setTypeValue(2).build();
                packetDataList.add(packet);
                packetNum++;
                count = 0;
            }else if(count < 9 && i == end){ // If last packet does not contain a byte array of size 10
                isLast = true;
                byte[] trimmedArray = new byte[count+1];
                System.arraycopy(packetData, 0, trimmedArray, 0, count+1);
                ByteString packetString = ByteString.copyFrom(trimmedArray);
                Chatproto.Data packet = Chatproto.Data.newBuilder().setSeqNo(packetNum).setData(packetString).setIsLast(isLast).setTypeValue(2).build();
                packetDataList.add(packet);
            }else{
                count++;
            }
        }
        //sets last packet.
        if(packetNum >= 4){
            this.lastPacket = 4;
        }else{
            this.lastPacket = packetDataList.size();
        }
    }

    /**
     * Sends window
     */
    private void sendWindow() {
        System.out.println("Resent window"+expSeqNo+"-"+lastPacket);
        for(int i = expSeqNo; i <= lastPacket; i++){
            sendPacket(packetDataList.get(i - 1));
        }
    }
    /**
     * Sends a data packet to the receiver
     */
    private void sendPacket(Chatproto.Data packet) {
        Random r = new Random();

        float chance = r.nextFloat();

        if (chance <= dropPercent){
            System.out.println("Didn't send packet: " + packet.getSeqNo());
        }else {
            ByteArrayOutputStream outstream = new ByteArrayOutputStream(1024);
            try {
                packet.writeDelimitedTo(outstream);
            } catch (IOException e) {
                e.printStackTrace();
            }
            byte[] item = outstream.toByteArray();

            //send
            try {
                DatagramPacket datagramPacket = new DatagramPacket(item, item.length, InetAddress.getByName(ip), port);
                socket.send(datagramPacket);
                System.out.println("Sent packet: " + packet.getSeqNo());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Creates a byte array of the chatproto history object
     * @return byte array
     */
    private byte[] createHistoryArray(){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            history.writeDelimitedTo(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outputStream.toByteArray();
    }

    /**
     * Method that waits for ack and sends packets*/
    @Override
    public void run() {
        synchronized (this) {

            byte[] historyArray = createHistoryArray();
            createPackets(historyArray);
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sendWindow();
            int numPackets = getNumPackets();
            int resentWindow = 0;
            while (running) {
                try {
                    this.wait(1000);
                    if (!ackList.isEmpty()) {
                        resentWindow = 0;
                        for(Chatproto.Data ack : ackList) {
                            if (!(ack.getSeqNo() == numPackets)) {
                                slideWindow(ack, numPackets);
                            } else {
                                finish();
                            }
                            ackList.remove(ack);
                        }
                    } else if(resentWindow == 7){
                        finish();
                        System.out.println("Session timeout. Did not receive any ack");
                    }else {
                        resentWindow++;
                        sendWindow();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Stops thread and removes thread from the senders map
     */
    private void finish() {
        running = false;
        udpHandler.removeSender(ip + port);
    }
    /**
     * Method chat sends new packets when ACK's are received
     * @param data ACK data
     * @param numPackets number of packets in the list
     */
    private void slideWindow(Chatproto.Data data,int numPackets) {
        int ackSeqNo = data.getSeqNo();
        int numSlides = (ackSeqNo - expSeqNo)+1;

        for(int i = 1; i <= numSlides; i++){
            expSeqNo++;
            if(!((lastPacket+1) > numPackets)){
                lastPacket++;
                System.out.println("Slide and send new last packet: " + lastPacket);
                sendPacket(packetDataList.get(lastPacket - 1));
            }
        }
    }
}
