package cs682;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class PacketReciver implements Runnable{
    private boolean running = true;
    private DatagramSocket socket;
    private int expected_packet;
    private ConcurrentLinkedQueue<Chatproto.Data> packetList;
    private HistoryData historyData;
    private UdpHandler udpHandler;
    private Chatproto.ZKData zkData;
    private ByteString byteString = ByteString.EMPTY;
    private Random random = new Random();







    public PacketReciver(DatagramSocket socket, Chatproto.ZKData zkData, HistoryData historyData, UdpHandler udpHandler){
        this.socket = socket;
        this.zkData = zkData;
        this.expected_packet = 1;
        this.historyData = historyData;
        this.udpHandler = udpHandler;
        this.packetList =  new ConcurrentLinkedQueue<>();
    }

    private void sendRequest()  {

            try {
                Chatproto.Data req = Chatproto.Data.newBuilder().setTypeValue(0).build();
                ByteArrayOutputStream outstream = new ByteArrayOutputStream(1024);
                req.writeDelimitedTo(outstream);

                byte[] item = outstream.toByteArray();
                InetAddress ipAddress = InetAddress.getByName(zkData.getIp());
                int port = Integer.parseInt(zkData.getUdpport());
                DatagramPacket datagramPacket = new DatagramPacket(item, item.length, ipAddress, port);
                socket.send(datagramPacket);
                System.out.println("Sent request..");

            } catch (IOException e) {
                e.printStackTrace();
            }

    }


    private void sendACK(int seq_no) throws IOException {

        float chance = random.nextFloat();

        if (chance <= 0.10f){
            System.out.println("Did not send ACK for packet: "+seq_no);
        }else {
            Chatproto.Data ack = Chatproto.Data.newBuilder().setTypeValue(1).setSeqNo(seq_no).build();
            ByteArrayOutputStream outstream = new ByteArrayOutputStream(1024);
            ack.writeDelimitedTo(outstream);

            byte[] item = outstream.toByteArray();
            InetAddress ipAddress = InetAddress.getByName(zkData.getIp());
            int port = Integer.parseInt(zkData.getUdpport());
            DatagramPacket datagramPacket = new DatagramPacket(item, item.length, ipAddress, port);
            socket.send(datagramPacket);
            System.out.println("Sent ACK for packet: "+seq_no);
        }
    }

    public void addPacket(Chatproto.Data data){
        System.out.println("Received packet: "+ data.getSeqNo() + " expected packet: " + expected_packet);
        synchronized (this) {
            if(this.expected_packet == data.getSeqNo()){
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


    @Override
    public void run() {
        sendRequest();
        synchronized (this) {
            int numReqest = 0;
            while (running) {
                try {
                    this.wait(5000);
                    if(!packetList.isEmpty()){
                        for (Chatproto.Data packet : packetList) {
                            if (packet.getSeqNo() == expected_packet) {
                                byteString = byteString.concat(packet.getData());
                                if (packet.getIsLast()) {
                                    ByteArrayInputStream inputStream = new ByteArrayInputStream(byteString.toByteArray());
                                    Chatproto.History newHistory = Chatproto.History.parseDelimitedFrom(inputStream);
                                    historyData.updateHistory(newHistory.getHistoryList());
                                    finish();
                                    System.out.println("List was updated");
                                    System.out.println("Command");
                                }
                                expected_packet++;
                            }
                            packetList.remove(packet);
                        }
                    }else{
                        finish();
                        System.out.println("Session timed out..");
                    }
                    //Add new data to bytes array
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private void finish(){
        running = false;
        String key = zkData.getIp()+zkData.getUdpport();
        udpHandler.removeReceiver(key);
    }

}
