import {registerPlugin} from '@capacitor/core';

import type {PlaylistPlugin} from './definitions';
import {validateTrack, validateTracks} from './utils';

// todo: find out why we get imported twice
let playListWebInstance: PlaylistPlugin;
const raw_playlist = registerPlugin<PlaylistPlugin>('Playlist', {
    web: () => import('./web').then(m => {
        if (!playListWebInstance) {
            playListWebInstance = new m.PlaylistWeb();
        }
        return playListWebInstance;
    }),
});

export const Playlist: PlaylistPlugin = {
    addListener: (eventName, listenerFunc) => raw_playlist.addListener(eventName, listenerFunc),
    setOptions: (options) => raw_playlist.setOptions(options),
    initialize: () => raw_playlist.initialize(),
    release: () => raw_playlist.release(),
    setPlaylistItems: (options) => raw_playlist.setPlaylistItems({
        ...options,
        items: validateTracks(options.items),
        options: options.options || {}
    }),
    addItem: (options) => {
        const item = validateTrack(options.item);
        return item ? raw_playlist.addItem({item}) : Promise.reject(new Error('Invalid track'))
    },
    addAllItems: (options) => raw_playlist.addAllItems({
        ...options,
        items: validateTracks(options.items)
    }),
    removeItem: (options) => raw_playlist.removeItem(options),
    removeItems: (options) => raw_playlist.removeItems(options),
    clearAllItems: () => raw_playlist.clearAllItems(),
    getPlaylist: () => raw_playlist.getPlaylist(),
    play: () => raw_playlist.play(),
    pause: () => raw_playlist.pause(),
    skipForward: () => raw_playlist.skipForward(),
    skipBack: () => raw_playlist.skipBack(),
    seekTo: (options) => raw_playlist.seekTo(options),
    playTrackByIndex: (options) => raw_playlist.playTrackByIndex(options),
    playTrackById: (options) => raw_playlist.playTrackById(options),
    selectTrackByIndex: (options) => raw_playlist.selectTrackByIndex(options),
    selectTrackById: (options) => raw_playlist.selectTrackById(options),
    setPlaybackVolume: (options) => raw_playlist.setPlaybackVolume(options),
    setLoop: (options) => raw_playlist.setLoop(options),
    setPlaybackRate: (options) => raw_playlist.setPlaybackRate(options)
}
