import { afterEach, describe, expect, it, vi } from 'vitest';

import { RmxAudioStatusMessage } from '../src/Constants';
import type { AudioTrack } from '../src/interfaces';
import { Playlist } from '../src/plugin';
import { PlaylistWeb } from '../src/web';

const track = (trackId: string): AudioTrack => ({
  trackId,
  assetUrl: `https://example.com/${trackId}.m4a`,
  artist: 'Artist',
  album: 'Album',
  title: `Track ${trackId}`,
});

class TestPlaylistWeb extends PlaylistWeb {
  setCurrentCalls: { item: AudioTrack; position?: number }[] = [];
  playCalls = 0;

  setAudio(audio: HTMLAudioElement): void {
    this.audio = audio;
  }

  setCurrentTrack(item: AudioTrack): void {
    this.currentTrack = item;
  }

  setHlsInstance(hls: { destroy: () => void }): void {
    (this as unknown as { hlsInstance: { destroy: () => void } }).hlsInstance = hls;
  }

  protected override async setCurrent(item: AudioTrack, position?: number): Promise<void> {
    this.currentTrack = item;
    this.setCurrentCalls.push({ item, position });
  }

  override async play(): Promise<void> {
    this.playCalls++;
  }
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('Playlist Web registration', () => {
  it('shares one implementation across concurrent first calls', async () => {
    const statuses: RmxAudioStatusMessage[] = [];

    const [listener] = await Promise.all([
      Playlist.addListener('status', ({ status }) => statuses.push(status.msgType)),
      Playlist.initialize(),
    ]);

    expect(statuses).toContain(RmxAudioStatusMessage.RMXSTATUS_INIT);
    await listener.remove();
  });
});

describe('PlaylistWeb transitions', () => {
  it('honors positions when playing the current or a different track', async () => {
    const player = new TestPlaylistWeb();
    await player.addAllItems({ items: [track('a'), track('b')] });

    await player.playTrackById({ id: 'a', position: 12.5 });
    await player.playTrackByIndex({ index: 1, position: 4 });

    expect(player.setCurrentCalls).toEqual([
      { item: expect.objectContaining({ trackId: 'a' }), position: 12.5 },
      { item: expect.objectContaining({ trackId: 'b' }), position: 4 },
    ]);
    expect(player.playCalls).toBe(2);
  });

  it('pauses instead of assigning an invalid zero playback rate', async () => {
    const pause = vi.fn();
    const audio = { pause, playbackRate: 1 } as unknown as HTMLAudioElement;
    const player = new TestPlaylistWeb();
    player.setAudio(audio);

    await player.setPlaybackRate({ rate: 0 });

    expect(pause).toHaveBeenCalledOnce();
    expect(audio.playbackRate).toBe(1);
  });

  it('returns playlist snapshots and emits one event per batch item', async () => {
    const player = new TestPlaylistWeb();
    const statuses: { msgType: RmxAudioStatusMessage; trackId: string }[] = [];
    await player.addListener('status', (event) => statuses.push(event.status));

    await player.addAllItems({ items: [track('a'), track('b')] });
    const first = await player.getPlaylist();
    first.items[0].title = 'Changed outside the plugin';
    first.items.push(track('c'));
    const second = await player.getPlaylist();

    expect(second.items.map((item) => item.trackId)).toEqual(['a', 'b']);
    expect(second.items[0].title).toBe('Track a');
    expect(statuses).toEqual([
      expect.objectContaining({
        msgType: RmxAudioStatusMessage.RMXSTATUS_ITEM_ADDED,
        trackId: 'a',
      }),
      expect.objectContaining({
        msgType: RmxAudioStatusMessage.RMXSTATUS_ITEM_ADDED,
        trackId: 'b',
      }),
    ]);
  });
});

describe('PlaylistWeb Media Session lifecycle', () => {
  it('works when Media Session is unavailable and still disposes playback resources', async () => {
    vi.stubGlobal('navigator', {});
    const pause = vi.fn();
    const destroy = vi.fn();
    const player = new TestPlaylistWeb();
    player.setAudio({ pause } as unknown as HTMLAudioElement);
    player.setHlsInstance({ destroy });

    await expect(player.release()).resolves.toBeUndefined();

    expect(pause).toHaveBeenCalledOnce();
    expect(destroy).toHaveBeenCalledOnce();
  });

  it('omits missing artwork and clears metadata and handlers on release', async () => {
    const setActionHandler = vi.fn();
    const mediaSession = { metadata: undefined as unknown, setActionHandler };
    vi.stubGlobal('navigator', { mediaSession });
    vi.stubGlobal(
      'MediaMetadata',
      class {
        constructor(data: MediaMetadataInit) {
          Object.assign(this, data);
        }
      },
    );
    const player = new TestPlaylistWeb();
    player.setCurrentTrack(track('a'));

    await player.setMediaSessionRemoteControlMetadata();
    expect(mediaSession.metadata).toEqual(expect.objectContaining({ artwork: [] }));

    await player.release();
    expect(mediaSession.metadata).toBeNull();
    expect(setActionHandler).toHaveBeenLastCalledWith('previoustrack', null);
    expect(setActionHandler.mock.calls.slice(-4)).toEqual([
      ['play', null],
      ['pause', null],
      ['nexttrack', null],
      ['previoustrack', null],
    ]);
  });
});
