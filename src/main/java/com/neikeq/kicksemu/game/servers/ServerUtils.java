package com.neikeq.kicksemu.game.servers;

import com.neikeq.kicksemu.config.Tips;
import com.neikeq.kicksemu.game.characters.PlayerInfo;
import com.neikeq.kicksemu.game.clubs.ClubManager;
import com.neikeq.kicksemu.game.clubs.MemberInfo;
import com.neikeq.kicksemu.game.sessions.Session;
import com.neikeq.kicksemu.network.packets.in.ClientMessage;
import com.neikeq.kicksemu.network.packets.out.MessageBuilder;
import com.neikeq.kicksemu.storage.ConnectionRef;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServerUtils {

    public static void serverList(Session session, ClientMessage msg) {
        short filter = msg.readShort();
        session.send(MessageBuilder.serverList(getServerList(filter), (short) 0));
    }

    public static void serverInfo(Session session, ClientMessage msg) {
        int playerId = session.getPlayerId();

        short serverId = msg.readShort();
        short level = PlayerInfo.getLevel(playerId);

        short result = 0;

        // TODO Check if character can access private server... Reject code is 251
        int clubId = MemberInfo.getClubId(playerId);

        if ((ServerInfo.getType(serverId) == ServerType.CLUB) &&
                ((clubId <= 0) || !ClubManager.clubExist(clubId))) {
            // Cannot join a club server without being a club member
            result = -4;
        } else if ((level < ServerInfo.getMinLevel(serverId)) ||
                (level > ServerInfo.getMaxLevel(serverId))) {
            // Player does not meet the level requirements
            result = -3;
        } else if (ServerInfo.getMaxUsers(serverId) <=
                ServerInfo.getConnectedUsers(serverId)) {
            // Server is full
            result = -2;
        }

        session.send(MessageBuilder.serverInfo(serverId, result));
    }

    private static List<Short> getServerList(short filter) {
        final String query = "SELECT id FROM servers WHERE filter = ?";

        try (ConnectionRef con = ConnectionRef.ref();
             PreparedStatement stmt = con.prepareStatement(query)) {
            stmt.setShort(1, filter);

            try (ResultSet rs = stmt.executeQuery()) {
                List<Short> servers = new ArrayList<>();

                while (rs.next()) {
                    servers.add(rs.getShort("id"));
                }

                return servers;
            }
        } catch (SQLException e) {
            return new ArrayList<>();
        }
    }

    public static boolean serverExist(short id) {
        final String query = "SELECT 1 FROM servers WHERE id = ? LIMIT 1";

        try (ConnectionRef con = ConnectionRef.ref();
             PreparedStatement stmt = con.prepareStatement(query)) {
            stmt.setShort(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public static void insertServer(ServerBase base) throws SQLException {
        final String query = "INSERT INTO servers (id, filter, name, address, port, min_level," +
                " max_level, max_users, type," +
                " exp_factor, point_factor, kash_factor, practice_rewards)" +
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (ConnectionRef con = ConnectionRef.ref();
             PreparedStatement stmt = con.prepareStatement(query)) {
            stmt.setShort(1, base.getId());
            stmt.setShort(2, base.getFilter());
            stmt.setString(3, base.getName());
            stmt.setString(4, base.getAddress());
            stmt.setShort(5, base.getPort());
            stmt.setByte(6, base.getMinLevel());
            stmt.setByte(7, base.getMaxLevel());
            stmt.setShort(8, base.getMaxUsers());
            stmt.setString(9, base.getType().name());
            stmt.setInt(10, base.getExpFactor());
            stmt.setInt(11, base.getPointFactor());
            stmt.setInt(12, base.getCashFactor());
            stmt.setBoolean(13, base.isPracticeRewards());

            stmt.executeUpdate();
        }
    }

    public static void updateServer(ServerBase base) throws SQLException {
        final String query = "UPDATE servers SET filter=?, name=?, address=?, port=?, min_level=?," +
                " max_level=?, max_users=?, connected_users=?, online=?, type=?, exp_factor=?," +
                " point_factor=?, kash_factor=?, practice_rewards=? WHERE id=?";


        try (ConnectionRef con = ConnectionRef.ref();
             PreparedStatement stmt = con.prepareStatement(query)) {
            stmt.setShort(1, base.getFilter());
            stmt.setString(2, base.getName());
            stmt.setString(3, base.getAddress());
            stmt.setShort(4, base.getPort());
            stmt.setByte(5, base.getMinLevel());
            stmt.setByte(6, base.getMaxLevel());
            stmt.setShort(7, base.getMaxUsers());
            stmt.setShort(8, (short) 0);
            stmt.setBoolean(9, true);
            stmt.setString(10, base.getType().name());
            stmt.setInt(11, base.getExpFactor());
            stmt.setInt(12, base.getPointFactor());
            stmt.setInt(13, base.getCashFactor());
            stmt.setBoolean(14, base.isPracticeRewards());
            stmt.setShort(15, base.getId());

            stmt.executeUpdate();
        }
    }

    public static void nextTip(Session session) {
        session.send(MessageBuilder.nextTip(Tips.getNext(), (short) 0));
    }
}
