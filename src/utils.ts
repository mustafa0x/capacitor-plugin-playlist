import { AudioTrack } from './interfaces';

/** Validate a list of tracks and omit invalid entries. */
export const validateTracks = (items: AudioTrack[]) =>
    Array.isArray(items)
        ? items.map(validateTrack).filter((track): track is AudioTrack => track !== null)
        : [];

/** Validate and normalize one track for playback. */
export const validateTrack = (track: AudioTrack) => {
    if (!track || typeof track.assetUrl !== 'string' || !track.assetUrl.trim()) {
        return null;
    }

    track.isStream = track.isStream === true;
    if (track.trackId !== undefined && track.trackId !== null) {
        track.trackId = String(track.trackId);
    }
    track.trackId = track.trackId || generateUUID();
    return track;
};

const generateUUID = () => {
    let seed = Date.now();
    if (typeof performance !== 'undefined' && typeof performance.now === 'function') {
        seed += performance.now();
    }

    return Array.from('xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx', (character) => {
        if (character === '-' || character === '4') {
            return character;
        }

        const random = ((seed + Math.random() * 16) % 16) | 0;
        seed = Math.floor(seed / 16);
        return (character === 'x' ? random : (random & 0x3) | 0x8).toString(16);
    }).join('');
};
