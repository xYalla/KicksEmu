package com.neikeq.kicksemu.game.rooms;

import com.neikeq.kicksemu.game.characters.PlayerInfo;
import com.neikeq.kicksemu.game.clubs.MemberInfo;
import com.neikeq.kicksemu.game.lobby.RoomLobby;
import com.neikeq.kicksemu.game.rooms.challenges.Challenge;
import com.neikeq.kicksemu.game.rooms.challenges.ChallengeOrganizer;
import com.neikeq.kicksemu.game.rooms.challenges.WinStreakCache;
import com.neikeq.kicksemu.game.rooms.enums.RoomAccessType;
import com.neikeq.kicksemu.game.rooms.enums.RoomLeaveReason;
import com.neikeq.kicksemu.game.rooms.enums.RoomSize;
import com.neikeq.kicksemu.game.rooms.enums.RoomState;
import com.neikeq.kicksemu.game.rooms.enums.RoomTeam;
import com.neikeq.kicksemu.game.sessions.Session;
import com.neikeq.kicksemu.io.Output;
import com.neikeq.kicksemu.io.logging.Level;
import com.neikeq.kicksemu.network.packets.out.MessageBuilder;
import com.neikeq.kicksemu.network.packets.out.ServerMessage;
import com.neikeq.kicksemu.storage.MySqlManager;
import com.neikeq.kicksemu.utils.mutable.MutableInteger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ClubRoom extends Room {

    public static final byte MIN_TEAM_PLAYERS = 4;

    { super.getRoomLobby().setTeamChatEnabled(false); }

    private byte totalWins = 0;
    private byte winStreak = 0;
    private int challengeTarget = 0;
    private int challengeId = -1;
    private int totalLevels = 0;

    private WinStreakCache winStreakCache = new WinStreakCache((byte) 0, getPlayers().keySet());
    protected RoomSize maxSize = RoomSize.SIZE_4V4;

    @Override
    public void removeRoom() {
        synchronized (locker) {
            if (TeamManager.isRegistered(getId())) {
                TeamManager.unregister(getId());
            }
            super.removeRoom();
        }
    }

    @Override
    public void tryJoinRoom(Session session, String password) {
        int playerId = session.getPlayerId();

        short result = 0;

        synchronized (locker) {
            if (isNotFull()) {
                if (isWaiting() && !TeamManager.isRegistered(getId())) {
                    if (MemberInfo.getClubId(playerId) == getId()) {
                        // Check password (moderators can bypass this)
                        if (getAccessType() != RoomAccessType.PASSWORD ||
                                password.equals(getPassword()) ||
                                PlayerInfo.isModerator(playerId)) {
                            short level = PlayerInfo.getLevel(playerId);

                            // If player level is allowed in room settings
                            if (isLevelAllowed(level)) {
                                // Join the room
                                addPlayer(session);
                            } else {
                                result = -8; // Invalid level
                            }
                        } else {
                            result = -5; // Wrong password
                        }
                    } else {
                        result = -9; // Not in the players list (not a member of the club)
                    }
                } else {
                    result = -6; // Match already started
                }
            } else {
                result = -4; // Room is full
            }
        }

        if (result != 0) {
            session.send(MessageBuilder.joinRoom(this, session.getPlayerId(), result));
        }
    }

    @Override
    public RoomTeam swapPlayerTeam(int playerId, RoomTeam currentTeam) {
        return currentTeam;
    }

    @Override
    protected void addPlayerToTeam(int playerId) {
        addPlayerToRedTeam(playerId);
    }

    @Override
    protected void addPlayerToTeam(int playerId, RoomTeam team) {
        addPlayerToRedTeam(playerId);
    }

    @Override
    protected void addPlayerToBlueTeam(int playerId) {
        addPlayerToRedTeam(playerId);
    }

    @Override
    public void startCountdown() { }

    @Override
    public void cancelCountdown() { }

    @Override
    protected void onPlayerJoined(Session session) {
        synchronized (locker) {
            super.onPlayerJoined(session);
            updateTotalLevels();

            if (getPlayers().size() == MIN_TEAM_PLAYERS && winStreakCache.getWins() > 0) {
                if (winStreakCache.matchesTeam(getPlayers().keySet())) {
                    setWinStreak(winStreakCache.getWins());
                }
            }
        }
    }

    @Override
    protected void onPlayerLeaved(Session session, RoomLeaveReason reason) {
        synchronized (locker) {
            if (challengeId >= 0) {
                Challenge challenge = ChallengeOrganizer.getChallengeById(challengeId);
                if (challenge != null) {
                    challenge.cancel();
                }
            }

            super.onPlayerLeaved(session, reason);
            TeamManager.unregister(getId());

            updateTotalLevels();

            if (getPlayers().size() == MIN_TEAM_PLAYERS - 1) {
                winStreakCache.setWins(winStreak);
                winStreakCache.setPlayers(getPlayers().keySet());
                setWinStreak((byte) 0);
            }
        }
    }

    @Override
    protected ServerMessage roomPlayerInfoMessage(Session session, Connection... con) {
        return MessageBuilder.clubRoomPlayerInfo(session, this, con);
    }

    @Override
    protected ServerMessage leaveRoomMessage(int playerId, RoomLeaveReason reason) {
        return MessageBuilder.clubLeaveRoom(playerId, reason);
    }

    @Override
    protected ServerMessage roomMasterMessage(int master) {
        return MessageBuilder.clubRoomCaptain(master);
    }

    @Override
    protected ServerMessage joinRoomMessage(Room room, int playerId, short result) {
        return MessageBuilder.clubJoinRoom(room, result);
    }

    @Override
    protected ServerMessage roomInfoMessage() {
        return MessageBuilder.clubRoomInfo(this);
    }

    public byte onChallengeRequest(int from) {
        synchronized (locker) {
            if (!isChallenging()) {
                setChallengeTarget(from);
                ServerMessage request = MessageBuilder.clubChallengeTeam(from, false, (short) 0);
                getPlayer(getMaster()).sendAndFlush(request);

                return 0;
            }

            return -6; // The club is applying for a match
        }
    }

    public short onChallengeResponse(int fromId, boolean accepted) {
        synchronized (locker) {
            if (challengeTarget == fromId) {
                if (!accepted) {
                    setChallengeTarget(0);
                }

                short result = accepted ?
                        (short) 0 : // Challenge request accepted
                        -4; // The opponent club refused the request
                broadcast(MessageBuilder.clubChallengeResponse(fromId, accepted, result));

                return 0;
            }

            return -6; // Failed to create the match room... Invalid requester target.
        }
    }

    public void onQuitChallenge() {
        setChallengeId(-1);
        setState(RoomState.WAITING);
        broadcast(MessageBuilder.clubCancelChallenge());
    }

    private void updateTotalLevels() {
        final MutableInteger totalLevels = new MutableInteger();

        try (Connection con = MySqlManager.getConnection()) {
            getPlayers().keySet().forEach(playerId ->
                    totalLevels.sum(PlayerInfo.getLevel(playerId, con)));
        } catch (SQLException e) {
            Output.println("Failed to calculate club room total levels: " + e.getMessage(),
                    Level.DEBUG);
        }

        this.totalLevels = totalLevels.get();
    }

    public byte getLevelGapFactorTo(ClubRoom room) {
        int difference = getTotalLevels() - room.getTotalLevels();
        return (byte) -(difference / 10);
    }

    @Override
    public boolean isObserver(int playerId) {
        return false;
    }

    @Override
    public RoomLobby getRoomLobby() {
        if (challengeId >= 0) {
            Challenge challenge = ChallengeOrganizer.getChallengeById(challengeId);
            return challenge.getRoom().getRoomLobby();
        } else {
            return super.getRoomLobby();
        }
    }

    @Override
    public List<Integer> getObservers() {
        return new ArrayList<>();
    }

    @Override
    protected void addObserver(int playerId) { }

    @Override
    public boolean isWaiting() {
        // TODO this should be temporal, until I find a way to display the APPLYING icon
        return super.isWaiting() || isChallenging();
    }

    public byte getTotalWins() {
        return totalWins;
    }

    public void setTotalWins(byte totalWins) {
        if (totalWins >= 0) {
            this.totalWins = totalWins;
            broadcast(MessageBuilder.clubUpdateWins(this));
        }
    }

    public byte getWinStreak() {
        return winStreak;
    }

    public void setWinStreak(byte winStreak) {
        this.winStreak = winStreak;
    }

    public boolean isChallenging() {
        return state() == RoomState.APPLYING;
    }

    public int getChallengeTarget() {
        return challengeTarget;
    }

    public void setChallengeTarget(int challengeTarget) {
        if (challengeTarget != 0) {
            setState(RoomState.APPLYING);
            winStreakCache.setWins((byte) 0);
        } else if (state() == RoomState.APPLYING) {
            setState(RoomState.WAITING);
        }

        this.challengeTarget = challengeTarget;
    }

    public int getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(int challengeId) {
        this.challengeId = challengeId;

        if (challengeId != -1) {
            winStreakCache.setWins((byte) 0);
        }
    }

    public void setMaxSize(RoomSize maxSize) {
        this.maxSize = RoomSize.SIZE_4V4;
    }

    public int getTotalLevels() {
        return totalLevels;
    }
}
