package com.neikeq.kicksemu.game.users;

import com.neikeq.kicksemu.game.characters.PlayerInfo;
import com.neikeq.kicksemu.game.characters.CharacterUtils;
import com.neikeq.kicksemu.game.characters.types.Position;
import com.neikeq.kicksemu.game.characters.types.StatsInfo;
import com.neikeq.kicksemu.game.characters.types.PlayerStats;
import com.neikeq.kicksemu.game.sessions.Session;
import com.neikeq.kicksemu.game.sessions.SessionInfo;
import com.neikeq.kicksemu.network.packets.in.ClientMessage;
import com.neikeq.kicksemu.network.packets.out.MessageBuilder;
import com.neikeq.kicksemu.storage.MySqlManager;
import com.neikeq.kicksemu.utils.mutable.MutableInteger;

import java.sql.Connection;
import java.sql.SQLException;

public class UserManager {

    public static void tcpPing(Session session, ClientMessage msg) {
        int ping = msg.readInt();

        session.setPing(ping);
        session.setPingState((byte)0);
    }

    public static void certifyExit(Session session) {
        session.send(MessageBuilder.certifyExit());

        SessionInfo.remove(session.getSessionId());

        session.close();
    }

    public static void instantExit(Session session) {
        session.send(MessageBuilder.instantExit());

        session.close();
    }

    public static void gameExit(Session session) {
        int playerId = session.getPlayerId();

        session.send(MessageBuilder.gameExit(session.getRemoteAddress(), playerId));

        session.close();
    }

    public static void characterInfo(Session session) {
        int userId = session.getUserId();

        try (Connection con = MySqlManager.getConnection()) {
            int[] slots = new int[]{
                    UserInfo.getSlotOne(userId, con),
                    UserInfo.getSlotTwo(userId, con),
                    UserInfo.getSlotThree(userId, con)
            };

            for (short i = 0; i < slots.length; i++) {
                if (slots[i] > 0) {
                    session.send(MessageBuilder.characterInfo(slots[i], userId, i, con));
                }
            }
        } catch (SQLException ignored) {}
    }

    public static void updateSettings(Session session, ClientMessage msg) {
        UserSettings settings = UserSettings.fromMessage(msg);

        byte result = 0;

        if (settings.isValid()) {
            UserInfo.setSettings(settings, session.getUserId());
        } else {
            result = (byte)255;
        }

        session.send(MessageBuilder.updateSettings(result));
    }

    public static void choiceCharacter(Session session, ClientMessage msg) {
        int charId = msg.readInt();
        int userId = session.getUserId();

        byte result = 0;

        if (UserInfo.hasCharacter(charId, userId) && CharacterUtils.characterExist(charId)) {
            if (!PlayerInfo.isBlocked(charId)) {
                SessionInfo.setPlayerId(charId, session.getSessionId());
                session.setPlayerId(charId);
            } else {
                result = (byte)255; // System problem
            }
        } else {
            result = (byte)254; // Character does not exist
        }

        session.send(MessageBuilder.choiceCharacter(charId, result));
    }

    public static void upgradeCharacter(Session session, ClientMessage msg) {
        msg.readInt();
        int userId = session.getUserId();
        int playerId = msg.readInt();
        short position = msg.readShort();

        if (UserInfo.hasCharacter(playerId, userId)) {
            byte result = 0;

            try (Connection con = MySqlManager.getConnection()) {
                short currentPosition = PlayerInfo.getPosition(playerId, con);

                if (Position.isValidNewPosition(currentPosition, position)) {
                    PlayerInfo.setPosition(position, playerId, con);
                    session.getPlayerCache().setPosition(position);

                    MutableInteger statsPoints = new MutableInteger(0);

                    PlayerStats stats = PlayerInfo.getStats(playerId, con);

                    PlayerStats.sumStats(StatsInfo.getInstance()
                            .getUpgradeStats().get(position), 1, stats, statsPoints);

                    PlayerInfo.setStats(stats, playerId, con);
                    PlayerInfo.sumStatsPoints((short)statsPoints.get(), playerId, con);

                } else {
                    result = -1;
                }
            } catch (SQLException ignored) {}

            session.send(MessageBuilder.upgradeCharacter(result));
        }
    }
}
