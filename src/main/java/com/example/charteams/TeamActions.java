package com.example.charteams;

import com.example.charteams.model.Invite;
import com.example.charteams.model.Result;
import com.example.charteams.model.Team;
import com.example.charteams.model.TeamBook;
import com.example.charteams.model.TeamRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

// What a player does to teams, with the chat answers. The commands and the menu both come through here, so they behave the same.
// All the answers are plain English text because players without the mod can't translate keys.
public final class TeamActions {
    private TeamActions() {}

    private static final int PINK = 0xFF88C8;

    static void say(ServerPlayer p, String text) {
        p.sendSystemMessage(prefix().append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
    }

    static void say(ServerPlayer p, Component text) {
        p.sendSystemMessage(prefix().append(text));
    }

    private static MutableComponent prefix() {
        return Component.literal("[Teams] ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(PINK)));
    }

    static MutableComponent teamText(String name, int colour) {
        int rgb = TeamRules.validColour(colour) ? TeamRules.COLOUR_RGB[colour] : PINK;
        return Component.literal(name).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true));
    }

    static String explain(Result r) {
        return switch (r) {
            case OK -> "Done.";
            case ALREADY_ON_TEAM -> "You are already on a team. Leave it first.";
            case NOT_ON_TEAM -> "You are not on a team.";
            case NOT_OWNER -> "Only the team's owner can do that.";
            case BAD_NAME -> "A team name needs " + TeamRules.MIN_NAME + " to " + TeamRules.MAX_NAME + " letters, numbers or simple signs.";
            case NAME_TAKEN -> "There is already a team with that name.";
            case BAD_COLOUR -> "Colours: " + String.join(", ", TeamRules.COLOUR_NAMES) + ".";
            case TEAM_FULL -> "That team is full.";
            case SELF -> "That is you.";
            case NOT_A_MEMBER -> "They are not on your team.";
            case ALREADY_MEMBER -> "They are already on your team.";
            case TARGET_HAS_TEAM -> "They are already on a team.";
            case ALREADY_INVITED -> "They already have an invite from your team.";
            case ASK_LATER -> "They said no a moment ago. Ask again in a minute.";
            case TOO_MANY_INVITES -> "They have too many invites waiting.";
            case NO_INVITE -> "There is no invite to answer.";
        };
    }

    private static TeamBook book(ServerPlayer p) {
        TeamBook b = TeamService.book();
        if (b == null) say(p, "Teams are not ready yet.");
        return b;
    }

    // Tell everyone on the team who is online, optionally except one person.
    private static void tellTeam(Team team, UUID except, Component text) {
        if (team == null || TeamService.server() == null) return;
        for (UUID id : team.memberIds()) {
            ServerPlayer q = TeamService.server().getPlayerList().getPlayer(id);
            if (q != null && !id.equals(except)) say(q, text);
        }
    }

