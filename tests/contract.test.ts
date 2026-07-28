import { beforeEach, describe, expect, it, vi } from 'vitest';

import { RmxAudioStatusMessage } from '../src/Constants';
import { RmxAudioPlayer } from '../src/RmxAudioPlayer';
import type {
  OnStatusCallbackUpdateData,
  OnStatusErrorCallbackData,
  OnStatusTrackChangedData,
} from '../src/interfaces';
import { validateTrack, validateTracks } from '../src/utils';

class ContractPlayer extends RmxAudioPlayer {
  receive(
    track_id: string,
    type: RmxAudioStatusMessage,
    value: OnStatusCallbackUpdateData | OnStatusTrackChangedData | OnStatusErrorCallbackData,
  ) {
    this.onStatus(track_id, type, value);
  }
}

beforeEach(() => {
  vi.stubGlobal('window', { addEventListener: vi.fn() });
});

describe('public player contracts', () => {
  it('normalizes valid tracks and omits invalid assets', () => {
    const tracks = validateTracks([
      {
        assetUrl: 'https://example.com/valid.m4a',
        artist: 'Artist',
        album: 'Album',
        title: 'Valid',
      },
      {
        assetUrl: '   ',
        artist: 'Artist',
        album: 'Album',
        title: 'Invalid',
      },
    ]);

    expect(tracks).toHaveLength(1);
    expect(tracks[0].trackId).toMatch(/^[0-9a-f-]{36}$/);
    expect(tracks[0].isStream).toBe(false);
  });

  it('preserves explicit track ids and strict stream booleans', () => {
    const track = validateTrack({
      trackId: 'track-a',
      assetUrl: 'https://example.com/a.m4a',
      artist: 'Artist',
      album: 'Album',
      title: 'Track A',
      isStream: true,
    });

    expect(track).toEqual(expect.objectContaining({ trackId: 'track-a', isStream: true }));
  });

  it('tracks loading, playable, playing, and cleared lifecycle state', () => {
    const player = new ContractPlayer();
    const track = {
      trackId: 'track-a',
      assetUrl: 'https://example.com/a.m4a',
      artist: 'Artist',
      album: 'Album',
      title: 'Track A',
    };

    player.receive('track-a', RmxAudioStatusMessage.RMXSTATUS_TRACK_CHANGED, {
      currentItem: track,
      currentIndex: 0,
      isAtEnd: true,
      isAtBeginning: true,
      hasNext: false,
      hasPrevious: false,
    });
    expect(player.currentTrack).toEqual(track);
    expect(player.currentState).toBe('loading');

    player.receive('track-a', RmxAudioStatusMessage.RMXSTATUS_CANPLAY, {
      status: 'paused',
    } as OnStatusCallbackUpdateData);
    expect(player.hasLoaded).toBe(true);
    expect(player.isPaused).toBe(true);

    player.receive('track-a', RmxAudioStatusMessage.RMXSTATUS_PLAYING, {
      status: 'playing',
    } as OnStatusCallbackUpdateData);
    expect(player.isPlaying).toBe(true);

    player.receive('track-a', RmxAudioStatusMessage.RMXSTATUS_STOPPED, {
      status: 'stopped',
    } as OnStatusCallbackUpdateData);
    expect(player.currentState).toBe('stopped');

    player.receive('INVALID', RmxAudioStatusMessage.RMXSTATUS_PLAYLIST_CLEARED, {
      status: 'stopped',
    } as OnStatusCallbackUpdateData);
    expect(player.currentTrack).toBeNull();
    expect(player.currentState).toBe('stopped');
    expect(player.hasLoaded).toBe(false);
  });

  it('does not let stale track events overwrite current state', () => {
    const player = new ContractPlayer();
    player.receive('current', RmxAudioStatusMessage.RMXSTATUS_TRACK_CHANGED, {
      currentItem: {
        trackId: 'current',
        assetUrl: 'https://example.com/current.m4a',
        artist: 'Artist',
        album: 'Album',
        title: 'Current',
      },
      currentIndex: 0,
      isAtEnd: true,
      isAtBeginning: true,
      hasNext: false,
      hasPrevious: false,
    });

    player.receive('stale', RmxAudioStatusMessage.RMXSTATUS_PLAYING, {
      status: 'playing',
    } as OnStatusCallbackUpdateData);

    expect(player.currentState).toBe('loading');
    expect(player.isPlaying).toBe(false);
  });
});
