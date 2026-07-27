package org.dwbn.plugins.playlist;

import org.json.JSONObject;

public class PlaylistItemOptions {
  private final boolean retainPosition;
  private final long playFromPosition;
  private final String playFromId;
  private final boolean startPaused;

  PlaylistItemOptions(JSONObject optionsObj) {
    JSONObject options = optionsObj != null ? optionsObj : new JSONObject();
    retainPosition = Boolean.TRUE.equals(options.opt("retainPosition"));
    startPaused = Boolean.TRUE.equals(options.opt("startPaused"));

    Object playFromIdValue = options.opt("playFromId");
    playFromId = playFromIdValue instanceof String ? (String) playFromIdValue : null;

    Object playFromPositionValue = options.opt("playFromPosition");
    playFromPosition = playFromPositionValue instanceof Number
        ? Math.round(((Number) playFromPositionValue).doubleValue() * 1000.0)
        : -1L;
  }

  PlaylistItemOptions(boolean retainPosition, long playFromPosition, boolean startPaused) {
    this.retainPosition = retainPosition;
    this.playFromPosition = playFromPosition;
    this.playFromId = null;
    this.startPaused = startPaused;
  }

  public boolean getStartPaused() {
    return startPaused;
  }

  public boolean getRetainPosition() {
    return retainPosition;
  }

  public long getPlayFromPosition() {
    return playFromPosition;
  }

  public String getPlayFromId() {
    return playFromId;
  }
}
