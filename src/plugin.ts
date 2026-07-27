import { registerPlugin } from '@capacitor/core';

import type { PlaylistPlugin } from './definitions';

let web_implementation: Promise<PlaylistPlugin> | undefined;

const Playlist = registerPlugin<PlaylistPlugin>('Playlist', {
  web: () => (web_implementation ??= import('./web').then(({ PlaylistWeb }) => new PlaylistWeb())),
});

export { Playlist };
