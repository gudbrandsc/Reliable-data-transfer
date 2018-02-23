package cs682;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @author gudbrand schistad
 * Zookeeper class that is used for all zookeeper related operations
 */
class ZookeeperInstance {
//2181
    private static final int PORT = 2181;
    private static final String HOST = "mc01";
    private static final String GROUP = "/CS682_Chat";
    private static String member;
    private static Chatproto.ZKData data;
    private String udpPort;
    private ZooKeeper zk;

    /**
     * Constructor
     * @param member name of the user
     * @param data ZKData object of protocol buffer
     */
    public ZookeeperInstance(String member, Chatproto.ZKData data, String udpPort){
        ZookeeperInstance.member = member;
        ZookeeperInstance.data = data;
        this.udpPort = udpPort;
        connectToZookeeper();
    }

    /**
     * Class that connects to zookeeper and calls the method that creates a znode of the connection was a success
     */
    private void connectToZookeeper() {
        //Connect to ZK instance
        final CountDownLatch connectedSignal = new CountDownLatch(1);
        try {
            zk = new ZooKeeper( HOST + ":" + PORT, 4000, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                        connectedSignal.countDown();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Connecting...");
        try {
            connectedSignal.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Connected");
        createZnode();
    }

    /**
     * Creates a znode with the username that was specified in the args
     */
    private void createZnode(){
        try {
            zk.create(GROUP + member,
                    ZookeeperInstance.data.toByteArray(),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL);
            System.out.println("Joined group " + GROUP + member);

        } catch(KeeperException ke) {
            System.out.println("Unable to join group " + GROUP + " as " + member);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Prints a list of all the users, their ip and port to the console
     */
    public void listAllMembers() throws InterruptedException {
        //to list members of a group
        try {
            List<String> children = zk.getChildren(GROUP, false);
            for(String child: children) {
                System.out.println("----------------");

                System.out.println("name: " + child);

                //get data about each child
                Stat s = new Stat();
                byte[] raw = zk.getData(GROUP + "/" + child, false, s);
                if(raw != null) {
                    try {
                        Chatproto.ZKData zkData = Chatproto.ZKData.parseFrom(raw);
                        System.out.println("port: " + zkData.getPort());
                        System.out.println("ip: " + zkData.getIp());
                        System.out.println("udp: " + zkData.getUdpport());

                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }

                } else {
                    System.out.println("\tNO DATA");
                }
            }
        } catch(KeeperException ke) {
            System.out.println("Unable to list members of group " + GROUP);
        }
    }

    /**
     * Method that return a byte array containing the ip and port for a username
     * @param name of the user that you want the ZKData from
     * @return Byte array of ZKData object
     *
     */
    public byte[] getZnodeData(String name) {
        Stat s = new Stat();
        try {
            if(zk.exists(GROUP+name,false)!=null){
                byte[] raw = zk.getData(GROUP + name, false, s);
                return raw;
            }
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Method that return a list with all the usernames in the network
     * @return list of all usernames
     * @throws InterruptedException
     */
    public List<String> getAllMembers() throws InterruptedException {

        try {
            List<String> children = zk.getChildren(GROUP, false);
            return children;
        } catch(KeeperException ke) {
            System.out.println("Unable to list members of group " + GROUP);
        }
        return null;
    }
}

