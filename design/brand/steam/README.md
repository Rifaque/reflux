# Reflux artwork for Steam

Reflux isn't sold on Steam, but you can add it to your Steam library as a non-Steam game, which is useful on a TV with Big Picture mode or on a Steam Deck. Steam then shows it with only its name. These images make it look like everything else in your library.

| File | Where Steam shows it | Size |
| --- | --- | --- |
| `steam-grid-600x900.png` | The cover in your library's grid (portrait) | 600 × 900 |
| `steam-wide-920x430.png` | Recent games and Big Picture's shelves (landscape) | 920 × 430 |
| `steam-hero-3840x1240.png` | The banner across the top of Reflux's page | 3840 × 1240 |
| `steam-logo-1280x264.png` | The Reflux name over that banner (transparent) | 1280 × 264 |
| `steam-icon-256.png` | The small icon in lists and on the shortcut | 256 × 256 |

They are drawn from Reflux's own mark and wordmark (`../README.md`), by `tools/brand/make-steam-artwork.py`.

## Add Reflux to Steam

1. In Steam, choose **Games › Add a Non-Steam Game to My Library…**.
2. **Windows:** tick **Reflux** in the list (or **Browse…** to `Reflux.exe` where you installed it). **Linux:** choose **Browse…** and pick the `Reflux` file in the `bin` folder where you unpacked Reflux.
3. Choose **Add Selected Programs**.

## Add the artwork (Windows and Linux alike)

Do this in Steam's normal window (desktop mode on a Steam Deck). Big Picture mode and the Deck's game mode then show the same images.

1. **Cover:** in your library, right-click **Reflux › Manage › Set custom artwork**, and choose `steam-grid-600x900.png`.
2. **Banner and logo:** open Reflux's page in your library. Right-click the empty banner at the top and choose **Set Custom Background**, then pick `steam-hero-3840x1240.png`. Right-click it again, choose **Set Custom Logo**, and pick `steam-logo-1280x264.png`. If the logo sits badly, right-click it and choose **Adjust Logo Position**.
3. **Landscape image:** play Reflux once so it appears under **Recent Games** on your library's home. Right-click it there, choose **Manage › Set custom artwork**, and pick `steam-wide-920x430.png`.
4. **Icon:** right-click **Reflux › Properties…**. In **Shortcut**, click the empty square beside the name and pick `steam-icon-256.png`.

Steam's menus move from time to time. If an option isn't where these steps say, right-click the image you want to change: the option to replace it is usually there.

## Good to know

- The images are kept on this computer only. Steam doesn't copy custom artwork for non-Steam games to your other computers, so add them again on each one.
- Removing Reflux from Steam and adding it again gives it a new identity in Steam, and the images have to be set again.
- **Linux:** if you move the Reflux folder later, change the shortcut's **Target** in **Properties…** to the new `bin/Reflux`; the artwork stays.
