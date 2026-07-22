package org.dwbn.plugins.playlist;

import org.json.JSONException;
import org.json.JSONObject;

public class PlaylistItemOptions {
  private JSONObject options;

  private boolean retainPosition = false;
  private long playFromPosition = -1L;
  private String playFromId = null;
  private boolean startPaused = true;

  PlaylistItemOptions(JSONObject optionsObj) {
    this.options = optionsObj;
    if (this.options == null) {
      this.options = new JSONObject();
    }

    this.retainPosition = Boolean.TRUE.equals(this.options.opt("retainPosition"));
    this.startPaused = Boolean.TRUE.equals(this.options.opt("startPaused"));
    Object playFromIdValue = this.options.opt("playFromId");
    this.playFromId = playFromIdValue instanceof String ? (String) playFromIdValue : null;

    try {
      playFromPosition = (long)this.options.getDouble("playFromPosition") * 1000L;
    } catch (JSONException ex) {
      playFromPosition = -1L;
    }
  }

  PlaylistItemOptions(boolean retainPosition, long playFromPosition, boolean startPaused) {
    this.startPaused = startPaused;
    this.retainPosition = retainPosition;
    this.playFromPosition = playFromPosition;
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
