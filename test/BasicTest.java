
/**
 * UdpChannel BasicTest, MIT (c) 2025 miktim@mail.ru
 */
import java.io.IOException;
import static java.lang.Thread.sleep;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.miktim.udpchannel.UdpChannel;

public class BasicTest {

    static final int PORT = 8099;
    static final String INTF = "eth1"; // local interface name;

//    static final String MC_ADDRESS = "224.0.1.199"; // iana unassigned multicast
//    static final String MC_ADDRESS = "224.0.0.1"; // iana All Systems on this Subnet
    static final String MC_ADDRESS = "FF09::114"; // iana private experiment
    static final String MS_ADDRESS = "232.0.1.199"; //iana source-specific

    InetSocketAddress loopSoc; // loopback 127.0.0.1 socket
    InetSocketAddress bcastSoc; // 225.225.225.225 
    InetSocketAddress mcastSoc; // MC_ADDRESS socket
    InetSocketAddress specmcSoc; // MS_ADDRES socket
    InetSocketAddress[] sockets;

    BasicTest() throws UnknownHostException {

        loopSoc = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT);
        bcastSoc = new InetSocketAddress(InetAddress.getByName("255.255.255.255"), PORT);
        mcastSoc = new InetSocketAddress(InetAddress.getByName(MC_ADDRESS), PORT);
        specmcSoc = new InetSocketAddress(InetAddress.getByName(MS_ADDRESS), PORT);
        sockets = new InetSocketAddress[]{loopSoc, bcastSoc, mcastSoc, specmcSoc};
    }

    void log(Object msg) {
        System.out.println(String.valueOf(msg));
    }
    UdpChannel.ChannelHandler chHandler = new UdpChannel.ChannelHandler() {
        @Override
        public void onStart(UdpChannel uc) {
            log("onStart");
        }

        @Override
        public void onPacket(UdpChannel uc, byte[] data) {
            log("onPacket: " + new String(data));
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
            log("onPacket: " + new String(data));
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
        if (!UdpChannel.isAvailable(PORT)) {
            log("Port unavailible: " + PORT);
            System.exit(1);
        }
        UdpChannel uc;
        for (InetSocketAddress remote : sockets) {
            uc = new UdpChannel(remote, INTF);
            log("\n\n"+uc);
            try {
                uc.receive(scHandler);
                uc.send("Send/receive unicast OK".getBytes());
                sleep(200);
            } catch (IOException e) {
                log(uc.getRemote().getAddress()+" "+ e.getClass());
            }
            uc.close();
        }
    }
}