    private static Component line(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    public static void create(ServerPlayer p, String name, int colour) {
        TeamBook b = book(p);
        if (b == null) return;
        Result r = b.create(p.getUUID(), p.getGameProfile().getName(), name, colour);
        if (r != Result.OK) {
            say(p, explain(r));
            return;
        }
        Team t = b.teamOf(p.getUUID());
        say(p, line("You made the team ").copy().append(teamText(t.name(), t.colour())).append(line(". Invite friends with /teams invite <player>.")));
    }

    public static void invite(ServerPlayer p, ServerPlayer target) {
        TeamBook b = book(p);
        if (b == null) return;
        Result r = b.invite(p.getUUID(), p.getGameProfile().getName(), target.getUUID(), System.currentTimeMillis());
        if (r != Result.OK) {
            say(p, explain(r));
            return;
        }
        Team t = b.teamOf(p.getUUID());
        say(p, line("You asked " + target.getGameProfile().getName() + " to join the team."));
        String quoted = t.name();
        MutableComponent accept = Component.literal("[Accept]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teams accept " + quoted)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Join " + quoted))));
        MutableComponent deny = Component.literal("[No thanks]").withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teams deny " + quoted)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Say no"))));
        say(target, line(p.getGameProfile().getName() + " asked you to join the team ").copy().append(teamText(t.name(), t.colour())).append(line(" ")).append(accept).append(line(" ")).append(deny));
    }

    public static void accept(ServerPlayer p, String teamName) {
        TeamBook b = book(p);
        if (b == null) return;
        UUID team = null;
        if (teamName != null && !teamName.isBlank()) {
            Team named = b.teamNamed(teamName.trim());
            if (named == null) {
                say(p, explain(Result.NO_INVITE));
                return;
            }
            team = named.id();
        }
        acceptId(p, team);
    }

    public static void acceptId(ServerPlayer p, UUID team) {
        TeamBook b = book(p);
        if (b == null) return;
        Result r = b.accept(p.getUUID(), p.getGameProfile().getName(), team, System.currentTimeMillis());
        if (r != Result.OK) {
            say(p, explain(r));
            return;
        }
        Team t = b.teamOf(p.getUUID());
        say(p, line("You joined ").copy().append(teamText(t.name(), t.colour())).append(line(".")));
        tellTeam(t, p.getUUID(), line(p.getGameProfile().getName() + " joined the team."));
    }

    public static void deny(ServerPlayer p, String teamName) {
        TeamBook b = book(p);
        if (b == null) return;
        UUID team = null;
        if (teamName != null && !teamName.isBlank()) {
            Team named = b.teamNamed(teamName.trim());
            if (named == null) {
                say(p, explain(Result.NO_INVITE));
                return;
            }
            team = named.id();
        }
        denyId(p, team);
    }

    public static void denyId(ServerPlayer p, UUID team) {
        TeamBook b = book(p);
        if (b == null) return;
        Result r = b.deny(p.getUUID(), team, System.currentTimeMillis());
        say(p, r == Result.OK ? "Invite turned down." : explain(r));
    }

    public static void leave(ServerPlayer p) {
        TeamBook b = book(p);
        if (b == null) return;
        Team t = b.teamOf(p.getUUID());
        Result r = b.leave(p.getUUID());
        if (r != Result.OK) {
            say(p, explain(r));
            return;
        }
        say(p, "You left the team.");
        tellTeam(t, p.getUUID(), line(p.getGameProfile().getName() + " left the team."));
    }

    public static void kick(ServerPlayer p, UUID target) {
        TeamBook b = book(p);
        if (b == null) return;
        Team t = b.teamOf(p.getUUID());
        String name = t == null ? null : t.memberName(target);
        Result r = b.kick(p.getUUID(), target);
        if (r != Result.OK) {
            say(p, explain(r));
            return;
        }
        say(p, name + " is no longer on the team.");
        ServerPlayer q = TeamService.server().getPlayerList().getPlayer(target);
        if (q != null) say(q, "You were taken off the team.");
        tellTeam(t, p.getUUID(), line(name + " was taken off the team."));
    }

    public static void kickNamed(ServerPlayer p, String name) {
        TeamBook b = book(p);
        if (b == null) return;
        Team t = b.teamOf(p.getUUID());
        UUID target = t == null ? null : t.memberNamed(name);
        if (target == null) say(p, explain(t == null ? Result.NOT_ON_TEAM : Result.NOT_A_MEMBER));
        else kick(p, target);
    }

    public static void disband(ServerPlayer p) {
        TeamBook b = book(p);
        if (b == null) return;
        Team t = b.teamOf(p.getUUID());
        List<UUID> members = t == null ? List.of() : List.copyOf(t.memberIds());
        String name = t == null ? "" : t.name();
        Result r = b.disband(p.getUUID());
        if (r != Result.OK) {
            say(p, explain(r));
            return;
        }
        for (UUID id : members) {
            ServerPlayer q = TeamService.server().getPlayerList().getPlayer(id);
            if (q != null) say(q, "The team " + name + " was disbanded.");
        }
    }

    public static void rename(ServerPlayer p, String name) {
        TeamBook b = book(p);
        if (b == null) return;
        Result r = b.rename(p.getUUID(), name);
        say(p, r == Result.OK ? "Renamed to " + b.teamOf(p.getUUID()).name() + "." : explain(r));
    }

    public static void colour(ServerPlayer p, int colour) {
        TeamBook b = book(p);
        if (b == null) return;
        Result r = b.colour(p.getUUID(), colour);
        say(p, r == Result.OK ? "Colour changed." : explain(r));
    }

    public static void owner(ServerPlayer p, UUID target) {
        TeamBook b = book(p);
        if (b == null) return;
        Result r = b.transfer(p.getUUID(), target);
        if (r != Result.OK) {
            say(p, explain(r));
            return;
        }
        Team t = b.teamOf(p.getUUID());
        tellTeam(t, null, line(t.memberName(target) + " is now the team's owner."));
    }

    public static void ownerNamed(ServerPlayer p, String name) {
        TeamBook b = book(p);
        if (b == null) return;
        Team t = b.teamOf(p.getUUID());
        UUID target = t == null ? null : t.memberNamed(name);
        if (target == null) say(p, explain(t == null ? Result.NOT_ON_TEAM : Result.NOT_A_MEMBER));
        else owner(p, target);
    }

    public static void list(ServerPlayer p) {
        TeamBook b = book(p);
        if (b == null) return;
        List<Team> teams = b.teams();
        if (teams.isEmpty()) {
            say(p, "No teams yet. Make one with /teams create <name>.");
            return;
        }
        for (Team t : teams) {
            say(p, teamText(t.name(), t.colour()).append(line("  " + t.size() + (t.size() == 1 ? " member" : " members") + ", owner " + t.memberName(t.owner()))));
        }
    }

    public static void info(ServerPlayer p, String teamName) {
        TeamBook b = book(p);
        if (b == null) return;
        Team t = teamName == null || teamName.isBlank() ? b.teamOf(p.getUUID()) : b.teamNamed(teamName.trim());
        if (t == null) {
            say(p, teamName == null || teamName.isBlank() ? explain(Result.NOT_ON_TEAM) : "There is no team with that name.");
            return;
        }
        StringBuilder who = new StringBuilder();
        for (var e : t.members().entrySet()) {
            ServerPlayer q = TeamService.server().getPlayerList().getPlayer(e.getKey());
            if (who.length() > 0) who.append(", ");
            who.append(e.getValue()).append(t.isOwner(e.getKey()) ? " (owner)" : "").append(q != null ? " [online]" : "");
        }
        say(p, teamText(t.name(), t.colour()).append(line(" (" + TeamRules.COLOUR_NAMES[t.colour()] + "): " + who)));
    }

    // Shows invites waiting for the player when they type /teams with nothing else.
    public static void overview(ServerPlayer p) {
        TeamBook b = book(p);
        if (b == null) return;
        if (b.teamOf(p.getUUID()) != null) info(p, null);
        else say(p, "You are not on a team. /teams create <name>, or /teams list.");
        for (Invite i : b.invitesFor(p.getUUID(), System.currentTimeMillis())) {
            Team t = b.teamById(i.team());
            if (t == null) continue;
            MutableComponent accept = Component.literal("[Accept]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teams accept " + t.name())));
            say(p, line("Invite from " + i.inviterName() + " to ").copy().append(teamText(t.name(), t.colour())).append(line(" ")).append(accept));
        }
    }

    // A button in the menu.
    static void fromScreen(ServerPlayer p, Net.Action a) {
        switch (a.kind()) {
            case Net.CREATE -> create(p, a.text(), a.number());
            case Net.INVITE -> {
                ServerPlayer target = TeamService.server() == null ? null : TeamService.server().getPlayerList().getPlayerByName(a.text());
                if (target == null) say(p, "No player with that name is online.");
                else invite(p, target);
            }
            case Net.ACCEPT -> acceptId(p, a.id());
            case Net.DENY -> denyId(p, a.id());
            case Net.LEAVE -> leave(p);
            case Net.KICK -> kick(p, a.id());
            case Net.DISBAND -> disband(p);
            case Net.RENAME -> rename(p, a.text());
            case Net.COLOUR -> colour(p, a.number());
            case Net.OWNER -> owner(p, a.id());
            default -> {
            }
        }
    }
}
