import { WebPlugin } from '@capacitor/core';
import { RmxAudioStatusMessage } from './Constants';
import {
    AddAllItemOptions,
    AddItemOptions,
    PlayByIdOptions,
    PlayByIndexOptions,
    PlaylistOptions,
    PlaylistPlugin,
    RemoveItemOptions,
    RemoveItemsOptions,
    SeekToOptions,
    SelectByIdOptions,
    SelectByIndexOptions,
    SetLoopOptions,
    SetPlaybackRateOptions,
    SetPlaybackVolumeOptions
} from './definitions';
import { AudioPlayerOptions, AudioTrack } from './interfaces';
import { validateTracks } from './utils';

declare var Hls: any;

export class PlaylistWeb extends WebPlugin implements PlaylistPlugin {
    protected audio: HTMLAudioElement | undefined;
    protected playlistItems: AudioTrack[] = [];
    protected loop = false;
    protected options: AudioPlayerOptions = {};
    protected currentTrack: AudioTrack | null = null;
    protected lastState = 'stopped';
    private hlsInstance: any;

    addAllItems(options: AddAllItemOptions): Promise<void> {
        const tracks = validateTracks(options.items);
        this.playlistItems = this.playlistItems.concat(tracks);
        for (const track of tracks) {
            this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_ITEM_ADDED, track, track.trackId);
        }
        return Promise.resolve();
    }

    addItem(options: AddItemOptions): Promise<void> {
        return this.addAllItems({ items: [options.item] });
    }

    async clearAllItems(): Promise<void> {
        await this.release();
        this.playlistItems = [];
        this.currentTrack = null;
        this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYLIST_CLEARED, null, "INVALID");
    }

    async getPlaylist(): Promise<{ items: AudioTrack[] }> {
        return { items: this.playlistItems.map(item => ({ ...item })) };
    }

    async initialize(): Promise<void> {
        this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_INIT, null, "INVALID");
    }

    async pause(): Promise<void> {
        this.audio?.pause();
    }

    async play(): Promise<void> {
        await this.audio?.play();
    }

    private async playTrack(item: AudioTrack, position?: number): Promise<void> {
        if (item !== this.currentTrack) {
            await this.setCurrent(item, position);
        } else if (position !== undefined) {
            await this.seekTo({ position });
        }
        await this.play();
    }

    playTrackById(options: PlayByIdOptions): Promise<void> {
        const track = this.playlistItems.find(item => item.trackId === options.id);
        return track ? this.playTrack(track, options.position) : Promise.reject();
    }

    playTrackByIndex(options: PlayByIndexOptions): Promise<void> {
        const track = this.playlistItems[options.index];
        return track ? this.playTrack(track, options.position) : Promise.reject();
    }

    async release(): Promise<void> {
        await this.pause();
        this.hlsInstance?.destroy();
        this.hlsInstance = undefined;
        this.audio = undefined;

        const mediaSession = navigator.mediaSession;
        if (!mediaSession) {
            return;
        }

        mediaSession.metadata = null;
        mediaSession.setActionHandler('play', null);
        mediaSession.setActionHandler('pause', null);
        mediaSession.setActionHandler('nexttrack', null);
        mediaSession.setActionHandler('previoustrack', null);
    }

    async create(): Promise<void> {
        this.audio = document.createElement('audio');
        this.audio.crossOrigin = 'anonymous';
        this.audio.preload = 'metadata';
        this.audio.controls = true;
        this.audio.autoplay = false;
    }

    removeItem(options: RemoveItemOptions): Promise<void> {
        return this.removeItems({ items: [options] });
    }

    async removeItems(options: RemoveItemsOptions): Promise<void> {
        const snapshot = [...this.playlistItems];
        const indices = new Set<number>();

        for (const item of options.items || []) {
            let index = -1;
            if (
                item.index !== undefined &&
                item.index !== null &&
                Number.isInteger(item.index) &&
                item.index >= 0 &&
                item.index < snapshot.length
            ) {
                index = item.index;
            } else if (item.id) {
                index = snapshot.findIndex((track) => track.trackId === item.id);
            }
            if (index >= 0) {
                indices.add(index);
            }
        }

        if (indices.size === 0) {
            return;
        }

        const currentIndex = this.currentTrack ? snapshot.indexOf(this.currentTrack) : -1;
        const removingCurrent = indices.has(currentIndex);
        const nextTrack = removingCurrent
            ? snapshot.find((_, index) => index > currentIndex && !indices.has(index))
            : undefined;
        const removedTracks = [...indices].map((index) => snapshot[index]);

        for (const index of [...indices].sort((a, b) => b - a)) {
            this.playlistItems.splice(index, 1);
        }
        for (const track of removedTracks) {
            this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_ITEM_REMOVED, track, track.trackId);
        }

        if (removingCurrent) {
            if (nextTrack) {
                await this.setCurrent(nextTrack);
            } else {
                await this.release();
                this.currentTrack = null;
                this.updateStatus(
                    RmxAudioStatusMessage.RMXSTATUS_TRACK_CHANGED,
                    {
                        currentItem: null,
                        currentIndex: -1,
                        isAtEnd: true,
                        isAtBeginning: true,
                        hasNext: false,
                        hasPrevious: false
                    },
                    "NONE"
                );
            }
        }
    }

    seekTo(options: SeekToOptions): Promise<void> {
        if (this.audio) {
            this.audio.currentTime = options.position;
            return Promise.resolve();
        }
        return Promise.reject();
    }

    selectTrackById(options: SelectByIdOptions): Promise<void> {
        const track = this.playlistItems.find(item => item.trackId === options.id);
        return track ? this.setCurrent(track, options.position) : Promise.reject();
    }

    selectTrackByIndex(options: SelectByIndexOptions): Promise<void> {
        const track = Number.isInteger(options.index)
            ? this.playlistItems[options.index]
            : undefined;
        return track ? this.setCurrent(track, options.position) : Promise.reject();
    }

    setLoop(options: SetLoopOptions): Promise<void> {
        this.loop = options.loop;
        return Promise.resolve();
    }

    setOptions(options: AudioPlayerOptions): Promise<void> {
        this.options = options || {};
        return Promise.resolve();
    }

    setPlaybackVolume(options: SetPlaybackVolumeOptions): Promise<void> {
        if (this.audio) {
            this.audio.volume = options.volume;
            return Promise.resolve();
        }
        return Promise.reject();
    }

    async setPlaylistItems(options: PlaylistOptions): Promise<void> {
        const items = validateTracks(options.items);
        if (!items.length) {
            return this.clearAllItems();
        }

        await this.release();
        this.currentTrack = null;
        this.playlistItems = items;

        const currentItem = items.find(item => item.trackId === options.options?.playFromId) ?? items[0];
        await this.setCurrent(currentItem, options.options?.playFromPosition ?? 0);
        if (!options.options?.startPaused) {
            await this.play();
        }
    }

    async skipForward(): Promise<void> {
        const currentIndex = this.getCurrentIndex();
        if (currentIndex < 0) {
            return;
        }

        let targetIndex = currentIndex + 1;
        if (targetIndex >= this.playlistItems.length) {
            if (!this.loop) {
                return;
            }
            targetIndex = 0;
        }

        const targetTrack = this.playlistItems[targetIndex];
        await this.setCurrent(targetTrack);
        this.updateStatus(RmxAudioStatusMessage.RMX_STATUS_SKIP_FORWARD, {
            currentIndex: targetIndex,
            currentItem: targetTrack
        }, targetTrack.trackId);
    }

    async skipBack(): Promise<void> {
        const currentIndex = this.getCurrentIndex();
        if (currentIndex <= 0) {
            return;
        }

        const targetIndex = currentIndex - 1;
        const targetTrack = this.playlistItems[targetIndex];
        await this.setCurrent(targetTrack);
        this.updateStatus(RmxAudioStatusMessage.RMX_STATUS_SKIP_BACK, {
            currentIndex: targetIndex,
            currentItem: targetTrack
        }, targetTrack.trackId);
    }

    setPlaybackRate(options: SetPlaybackRateOptions): Promise<void> {
        if (this.audio) {
            if (options.rate === 0) {
                return this.pause();
            }
            this.audio.playbackRate = options.rate;
            return Promise.resolve();
        }
        return Promise.reject();
    }

    protected lastKnownHandoffPosition = 0;

    async prepareForVideoHandoff(): Promise<void> {
        this.lastKnownHandoffPosition = this.audio?.currentTime ?? 0;
        await this.pause();
    }

    async resumeAfterVideoHandoff(options: { position: number }): Promise<{ resumed: boolean }> {
        this.lastKnownHandoffPosition = options.position;
        return { resumed: false };
    }

    async getLastKnownPosition(): Promise<{ position: number }> {
        return { position: this.lastKnownHandoffPosition };
    }

    async setMediaSessionRemoteControlMetadata(): Promise<void> {
        if (!navigator.mediaSession) {
            return;
        }
        const audioTrack: AudioTrack = this.currentTrack!;

        navigator.mediaSession.metadata = new MediaMetadata({
            title: audioTrack.title,
            artist: audioTrack.artist,
            album: audioTrack.album,
            artwork: audioTrack.albumArt ? [{ src: audioTrack.albumArt }] : []
        });

        navigator.mediaSession.setActionHandler('play', (details) => {this.mediaSessionControlsHandler(details)});
        navigator.mediaSession.setActionHandler('pause', (details) => {this.mediaSessionControlsHandler(details)});
        navigator.mediaSession.setActionHandler('nexttrack', (details) => {this.mediaSessionControlsHandler(details)});
        navigator.mediaSession.setActionHandler('previoustrack', (details) => {this.mediaSessionControlsHandler(details)});
    }

    async mediaSessionControlsHandler(actionDetails: MediaSessionActionDetails): Promise<void> {
        switch(actionDetails.action) {
            case 'play':
              this.play();
              break;
            case 'pause':
              this.pause();
              break;
            case 'nexttrack':
              this.skipForward();
              break;
            case 'previoustrack':
              this.skipBack();
              break;
          }
    }

    registerHtmlListeners(position?: number) {
        const canPlayListener = async () => {
            this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_CANPLAY, this.getCurrentTrackStatus('paused'));
            if (position) {
                await this.seekTo({ position });
            }
            this.audio?.removeEventListener('canplay', canPlayListener);
        };
        if (this.audio) {
            this.audio.addEventListener('loadstart', () => {this.setMediaSessionRemoteControlMetadata()});
            this.audio.addEventListener('canplay', canPlayListener);
            this.audio.addEventListener('playing', () => {
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYING, this.getCurrentTrackStatus('playing'));
            });

            this.audio.addEventListener('pause', () => {
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PAUSE, this.getCurrentTrackStatus('paused'));
            });

            this.audio.addEventListener('error', () => {
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_ERROR, this.getCurrentTrackStatus('error'));
            });

            this.audio.addEventListener('ended', () => {
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_COMPLETED, this.getCurrentTrackStatus('stopped'));
                const currentTrackIndex = this.getCurrentIndex();
                if (currentTrackIndex < 0) {
                    return;
                }

                const nextTrack = this.playlistItems[currentTrackIndex + 1];
                if (nextTrack) {
                    this.setCurrent(nextTrack, undefined, true);
                    return;
                }

                if (this.loop) {
                    this.setCurrent(this.playlistItems[0], undefined, true);
                    return;
                }

                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYLIST_COMPLETED, this.getCurrentTrackStatus('stopped'));
            });

            let lastTrackId: string | undefined;
            let lastPosition: number | undefined;
            this.audio.addEventListener('timeupdate', () => {
                const status = this.getCurrentTrackStatus(this.lastState);
                if (lastTrackId !== this.getCurrentTrackId() || lastPosition !== status.currentPosition) {
                    this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_PLAYBACK_POSITION, status);
                    lastTrackId = this.getCurrentTrackId();
                    lastPosition = status.currentPosition;
                }
            });

            this.audio.addEventListener('durationchange', () => {
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_DURATION, this.getCurrentTrackStatus(this.lastState));
            });

            this.audio.addEventListener('seeking', () => {
                const status = this.getCurrentTrackStatus(this.lastState);
                this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_SEEK, status);
            });
        }
    }

    protected getCurrentTrackId() {
        return this.currentTrack?.trackId ?? 'INVALID';
    }

    protected getCurrentIndex() {
        return this.currentTrack ? this.playlistItems.indexOf(this.currentTrack) : -1;
    }

    protected getCurrentTrackStatus(currentState: string) {
        this.lastState = currentState;
        return {
            trackId: this.getCurrentTrackId(),
            isStream: !!this.currentTrack?.isStream,
            currentIndex: this.getCurrentIndex(),
            status: currentState,
            currentPosition: this.audio?.currentTime || 0,
            duration: this.audio?.duration || 0,
        };
    }

    protected async setCurrent(item: AudioTrack, position?: number, forceAutoplay: boolean = false) {
        let wasPlaying = false;
        if (this.audio) {
            wasPlaying = !this.audio.paused;
            await this.release();
        }
        await this.create();

        this.currentTrack = item;
        if (item.assetUrl.includes('.m3u8')) {
            await this.loadHlsJs();

            const hls = new Hls({
                autoStartLoad: true,
                debug: false,
                enableWorker: true,
            });
            this.hlsInstance = hls;
            hls.attachMedia(this.audio);
            hls.on(Hls.Events.MEDIA_ATTACHED, () => {
                hls.loadSource(item.assetUrl);
            });

        } else {
            this.audio!.src = item.assetUrl;
        }

        await this.registerHtmlListeners(position);

        this.updateStatus(RmxAudioStatusMessage.RMXSTATUS_TRACK_CHANGED, {
            currentItem: item
        })

        if (wasPlaying || forceAutoplay) {
            this.audio!.addEventListener('canplay', () => {
                this.play();
            });
        }
    }

    protected updateStatus(msgType: RmxAudioStatusMessage, value: any, trackId?: string) {
        this.notifyListeners('status', {
            action: 'status',
            status: {
                msgType: msgType,
                trackId: trackId ? trackId : this.getCurrentTrackId(),
                value: value
            }
        });
    }

    private hlsLoaded = false;

    protected loadHlsJs() {
        if (window.Hls !== undefined || this.hlsLoaded) {
            return Promise.resolve();
        }
        return new Promise(
            (resolve, reject) => {
                console.log("LOADING HLS FROM CDN");
                const script = document.createElement('script');
                script.type = 'text/javascript';
                script.src = 'https://cdn.jsdelivr.net/npm/hls.js@1.1.1';
                document.getElementsByTagName('head')[0].appendChild(script);
                script.onload = () => {
                    this.hlsLoaded = true;
                    resolve(void 0);
                };
                script.onerror = () => {
                    reject();
                };
            });
    }
}
