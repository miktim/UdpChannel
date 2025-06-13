/**
 * UdpChannel package, MIT (c) 2025 miktim@mail.ru
 * Manage UDP via DatagramChannel class
 *
 * Created 2025-05-28
 */
package org.miktim.udpchannel;

import java.io.Closeable;
import java.io.IOException;
import static java.lang.Thread.sleep;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.MulticastChannel;
import java.nio.channels.SelectionKey;
import java.util.Arrays;

public final class UdpChannel implements Closeable, AutoCloseable {

    public static final String VERSION = "2.1.0";
    private String mode;
    private DatagramChannel channel;
    private InetSocketAddress remoteSocket;

    public static boolean isAvailable(int port) {
// https://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
        try (DatagramChannel ch = DatagramChannel.open();) {
            InetSocketAddress soc = new InetSocketAddress(InetAddress.getByName("255.255.255.255"), port);
            ch.setOption(StandardSocketOptions.SO_BROADCAST, true);
            ch.bind(new InetSocketAddress(port));
            ch.send(ByteBuffer.allocate(10), soc);
            sleep(200);
            ch.configureBlocking(false);
            if (ch.receive(ByteBuffer.allocate(10)) != null) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    public static boolean seemsBroadcast(InetAddress addr) {
        if (addr.isMulticastAddress()) {
            return false;
        }
        byte[] b = addr.getAddress();
        return b.length == 4 && (b[3] == (byte) 255);
    }

    void channelByMode() throws IOException {
        mode = mode.toUpperCase();
        if (mode.equals("AUTO")) {
            channel = DatagramChannel.open(
                    remoteSocket.getAddress() instanceof Inet6Address
                    ? StandardProtocolFamily.INET6 : StandardProtocolFamily.INET);
        } else if (mode.equals("DEFAULT")) {
            channel = DatagramChannel.open();
        } else {
            for (ProtocolFamily pf : StandardProtocolFamily.values()) {
                if (pf.name().toUpperCase().equals(mode)) {
                    channel = DatagramChannel.open(pf);
                    return;
                }
            }
            throw new IllegalArgumentException();
        }
    }

    public UdpChannel(String mode, InetSocketAddress remoteSoc, NetworkInterface intf)
            throws IOException {
        this.mode = mode;
        remoteSocket = remoteSoc;
        channelByMode();
        if (intf != null) {
            setNetworkInterface(intf);
        }

        setBroadcast(true);
        setReuseAddress(true);
        setLoopback(true); // disable multicast loopback
        if (isMulticast()) {
            bind();
        }
    }

    public UdpChannel(String mode, InetAddress remoteAddr, int remotePort, String intfName)
            throws IOException {
        this(mode, new InetSocketAddress(remoteAddr, remotePort), intfName == null ? null : NetworkInterface.getByName(intfName));
    }

    public UdpChannel(InetSocketAddress remoteSoc, NetworkInterface intf)
            throws IOException {
        this("AUTO", remoteSoc, intf);
    }

    public UdpChannel(InetAddress remoteAddr, int remotePort, String intfName)
            throws IOException {
        this("AUTO", remoteAddr, remotePort, intfName);
    }

    public DatagramChannel getChannel() {
        return channel;
    }

    public String getMode() {
        return mode;
    }

    public DatagramSocket getSocket() {
        return channel.socket();
    }

    public UdpChannel bind() throws IOException {
        if (!isBound()) {
            bind(new InetSocketAddress(remoteSocket.getPort()));
        }
        return this;
    }

    public UdpChannel bind(InetSocketAddress soc) throws IOException {
        channel.bind(soc);
        return this;
    }

    public boolean isBound() throws IOException {
        return channel.getLocalAddress() != null;
    }

    public boolean validWrite() {
        return (channel.validOps() & SelectionKey.OP_WRITE) > 0;
    }

    public boolean validRead() {
        return (channel.validOps() & SelectionKey.OP_READ) > 0;
    }

    public InetSocketAddress getLocal() throws IOException {
        NetworkInterface ni = getNetworkInterface();
        if (ni == null) {
            return new InetSocketAddress(remoteSocket.getPort());
        }
        Class cls = remoteSocket.getAddress().getClass();//tinet6channel ? Inet6Address.class : Inet4Address.class;
        for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
            if (ia.getAddress().getClass().equals(cls)) {
                return new InetSocketAddress(ia.getAddress(), remoteSocket.getPort());
            }
        }
        throw new SocketException();
    }

    public MembershipKey joinGroup() throws IOException {
        return channel.join(remoteSocket.getAddress(), getNetworkInterface());
    }

    public MembershipKey joinGroup(InetAddress source) throws IOException {
        return channel.join(remoteSocket.getAddress(), getNetworkInterface(), source);
    }

    /*
    public int send(byte[] buf, int off, int len) throws IOException {
        bind();
        return channel.send(ByteBuffer.wrap(buf, off, len), remoteSocket);
    }
     */
    public int send(byte[] buf, SocketAddress target) throws IOException {
        if (!isBound()) {
            bind();
        }
        return channel.send(ByteBuffer.wrap(buf), target);
    }

    public int send(byte[] buf) throws IOException {
        return send(buf, remoteSocket);
    }

    public void send(DatagramPacket dp) throws IOException {
        bind();
        if (dp.getAddress() == null) {
            dp.setSocketAddress(remoteSocket);
        }
        send(Arrays.copyOfRange(dp.getData(), dp.getOffset(), dp.getLength()),
                dp.getSocketAddress());
    }

    public void socketSend(DatagramPacket dp) throws IOException {
        bind();
        if (dp.getAddress() == null) {
            dp.setSocketAddress(remoteSocket);
        }
        getSocket().send(dp);
    }

    public interface Handler {

        void onStart(UdpChannel uc);

        void onError(UdpChannel uc, Exception e);

        void onClose(UdpChannel uc); // called before closing datagram channel

        void onPacket(UdpChannel uc, DatagramPacket dp);
    }

    public interface ChannelHandler extends Handler {

    }

    public interface SocketHandler extends Handler {

    }

    private UdpChannel.Handler handler;
    private boolean isRunning;
    private int payloadSize = 1500;

    public boolean isReceiving() {
        return isRunning;
    }

    public UdpChannel setPayloadSize(int size) {
        payloadSize = size;
        return this;
    }

    public int getPayloadSize() {
        return payloadSize;
    }

    class ChannelListenr extends Thread {

        UdpChannel uch;

        ChannelListenr(UdpChannel udpChannel) {
            uch = udpChannel;
        }

        @Override
        public void run() {
            uch.isRunning = true;
            uch.handler.onStart(uch);
            while (uch.isReceiving() && uch.channel.isOpen()) {
                try {
                    if (uch.handler instanceof SocketHandler) {
                        DatagramPacket dp
                                = new DatagramPacket(new byte[uch.payloadSize], uch.payloadSize);
                        uch.getSocket().receive(dp);
                        ((SocketHandler) uch.handler).onPacket(uch, dp);
                    } else {
                        ByteBuffer buf = ByteBuffer.allocate(uch.payloadSize);
//                        int len = ch.channel.read(buf);
//                        ((ChannelHandler) ch.handler).onPacket(ch, Arrays.copyOf(buf.array(), len));
                        SocketAddress soc = uch.channel.receive(buf);
                        if (soc == null) {
                            continue;
                        }
                        buf.flip(); // Prepare for reading
                        byte[] data = new byte[buf.remaining()];
                        buf.get(data);
                        DatagramPacket dp = new DatagramPacket(data, data.length, soc);
                        ((Handler) uch.handler).onPacket(uch, dp);
                    }
                } catch (java.net.SocketTimeoutException e) {
                } catch (Exception e) {
                    if (!isReceiving() || !uch.channel.isOpen()) { // !(isReceivimg() && channel.isOpen())
                        break;
                    }
                    try {
                        uch.handler.onError(uch, e);
                        uch.close();
                    } catch (Exception ignore) {
                    }
                }
            }
        }
    }

    public void receive(UdpChannel.Handler handler) throws IOException {
        if (isReceiving()) {
            throw new IllegalStateException("Already receiving");
        }
        if (handler == null) {
            throw new NullPointerException("No handler");
        }
        bind();
//        if (handler instanceof ChannelHandler && !isConnected()) {
//            connect();
//        }
        this.handler = handler;
        (new ChannelListenr(this)).start();
    }

    @Override
    public void close() throws IOException {
        if (isReceiving()) {
            isRunning = false;
            handler.onClose(this);
            handler = null;
        }
        try {
            ((MulticastChannel) channel).close();
        } catch (IOException ignore) {

        }
    }

    public InetSocketAddress getRemote() {
        return remoteSocket;
    }

    public final boolean isMulticast() throws IOException {
        return remoteSocket.getAddress().isMulticastAddress();
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

//TODO    UdpChannel setOption(SocketOption<T> name, T value) throws IOException {
//        channel.setOption(name, value);
//        return this;
//    }
    public UdpChannel setBroadcast(boolean on) throws IOException {
        channel.setOption(StandardSocketOptions.SO_BROADCAST, on);
        return this;
    }

    public boolean getBroadcast() throws IOException {
        return channel.getOption(StandardSocketOptions.SO_BROADCAST);
    }

    public UdpChannel setReuseAddress(boolean on) throws IOException {
        channel.setOption(StandardSocketOptions.SO_REUSEADDR, on);
        return this;
    }

    public boolean getReuseAddress() throws IOException {
        return channel.getOption(StandardSocketOptions.SO_REUSEADDR);
    }
    public int getReceiveBufferSize() throws IOException {
       return channel.getOption(StandardSocketOptions.SO_RCVBUF); 
    }
    public UdpChannel setReceiveBufferSize(int size) throws IOException {
        channel.setOption(StandardSocketOptions.SO_RCVBUF, size);
        return this;
    }
    public int getSendBufferSize() throws IOException {
       return channel.getOption(StandardSocketOptions.SO_SNDBUF); 
    }
    public UdpChannel setSendBufferSize(int size) throws IOException {
        channel.setOption(StandardSocketOptions.SO_SNDBUF, size);
        return this;
    }
    public UdpChannel setLoopback(boolean enable) throws IOException {
        channel.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, enable);
        return this;
    }
    public boolean getLoopback() throws IOException {
        return channel.getOption(StandardSocketOptions.IP_MULTICAST_LOOP);
    }
    public UdpChannel setLoopbackMode(boolean disable) throws IOException {
        return setLoopback(!disable);
    }
    public boolean getLoopbackMode() throws IOException {
        return !getLoopback();
    }
    public UdpChannel setTimeToLive(int ttl) throws IOException {
        channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, ttl);
        return this;
    }

