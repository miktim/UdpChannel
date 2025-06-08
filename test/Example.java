/*
 * Example from https://docs.oracle.com/javase/8/docs/api/java/nio/channels/MulticastChannel.html
 */

import java.io.IOException;
import static java.lang.Thread.sleep;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import org.miktim.udpchannel.UdpChannel;

public class Example {
    public static final int PORT = 9099;
    
    public static void main(String[] args) throws IOException, InterruptedException {
        if (!UdpChannel.isAvailable(PORT)) {
            System.out.println("Port unavailable: " + PORT);
            System.exit(1);
        }
        
        // join multicast group on this interface, and also use this
        // interface for outgoing multicast datagrams
        NetworkInterface ni = NetworkInterface.getByName("eth1");

        DatagramChannel dc = DatagramChannel.open(StandardProtocolFamily.INET)
                .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                .bind(new InetSocketAddress(PORT))
                .setOption(StandardSocketOptions.IP_MULTICAST_IF, ni);

        InetAddress group = InetAddress.getByName("225.4.5.6");

        MembershipKey key = dc.join(group, ni);
        
        dc.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true); //enable loopback

        System.out.println(dc.isBlocking());
        dc.configureBlocking(false);

        dc.send(ByteBuffer.allocate(16), new InetSocketAddress(group, PORT));
        sleep(500);

        System.out.println(dc.receive(ByteBuffer.allocate(16)));

    }
}
