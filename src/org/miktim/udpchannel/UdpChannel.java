/**
 * UdpChannel package, MIT (c) 2025 miktim@mail.ru
 * Manage UDP via DatagramChannel
 *
 * Created 2025-05-28
 */
package org.miktim.udpchannel;

import java.io.Closeable;
import java.io.IOException;
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
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.MulticastChannel;
import java.util.Arrays;

public final class UdpChannel extends Thread implements Closeable, AutoCloseable {

    public static final String VERSION = "1.0.1";
    private ProtocolFamily protocolFamily;
    private DatagramChannel channel;
    private InetSocketAddress remoteSocket;
    private NetworkInterface ni;

    public static boolean isAvailable(int port) {
// https://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
        DatagramSocket soc;
        try {
            soc = new DatagramSocket(port);
        } catch (SocketException e) {
            return false;
        }
        soc.close();
        return true;
    }

    public static boolean seemsBroadcast(InetAddress addr) {
        if (addr.isMulticastAddress()) {
            return false;
        }
        byte[] b = addr.getAddress();
        return b.length == 4 && (b[3] == (byte) 255);
    }

    UdpChannel prepareChannel(ProtocolFamily pf, InetSocketAddress remote, NetworkInterface intf)
            throws IOException {
        protocolFamily = pf;
        remoteSocket = remote;
        ni = intf;
        channel = DatagramChannel.open(protocolFamily);
        if (seemsBroadcast(remote.getAddress())) setBroadcast(true);
        setReuseAddress(true);
        setMulticastInterface(ni);
        bind();
        return this;
    }

    public UdpChannel(InetSocketAddress remote, NetworkInterface intf)
            throws IOException {
        prepareChannel(remote.getAddress() instanceof Inet6Address
                ? StandardProtocolFamily.INET6 : StandardProtocolFamily.INET,
                remote, intf);
    }

    public UdpChannel(InetSocketAddress remote, String intfName)
            throws IOException {
        this(remote, NetworkInterface.getByName(intfName));
    }

    public DatagramChannel getChannel() {
        return channel;
    }

    public UdpChannel setChannel(ProtocolFamily pf) throws IOException {
        this.channel.close();
        return prepareChannel(pf, remoteSocket, ni);
    }

    ProtocolFamily getProtocolFamily() {
        return protocolFamily;
    }

    public DatagramSocket getSocket() {
        return channel.socket();
    }

    void bind() throws IOException {
        if (!getSocket().isBound()) {
            channel.bind(new InetSocketAddress(remoteSocket.getPort()));
        }
    }

    public InetSocketAddress getLocal() throws SocketException {
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
//        setMulticastInterface(ni);
        return channel.join(remoteSocket.getAddress(), ni);
    }

    public MembershipKey joinGroup(InetAddress source) throws IOException {
//        setMulticastInterface(ni);
        return channel.join(remoteSocket.getAddress(), ni, source);
    }

    public int send(byte[] buf, int off, int len) throws IOException {
        return channel.send(ByteBuffer.wrap(buf, off, len), remoteSocket);
    }

    public int send(byte[] buf) throws IOException {
        return send(buf, 0, buf.length);
    }

    public void send(DatagramPacket dp) throws IOException {
        if(dp.getAddress() == null) dp.setSocketAddress(remoteSocket);
        getSocket().send(dp);
    }
    public interface Handler {

        void onStart(UdpChannel uc);

        void onError(UdpChannel uc, Exception e);

        void onClose(UdpChannel uc); // called before closing datagram socket
    }

    public interface ChannelHandler extends Handler {

        void onPacket(UdpChannel uc, byte[] data);
    }

    public interface SocketHandler extends Handler {

        void onPacket(UdpChannel uc, DatagramPacket dp);
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

    public void receive(UdpChannel.Handler handler) throws IOException {
        if (isReceiving()) {
            throw new IllegalStateException("Already receiving");
        }
        if (handler instanceof ChannelHandler) {
            connect();
        }
        this.handler = handler;
        start();
    }

    @Override
    public void start() {
        if (handler == null) {
            throw new NullPointerException("No handler");
        }
        super.start();
    }

    @Override
    public void run() {
        isRunning = true;
        handler.onStart(this);
        while (isReceiving() && channel.isOpen()) {
            try {
                if (handler instanceof ChannelHandler) {
                    ByteBuffer buf = ByteBuffer.allocate(payloadSize);
                    int len = channel.read(buf);
                    ((ChannelHandler) handler).onPacket(this, Arrays.copyOf(buf.array(), len));
                } else {
                    DatagramPacket dp
                            = new DatagramPacket(new byte[payloadSize], payloadSize);
                    getSocket().receive(dp);
                    ((SocketHandler) handler).onPacket(this, dp);
                }
            } catch (java.net.SocketTimeoutException e) {
            } catch (Exception e) {
                if (!isReceiving() || getSocket().isClosed()) { // !(isReceivimg() && channel.isOpen())
                    break;
                }
                try {
                    handler.onError(this, e);
//                    close();
                } catch (Exception ignore) {
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (isReceiving()) {
            isRunning = false;
            handler.onClose(this);
            handler = null;
        }
        try {
//            disconnect(); // HANG thread!
//            if (isMulticast()) {
            ((MulticastChannel) channel).close();
//            } else {
//                channel.close();
//            }
        } catch (IOException ignore) {

        }
    }

    public InetSocketAddress getRemote() {
        return remoteSocket;
    }

    public NetworkInterface getInterface() {
        return ni;
    }

    public NetworkInterface getMulticastInterface() throws IOException {
        return (NetworkInterface) channel.getOption(StandardSocketOptions.IP_MULTICAST_IF);
    }

    UdpChannel setMulticastInterface(NetworkInterface intf) throws IOException {
        if (intf != null) {
            channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, intf);
        }
        return this;
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

    public UdpChannel setLoopbackMode(boolean on) throws IOException {
        channel.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, on);
        return this;
    }

    public boolean getLoopbackMode() throws IOException {
        return channel.getOption(StandardSocketOptions.IP_MULTICAST_LOOP);
    }

    public UdpChannel setTimeToLive(int ttl) throws IOException {
        channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, ttl);
        return this;
    }

    public int getTimeToLive() throws IOException {
        return channel.getOption(StandardSocketOptions.IP_MULTICAST_TTL);
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
        sb.append(protocolFamily == null ? "Default" : protocolFamily.name());
        try {
            sb.append(String.format(" UdpChannel remote: %s %s bound to: %s\n\r",
                    addressType(remoteSocket.getAddress()), getRemote(), getChannel().getLocalAddress()));
            sb.append("Options:\r\n");
            sb.append(String.format("SO_SNDBUF: %d SO_RCVBUF: %d SO_REUSEADDR: %b SO_BROADCAST: %b\n\r",
                    channel.getOption(StandardSocketOptions.SO_SNDBUF),
                    channel.getOption(StandardSocketOptions.SO_RCVBUF),
                    getReuseAddress(),
                    getBroadcast()));
            NetworkInterface intf = getMulticastInterface();
            sb.append(String.format("IP_TOS: %d IP_MULTICAST_IF: %s IP_MULTICAST_TTL: %d IP_MULTICAST_LOOP: %b",
                    channel.getOption(StandardSocketOptions.IP_TOS),
                    intf != null ? intf.getDisplayName() : "null",
                    getTimeToLive(),
                    getLoopbackMode()));
        } catch (IOException e) {
            sb.append(e.getClass().getName());
        }
        return sb.toString();
    }

}
