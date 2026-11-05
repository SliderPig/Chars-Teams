package com.example.charteams;

import com.example.charteams.model.Team;
import com.example.charteams.model.TeamRules;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

// /teams for everyone, no permission level, so friends without the mod can use it.
final class TeamCommands {
    private TeamCommands() {}

    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (c, b) -> {
        var book = TeamService.book();
        ServerPlayer p = c.getSource().getPlayer();
        Team t = book == null || p == null ? null : book.teamOf(p.getUUID());
        return SharedSuggestionProvider.suggest(t == null ? java.util.List.<String>of() : t.members().values(), b);
    };
    private static final SuggestionProvider<CommandSourceStack> TEAMS = (c, b) -> {
        var book = TeamService.book();
        return SharedSuggestionProvider.suggest(book == null ? java.util.List.<String>of() : book.teams().stream().map(Team::name).toList(), b);
    };
    private static final SuggestionProvider<CommandSourceStack> COLOURS = (c, b) -> SharedSuggestionProvider.suggest(TeamRules.COLOUR_NAMES, b);

    private static LiteralArgumentBuilder<CommandSourceStack> lit(String name) {
        return Commands.literal(name);
    }

    private static int run(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, java.util.function.Consumer<ServerPlayer> what) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) {
            c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Only players are on teams."));
            return 0;
        }
        what.accept(p);
        return 1;
    }

    private static int word(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String arg, BiConsumer<ServerPlayer, String> what) {
        String text = StringArgumentType.getString(c, arg);
        return run(c, p -> what.accept(p, text));
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(lit("teams")
                .executes(c -> run(c, TeamActions::overview))
                .then(lit("create").then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> word(c, "name", (p, n) -> TeamActions.create(p, n, 0)))))
                .then(lit("invite").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer target = EntityArgument.getPlayer(c, "player");
                    return run(c, p -> TeamActions.invite(p, target));
                })))
                .then(lit("accept").executes(c -> run(c, p -> TeamActions.accept(p, null)))
                        .then(Commands.argument("team", StringArgumentType.greedyString()).suggests(TEAMS).executes(c -> word(c, "team", TeamActions::accept))))
                .then(lit("deny").executes(c -> run(c, p -> TeamActions.deny(p, null)))
                        .then(Commands.argument("team", StringArgumentType.greedyString()).suggests(TEAMS).executes(c -> word(c, "team", TeamActions::deny))))
                .then(lit("leave").executes(c -> run(c, TeamActions::leave)))
                .then(lit("kick").then(Commands.argument("member", StringArgumentType.word()).suggests(MEMBERS).executes(c -> word(c, "member", TeamActions::kickNamed))))
                .then(lit("disband").executes(c -> run(c, TeamActions::disband)))
                .then(lit("list").executes(c -> run(c, TeamActions::list)))
                .then(lit("info").executes(c -> run(c, p -> TeamActions.info(p, null)))
                        .then(Commands.argument("team", StringArgumentType.greedyString()).suggests(TEAMS).executes(c -> word(c, "team", TeamActions::info))))
                .then(lit("rename").then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> word(c, "name", TeamActions::rename))))
                .then(lit("colour").then(Commands.argument("colour", StringArgumentType.word()).suggests(COLOURS).executes(c -> word(c, "colour", (p, n) -> {
                    int colour = TeamRules.colourNamed(n);
                    TeamActions.colour(p, colour);
                }))))
                .then(lit("owner").then(Commands.argument("member", StringArgumentType.word()).suggests(MEMBERS).executes(c -> word(c, "member", TeamActions::ownerNamed)))));
    }
}
