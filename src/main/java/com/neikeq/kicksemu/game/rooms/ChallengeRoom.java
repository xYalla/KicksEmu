package com.neikeq.kicksemu.game.rooms;

import com.neikeq.kicksemu.game.clubs.ClubInfo;
import com.neikeq.kicksemu.game.rooms.challenges.Challenge;
import com.neikeq.kicksemu.game.rooms.challenges.ChallengeOrganizer;
import com.neikeq.kicksemu.game.rooms.enums.RoomLeaveReason;
import com.neikeq.kicksemu.game.rooms.enums.RoomState;
import com.neikeq.kicksemu.game.rooms.enums.RoomTeam;
import com.neikeq.kicksemu.game.sessions.Session;
import com.neikeq.kicksemu.io.Output;
import com.neikeq.kicksemu.io.logging.Level;
import com.neikeq.kicksemu.network.packets.out.MessageBuilder;
import com.neikeq.kicksemu.network.packets.out.ServerMessage;
import com.neikeq.kicksemu.storage.ConnectionRef;
import com.neikeq.kicksemu.utils.DateUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;

public class ChallengeRoom extends Room implements Observer {

    private Challenge challenge;

    @Override
    public void removeRoom() {
        synchronized (locker) {
            if (challenge != null) {
                ChallengeOrganizer.remove(challenge);

                challenge.getRedTeam().onQuitChallenge();
                challenge.getBlueTeam().onQuitChallenge();
            }
        }
    }

    public void addChallengePlayers() {
        synchronized (locker) {
            getPlayers().putAll(challenge.getRedTeam().getPlayers());
            getPlayers().putAll(challenge.getBlueTeam().getPlayers());
            getRedTeam().addAll(challenge.getRedTeam().getRedTeam());
            getBlueTeam().addAll(challenge.getBlueTeam().getRedTeam());
            getPlayers().keySet().forEach(playerId -> getRoomLobby().addPlayer(playerId));
            getPlayers().values().forEach(this::onPlayerJoined);
        }
    }

    @Override
    public void removePlayer(Session session, RoomLeaveReason reason) {
        synchronized (locker) {
            onPlayerLeaved(session, reason);
        }
    }

    @Override
    public RoomTeam swapPlayerTeam(int playerId, RoomTeam currentTeam) {
        return currentTeam;
    }

    @Override
    void onPlayerJoined(Session session) {
        session.send(MessageBuilder.joinRoom(Optional.of(this), session.getPlayerId(), (short) 0));
        // Send to the client information about players inside the room
        sendRoomPlayersInfo(session);
        // Send the room info to the client
        session.send(roomInfoMessage());
        session.flush();

        timeLastJoin = DateUtils.currentTimeMillis();
    }

    @Override
    protected void onPlayerLeaved(Session session, RoomLeaveReason reason) {
        synchronized (locker) {
            super.onPlayerLeaved(session, reason);

            if (isInLobbyScreen()) {
                removeRoom();
            }
        }
    }

    @Override
    protected void notifyAboutNewPlayer(Session session) {}

    @Override
    protected ServerMessage roomInfoMessage() {
        return MessageBuilder.challengeRoomInfo(this);
    }

    @Override
    void sendRoomPlayersInfo(Session session) {
        List<Integer> team = getPlayerTeam(session.getPlayerId())
                .map(playerTeam -> (playerTeam == RoomTeam.RED) ? getBlueTeam() : getRedTeam())
                .orElse(new ArrayList<>());
        try (ConnectionRef con = ConnectionRef.ref()) {
            team.forEach(player ->
                    session.send(roomPlayerInfoMessage(getPlayer(player), con)));
        } catch (SQLException e) {
            Output.println("Exception when sending challenge room players info to a player: " +
                    e.getMessage(), Level.DEBUG);
        }
    }

    @Override
    public boolean isObserver(int playerId) {
        return false;
    }

    @Override
    public List<Integer> getObservers() {
        return new ArrayList<>();
    }

    @Override
    protected void addObserver(int playerId) {}

    void onStateChanged(RoomState oldState) {
        if ((state() == RoomState.WAITING) && !getDisconnectedPlayers().isEmpty()) {
            // Notify players to remove disconnected player definitely
            getDisconnectedPlayers().forEach(playerId -> broadcast(
                    MessageBuilder.leaveRoom(playerId, RoomLeaveReason.DISCONNECTED)));
            getDisconnectedPlayers().clear();
            removeRoom();
        }
    }

    @Override
    public boolean isTraining() {
        return false;
    }

    public Challenge getChallenge() {
        return challenge;
    }

    public void setChallenge(Challenge challenge) {
        this.challenge = challenge;

        challenge.addObserver(this);
        setId(challenge.getId());
        ClubRoom redTeam = challenge.getRedTeam();
        ClubRoom blueTeam = challenge.getBlueTeam();
        setMaster(redTeam.getMaster());
        setHost(redTeam.getMaster());
        setName(ClubInfo.getName(redTeam.getId()) + " vs " + ClubInfo.getName(blueTeam.getId()));
    }

    @Override
    public void update(Observable observable, Object o) {
        Challenge challenge = (Challenge) observable;

        if (challenge.isCanceled()) {
            removeRoom();
        }
    }
}
