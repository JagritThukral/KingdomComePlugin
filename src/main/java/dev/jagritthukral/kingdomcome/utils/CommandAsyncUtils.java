package dev.jagritthukral.kingdomcome.utils;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class CommandAsyncUtils {
    private CommandAsyncUtils() {
    }

    public static <T> Handler<T> handle(JavaPlugin plugin, Player player, CompletableFuture<T> future) {
        return new Handler<>(plugin, player, future);
    }

    private static Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    public static final class Handler<T> {
        private final JavaPlugin plugin;
        private final Player player;
        private final CompletableFuture<T> future;
        private Consumer<T> onSuccess = _ -> {
        };
        // LinkedHashMap keeps registration order, so put specific exceptions first
        private final Map<Class<? extends Throwable>, Consumer<Throwable>> handlers = new LinkedHashMap<>();

        private Handler(JavaPlugin plugin, Player player, CompletableFuture<T> future) {
            this.plugin = plugin;
            this.player = player;
            this.future = future;
        }

        public Handler<T> onSuccess(Consumer<T> action) {
            this.onSuccess = action;
            return this;
        }

        /**
         * Handle an expected exception and get access to it.
         */
        public <E extends Throwable> Handler<T> on(Class<E> type, Consumer<E> action) {
            handlers.put(type, t -> action.accept(type.cast(t)));
            return this;
        }

        /**
         * Shortcut: just send a fixed message to the player.
         */
        public Handler<T> on(Class<? extends Throwable> type, String message) {
            handlers.put(type, _ -> player.sendMessage(message));
            return this;
        }

        public void run() {
            future.whenComplete((result, throwable) ->
                    // Runs on the player's region thread (Folia-safe). The last arg runs if the player was removed.
                    player.getScheduler()
                            .run(plugin, _ -> {
                                if (throwable == null) {
                                    onSuccess.accept(result);
                                    return;
                                }
                                Throwable cause = unwrap(throwable);
                                for (var entry : handlers.entrySet()) {
                                    if (entry.getKey()
                                            .isInstance(cause)) { // isInstance also matches subclasses
                                        entry.getValue()
                                                .accept(cause);
                                        return;
                                    }
                                }
                                player.sendMessage("An internal error occurred. Check server console logs for info.");
                                plugin.getSLF4JLogger()
                                        .error("Async operation failed for {}", player.getName(), cause);
                            }, null)
            );
        }
    }
}
