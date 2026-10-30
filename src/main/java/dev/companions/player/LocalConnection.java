package dev.companions.player;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/** In-process endpoint: no socket, packet queue, authentication request or external service. */
public final class LocalConnection extends Connection {
    public LocalConnection() { super(PacketFlow.SERVERBOUND); }
    @Override public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {}
    @Override public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {}
    @Override public void setupOutboundProtocol(ProtocolInfo<?> protocol) {}
    @Override public void setListenerForServerboundHandshake(PacketListener listener) {}
    @Override public void setReadOnly() {}
    @Override public void handleDisconnection() {}
    @Override public void flushChannel() {}
    @Override public void disconnect(DisconnectionDetails details) {}
    @Override public boolean isConnected() { return true; }
    @Override public SocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 0); }
}
