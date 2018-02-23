package cs682;

import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    /**
     * */
    public PacketSender(DatagramSocket socket, UdpHandler udpHandler, int port, String ip) {
        this.socket = socket;
        this.udpHandler = udpHandler;
        this.history = this.udpHandler.getHistory();
        this.ackList = new ConcurrentLinkedQueue<>();
        this.packetDataList = new ArrayList<>();
        this.expSeqNo = 1;
        this.ip = ip;
        this.port = port;

    }

    //Check if the message that is recived is a new ACK and call notify to send new packets
    public void addAck(Chatproto.Data data) {
        System.out.println("Received ACK for: "+data.getSeqNo());
        synchronized (this) {
            if (data.getSeqNo() >= this.expSeqNo && data.getSeqNo() <= this.lastPacket) {
                ackList.add(data);
                this.notify();
            }
        }
    }


    private int getNumPackets(){
        return this.packetDataList.size();
    }

    private void createPackets(byte[] history){
        int end = history.length-1;
        int count = 0;
        boolean isLast = false;
        byte[] packetData = new byte[10];
        int packet_num = 1;
        System.out.println(end);
        for(int i = 0; i <= end; i++){
            packetData[count] = history[i];
            if(count == 9){
                if(i == end){
                    isLast = true;
                }
                ByteString packetString = ByteString.copyFrom(packetData);
                Chatproto.Data packet = Chatproto.Data.newBuilder().setSeqNo(packet_num).setData(packetString).setIsLast(isLast).setTypeValue(2).build();
                packetDataList.add(packet);
                packet_num++;
                count=0;
            }else if(count < 9 && i == end){
                isLast = true;
                byte[] trimmedArray = new byte[count+1];
                System.arraycopy(packetData, 0, trimmedArray, 0, count+1);
                ByteString packetString = ByteString.copyFrom(trimmedArray);
                Chatproto.Data packet = Chatproto.Data.newBuilder().setSeqNo(packet_num).setData(packetString).setIsLast(isLast).setTypeValue(2).build();
                packetDataList.add(packet);
            }else{
                count++;
            }
        }
        if(packet_num >= 4){
            this.lastPacket = 4;
        }else{
            this.lastPacket = packetDataList.size();
            System.out.println(lastPacket);
        }
    }

    private void sendWindow() {
        System.out.println("Resent window"+expSeqNo+"-"+lastPacket);
        for(int i = expSeqNo; i <= lastPacket; i++){
            sendPacket(packetDataList.get(i - 1));
        }
    }

    private void sendPacket(Chatproto.Data packet) {
        Random r = new Random();

        float chance = r.nextFloat();

        if (chance <= 0.25f){
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
                System.out.println("Sent packet: "+packet.getSeqNo());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private byte[] createHistoryArray(){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            history.writeDelimitedTo(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outputStream.toByteArray();
    }

    @Override
    public void run() {
        synchronized (this) {

            byte[] historyArray = createHistoryArray();
            createPackets(historyArray);
            sendWindow();
            int numPackets = getNumPackets();
            int resentWindow = 0;
            while (running) {
                try {
                    this.wait(2000);
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

    private void finish() {
        running = false;
        udpHandler.removeSender(ip + port);
    }

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
