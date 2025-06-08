/**
 * UdpChannel BasicTest, MIT (c) 2025 miktim@mail.ru
 * Autodetect protocol family, native handler and sender
 */
import java.io.IOException;
import static java.lang.String.format;
import static java.lang.Thread.sleep;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import org.miktim.udpchannel.UdpChannel;

public class BasicTest {
    boolean nativeReceiver = true; // true - DatagramChannel, false - DatagramSocket
    boolean nativeSender = true;   // true - DatagramChannel, false - DatagramSocket

    static final int PORT = 9099;
    static final String INTF = "eth1"; // local interface name;
    
    static final String MU_ADDRESS = "224.0.1.191"; // iana unassigned multicast
//    static final String MC_ADDRESS = "224.0.0.1"; // iana All Systems on this Subnet
    static final String MC_ADDRESS = "FF09::114"; // iana private experiment
//    static final String MS_ADDRESS = "232.0.1.199"; //iana source-specific

    InetSocketAddress loopSoc; // loopback 127.0.0.1 socket
    InetSocketAddress hostSoc; // localhost
    InetSocketAddress bcastSoc; // broadcast 225.225.225.225 
    InetSocketAddress mcastSoc; // MC_ADDRESS socket
    InetSocketAddress freemcSoc; // MU_ADDRES socket
    InetSocketAddress wildSoc; // 0.0.0.0

    InetSocketAddress[] sockets;

    BasicTest() throws UnknownHostException, SocketException {

        loopSoc = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT);
        bcastSoc = new InetSocketAddress(InetAddress.getByName("255.255.255.255"), PORT);
        mcastSoc = new InetSocketAddress(InetAddress.getByName(MC_ADDRESS), PORT);
        freemcSoc = new InetSocketAddress(InetAddress.getByName(MU_ADDRESS), PORT);
        InetAddress hostAddr = getInet4Address(NetworkInterface.getByName(INTF));
        hostSoc = new InetSocketAddress(hostAddr, PORT);
        wildSoc = new InetSocketAddress(PORT);

        sockets = new InetSocketAddress[]{loopSoc, hostSoc, bcastSoc, wildSoc, mcastSoc, freemcSoc};
    }

    void log(Object msg) {
        System.out.println(String.valueOf(msg));
    }

    static InetAddress getInet4Address(NetworkInterface ni) {
        if (ni == null) {
            return null;
        }
        Enumeration<InetAddress> iaEnum = ni.getInetAddresses();
        while (iaEnum.hasMoreElements()) {
            InetAddress ia = iaEnum.nextElement();
            if (ia instanceof Inet4Address) {
                return ia;
            }
        }
        return null;
    }

    UdpChannel.Handler chHandler = new UdpChannel.Handler() {
        @Override
        public void onStart(UdpChannel uc) {
            log("onStart");
        }

        @Override
        public void onPacket(UdpChannel uc, DatagramPacket dp) {
//            log("onPacket: " + new String(data));
            byte[] data = Arrays.copyOfRange(dp.getData(), dp.getOffset(), dp.getLength());
            log(format("onPacket: %s %s", new String(data), dp.getSocketAddress()));
        }

        @Override
        public void onError(UdpChannel uc, Exception e) {
            log("onError:");
            e.printStackTrace();
        }

        @Override
        public void onClose(UdpChannel uc) {
            log("onClose");
        }
    };
    UdpChannel.SocketHandler scHandler = new UdpChannel.SocketHandler() {
        @Override
        public void onStart(UdpChannel uc) {
            log("onStart");
        }

        @Override
        public void onPacket(UdpChannel uc, DatagramPacket dp) {
            byte[] data = Arrays.copyOfRange(dp.getData(), dp.getOffset(), dp.getLength());
            log(format("onPacket: %s %s", new String(data), dp.getSocketAddress()));
        }

        @Override
        public void onError(UdpChannel uc, Exception e) {
            log("onError:");
            e.printStackTrace();
        }

        @Override
        public void onClose(UdpChannel uc) {
            log("onClose");
        }
    };

    public static void main(String[] args) throws IOException, InterruptedException {
        (new BasicTest()).run();
    }

    void run() throws IOException, InterruptedException {
        log(format("UdpChannel %s basic test", UdpChannel.VERSION));
        log(format("Receiver: %s Sender: %s",
                nativeReceiver ? "native" : "socket",
                nativeSender ? "native" : "socket"));
        if (!UdpChannel.isAvailable(PORT)) {
            log("Port unavailable: " + PORT);
            System.exit(1);
        }
        UdpChannel uc;

        for (InetSocketAddress remote : sockets) {
            uc = new UdpChannel("AUTO",remote, NetworkInterface.getByName(INTF));
            uc.bind();
            uc.setMulticastLoop(true); // enable loopback
            log("\n" + uc);
            if (uc.isMulticast()) {
                log(uc.joinGroup());
            }
            log(format("Valid read: %b write: %b", uc.validRead(), uc.validWrite()));
            try {
                uc.receive(nativeReceiver ? chHandler : scHandler);
                byte[] msg = "Send/receive OK".getBytes();
                sleep(200);
                if(nativeSender) {
                    uc.send(msg);
                } else {
                    DatagramPacket dp = new DatagramPacket(msg, msg.length);
                    uc.socketSend(dp);
                }    
                sleep(300);
                
            } catch (IOException e) {
                e.printStackTrace();
//                log(uc.getRemote().getAddress()+" "+ e.getClass());
            }

            uc.close();
        }
        log("\nCompleted");
    }
}
