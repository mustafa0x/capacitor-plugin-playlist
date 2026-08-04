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

class FakeAudio {
  autoplay = false;
  controls = false;
  crossOrigin = '';
  currentTime = 0;
  duration = 30;
  paused = true;
  playbackRate = 1;
  preload = '';
  src = '';
  volume = 1;
  playCalls = 0;
  private listeners = new Map<string, Set<() => void>>();

  addEventListener(type: string, listener: () => void): void {
    const listeners = this.listeners.get(type) ?? new Set();
    listeners.add(listener);
    this.listeners.set(type, listeners);
  }

  removeEventListener(type: string, listener: () => void): void {
    this.listeners.get(type)?.delete(listener);
  }

  async play(): Promise<void> {
    this.playCalls++;
    this.paused = false;
    this.emit('playing');
  }

  pause(): void {
    this.paused = true;
    this.emit('pause');
  }

  emit(type: string): void {
    for (const listener of [...(this.listeners.get(type) ?? [])]) listener();
  }
}

class LifecyclePlaylistWeb extends PlaylistWeb {
  get currentTrackId(): string | undefined {
    return this.currentTrack?.trackId;
  }
}

const installAudioDocument = (): FakeAudio[] => {
  const audios: FakeAudio[] = [];
  vi.stubGlobal('navigator', {});
  vi.stubGlobal('document', {
    createElement: vi.fn(() => {
      const audio = new FakeAudio();
      audios.push(audio);
      return audio;
    }),
  });
  return audios;
};

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

  it('retains playback rate before creating an audio element', async () => {
    const audio = { playbackRate: 1 } as unknown as HTMLAudioElement;
    vi.stubGlobal('document', { createElement: vi.fn(() => audio) });
    const player = new TestPlaylistWeb();

    await player.setPlaybackRate({ rate: 2 });
    await player.create();

    expect(audio.playbackRate).toBe(2);
  });

  it('treats seeking before audio creation as a no-op', async () => {
    const player = new TestPlaylistWeb();

    await expect(player.seekTo({ position: 12.5 })).resolves.toBeUndefined();
  });

  it('keeps the preferred playback rate after pausing with zero', async () => {
    const audio = { pause: vi.fn(), playbackRate: 1 } as unknown as HTMLAudioElement;
    const player = new TestPlaylistWeb();
    player.setAudio(audio);

    await player.setPlaybackRate({ rate: 2 });
    await player.setPlaybackRate({ rate: 0 });
    vi.stubGlobal('document', { createElement: vi.fn(() => audio) });
    await player.create();

    expect(audio.playbackRate).toBe(2);
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

  it('publishes selected track metadata and clears it on release', async () => {
    const setActionHandler = vi.fn();
    const mediaSession = { metadata: undefined as unknown, setActionHandler };
    installAudioDocument();
    vi.stubGlobal('navigator', { mediaSession });
    vi.stubGlobal(
      'MediaMetadata',
      class {
        constructor(data: MediaMetadataInit) {
          Object.assign(this, data);
        }
      },
    );
    const player = new PlaylistWeb();

    await player.setPlaylistItems({ items: [track('a')], options: { startPaused: true } });
    expect(mediaSession.metadata).toMatchObject({ title: 'Track a', artwork: [] });

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

describe('PlaylistWeb playback lifecycle contracts', () => {
  it('loads the requested track and position without violating paused intent', async () => {
    const audios = installAudioDocument();
    const player = new LifecyclePlaylistWeb();

    await player.setPlaylistItems({
      items: [track('a'), track('b')],
      options: { startPaused: true, playFromId: 'b', playFromPosition: 4 },
    });

    expect(player.currentTrackId).toBe('b');
    expect(audios[0].paused).toBe(true);
    audios[0].emit('canplay');
    expect(audios[0].currentTime).toBe(4);
    expect(audios[0].paused).toBe(true);
  });

  it('starts immediately when startPaused is omitted', async () => {
    const audios = installAudioDocument();
    const player = new LifecyclePlaylistWeb();

    await player.setPlaylistItems({ items: [track('a')], options: {} });

    expect(audios[0].playCalls).toBe(1);
    expect(audios[0].paused).toBe(false);
  });

  it('advances after natural completion and clears state at playlist end', async () => {
    const audios = installAudioDocument();
    const player = new LifecyclePlaylistWeb();
    const statuses: { msgType: RmxAudioStatusMessage; value: unknown }[] = [];
    await player.addListener('status', ({ status }) => statuses.push(status));
    await player.setPlaylistItems({ items: [track('a'), track('b')], options: {} });

    audios[0].emit('ended');
    await vi.waitFor(() => expect(audios).toHaveLength(2));
    audios[1].emit('canplay');
    expect(player.currentTrackId).toBe('b');
    expect(audios[1].paused).toBe(false);

    audios[1].emit('ended');
    await vi.waitFor(() => expect(player.currentTrackId).toBeUndefined());

    expect(statuses.map(({ msgType }) => msgType)).toContain(RmxAudioStatusMessage.RMXSTATUS_PLAYLIST_COMPLETED);
    expect(statuses).toContainEqual(
      expect.objectContaining({
        msgType: RmxAudioStatusMessage.RMXSTATUS_TRACK_CHANGED,
        value: expect.objectContaining({ currentItem: null, currentIndex: -1 }),
      }),
    );
  });

  it('loops the terminal track and resumes only when media is ready', async () => {
    const audios = installAudioDocument();
    const player = new LifecyclePlaylistWeb();
    await player.setLoop({ loop: true });
    await player.setPlaylistItems({ items: [track('a')], options: {} });

    audios[0].emit('ended');
    await vi.waitFor(() => expect(audios).toHaveLength(2));
    expect(audios[1].paused).toBe(true);
    audios[1].emit('canplay');

    expect(player.currentTrackId).toBe('a');
    expect(audios[1].playCalls).toBe(1);
    expect(audios[1].paused).toBe(false);
  });

  it('captures video handoff position and remains paused on web resume', async () => {
    const audios = installAudioDocument();
    const player = new LifecyclePlaylistWeb();
    await player.setPlaylistItems({ items: [track('a')], options: {} });
    audios[0].currentTime = 7.5;

    await player.prepareForVideoHandoff();
    expect(await player.getLastKnownPosition()).toEqual({ position: 7.5 });
    expect(audios[0].paused).toBe(true);

    await expect(player.resumeAfterVideoHandoff({ position: 9 })).resolves.toEqual({ resumed: false });
    expect(await player.getLastKnownPosition()).toEqual({ position: 9 });
    expect(audios[0].paused).toBe(true);
  });
});
