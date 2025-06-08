/*
 * UdpChannelTest, MIT (c) 2025 miktim@mail.ru
 * Autodetect network protocol family, DatagramChannel sender, DatagramSocket receiver
 *   Enable the UDP_PORT in your firewall
 *   Linux: internal, external, public zones
 */

import java.io.IOException;
import static java.lang.String.format;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import org.miktim.udpchannel.UdpChannel;

public class UdpChannelTest {

    static final int PORT = 9099; // IANA registry: unused
    static final String REMOTE_ADDRESS = "192.168.0.105";
    static final String MULTICAST_ADDRESS = "224.0.1.191"; // IANA registry: unused global
    static final int SEND_DELAY = 300; // send datagram delay millis
// test closure timeout millis
    static final int TEST_TIMEOUT = 20000;
    static final String INTF = "eth1"; // interface name
    
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

    public static InetAddress getLocalHost() {
        try {
            Enumeration<NetworkInterface> niEnum = NetworkInterface.getNetworkInterfaces();
            while (niEnum.hasMoreElements()) {
                NetworkInterface ni = niEnum.nextElement();
                InetAddress ia = getInet4Address(ni);
                if (!(ia == null || ia.isLoopbackAddress())) {
                    return ia;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    static void log(Object obj) {
        System.out.println(String.valueOf(obj));
    }

    static void logOk(boolean ok) {
        log(ok ? "Ok" : "Something wrong...");
    }

    static int sent = 0;
    static int received = 0;
    static int errors = 0;

    static void testChannel(UdpChannel channel) throws IOException {
        testChannel(channel, 5);
    }

    static void testChannel(UdpChannel uc, int count) throws IOException {
        log("\r\n" + uc.toString());
        received = 0;
        errors = 0;
        byte[] payload = new byte[308];
        if(uc.isMulticast()) log(uc.joinGroup());
        uc.receive(handler);
        for (sent = 0; sent < count; sent++) {
            try {
                uc.send(payload);
                log(String.format("snt: %d %s:%d",
                        payload.length,
                        uc.getRemote().getAddress().toString(),
                        uc.getRemote().getPort()));
                Thread.sleep(SEND_DELAY);
            } catch (IOException ex) {
                errors++;
                log("err: " + ex);//ex.getMessage());
            } catch (InterruptedException ex) {
            }
        }
        log(String.format("Packets sent: %d received: %d Errors: %d", sent, received, errors));
        uc.close();
    }

    static UdpChannel.SocketHandler handler = new UdpChannel.SocketHandler() {
        @Override
        public void onStart(UdpChannel channel) {
        }

        @Override
        public void onClose(UdpChannel channel) {
        }

        @Override
        public void onPacket(UdpChannel uc, DatagramPacket packet) {
            received++;
            log(String.format("rcv: %d %s:%d",
                    packet.getLength(),
                    packet.getAddress(),
                    packet.getPort()));
            if (packet.getAddress().equals(REMOTE_ADDRESS)) { // echo host
                try { // echo packet
                    uc.getSocket().send(packet);
                } catch (IOException ex) {
                    errors++;
                    log("err: " + ex);
                }

            }
        }

        @Override
        public void onError(UdpChannel channel, Exception e) {
            errors++;
            log("err: " + e);
        }
    };

    public static void main(String[] args) throws Exception {

        InetAddress ias = InetAddress.getByName("0.0.0.0");
        InetAddress iab = InetAddress.getByName("255.255.255.255");
//        InetAddress iah = InetAddress.getLocalHost();
        InetAddress iah = getLocalHost();
        InetAddress ial = InetAddress.getByName("localhost");
        InetAddress iam = InetAddress.getByName(MULTICAST_ADDRESS);
        InetAddress iar = InetAddress.getByName(REMOTE_ADDRESS);

        log(format("UdpChannel %s test.",UdpChannel.VERSION));
        log("Receiver: socket Sender: native\n");
        log("UDP port: " + PORT);
        log("loopback: " + ial.toString());
        log("host: " + iah.toString());
        log("broadcast: " + iab.toString());
        log("multicast: " + iam.toString());
        log("remote: " + iar.toString());
        log("special: " + ias.toString());
        log("Test timeout: " + TEST_TIMEOUT);
        /*
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                log("Test terminated. Timeout.");
                timer.cancel();
                System.exit(0);
            }
        }, TEST_TIMEOUT);
         */
        if (!UdpChannel.isAvailable(PORT)) {
            log("\r\nUDP port " + PORT + " is unavailable! Exit.\r\n");
            System.exit(1);
        } else {
            log("\r\nUDP port " + PORT + " is available.");
        }

        InetSocketAddress remote = new InetSocketAddress(iar, PORT);
        try {
            UdpChannel channel1 = new UdpChannel(remote, NetworkInterface.getByName(INTF));
            UdpChannel channel2 = new UdpChannel(remote, NetworkInterface.getByName(INTF));
            channel1.close();
            channel2.close();
        } catch (IOException e) {
            log("Reused address failed. Exit.\r\n");
            System.exit(1);
        }
        UdpChannel channel1 = new UdpChannel(remote, NetworkInterface.getByName(INTF));
        try {
            channel1.setReuseAddress(false);
            UdpChannel channel2 = new UdpChannel(remote, NetworkInterface.getByName(INTF));
            channel1.close();
            channel2.close();
        } catch (IOException e) {
            channel1.close();
            log("Reused address Ok.\r\n");
        }

// test autocloseable  
        try (UdpChannel chn = new UdpChannel(remote,NetworkInterface.getByName(INTF))) {
            chn.setReuseAddress(false);
        }
        try {
            channel1 = new UdpChannel(remote,NetworkInterface.getByName(INTF));
        } catch (IOException ex) {
            log("AutoCloseable failed.");
        }

        channel1.send(new byte[1]);

// broadcast 
        remote = new InetSocketAddress(iab, PORT);
        channel1 = new UdpChannel(remote, NetworkInterface.getByName(INTF));
        channel1.setBroadcast(true);
        channel1.setMulticastLoop(false);
        testChannel(channel1,1); // test closes channel
        logOk(sent == 1 && received == 1 && errors == 0);

        UdpChannel channel0 = new UdpChannel(new InetSocketAddress(PORT), NetworkInterface.getByName(INTF));
        testChannel(channel0, 1);
        logOk(sent == 1 && received == 1 && errors == 0);

// send to loopback
        testChannel((new UdpChannel(new InetSocketAddress(ial, PORT), NetworkInterface.getByName(INTF))),1);
//                .setChannel(StandardProtocolFamily.INET6));
        logOk(sent == 1 && received == 1 && errors == 0);

// send to myself       
        testChannel((new UdpChannel(new InetSocketAddress(iah, PORT), NetworkInterface.getByName(INTF))),1);
        logOk(sent == 1 && received == 1 && errors == 0);

// remote echo
        if (iar.isReachable(200)) {
            log(String.format("\r\nEcho host %s is reachable.", iar.toString()));
            testChannel((new UdpChannel(new InetSocketAddress(iar, PORT), NetworkInterface.getByName(INTF))));
            logOk(received > 0);
        } else {
            log(String.format("\r\nEcho host %s is unreachable.", iar.toString()));
        }

// send to multicast
        UdpChannel channel = (new UdpChannel(new InetSocketAddress(iam, PORT), NetworkInterface.getByName(INTF)))
//                .setBroadcast(true)
                .setMulticastLoop(false);
        testChannel(channel, 25);
        log(received > 0 ? "Ok" : "There is no one in the group");

//        timer.cancel();
        log("\nCompleted");
    }
}
