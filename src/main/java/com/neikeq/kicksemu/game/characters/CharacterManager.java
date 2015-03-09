package com.neikeq.kicksemu.game.characters;

import com.neikeq.kicksemu.game.characters.upgrade.CharacterUpgrade;
import com.neikeq.kicksemu.game.inventory.Celebration;
import com.neikeq.kicksemu.game.inventory.Item;
import com.neikeq.kicksemu.game.inventory.Skill;
import com.neikeq.kicksemu.game.inventory.Training;
import com.neikeq.kicksemu.game.table.LevelInfo;
import com.neikeq.kicksemu.game.table.TableManager;
import com.neikeq.kicksemu.game.sessions.Session;
import com.neikeq.kicksemu.network.packets.in.ClientMessage;
import com.neikeq.kicksemu.network.packets.out.MessageBuilder;
import com.neikeq.kicksemu.network.packets.out.ServerMessage;
import com.neikeq.kicksemu.network.server.ServerManager;
import com.neikeq.kicksemu.storage.MySqlManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class CharacterManager {

    public static void playerInfo(Session session) {
        sendItemList(session);
        sendTrainingList(session);
        sendSkillList(session);
        sendCelebrationList(session);
        sendPlayerInfo(session);
        sendItemsInUse(session);
    }

    public static void playerDetails(Session session, ClientMessage msg) {
        int playerId = msg.readInt();

        if (ServerManager.isPlayerConnected(playerId)) {
            session.send(MessageBuilder.playerDetails(playerId, (byte) 0));
        }
    }

    private static void sendPlayerInfo(Session session) {
        try (Connection con = MySqlManager.getConnection()) {
            ServerMessage msg = MessageBuilder.playerInfo(session.getPlayerId(), (byte) 0, con);
            session.send(msg);
        } catch (SQLException ignored) {}
    }

    public static void sendItemList(Session session) {
        Map<Integer, Item> items = PlayerInfo.getInventoryItems(session.getPlayerId());

        session.send(MessageBuilder.itemList(items, (byte) 0));
    }

    /** This is a trick to update client's inventory items in use. */
    public static void sendItemsInUse(Session session) {
        int playerId = session.getPlayerId();

        Map<Integer, Item> items = PlayerInfo.getInventoryItems(session.getPlayerId());

        if (items.size() > 0) {
            Item item = items.values().iterator().next();

            session.send(item.isSelected() ?
                    MessageBuilder.activateItem(item.getInventoryId(), playerId, (byte) 0) :
                    MessageBuilder.deactivateItem(item.getInventoryId(), playerId, (byte) 0));
        }
    }

    private static void sendTrainingList(Session session) {
        Map<Integer, Training> trainings = PlayerInfo.getInventoryTraining(session.getPlayerId());

        ServerMessage msg = MessageBuilder.trainingList(trainings, (byte) 0);
        session.send(msg);
    }

    private static void sendSkillList(Session session) {
        Map<Integer, Skill> items = PlayerInfo.getInventorySkills(session.getPlayerId());

        ServerMessage msg = MessageBuilder.skillList(items, (byte) 0);
        session.send(msg);
    }

    private static void sendCelebrationList(Session session) {
        Map<Integer, Celebration> items =
                PlayerInfo.getInventoryCelebration(session.getPlayerId());

        ServerMessage msg = MessageBuilder.celebrationList(items, (byte) 0);
        session.send(msg);
    }

    public static void gameExit(Session session) {
        int playerId = session.getPlayerId();

        ServerMessage response = MessageBuilder.gameExit(session.getRemoteAddress(), playerId);
        session.send(response);

        session.close();
    }

    public static void resetStats(int playerId) {
        short position = PlayerInfo.getPosition(playerId);
        short level = PlayerInfo.getLevel(playerId);
        short branchPosition = Position.trunk(position);

        PlayerStats creationStats = CharacterUpgrade.getInstance()
                .getUpgradeStats().get(position);

        short statsPoints = 10;

        PlayerInfo.setStatsRunning(creationStats.getRunning(), playerId);
        PlayerInfo.setStatsEndurance(creationStats.getEndurance(), playerId);
        PlayerInfo.setStatsAgility(creationStats.getAgility(), playerId);
        PlayerInfo.setStatsBallControl(creationStats.getBallControl(), playerId);
        PlayerInfo.setStatsDribbling(creationStats.getDribbling(), playerId);
        PlayerInfo.setStatsStealing(creationStats.getStealing(), playerId);
        PlayerInfo.setStatsTackling(creationStats.getTackling(), playerId);
        PlayerInfo.setStatsHeading(creationStats.getHeading(), playerId);
        PlayerInfo.setStatsShortShots(creationStats.getShortShots(), playerId);
        PlayerInfo.setStatsLongShots(creationStats.getLongShots(), playerId);
        PlayerInfo.setStatsCrossing(creationStats.getCrossing(), playerId);
        PlayerInfo.setStatsShortPasses(creationStats.getShortPasses(), playerId);
        PlayerInfo.setStatsLongPasses(creationStats.getLongPasses(), playerId);
        PlayerInfo.setStatsMarking(creationStats.getMarking(), playerId);
        PlayerInfo.setStatsGoalkeeping(creationStats.getGoalkeeping(), playerId);
        PlayerInfo.setStatsPunching(creationStats.getPunching(), playerId);
        PlayerInfo.setStatsDefense(creationStats.getDefense(), playerId);

        if (level > 18) {
            onPlayerLevelUp(playerId, (short)18, (short)17, branchPosition);
            onPlayerLevelUp(playerId, level, (short)(level - 18), branchPosition);
        } else {
            onPlayerLevelUp(playerId, level, (short)(level - 1), branchPosition);
        }

        if (level >= 18) {
            // Apply upgrade stats
            PlayerStats upgradeStats = CharacterUpgrade.getInstance().getUpgradeStats().get(position);

            statsPoints += PlayerInfo.sumStatsRunning(upgradeStats.getRunning(), playerId);
            statsPoints += PlayerInfo.sumStatsEndurance(upgradeStats.getEndurance(), playerId);
            statsPoints += PlayerInfo.sumStatsAgility(upgradeStats.getAgility(), playerId);
            statsPoints += PlayerInfo.sumStatsBallControl(upgradeStats.getBallControl(), playerId);
            statsPoints += PlayerInfo.sumStatsDribbling(upgradeStats.getDribbling(), playerId);
            statsPoints += PlayerInfo.sumStatsStealing(upgradeStats.getStealing(), playerId);
            statsPoints += PlayerInfo.sumStatsTackling(upgradeStats.getTackling(), playerId);
            statsPoints += PlayerInfo.sumStatsHeading(upgradeStats.getHeading(), playerId);
            statsPoints += PlayerInfo.sumStatsShortShots(upgradeStats.getShortShots(), playerId);
            statsPoints += PlayerInfo.sumStatsLongShots(upgradeStats.getLongShots(), playerId);
            statsPoints += PlayerInfo.sumStatsCrossing(upgradeStats.getCrossing(), playerId);
            statsPoints += PlayerInfo.sumStatsShortPasses(upgradeStats.getShortPasses(), playerId);
            statsPoints += PlayerInfo.sumStatsLongPasses(upgradeStats.getLongPasses(), playerId);
            statsPoints += PlayerInfo.sumStatsMarking(upgradeStats.getMarking(), playerId);
            statsPoints += PlayerInfo.sumStatsGoalkeeping(upgradeStats.getGoalkeeping(), playerId);
            statsPoints += PlayerInfo.sumStatsPunching(upgradeStats.getPunching(), playerId);
            statsPoints += PlayerInfo.sumStatsDefense(upgradeStats.getDefense(), playerId);
        }

        // Add stats point
        PlayerInfo.sumStatsPoints(statsPoints, playerId);
    }

    public static short checkExperience(int playerId, Connection ... con) {
        short levels = 0;
        final short level = PlayerInfo.getLevel(playerId, con);
        final int experience = PlayerInfo.getExperience(playerId, con);

        LevelInfo newLevelInfo = TableManager.getLevelInfo(li ->
                li.getLevel() > level && li.getExperience() <= experience);

        if (newLevelInfo != null) {
            short newLevel = newLevelInfo.getLevel();

            if (newLevel > level) {
                levels += newLevel - level;
            }

            if (levels > 0) {
                PlayerInfo.setLevel(newLevel, playerId, con);

                short position = PlayerInfo.getPosition(playerId, con);
                onPlayerLevelUp(playerId, newLevel, levels, position, con);
            }
        }

        return levels;
    }

    public static void onPlayerLevelUp(int id, short level, short levels,
                                       short position, Connection ... con) {
        int from = level - levels;

        // Calculate stats points to add
        short statsPoints = 0;

        for (int i = from; i < level; i++) {
            statsPoints += CharacterUpgrade.getInstance().statsPointsForLevel(i+1);
        }

        // Add auto stats
        PlayerStats autoStats = CharacterUpgrade.getInstance().getAutoStats().get(position);

        statsPoints += PlayerInfo.sumStatsRunning(autoStats.getRunning() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsEndurance(autoStats.getEndurance() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsAgility(autoStats.getAgility() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsBallControl(autoStats.getBallControl() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsDribbling(autoStats.getDribbling() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsStealing(autoStats.getStealing() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsTackling(autoStats.getTackling() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsHeading(autoStats.getHeading() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsShortShots(autoStats.getShortShots() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsLongShots(autoStats.getLongShots() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsCrossing(autoStats.getCrossing() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsShortPasses(autoStats.getShortPasses() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsLongPasses(autoStats.getLongPasses() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsMarking(autoStats.getMarking() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsGoalkeeping(autoStats.getGoalkeeping() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsPunching(autoStats.getPunching() * levels, id, con);
        statsPoints += PlayerInfo.sumStatsDefense(autoStats.getDefense() * levels, id, con);

        // Add stats point
        PlayerInfo.sumStatsPoints(statsPoints, id, con);
    }

    public static void addStatsPoints(Session session, ClientMessage msg) {
        int playerId = msg.readInt();

        if (session.getPlayerId() == playerId) {
            byte result = 0;

            short[] values = new short[17];
            short total = 0;

            for (int i = 0; i < values.length; i++) {
                values[i] = msg.readShort();
                total += values[i];

                if (values[i] < 0) {
                    result = (byte)254; // Invalid value
                    break;
                }
            }

            try (Connection con = MySqlManager.getConnection()) {
                // If all values are valid
                if (result == 0) {
                        short statsPoints = PlayerInfo.getStatsPoints(playerId, con);

                        // If player have enough points
                        if (total <= statsPoints) {
                            total -= PlayerInfo.sumStatsRunning(values[0], playerId, con);
                            total -= PlayerInfo.sumStatsEndurance(values[1], playerId, con);
                            total -= PlayerInfo.sumStatsAgility(values[2], playerId, con);
                            total -= PlayerInfo.sumStatsBallControl(values[3], playerId, con);
                            total -= PlayerInfo.sumStatsDribbling(values[4], playerId, con);
                            total -= PlayerInfo.sumStatsStealing(values[5], playerId, con);
                            total -= PlayerInfo.sumStatsTackling(values[6], playerId, con);
                            total -= PlayerInfo.sumStatsHeading(values[7], playerId, con);
                            total -= PlayerInfo.sumStatsShortShots(values[8], playerId, con);
                            total -= PlayerInfo.sumStatsLongShots(values[9], playerId, con);
                            total -= PlayerInfo.sumStatsCrossing(values[10], playerId, con);
                            total -= PlayerInfo.sumStatsShortPasses(values[11], playerId, con);
                            total -= PlayerInfo.sumStatsLongPasses(values[12], playerId, con);
                            total -= PlayerInfo.sumStatsMarking(values[13], playerId, con);
                            total -= PlayerInfo.sumStatsGoalkeeping(values[14], playerId, con);
                            total -= PlayerInfo.sumStatsPunching(values[15], playerId, con);
                            total -= PlayerInfo.sumStatsDefense(values[16], playerId, con);

                            PlayerInfo.sumStatsPoints((short) -total, playerId, con);
                        } else {
                            result = (byte) 253; // Not enough stats points
                        }
                }

                session.sendAndFlush(MessageBuilder.addStatsPoints(playerId, result, con));
            } catch (SQLException ignored) {}
        }
    }
}
