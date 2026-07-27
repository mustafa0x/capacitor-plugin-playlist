from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


path = Path("src/RmxAudioPlayer.ts")
text = path.read_text()

text = replace_once(
    text,
    """import { validateTrack, validateTracks } from './utils';


/*!
 * Module dependencies.
 */

""",
    """import { validateTrack, validateTracks } from './utils';

""",
    "module header",
)
text = replace_once(
    text,
    "    options: AudioPlayerOptions = {verbose: false, resetStreamOnPause: true};",
    "    options: AudioPlayerOptions = { verbose: false, resetStreamOnPause: true };",
    "options formatting",
)
text = replace_once(
    text,
    """    private _readyResolve: (value?: any | PromiseLike<boolean>) => void = () => {
    };
    private _readyReject: (reason?: any) => void = () => {
    };
""",
    """    private _readyResolve: (value?: any | PromiseLike<boolean>) => void = () => {};
    private _readyReject: (reason?: any) => void = () => {};
""",
    "ready callbacks",
)
text = replace_once(text, "    private _hasError: boolean = false;", "    private _hasError = false;", "error type")
text = replace_once(text, "    private _hasLoaded: boolean = false;", "    private _hasLoaded = false;", "loaded type")
text = replace_once(text, "     * If the playlist is currently playling a track.", "     * True if the playlist is currently playing a track.", "playing documentation")
text = replace_once(
    text,
    """    constructor() {
        this.handlers = {};
        new Promise<void>((resolve) => {
""",
    """    constructor() {
        new Promise<void>((resolve) => {
""",
    "duplicate handler initialization",
)
text = replace_once(
    text,
    """    ready = () => {
        return this._initPromise;
    };
""",
    "    ready = () => this._initPromise;\n",
    "ready delegation",
)
text = replace_once(
    text,
    """    setPlaylistItems = (items: AudioTrack[], options?: PlaylistItemOptions) => {
        return Playlist.setPlaylistItems({items: validateTracks(items), options: options || {}});
    };
""",
    """    setPlaylistItems = (items: AudioTrack[], options?: PlaylistItemOptions) =>
        Playlist.setPlaylistItems({items: validateTracks(items), options: options || {}});
""",
    "setPlaylistItems delegation",
)
text = replace_once(
    text,
    """    addAllItems = (items: AudioTrack[]) => {
        return Playlist.addAllItems({items: validateTracks(items)});
    };
""",
    "    addAllItems = (items: AudioTrack[]) => Playlist.addAllItems({items: validateTracks(items)});\n",
    "addAllItems delegation",
)
text = replace_once(
    text,
    """    clearAllItems = () => {
        return Playlist.clearAllItems();
    };
""",
    "    clearAllItems = () => Playlist.clearAllItems();\n",
    "clearAllItems delegation",
)
text = replace_once(
    text,
    """    play = () => {
        return Playlist.play();
    };
""",
    "    play = () => Playlist.play();\n",
    "play delegation",
)
text = replace_once(
    text,
    """    playTrackByIndex = (index: number, position?: number) => {
        return Playlist.playTrackByIndex({index, position: position || 0});
    };
""",
    """    playTrackByIndex = (index: number, position?: number) =>
        Playlist.playTrackByIndex({index, position: position || 0});
""",
    "playTrackByIndex delegation",
)
text = replace_once(
    text,
    """    playTrackById = (id: string, position?: number) => {
        return Playlist.playTrackById({id, position: position || 0});
    };
""",
    """    playTrackById = (id: string, position?: number) =>
        Playlist.playTrackById({id, position: position || 0});
""",
    "playTrackById delegation",
)
text = replace_once(
    text,
    "     * Play the track matching the given trackId. If the track does not exist, this has no effect.\n     */\n    selectTrackByIndex",
    "     * Select the track at the given index without starting playback. If the track does not exist, this has no effect.\n     */\n    selectTrackByIndex",
    "selectTrackByIndex documentation",
)
text = replace_once(
    text,
    """    selectTrackByIndex = (index: number, position?: number) => {
        return Playlist.selectTrackByIndex({index, position: position || 0});
    };
""",
    """    selectTrackByIndex = (index: number, position?: number) =>
        Playlist.selectTrackByIndex({index, position: position || 0});
""",
    "selectTrackByIndex delegation",
)
text = replace_once(
    text,
    "     * Play the track matching the given trackId. If the track does not exist, this has no effect.\n     */\n    selectTrackById",
    "     * Select the track matching the given trackId without starting playback. If it does not exist, this has no effect.\n     */\n    selectTrackById",
    "selectTrackById documentation",
)
text = replace_once(
    text,
    """    selectTrackById = (id: string, position?: number) => {
        return Playlist.selectTrackById({id, position: position || 0});
    };
""",
    """    selectTrackById = (id: string, position?: number) =>
        Playlist.selectTrackById({id, position: position || 0});
""",
    "selectTrackById delegation",
)
text = replace_once(
    text,
    """    pause = () => {
        return Playlist.pause();
    };
""",
    "    pause = () => Playlist.pause();\n",
    "pause delegation",
)
text = replace_once(
    text,
    """    skipForward = () => {
        return Playlist.skipForward();
    };
""",
    "    skipForward = () => Playlist.skipForward();\n",
    "skipForward delegation",
)
text = replace_once(
    text,
    """    skipBack = () => {
        return Playlist.skipBack();
    };
""",
    "    skipBack = () => Playlist.skipBack();\n",
    "skipBack delegation",
)
text = replace_once(
    text,
    """    seekTo = (position: number) => {
        return Playlist.seekTo({position});
    };
""",
    "    seekTo = (position: number) => Playlist.seekTo({position});\n",
    "seekTo delegation",
)
text = replace_once(
    text,
    "     * Set the playback speed; a float value between [-1, 1] inclusive. If set to 0, this pauses playback.",
    "     * Set the playback speed. A value of 0 pauses playback; 1 is normal speed.",
    "playback rate documentation",
)
text = replace_once(
    text,
    """    setPlaybackRate = (rate: number) => {
        return Playlist.setPlaybackRate({rate});
    };
""",
    "    setPlaybackRate = (rate: number) => Playlist.setPlaybackRate({rate});\n",
    "setPlaybackRate delegation",
)
text = replace_once(
    text,
    """    setVolume = (volume: number) => {
        return Playlist.setPlaybackVolume({volume});
    };
""",
    "    setVolume = (volume: number) => Playlist.setPlaybackVolume({volume});\n",
    "setVolume delegation",
)
text = replace_once(
    text,
    """    setLoop = (loop: boolean) => {
        return Playlist.setLoop({loop: loop});
    };
""",
    "    setLoop = (loop: boolean) => Playlist.setLoop({loop});\n",
    "setLoop delegation",
)
text = replace_once(
    text,
    "        const status = <OnStatusCallbackData> {msgType: type, trackId: trackId, value: value};",
    "        const status: OnStatusCallbackData = {msgType: type, trackId, value};",
    "status construction",
)
text = replace_once(
    text,
    "        if (itemStatusChangeTypes.indexOf(status.msgType) >= 0) {",
    "        if (itemStatusChangeTypes.includes(status.msgType)) {",
    "status membership",
)
text = replace_once(
    text,
    """                if (status.value && (<any> status.value).status) {
                    this._currentState = (<any> status.value).status;
                }
""",
    """                const nextState = (status.value as OnStatusCallbackUpdateData | undefined)?.status;
                if (nextState) {
                    this._currentState = nextState;
                }
""",
    "status state extraction",
)

path.write_text(text)
