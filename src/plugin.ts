import { registerPlugin } from '@capacitor/core';

import type { PlaylistPlugin } from './definitions';

const Playlist = registerPlugin<PlaylistPlugin>('Playlist', {
    web: () => import('./web').then(({ PlaylistWeb }) => new PlaylistWeb()),
});

export { Playlist };
