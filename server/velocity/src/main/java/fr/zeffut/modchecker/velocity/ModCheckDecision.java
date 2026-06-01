package fr.zeffut.modchecker.velocity;

import fr.zeffut.modchecker.BanPolicy;
import fr.zeffut.modchecker.ModInfo;
import fr.zeffut.modchecker.ModListCodec;
import fr.zeffut.modchecker.ModStatus;

import java.util.List;
import java.util.Map;

/**
 * Pure, side-effect-free decision logic for the mod-check.
 * Accepts raw bytes + configuration, returns a {@link Result}.
 * No Velocity API imports — fully unit-testable.
 */
public final class ModCheckDecision {

    private ModCheckDecision() {}

    /**
     * Evaluates whether a player should be disconnected.
     *
     * @param rawPayload       the raw bytes received on {@code modchecker:modlist}
     * @param statusMap        the configured status map
     * @param kickMessage      formatted message template ({@code {mods}} is substituted with banned mod ids)
     * @return a {@link Result}
     */
    public static Result evaluate(byte[] rawPayload,
                                  Map<String, ModStatus> statusMap,
                                  String kickMessage) {
        if (rawPayload == null) {
            return Result.pass(List.of());
        }

        String json = ModListCodec.decode(rawPayload);
        if (json == null) {
            return Result.pass(List.of());  // malformed → ignore
        }

        List<ModInfo> mods = ModListCodec.parse(json);
        if (mods == null) {
            return Result.pass(List.of());  // unparseable → ignore
        }

        List<String> banned = BanPolicy.bannedAmong(mods, statusMap);
        if (banned.isEmpty()) {
            return Result.pass(mods);
        }

        // Format the kick message by substituting {mods}
        String msg = kickMessage.replace("{mods}", String.join(", ", banned));
        return Result.disconnect(mods, banned, msg);
    }

    /**
     * Pure decision: should a pending player (one that never sent a modlist) be kicked?
     * <p>
     * This is the testable core of the grace-period enforcement. The proxy-side scheduler
     * glue (fetching the online player, calling {@code disconnect}) is not covered here.
     *
     * @param isStillPending    true if the player's UUID is still in the pending set
     * @param kickWithoutMod    value of the {@code kick-without-mod} config flag
     * @param isExempt          true if the player's UUID is in the exempt-players list
     * @return true if the player should be disconnected for missing mod
     */
    public static boolean shouldKickPendingPlayer(boolean isStillPending,
                                                   boolean kickWithoutMod,
                                                   boolean isExempt) {
        return isStillPending && kickWithoutMod && !isExempt;
    }

    // ---- Result DTO ----

    public static final class Result {
        private final boolean shouldDisconnect;
        private final List<ModInfo> allMods;
        private final List<String> bannedMods;
        private final String disconnectMessage;  // null if shouldDisconnect == false

        private Result(boolean shouldDisconnect,
                       List<ModInfo> allMods,
                       List<String> bannedMods,
                       String disconnectMessage) {
            this.shouldDisconnect   = shouldDisconnect;
            this.allMods            = allMods;
            this.bannedMods         = bannedMods;
            this.disconnectMessage  = disconnectMessage;
        }

        public static Result pass(List<ModInfo> allMods) {
            return new Result(false, allMods, List.of(), null);
        }

        public static Result disconnect(List<ModInfo> allMods,
                                        List<String> bannedMods,
                                        String message) {
            return new Result(true, allMods, bannedMods, message);
        }

        public boolean isShouldDisconnect()    { return shouldDisconnect; }
        public List<ModInfo> getAllMods()       { return allMods; }
        public List<String> getBannedMods()    { return bannedMods; }
        public String getDisconnectMessage()   { return disconnectMessage; }
    }
}
