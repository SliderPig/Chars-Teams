package com.example.charteams.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeamBookTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), D = new UUID(0, 4);
    private static final long NOW = 1_000_000L;
    private TeamBook book;
    private int saves;

    @BeforeEach
    void setUp() {
        book = new TeamBook();
        book.limits(3, 60_000L);
        saves = 0;
        book.onChange(() -> saves++);
    }

    private void makeTeam() {
        assertEquals(Result.OK, book.create(A, "Ann", "Cool Cats", 2));
    }

    private void join(UUID who, String name) {
        assertEquals(Result.OK, book.invite(A, "Ann", who, NOW));
        assertEquals(Result.OK, book.accept(who, name, null, NOW));
    }

    @Test
    void createMakesYouTheOwner() {
        makeTeam();
        Team team = book.teamOf(A);
        assertEquals("Cool Cats", team.name());
        assertEquals(2, team.colour());
        assertTrue(team.isOwner(A));
        assertEquals(Set.of(A), book.teammates(A));
        assertEquals(1, saves);
    }

    @Test
    void onlyOneTeamPerPlayer() {
        makeTeam();
        assertEquals(Result.ALREADY_ON_TEAM, book.create(A, "Ann", "Second", 0));
        assertEquals(1, book.teams().size());
    }

    @Test
    void namesAreCheckedAndTidied() {
        assertEquals("Cool Cats", TeamRules.cleanName("  Cool   Cats "));
        assertEquals("Ñandú's-2", TeamRules.cleanName("Ñandú's-2"));
        assertNull(TeamRules.cleanName("x"));
        assertNull(TeamRules.cleanName("   "));
        assertNull(TeamRules.cleanName(null));
        assertNull(TeamRules.cleanName("a".repeat(TeamRules.MAX_NAME + 1)));
        assertNotNull(TeamRules.cleanName("a".repeat(TeamRules.MAX_NAME)));
        assertNull(TeamRules.cleanName("§cRed"));
        assertNull(TeamRules.cleanName("new\nline"));
        assertEquals(Result.BAD_NAME, book.create(A, "Ann", "x", 0));
    }

    @Test
    void namesAreUniqueWhateverTheCase() {
        makeTeam();
        assertEquals(Result.NAME_TAKEN, book.create(B, "Bob", "COOL cats", 0));
        assertEquals(Result.OK, book.create(B, "Bob", "Dogs", 0));
        assertEquals(Result.NAME_TAKEN, book.rename(B, "cool cats"));
        assertEquals(Result.OK, book.rename(B, "DOGS"), "changing your own name's capitals is fine");
        assertEquals("DOGS", book.teamOf(B).name());
    }

    @Test
    void badColourRefused() {
        assertEquals(Result.BAD_COLOUR, book.create(A, "Ann", "Cats", 99));
        assertEquals(Result.BAD_COLOUR, book.create(A, "Ann", "Cats", -1));
        makeTeam();
        assertEquals(Result.BAD_COLOUR, book.colour(A, 99));
        assertEquals(Result.OK, book.colour(A, 5));
        assertEquals(5, book.teamOf(A).colour());
        assertEquals(1, TeamRules.colourNamed("RED"));
        assertEquals(-1, TeamRules.colourNamed("beige"));
    }

    @Test
    void anInviteMustBeAccepted() {
        makeTeam();
        assertEquals(Result.OK, book.invite(A, "Ann", B, NOW));
        assertNull(book.teamOf(B), "asked, not added");
        assertEquals(1, book.invitesFor(B, NOW).size());
        assertEquals(Result.OK, book.accept(B, "Bob", null, NOW));
        assertEquals(book.teamOf(A), book.teamOf(B));
        assertTrue(book.sameTeam(A, B));
        assertEquals(Set.of(A, B), book.teammates(B));
        assertTrue(book.invitesFor(B, NOW).isEmpty());
    }

    @Test
    void anyMemberCanInviteButNotTwice() {
        makeTeam();
        join(B, "Bob");
        assertEquals(Result.OK, book.invite(B, "Bob", C, NOW));
        assertEquals(Result.ALREADY_INVITED, book.invite(A, "Ann", C, NOW));
    }

    @Test
    void whoCantBeInvited() {
        assertEquals(Result.NOT_ON_TEAM, book.invite(A, "Ann", B, NOW));
        makeTeam();
        assertEquals(Result.SELF, book.invite(A, "Ann", A, NOW));
        join(B, "Bob");
        assertEquals(Result.ALREADY_MEMBER, book.invite(A, "Ann", B, NOW));
        assertEquals(Result.OK, book.create(C, "Cat", "Others", 0));
        assertEquals(Result.TARGET_HAS_TEAM, book.invite(A, "Ann", C, NOW));
    }

    @Test
    void invitesRunOut() {
        makeTeam();
        book.invite(A, "Ann", B, NOW);
        assertEquals(Result.NO_INVITE, book.accept(B, "Bob", null, NOW + 60_000L));
        assertTrue(book.invitesFor(B, NOW + 60_000L).isEmpty());
        assertEquals(Result.OK, book.invite(A, "Ann", B, NOW + 60_000L), "can be asked again after it ran out");
    }

    @Test
    void dropOldSaysWhoLostAnInvite() {
        makeTeam();
        book.invite(A, "Ann", B, NOW);
        assertTrue(book.dropOld(NOW + 1).isEmpty());
        assertEquals(Set.of(B), book.dropOld(NOW + 60_000L));
        assertTrue(book.dropOld(NOW + 60_001L).isEmpty());
    }

    @Test
    void sayingNoStopsPesteringForAMinute() {
        makeTeam();
        book.invite(A, "Ann", B, NOW);
        assertEquals(Result.OK, book.deny(B, null, NOW));
        assertEquals(Result.NO_INVITE, book.deny(B, null, NOW));
        assertEquals(Result.ASK_LATER, book.invite(A, "Ann", B, NOW + 1000));
        assertEquals(Result.OK, book.invite(A, "Ann", B, NOW + TeamRules.ASK_AGAIN_MS));
    }

    @Test
    void acceptPicksTheTeamYouClicked() {
        makeTeam();
        assertEquals(Result.OK, book.create(B, "Bob", "Dogs", 0));
        book.invite(A, "Ann", C, NOW);
        book.invite(B, "Bob", C, NOW);
        UUID cats = book.teamOf(A).id();
        assertEquals(2, book.invitesFor(C, NOW).size());
        assertEquals(Result.NO_INVITE, book.accept(C, "Cat", UUID.randomUUID(), NOW));
        assertEquals(Result.OK, book.accept(C, "Cat", cats, NOW));
        assertEquals(cats, book.teamOf(C).id());
        assertTrue(book.invitesFor(C, NOW).isEmpty(), "other invites go when you join one");
    }

    @Test
    void acceptWithNoTeamNamedTakesTheNewest() {
        makeTeam();
        assertEquals(Result.OK, book.create(B, "Bob", "Dogs", 0));
        book.invite(A, "Ann", C, NOW);
        book.invite(B, "Bob", C, NOW);
        assertEquals(Result.OK, book.accept(C, "Cat", null, NOW));
        assertEquals("Dogs", book.teamOf(C).name());
    }

    @Test
    void makingATeamDropsYourInvites() {
        makeTeam();
        book.invite(A, "Ann", B, NOW);
        assertEquals(Result.OK, book.create(B, "Bob", "Dogs", 0));
        assertTrue(book.invitesFor(B, NOW).isEmpty());
        assertEquals(Result.ALREADY_ON_TEAM, book.accept(B, "Bob", null, NOW));
    }

    @Test
    void aFullTeamCantTakeMore() {
        makeTeam();
        join(B, "Bob");
        assertEquals(Result.OK, book.invite(A, "Ann", C, NOW));
        assertEquals(Result.OK, book.invite(A, "Ann", D, NOW));
        assertEquals(Result.OK, book.accept(C, "Cat", null, NOW));
        assertEquals(Result.TEAM_FULL, book.accept(D, "Dot", null, NOW), "two were asked, one space left");
        assertNull(book.teamOf(D));
        assertEquals(Result.TEAM_FULL, book.invite(A, "Ann", new UUID(0, 9), NOW));
        assertEquals(1, book.invitesFor(D, NOW).size(), "still there in case a space opens");
    }

    @Test
    void tooManyInvitesAtOnceRefused() {
        for (int i = 0; i < TeamRules.MAX_INVITES_PER_PLAYER; i++) {
            UUID owner = new UUID(1, i);
            assertEquals(Result.OK, book.create(owner, "O" + i, "Team " + i, 0));
            assertEquals(Result.OK, book.invite(owner, "O" + i, D, NOW));
        }
        UUID last = new UUID(2, 0);
        assertEquals(Result.OK, book.create(last, "Last", "Last", 0));
        assertEquals(Result.TOO_MANY_INVITES, book.invite(last, "Last", D, NOW));
    }

    @Test
    void onlyTheOwnerRemovesPeople() {
        makeTeam();
        join(B, "Bob");
        join(C, "Cat");
        assertEquals(Result.NOT_OWNER, book.kick(B, C));
        assertEquals(Result.SELF, book.kick(A, A));
        assertEquals(Result.NOT_A_MEMBER, book.kick(A, D));
        assertEquals(Result.OK, book.kick(A, B));
        assertNull(book.teamOf(B));
        assertFalse(book.sameTeam(A, B));
        assertEquals(Set.of(A, C), book.teammates(A));
        assertEquals(Result.NOT_ON_TEAM, book.kick(B, C));
    }

    @Test
    void ownerLeavingHandsOverToTheLongestServing() {
        makeTeam();
        join(B, "Bob");
        join(C, "Cat");
        assertEquals(Result.OK, book.leave(A));
        assertTrue(book.teamOf(B).isOwner(B));
        assertEquals(Set.of(B, C), book.teammates(C));
        assertNull(book.teamOf(A));
    }

    @Test
    void memberLeavingKeepsTheOwner() {
        makeTeam();
        join(B, "Bob");
        assertEquals(Result.OK, book.leave(B));
        assertTrue(book.teamOf(A).isOwner(A));
        assertEquals(Result.NOT_ON_TEAM, book.leave(B));
    }

    @Test
    void lastOneOutEndsTheTeamAndItsInvites() {
        makeTeam();
        book.invite(A, "Ann", B, NOW);
        assertEquals(Result.OK, book.leave(A));
        assertTrue(book.teams().isEmpty());
        assertTrue(book.invitesFor(B, NOW).isEmpty());
        assertEquals(Result.OK, book.create(B, "Bob", "Cool Cats", 0), "the name is free again");
    }

    @Test
    void disbandIsOwnerOnlyAndFreesEveryone() {
        makeTeam();
        join(B, "Bob");
        book.invite(A, "Ann", C, NOW);
        assertEquals(Result.NOT_OWNER, book.disband(B));
        assertEquals(Result.OK, book.disband(A));
        assertNull(book.teamOf(A));
        assertNull(book.teamOf(B));
        assertTrue(book.teams().isEmpty());
        assertTrue(book.invitesFor(C, NOW).isEmpty());
        assertEquals(Result.NOT_ON_TEAM, book.disband(A));
    }

    @Test
    void transferMovesTheCrown() {
        makeTeam();
        join(B, "Bob");
        assertEquals(Result.NOT_OWNER, book.transfer(B, A));
        assertEquals(Result.SELF, book.transfer(A, A));
        assertEquals(Result.NOT_A_MEMBER, book.transfer(A, D));
        assertEquals(Result.OK, book.transfer(A, B));
        assertTrue(book.teamOf(A).isOwner(B));
        assertEquals(Result.NOT_OWNER, book.kick(A, B));
        assertEquals(Result.OK, book.kick(B, A));
    }

    @Test
    void renameAndColourAreOwnerOnly() {
        makeTeam();
        join(B, "Bob");
        assertEquals(Result.NOT_OWNER, book.rename(B, "Mine now"));
        assertEquals(Result.NOT_OWNER, book.colour(B, 1));
        assertEquals(Result.OK, book.rename(A, "Cooler Cats"));
        assertNotNull(book.teamNamed("cooler cats"));
    }

    @Test
    void askingAboutNobodyIsSafe() {
        assertNull(book.teamOf(null));
        assertEquals(Set.of(), book.teammates(null));
        assertFalse(book.sameTeam(null, null));
        assertFalse(book.sameTeam(A, A), "not on a team, so not on the same one");
        makeTeam();
        assertTrue(book.sameTeam(A, A));
        assertFalse(book.sameTeam(A, null));
    }

    @Test
    void teammatesCopyDoesntChangeUnderYou() {
        makeTeam();
        Set<UUID> before = book.teammates(A);
        join(B, "Bob");
        assertEquals(Set.of(A), before);
        assertThrows(UnsupportedOperationException.class, () -> book.teammates(A).clear());
    }

    @Test
    void savingOnlyCountsRealChanges() {
        makeTeam();
        int after = saves;
        book.invite(A, "Ann", B, NOW);
        book.deny(B, null, NOW);
        assertEquals(after, saves, "invites aren't saved");
        assertFalse(book.updateName(A, "Ann"));
        assertEquals(after, saves);
        assertTrue(book.updateName(A, "Annie"));
        assertEquals(after + 1, saves);
        assertEquals("Annie", book.teamOf(A).memberName(A));
    }

    @Test
    void updatingANameKeepsTheJoiningOrder() {
        makeTeam();
        join(B, "Bob");
        book.updateName(A, "Annie");
        assertEquals(List.of(A, B), List.copyOf(book.teamOf(A).members().keySet()));
    }

    @Test
    void savedTeamsComeBackAndBadDataIsMended() {
        UUID one = new UUID(5, 1), two = new UUID(5, 2), three = new UUID(5, 3), four = new UUID(5, 4);
        Map<UUID, String> first = new LinkedHashMap<>();
        first.put(A, "Ann");
        first.put(B, "Bob");
        assertTrue(book.restore(one, "Cats", 3, A, first));
        assertEquals(0, saves, "loading isn't a change");
        assertTrue(book.teamOf(B).isOwner(A));

        Map<UUID, String> second = new LinkedHashMap<>();
        second.put(B, "Bob");
        second.put(C, "Cat");
        assertTrue(book.restore(two, "Dogs", 99, B, second), "Bob is taken, Cat stays and takes over");
        assertEquals("Cats", book.teamOf(B).name());
        assertTrue(book.teamOf(C).isOwner(C));
        assertEquals(0, book.teamOf(C).colour(), "a colour that doesn't exist goes back to pink");

        assertFalse(book.restore(three, "Cats", 0, D, Map.of(D, "Dot")), "name in use");
        assertFalse(book.restore(three, "x", 0, D, Map.of(D, "Dot")), "name not allowed");
        assertFalse(book.restore(four, "Nobody", 0, A, Map.of()), "nobody on it");
        assertFalse(book.restore(one, "Cats Again", 0, D, Map.of(D, "Dot")), "id in use");
        assertFalse(book.restore(null, "No id", 0, D, Map.of(D, "Dot")));
        assertNull(book.teamOf(D));
    }

    @Test
    void limitsCantGoSilly() {
        book.limits(0, -5);
        assertEquals(2, book.maxMembers());
    }
}
