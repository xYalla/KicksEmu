package com.neikeq.kicksemu.game.sessions;

import com.neikeq.kicksemu.game.clubs.ClubManager;
import com.neikeq.kicksemu.game.clubs.UniformType;
import com.neikeq.kicksemu.game.lobby.Lobby;
import com.neikeq.kicksemu.game.lobby.LobbyManager;
import com.neikeq.kicksemu.game.rooms.enums.RoomLeaveReason;
import com.neikeq.kicksemu.game.rooms.RoomManager;
import com.neikeq.kicksemu.network.packets.out.ServerMessage;
import com.neikeq.kicksemu.game.users.UserInfo;

import com.neikeq.kicksemu.network.server.ServerManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.util.concurrent.ScheduledFuture;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class Session {

    private final Channel channel;
    private final Object locker = new Object();
    private final SessionCache sessionCache = new SessionCache(this);
    private final List<ByteBuf> packetsQueue = new ArrayList<>();

    private ScheduledFuture<?> udpPingFuture;

    private int userId = -1;
    private int playerId = -1;
    private int sessionId = -1;
    private int roomId = -1;
    private int ping = -1;
    private int udpPort = -1;

    private byte pingState;
    private long lastPingResponse;

    private UniformType equippedUniform = UniformType.NONE;

    private boolean authenticated;
    private boolean udpAuthenticated;
    private boolean observer;

    /**
     * Write a message to the channel without flushing.<br>
     * Client handler will flush the channel after reading is complete as seen in method:
     * {@link com.neikeq.kicksemu.network.server.tcp.ClientHandler#channelReadComplete}<br>
     * This increases the performance when writing multiple messages during a single reading.
     */
    public synchronized void send(ServerMessage msg) {
        packetsQueue.add(msg.getByteBuf(playerId).copy());
        msg.release();
    }

    /**
     * Write a message to the channel and flush after that.
     * Useful for chat messages and non-response messages.
     */
    public synchronized void sendAndFlush(ServerMessage msg)  {
        send(msg);
        flush();
    }

    public synchronized void flush() {
        final ByteBuf mergedPackets = ByteBufAllocator.DEFAULT.buffer().order(ByteOrder.LITTLE_ENDIAN);

        packetsQueue.forEach(packet -> {
            try {
                mergedPackets.writeBytes(packet);
            } finally {
                packet.release();
            }
        });

        packetsQueue.clear();

        if (getChannel().isOpen() && getChannel().isWritable()) {
            getChannel().writeAndFlush(mergedPackets);
        } else {
            mergedPackets.release();
        }
    }

    /** Called when the session leaved a room */
    public void onLeavedRoom() {
        roomId = -1;

        // If session is still alive, add it to the main lobby
        if (authenticated) {
            LobbyManager.addPlayer(playerId);
        }
    }

    /** If the session is inside a room, leave it */
    public boolean leaveRoom(RoomLeaveReason reason) {
        return RoomManager.getRoomById(roomId).map(room -> {
            boolean insideRoom = room.isPlayerIn(playerId);

            // If room exist and player is inside the room
            if (insideRoom) {
                room.removePlayer(this, reason);

                // Needed to notify club members in the same room
                if (reason == RoomLeaveReason.DISCONNECTED) {
                    roomId = room.getId();
                }
            }

            return insideRoom;
        }).orElse(false);
    }

    /** Returns the room lobby if player is inside a room, otherwise return main lobby */
    public Lobby getCurrentLobby() {
        return RoomManager.getRoomById(getRoomId())
                .map(room -> (Lobby) room.getRoomLobby())
                .orElse(LobbyManager.getMainLobby());
    }

    public void close() {
        if (getChannel().isOpen()) {
            // Ensure we have sent everything
            flush();
            // Close connection with the client
            getChannel().close();
        }

        // Close Udp Ping schedule
        if ((getUdpPingFuture() != null) && getUdpPingFuture().isCancellable()) {
            getUdpPingFuture().cancel(true);
        }

        // If session information were not yet cleared
        if (authenticated) {
            setAuthenticated(false);
            setUdpAuthenticated(false);

            // Reduce the session lifetime. The client has 30 seconds to authenticate
            SessionInfo.reduceExpiration(getSessionId());

            // Update user status on database
            UserInfo.setServer((short) -1, userId);
            UserInfo.setOnline(-1, userId);

            if (playerId > 0) {
                // Remove session from the list of connected clients
                ServerManager.removePlayer(playerId);
                // If session player is in the main lobby, leave it
                LobbyManager.removePlayer(playerId);
            }

            // If session player is inside a room, leave it
            leaveRoom(RoomLeaveReason.DISCONNECTED);

            ClubManager.onMemberConnectedStateChanged(this);

            sessionCache.clear();
        }
    }

    public Session(Channel channel) {
        this.channel = channel;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean value) {
        authenticated = value;
    }

    public Channel getChannel() {
        return channel;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int id) {
        userId = id;
    }

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int id) {
        playerId = id;
    }

    public boolean isUdpAuthenticated() {
        return udpAuthenticated;
    }

    public void setUdpAuthenticated(boolean udpAuthenticated) {
        this.udpAuthenticated = udpAuthenticated;
    }

    public InetAddress getRemoteAddress() {
        return ((InetSocketAddress) getChannel().remoteAddress()).getAddress();
    }

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public Object getLocker() {
        return locker;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public void setUdpPort(int udpPort) {
        this.udpPort = udpPort;
    }

    public boolean isObserver() {
        return observer;
    }

    public void setObserver(boolean observer) {
        this.observer = observer;
    }

    public int getPingRay() {
        int pingRay = 0; // red ping by default

        if (ping <= 100) {
            pingRay = 64; // green ping
        } else if (ping <= 100) {
            pingRay = 32; // yellow ping
        }

        return pingRay;
    }

    public void setPing(int ping) {
        this.ping = ping;
    }

    public byte getPingState() {
        return pingState;
    }

    public void setPingState(byte pingState) {
        this.pingState = pingState;
    }

    public SessionCache getCache() {
        return sessionCache;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public long getLastPingResponse() {
        return lastPingResponse;
    }

    public void setLastPingResponse(long lastPingResponse) {
        this.lastPingResponse = lastPingResponse;
    }

    public ScheduledFuture<?> getUdpPingFuture() {
        return udpPingFuture;
    }

    public void setUdpPingFuture(ScheduledFuture<?> udpPingFuture) {
        this.udpPingFuture = udpPingFuture;
    }

    public UniformType getEquippedUniform() {
        return equippedUniform;
    }

    public void setEquippedUniform(UniformType equippedUniform) {
        this.equippedUniform = equippedUniform;
    }
}
