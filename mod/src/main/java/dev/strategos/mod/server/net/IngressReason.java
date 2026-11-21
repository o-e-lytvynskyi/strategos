package dev.strategos.mod.server.net;

/** Closed ingress audit vocabulary: never payloads, targets, exception messages or config prose. */
public enum IngressReason {
    UNREADABLE("unreadable"),
    STALE_CONNECTION("stale_connection"),
    STALE_MATCH_EPOCH("stale_match_epoch"),
    COMMAND_RATE("command_rate"),
    UNSEATED("unseated"),
    OBSERVER("observer"),
    INACTIVE_MATCH("inactive_match"),
    COMMAND_SEQUENCE("command_sequence"),
    LOBBY_RATE("lobby_rate"),
    AUXILIARY_RATE("auxiliary_rate"),
    INVALID_TEMPLATE_ACK("invalid_template_ack"),
    INVALID_CAMERA_JUMP("invalid_camera_jump"),
    INVALID_UI_QUERY("invalid_ui_query"),
    INVALID_MATCH_CONTROL("invalid_match_control"),
    INVALID_MODE_SWITCH("invalid_mode_switch"),
    INVALID_SKIRMISH("invalid_skirmish"),
    INVALID_LOBBY_ACTION("invalid_lobby_action"),
    INVALID_DEBUG_SUBSCRIBE("invalid_debug_subscribe"),
    LOBBY_REFUSED("lobby_refused"),
    DEBUG_PERMISSION("debug_permission"),
    DEBUG_RATE("debug_rate"),
    SKIRMISH_PERMISSION("skirmish_permission"),
    SKIRMISH_REFUSED("skirmish_refused"),
    CAMERA_REFUSED("camera_refused"),
    MODE_REFUSED("mode_refused"),
    UI_QUERY_UNAVAILABLE("ui_query_unavailable"),
    UI_QUERY_RATE("ui_query_rate"),
    MATCH_CONTROL_UNAVAILABLE("match_control_unavailable"),
    RECONNECT_EXPIRED("reconnect_expired");
    private final String code;

    IngressReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
