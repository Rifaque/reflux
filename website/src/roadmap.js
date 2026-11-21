// The public roadmap: docs/ROADMAP.md simplified for people using Reflux ("Public roadmap source"). It is changed in the
// same commit as that file. Rules: nothing is shown as done before it is in a downloadable build; no date is promised;
// later stages are marked as plans or exploration, never as commitments; F-003 (video haptics) is not shown until the
// owner has reviewed its research.

export const STATUS = {
  done: { label: 'Released', note: 'In a build you can download today.' },
  current: { label: 'In progress', note: 'Being finished and tested now.' },
  committed: { label: 'Committed', note: 'Decided; it will be in this version.' },
  planned: { label: 'Planned', note: 'Placed in this version; details can still change.' },
  exploring: { label: 'Exploring', note: 'A direction we are researching, not a promise.' },
}

/** Each stage: id (the anchor), version, theme, status, a sentence, and three to five things it brings. */
export const STAGES = [
  {
    id: 'alpha',
    version: 'Alpha',
    theme: 'The engine',
    status: 'done',
    summary: 'Reflux learned to find, understand and play a normal media folder, on five platforms at once.',
    items: [
      'Films and shows identified from ordinary file names',
      'Folders, Jellyfin, Plex, SMB and WebDAV in one library',
      'Play picks the copy this screen plays best',
      'Downloads for offline, on Android, Windows and Linux',
    ],
  },
  {
    id: 'beta-1',
    version: 'Beta 1',
    theme: 'Into testers’ hands',
    status: 'done',
    summary: 'The first build for people outside the project, through Google Play, Amazon and GitHub.',
    items: [
      'Profiles: everyone’s own history, favourites and accounts',
      'A lighter player with a mini-player and picture-in-picture',
      'Subtitles remembered for each profile',
      'Windows and Linux downloads with checksums',
    ],
  },
  {
    id: 'beta-2',
    version: 'Beta 2',
    theme: 'Controllers, typing and scenes',
    status: 'current',
    summary: 'Finished and being prepared for testers: the biggest beta yet, with no new features after it until 1.0.',
    // Shown instead once a Beta 2 publication record exists (src/released.js).
    summaryOut: 'Out now for testers: the biggest beta yet, and the last with new features before 1.0.',
    items: [
      'Scene previews on the timeline, and the right stick to move through them',
      'One consistent controller layout everywhere, with a quick view on X',
      'Reflux’s own keyboard for controllers and remotes',
      'A featured title chosen for you, not alphabetically',
    ],
  },
  {
    id: 'v1-0',
    version: '1.0',
    theme: 'Stable',
    status: 'committed',
    summary: 'No new features: proof. Reflux 1.0 ships when everything it does works well on every platform, checked on real devices.',
    items: [
      'Tested by beta testers on real TVs, phones, controllers and desktops',
      'Public listings on Google Play and the Amazon Appstore',
      'Signed builds that upgrade without losing anything',
      'Every known issue fixed or written down',
    ],
  },
  {
    id: 'v1-1',
    version: '1.1',
    theme: 'Your library, your way',
    status: 'planned',
    summary: 'Browse a large library the way you think about it.',
    items: [
      'Your server’s own libraries, beside Movies and Shows',
      'Sorting and filters: year, genre, watched, 4K and more',
      'A sidebar that folds away',
      'More varied, more personal rows on Home',
    ],
  },
  {
    id: 'v1-2',
    version: '1.2',
    theme: 'Playback depth',
    status: 'planned',
    summary: 'The player goes further, where it takes real equipment to get right.',
    items: [
      'Surround sound passed straight to your receiver',
      'Audio and downloads that carry on in the background on Android',
      'Media keys and system media controls on Windows and Linux',
      'Subtitle styles of your own',
    ],
  },
  {
    id: 'v1-3',
    version: '1.3',
    theme: 'More sources, easier installs',
    status: 'planned',
    summary: 'More of the media you have, and less work to install Reflux.',
    items: [
      'Emby and DLNA sources',
      'Plex Home users',
      'Linux packages, shaped by what beta testers need',
      'A check for new versions on desktop',
    ],
  },
  {
    id: 'v1-5',
    version: '1.5',
    theme: 'The household',
    status: 'exploring',
    summary: 'Reflux shared by everyone at home, set up the way each person likes.',
    items: [
      'A guest mode that leaves no trace',
      'What each profile sees, chosen per profile',
      'Controller buttons you can remap',
      'A mini-player on the TV',
    ],
  },
  {
    id: 'v2-0',
    version: '2.0',
    theme: 'Across your devices',
    status: 'exploring',
    summary: 'Your Reflux follows you from one device to the next, still with no account and no Reflux server.',
    items: [
      'Move a profile to a new device, encrypted, over your own network',
      'Start on one screen, carry on on another',
      'Smarter search and suggestions, never required for the basics',
      'Themes, and new platforms by evaluation',
    ],
  },
]
