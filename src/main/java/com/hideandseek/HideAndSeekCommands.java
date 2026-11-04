package com.hideandseek;

import static com.hideandseek.HideAndSeekAnnouncements.legacy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.List;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

/** /hideandseek (alias /hns). Admin subcommands need HideAndSeekConfig.permissionLevel(). */
public final class HideAndSeekCommands {

    private HideAndSeekCommands() {}

    @FunctionalInterface
    private interface Action {
        HideAndSeekManager.Result run(HideAndSeekManager manager) throws CommandSyntaxException;
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return source.hasPermission(HideAndSeekConfig.permissionLevel());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("hideandseek")
                .executes(HideAndSeekCommands::help)
                .then(Commands.literal("help").executes(HideAndSeekCommands::help))

                .then(Commands.literal("start").requires(HideAndSeekCommands::isAdmin)
                        .executes(ctx -> run(ctx, m -> m.startWithPreselected(null)))
                        .then(Commands.argument("seeker", EntityArgument.player())
                                .executes(ctx -> run(ctx, m -> m.start(EntityArgument.getPlayer(ctx, "seeker"), null)))
                                .then(Commands.argument("match_time_minutes", IntegerArgumentType.integer(1, 240))
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"3", "5", "10", "15"}, b))
                                        .executes(ctx -> run(ctx, m -> m.start(
                                                EntityArgument.getPlayer(ctx, "seeker"),
                                                IntegerArgumentType.getInteger(ctx, "match_time_minutes")))))))

                .then(Commands.literal("stop").requires(HideAndSeekCommands::isAdmin)
                        .executes(ctx -> run(ctx, HideAndSeekManager::stop)))
                .then(Commands.literal("reset").requires(HideAndSeekCommands::isAdmin)
                        .executes(ctx -> run(ctx, HideAndSeekManager::reset)))
                .then(Commands.literal("pause").requires(HideAndSeekCommands::isAdmin)
                        .executes(ctx -> run(ctx, HideAndSeekManager::pause)))
                .then(Commands.literal("resume").requires(HideAndSeekCommands::isAdmin)
                        .executes(ctx -> run(ctx, HideAndSeekManager::resume)))

                .then(Commands.literal("setseeker").requires(HideAndSeekCommands::isAdmin)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> run(ctx, m -> m.setSeeker(EntityArgument.getPlayer(ctx, "player"))))))

                .then(Commands.literal("catch").requires(HideAndSeekCommands::isAdmin)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> run(ctx, m -> m.catchHider(EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("uncatch").requires(HideAndSeekCommands::isAdmin)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> run(ctx, m -> m.uncatchHider(EntityArgument.getPlayer(ctx, "player"))))))

                .then(Commands.literal("config").requires(HideAndSeekCommands::isAdmin)
                        .executes(ctx -> {
                            for (String line : HideAndSeekConfig.describe()) {
                                ctx.getSource().sendSuccess(() -> legacy(line), false);
                            }
                            return 1;
                        }))
                .then(Commands.literal("reload").requires(HideAndSeekCommands::isAdmin)
                        .executes(ctx -> {
                            String error = HideAndSeekConfig.reloadFromDisk();
                            if (error != null) {
                                ctx.getSource().sendFailure(Component.literal("Could not reload config: " + error));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Hide & Seek config reloaded."), true);
                            return 1;
                        }))

                .then(Commands.literal("status").executes(ctx -> lines(ctx, HideAndSeekManager::statusLines)))
                .then(Commands.literal("list").executes(ctx -> lines(ctx, HideAndSeekManager::listLines)))

                .then(Commands.literal("join")
                        .executes(ctx -> run(ctx, m -> m.join(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("leave")
                        .executes(ctx -> run(ctx, m -> m.leave(ctx.getSource().getPlayerOrException())))));

        dispatcher.register(Commands.literal("hns").redirect(root));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Action action) throws CommandSyntaxException {
        HideAndSeekManager manager = HideAndSeekMod.manager();
        if (manager == null) {
            ctx.getSource().sendFailure(Component.literal("Hide & Seek is not ready yet."));
            return 0;
        }
        HideAndSeekManager.Result result = action.run(manager);
        if (result.success()) {
            if (!result.message().isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal(result.message()).withStyle(ChatFormatting.GREEN), false);
            }
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal(result.message()).withStyle(ChatFormatting.RED));
        return 0;
    }

    private static int lines(CommandContext<CommandSourceStack> ctx,
                             Function<HideAndSeekManager, List<Component>> producer) {
        HideAndSeekManager manager = HideAndSeekMod.manager();
        if (manager == null) {
            ctx.getSource().sendFailure(Component.literal("Hide & Seek is not ready yet."));
            return 0;
        }
        for (Component line : producer.apply(manager)) {
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        boolean admin = isAdmin(ctx.getSource());
        String[] lines = {
                "&6&l=== Hide & Seek Commands ===",
                "&e/hideandseek status &7- match information",
                "&e/hideandseek list &7- seeker, hiders and caught players",
                "&e/hideandseek join &7- join a running match (if enabled)",
                "&e/hideandseek leave &7- leave the match (if enabled)"
        };
        String[] adminLines = {
                "&c/hideandseek start <seeker> [minutes] &7- start a match",
                "&c/hideandseek start &7- start with the seeker from setseeker",
                "&c/hideandseek setseeker <player> &7- preselect the seeker",
                "&c/hideandseek stop &7- stop the current match",
                "&c/hideandseek reset &7- reset all state",
                "&c/hideandseek pause | resume &7- pause or resume the timer",
                "&c/hideandseek catch <player> &7- eliminate a hider",
                "&c/hideandseek uncatch <player> &7- return a hider to the game",
                "&c/hideandseek config &7- show configuration",
                "&c/hideandseek reload &7- reload hideandseek-common.toml"
        };
        for (String l : lines) {
            ctx.getSource().sendSuccess(() -> legacy(l), false);
        }
        if (admin) {
            for (String l : adminLines) {
                ctx.getSource().sendSuccess(() -> legacy(l), false);
            }
        }
        return 1;
    }
}