    public int getTimeToLive() throws IOException {
        return channel.getOption(StandardSocketOptions.IP_MULTICAST_TTL);
    }

    public NetworkInterface getNetworkInterface() throws IOException {
        return (NetworkInterface) channel.getOption(StandardSocketOptions.IP_MULTICAST_IF);
    }

    public UdpChannel setNetworkInterface(NetworkInterface intf) throws IOException {
        channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, intf);
        return this;
    }

    public boolean isConnected() {
        return channel.isConnected();
    }

    public UdpChannel connect() throws IOException {
        channel.connect(remoteSocket);
        return this;
    }

    public UdpChannel disconnect() throws IOException {
        channel.disconnect();
        return this;
    }

    String addressType(InetAddress ia) {
        if (ia.isSiteLocalAddress()) {
            return "SL";
        }
        if (ia.isAnyLocalAddress()) {
            return "AL";
        }
        if (ia.isLoopbackAddress()) {
            return "LO";
        }
        if (ia.isLinkLocalAddress()) {
            return "LL";
        }
        if (ia.isMCGlobal()) {
            return "MCG";
        }
        if (ia.isMCLinkLocal()) {
            return "MCL";
        }
        if (ia.isMCNodeLocal()) {
            return "MCN";
        }
        if (ia.isMCOrgLocal()) {
            return "MCO";
        }
        if (ia.isMCSiteLocal()) {
            return "MCS";
        }
        if (ia.isMulticastAddress()) {
            return "MC";
        }
        return "G";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        DatagramChannel channel = getChannel();
        sb.append(mode);
        try {
            sb.append(String.format(" UdpChannel remote: %s %s bound to: %s\n\r",
                    addressType(remoteSocket.getAddress()), getRemote(), getChannel().getLocalAddress()));
            sb.append("Options:\r\n");
            sb.append(String.format("SO_SNDBUF: %d SO_RCVBUF: %d SO_REUSEADDR: %b SO_BROADCAST: %b\n\r",
                    getSendBufferSize(),
                    getReceiveBufferSize(),
                    getReuseAddress(),
                    getBroadcast()));
            NetworkInterface intf = getNetworkInterface();
            sb.append(String.format("IP_TOS: %d IP_MULTICAST_IF: %s IP_MULTICAST_TTL: %d IP_MULTICAST_LOOP: %b",
                    channel.getOption(StandardSocketOptions.IP_TOS),
                    intf != null ? intf.getDisplayName() : "null",
                    getTimeToLive(),
                    getLoopback()));
        } catch (IOException e) {
            sb.append(e.getClass().getName());
        }
        return sb.toString();
    }

}
